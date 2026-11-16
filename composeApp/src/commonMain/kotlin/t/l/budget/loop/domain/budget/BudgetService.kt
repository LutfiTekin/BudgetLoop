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
    val cycleStart: LocalDate,
    val cycleEndInclusive: LocalDate,
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
        val resetDay = settings.cycleResetDay
        val cycle = engine.currentCycle(date, resetDay)
        val carryOverDates = savingsEntryDao.list()
            .asSequence()
            .filter { it.source == SavingsSource.CARRY_OVER }
            .map { it.date }
            .toSet()
        val effectiveStart = resolveCycleCalculationStart(
            cycleStart = cycle.start,
            date = date,
            timeZone = timeZone,
            loopStartDate = settings.loopStartDate,
        )
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

            carryMinor = if (engine.isLastDayOfMonth(currentDate, resetDay)) 0L else remainingMinor
            currentDate = currentDate.plus(DatePeriod(days = 1))
        }

        return latestState ?: error("Failed to compute daily budget state for $date.")
    }

    suspend fun applyEndOfDay(
        date: LocalDate,
        timeZone: TimeZone,
    ): MonthEndResult? {
        val settings = settingsDao.get() ?: error("Settings must be initialized before budgeting.")
        val state = ensureDailyState(date, timeZone)
        if (!engine.isLastDayOfMonth(date, settings.cycleResetDay)) {
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
     * Checks prior cycles back to the earliest recorded activity and finalizes any missing
     * cycle-end summaries so resets still apply even if background workers did not fire.
     */
    suspend fun finalizeMissedMonthEnds(
        today: LocalDate,
        timeZone: TimeZone,
    ): MonthEndResult? {
        val settings = settingsDao.get() ?: return null
        val resetDay = settings.cycleResetDay
        val currentCycle = engine.currentCycle(today, resetDay)
        val earliestCandidates = listOfNotNull(
            settings.loopStartDate,
            dailyBudgetStateDao.earliestDate(),
            transactionDao.earliestDate()?.toLocalDateTime(timeZone)?.date,
        )
        val earliest = earliestCandidates.minOrNull() ?: return null
        if (earliest >= currentCycle.start) return null

        var cursorCycle = engine.currentCycle(earliest, resetDay)
        var latestNewResult: MonthEndResult? = null
        while (cursorCycle.endInclusive < currentCycle.start) {
            val cycleEnd = cursorCycle.endInclusive
            val alreadyRecorded = savingsEntryDao.findBySourceAndDate(SavingsSource.MONTH_END, cycleEnd)
            if (alreadyRecorded == null) {
                val created = applyEndOfDay(cycleEnd, timeZone)
                if (created != null) {
                    latestNewResult = created
                }
            }
            cursorCycle = engine.currentCycle(cycleEnd.plus(DatePeriod(days = 1)), resetDay)
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
        val resetDay = settings.cycleResetDay
        val cycle = engine.currentCycle(today, resetDay)
        val endExclusive = cycle.endInclusive.plus(DatePeriod(days = 1))
        val spentInCycle = transactionDao.sumDefaultBetween(
            cycle.start.atStartOfDayIn(timeZone),
            endExclusive.atStartOfDayIn(timeZone),
        )
        val cycleCap = engine.effectiveMonthlyCapMinor(
            dailyBudgetMinor = settings.dailyBudgetMinor,
            customCapMinor = settings.monthlyCapMinor,
            date = today,
            resetDay = resetDay,
        )
        val streak = computeCleanStreakDays(today, cycle.start, settings.loopStartDate)
        val latestClosed = savingsEntryDao.latestBySource(SavingsSource.MONTH_END)?.let {
            MonthEndResult(date = it.date, savedMinor = it.amountMinor)
        }
        return LoopMonthStats(
            spentThisMonthMinor = spentInCycle,
            monthlyCapMinor = cycleCap,
            cleanStreakDays = streak,
            latestClosedMonthResult = latestClosed,
            cycleStart = cycle.start,
            cycleEndInclusive = cycle.endInclusive,
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

    private suspend fun resolveCycleCalculationStart(
        cycleStart: LocalDate,
        date: LocalDate,
        timeZone: TimeZone,
        loopStartDate: LocalDate?,
    ): LocalDate {
        if (loopStartDate == null || loopStartDate <= cycleStart || loopStartDate > date) {
            return cycleStart
        }
        val earliestTxnDate = transactionDao.earliestDate()?.toLocalDateTime(timeZone)?.date
        return if (earliestTxnDate != null && earliestTxnDate >= cycleStart && earliestTxnDate < loopStartDate) {
            earliestTxnDate
        } else {
            loopStartDate
        }
    }

    private suspend fun computeCleanStreakDays(
        today: LocalDate,
        cycleStart: LocalDate,
        loopStartDate: LocalDate?,
    ): Int {
        val floorDate = listOfNotNull(
            cycleStart,
            loopStartDate?.takeIf { it <= today },
        ).maxOrNull() ?: cycleStart
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
