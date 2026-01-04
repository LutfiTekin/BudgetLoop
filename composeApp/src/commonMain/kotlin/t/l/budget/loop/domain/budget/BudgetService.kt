package t.l.budget.loop.domain.budget

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import t.l.budget.loop.data.db.DailyBudgetStateDao
import t.l.budget.loop.data.db.DailyBudgetStateEntity
import t.l.budget.loop.data.db.SavingsEntryDao
import t.l.budget.loop.data.db.SavingsEntryEntity
import t.l.budget.loop.data.db.SavingsSource
import t.l.budget.loop.data.db.SettingsDao
import t.l.budget.loop.data.db.TransactionDao
import t.l.budget.loop.money.Money

data class MonthEndResult(
    val date: LocalDate,
    val savedMinor: Long,
)

class BudgetService(
    private val settingsDao: SettingsDao,
    private val transactionDao: TransactionDao,
    private val dailyBudgetStateDao: DailyBudgetStateDao,
    private val savingsEntryDao: SavingsEntryDao,
    private val engine: BudgetEngine = BudgetEngine(),
) {
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
        val startOfMonth = LocalDate(date.year, date.monthNumber, 1)
        var carryMinor = 0L
        var currentDate = startOfMonth
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

    suspend fun bankCarryOver(
        date: LocalDate,
        timeZone: TimeZone,
    ): Money {
        val settings = settingsDao.get() ?: error("Settings must be initialized before banking.")
        val state = ensureDailyState(date, timeZone)
        val carryMinor = state.carryMinor
        check(carryMinor > 0L) { "No carry-over available to bank." }

        savingsEntryDao.insert(
            SavingsEntryEntity(
                amountMinor = carryMinor,
                date = date,
                source = SavingsSource.CARRY_OVER,
                note = null,
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

        return Money.of(carryMinor, settings.defaultCurrency)
    }

    private suspend fun spentForDate(date: LocalDate, timeZone: TimeZone): Long {
        val start = date.atStartOfDayIn(timeZone)
        val endExclusive = date.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone)
        return transactionDao.sumDefaultBetween(start, endExclusive)
    }
}
