package t.l.budget.loop.domain.budget

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import t.l.budget.loop.data.db.DailyBudgetStateDao
import t.l.budget.loop.data.db.DailyBudgetStateEntity
import t.l.budget.loop.data.db.SavingsEntryDao
import t.l.budget.loop.data.db.SavingsEntryEntity
import t.l.budget.loop.data.db.SavingsGoalDao
import t.l.budget.loop.data.db.SavingsSource
import t.l.budget.loop.data.db.SettingsDao
import t.l.budget.loop.data.db.TransactionDao
import t.l.budget.loop.domain.savings.SavingsGoalService
import t.l.budget.loop.money.Money

data class MonthEndResult(
    val date: LocalDate,
    val savedMinor: Long,
)

data class InfuseResult(
    val amount: Money,
    val creditedGoalName: String?,
)

data class LoopMonthStats(
    val spentThisMonthMinor: Long,
    val monthlyCapMinor: Long,
    val cleanStreakDays: Int,
    val latestClosedMonthResult: MonthEndResult?,
)

class BudgetService(
    private val settingsDao: SettingsDao,
    private val transactionDao: TransactionDao,
    private val dailyBudgetStateDao: DailyBudgetStateDao,
    private val savingsEntryDao: SavingsEntryDao,
    private val engine: BudgetEngine = BudgetEngine(),
    private val savingsGoalDao: SavingsGoalDao? = null,
) {
    private val goalService: SavingsGoalService? = savingsGoalDao?.let(::SavingsGoalService)

    suspend fun ensureDailyState(
        date: LocalDate,
        timeZone: TimeZone,
    ): DailyBudgetStateEntity {
        val settings = settingsDao.get() ?: error("Settings must be initialized before budgeting.")
        val carryOverDates = savingsEntryDao.list()
            .asSequence()
            .filter { it.source == SavingsSource.CARRY_OVER }
            .map { it.date }
            .toSet()
        val effectiveStart = resolveMonthCalculationStart(date, timeZone, settings.loopStartDate)
        var carryMinor = 0L
        var currentDate = effectiveStart
        var latestState: DailyBudgetStateEntity? = null

        while (currentDate <= date) {
            val carryBlocked = currentDate in carryOverDates
            val effectiveCarryMinor = if (carryBlocked) 0L else carryMinor
            val spentTodayMinor = spentForDate(currentDate, timeZone)
            val remainingMinor = engine.calculateDailyRemaining(
                dailyBudgetMinor = settings.dailyBudgetMinor,
                carryMinor = effectiveCarryMinor,
                spentTodayMinor = spentTodayMinor,
            )
            val existingState = dailyBudgetStateDao.getByDate(currentDate)
            val state = DailyBudgetStateEntity(
                date = currentDate,
                dailyBudgetMinor = settings.dailyBudgetMinor,
                carryMinor = effectiveCarryMinor,
                remainingMinor = remainingMinor,
                monthlySavedMinor = existingState?.monthlySavedMinor ?: 0L,
            )
            dailyBudgetStateDao.upsert(state)
            latestState = state

            carryMinor = if (engine.isLastDayOfMonth(currentDate)) 0L else remainingMinor
            currentDate = currentDate.plus(DatePeriod(days = 1))
        }

        return latestState ?: error("Failed to compute daily budget state for $date.")
    }

    suspend fun applyEndOfDay(
        date: LocalDate,
        timeZone: TimeZone,
    ): MonthEndResult? {
        val state = ensureDailyState(date, timeZone)
        if (!engine.isLastDayOfMonth(date)) {
            return null
        }

        val existingEntry = savingsEntryDao.findBySourceAndDate(SavingsSource.MONTH_END, date)
        if (existingEntry != null) {
            return MonthEndResult(date = date, savedMinor = existingEntry.amountMinor)
        }

        val savedMinor = engine.monthEndSaved(state.remainingMinor)
        savingsEntryDao.insert(
            SavingsEntryEntity(
                amountMinor = savedMinor,
                date = date,
                source = SavingsSource.MONTH_END,
                note = null,
            ),
        )
        dailyBudgetStateDao.upsert(state.copy(monthlySavedMinor = savedMinor))
        return MonthEndResult(date = date, savedMinor = savedMinor)
    }

    /**
     * Checks prior months back to the earliest recorded activity and finalizes any missing
     * month-end summaries so resets still apply even if background workers did not fire.
     */
    suspend fun finalizeMissedMonthEnds(
        today: LocalDate,
        timeZone: TimeZone,
    ): MonthEndResult? {
        val settings = settingsDao.get() ?: return null
        val startOfCurrentMonth = LocalDate(today.year, today.month, 1)
        val earliestCandidates = listOfNotNull(
            settings.loopStartDate,
            dailyBudgetStateDao.earliestDate(),
            transactionDao.earliestDate()?.toLocalDateTime(timeZone)?.date,
        )
        val earliest = earliestCandidates.minOrNull() ?: return null
        if (earliest >= startOfCurrentMonth) return null

        var monthCursor = LocalDate(earliest.year, earliest.month, 1)
        var latestNewResult: MonthEndResult? = null
        while (monthCursor < startOfCurrentMonth) {
            val monthEnd = engine.lastDayOfMonth(monthCursor)
            val alreadyRecorded = savingsEntryDao.findBySourceAndDate(SavingsSource.MONTH_END, monthEnd)
            if (alreadyRecorded == null) {
                val created = applyEndOfDay(monthEnd, timeZone)
                if (created != null) {
                    latestNewResult = created
                }
            }
            monthCursor = monthCursor.plus(DatePeriod(months = 1))
        }
        return latestNewResult
    }

    suspend fun infuseCarryOver(
        date: LocalDate,
        timeZone: TimeZone,
        preferredGoalId: Long? = null,
    ): InfuseResult {
        val settings = settingsDao.get() ?: error("Settings must be initialized before banking.")
        val state = ensureDailyState(date, timeZone)
        val carryMinor = state.carryMinor
        check(carryMinor > 0L) { "No carry-over available to bank." }

        val targetGoalId = goalService?.allocateSurplus(carryMinor, preferredGoalId = preferredGoalId)
        val creditedGoalName = if (targetGoalId != null) {
            savingsGoalDao?.getById(targetGoalId)?.name
        } else {
            null
        }

        savingsEntryDao.insert(
            SavingsEntryEntity(
                amountMinor = carryMinor,
                date = date,
                source = SavingsSource.CARRY_OVER,
                note = creditedGoalName,
                goalId = targetGoalId,
            ),
        )

        val spentTodayMinor = spentForDate(date, timeZone)
        val remainingMinor = engine.calculateDailyRemaining(
            dailyBudgetMinor = settings.dailyBudgetMinor,
            carryMinor = 0L,
            spentTodayMinor = spentTodayMinor,
        )
        dailyBudgetStateDao.upsert(
            state.copy(
                carryMinor = 0L,
                remainingMinor = remainingMinor,
            ),
        )

        return InfuseResult(
            amount = Money.of(carryMinor, settings.defaultCurrency),
            creditedGoalName = creditedGoalName,
        )
    }

    suspend fun bankCarryOver(
        date: LocalDate,
        timeZone: TimeZone,
    ): Money = infuseCarryOver(date, timeZone).amount

    suspend fun getLoopMonthStats(
        today: LocalDate,
        timeZone: TimeZone,
    ): LoopMonthStats {
        val settings = settingsDao.get() ?: error("Settings must be initialized.")
        val startOfMonth = LocalDate(today.year, today.month, 1)
        val startOfNextMonth = startOfMonth.plus(DatePeriod(months = 1))
        val spentThisMonth = transactionDao.sumDefaultBetween(
            startOfMonth.atStartOfDayIn(timeZone),
            startOfNextMonth.atStartOfDayIn(timeZone),
        )
        val monthlyCap = engine.effectiveMonthlyCapMinor(
            dailyBudgetMinor = settings.dailyBudgetMinor,
            customCapMinor = settings.monthlyCapMinor,
            date = today,
        )
        val streak = computeCleanStreakDays(today, timeZone, settings.loopStartDate)
        val latestClosed = savingsEntryDao.latestBySource(SavingsSource.MONTH_END)?.let {
            MonthEndResult(date = it.date, savedMinor = it.amountMinor)
        }
        return LoopMonthStats(
            spentThisMonthMinor = spentThisMonth,
            monthlyCapMinor = monthlyCap,
            cleanStreakDays = streak,
            latestClosedMonthResult = latestClosed,
        )
    }

    suspend fun spentForDate(date: LocalDate, timeZone: TimeZone): Long {
        val start = date.atStartOfDayIn(timeZone)
        val endExclusive = date.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone)
        return transactionDao.sumDefaultBetween(start, endExclusive)
    }

    suspend fun getTotalSaved(): Money {
        val totalMinor = savingsEntryDao.sumTotal()
        val settings = settingsDao.get() ?: error("Settings must be initialized.")
        return Money.of(totalMinor, settings.defaultCurrency)
    }

    private suspend fun resolveMonthCalculationStart(
        date: LocalDate,
        timeZone: TimeZone,
        loopStartDate: LocalDate?,
    ): LocalDate {
        val startOfMonth = LocalDate(date.year, date.month, 1)
        if (loopStartDate == null || loopStartDate <= startOfMonth || loopStartDate > date) {
            return startOfMonth
        }
        val earliestTxnDate = transactionDao.earliestDate()?.toLocalDateTime(timeZone)?.date
        return if (earliestTxnDate != null && earliestTxnDate >= startOfMonth && earliestTxnDate < loopStartDate) {
            earliestTxnDate
        } else {
            loopStartDate
        }
    }

    private suspend fun computeCleanStreakDays(
        today: LocalDate,
        timeZone: TimeZone,
        loopStartDate: LocalDate?,
    ): Int {
        val startOfMonth = LocalDate(today.year, today.month, 1)
        val floorDate = listOfNotNull(
            startOfMonth,
            loopStartDate?.takeIf { it <= today },
        ).maxOrNull() ?: startOfMonth
        var cursor = today
        var streak = 0
        while (cursor >= floorDate) {
            val state = dailyBudgetStateDao.getByDate(cursor) ?: break
            if (state.remainingMinor < 0L) break
            streak += 1
            cursor = cursor.minus(DatePeriod(days = 1))
        }
        return streak
    }
}
