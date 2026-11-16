package t.l.budget.loop.domain.budget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import t.l.budget.loop.data.db.DailyBudgetStateDao
import t.l.budget.loop.data.db.DailyBudgetStateEntity
import t.l.budget.loop.data.db.GoalKind
import t.l.budget.loop.data.db.SavingsEntryDao
import t.l.budget.loop.data.db.SavingsEntryEntity
import t.l.budget.loop.data.db.SavingsGoalDao
import t.l.budget.loop.data.db.SavingsGoalEntity
import t.l.budget.loop.data.db.SavingsSource
import t.l.budget.loop.data.db.SettingsDao
import t.l.budget.loop.data.db.SettingsEntity
import t.l.budget.loop.data.db.TransactionDao
import t.l.budget.loop.data.db.TransactionEntity

class BudgetServiceTest {
    @Test
    fun carryResetsAfterMonthEnd() = runTest {
        val settingsDao = FakeSettingsDao(
            SettingsEntity(
                defaultCurrency = "USD",
                timezone = "UTC",
                dailyBudgetMinor = 1000L,
                reportTimeMinutes = 1200,
            ),
        )
        val transactionDao = FakeTransactionDao { _, _ -> 0L }
        val dailyDao = FakeDailyBudgetStateDao()
        val savingsDao = FakeSavingsEntryDao()
        val service = BudgetService(settingsDao, transactionDao, dailyDao, savingsDao, BudgetEngine())

        val previousDate = LocalDate(2025, 2, 28)
        dailyDao.upsert(
            DailyBudgetStateEntity(
                date = previousDate,
                dailyBudgetMinor = 1000L,
                carryMinor = 0L,
                remainingMinor = 300L,
                monthlySavedMinor = 0L,
            ),
        )

        val state = service.ensureDailyState(LocalDate(2025, 3, 1), TimeZone.UTC)
        assertEquals(0L, state.carryMinor)
    }

    @Test
    fun carryBackfillsMissingDays() = runTest {
        val settingsDao = FakeSettingsDao(
            SettingsEntity(
                defaultCurrency = "USD",
                timezone = "UTC",
                dailyBudgetMinor = 1000L,
                reportTimeMinutes = 1200,
            ),
        )
        val transactionDao = FakeTransactionDao { start, _ ->
            val date = start.toLocalDateTime(TimeZone.UTC).date
            when {
                date.dayOfMonth == 15 -> 780L
                date.dayOfMonth in 1..14 -> 1000L
                else -> 0L
            }
        }
        val dailyDao = FakeDailyBudgetStateDao()
        val savingsDao = FakeSavingsEntryDao()
        val service = BudgetService(settingsDao, transactionDao, dailyDao, savingsDao, BudgetEngine())

        val state = service.ensureDailyState(LocalDate(2025, 3, 16), TimeZone.UTC)
        assertEquals(220L, state.carryMinor)
    }

    @Test
    fun monthEndCreatesSavingsEntry() = runTest {
        val settingsDao = FakeSettingsDao(
            SettingsEntity(
                defaultCurrency = "USD",
                timezone = "UTC",
                dailyBudgetMinor = 1000L,
                reportTimeMinutes = 1200,
            ),
        )
        val transactionDao = FakeTransactionDao { start, _ ->
            val date = start.toLocalDateTime(TimeZone.UTC).date
            if (date.dayOfMonth == 31) 200L else 1000L
        }
        val dailyDao = FakeDailyBudgetStateDao()
        val savingsDao = FakeSavingsEntryDao()
        val service = BudgetService(settingsDao, transactionDao, dailyDao, savingsDao, BudgetEngine())

        val result = service.applyEndOfDay(LocalDate(2025, 1, 31), TimeZone.UTC)
        assertEquals(800L, result?.savedMinor)
        assertEquals(1, savingsDao.entries.size)
        assertEquals(800L, savingsDao.entries.first().amountMinor)
        val savedState = dailyDao.getByDate(LocalDate(2025, 1, 31))
        assertEquals(800L, savedState?.monthlySavedMinor)
    }

    @Test
    fun ensureDailyStateRefreshesRemaining() = runTest {
        val settingsDao = FakeSettingsDao(
            SettingsEntity(
                defaultCurrency = "USD",
                timezone = "UTC",
                dailyBudgetMinor = 1000L,
                reportTimeMinutes = 1200,
            ),
        )
        val date = LocalDate(2025, 4, 10)
        var spentOnDate = 120L
        val transactionDao = FakeTransactionDao { start, _ ->
            val currentDate = start.toLocalDateTime(TimeZone.UTC).date
            when {
                currentDate < date -> 1000L
                currentDate == date -> spentOnDate
                else -> 0L
            }
        }
        val dailyDao = FakeDailyBudgetStateDao()
        val savingsDao = FakeSavingsEntryDao()
        val service = BudgetService(settingsDao, transactionDao, dailyDao, savingsDao, BudgetEngine())

        val first = service.ensureDailyState(date, TimeZone.UTC)
        assertEquals(880L, first.remainingMinor)

        spentOnDate = 350L
        val refreshed = service.ensureDailyState(date, TimeZone.UTC)
        assertEquals(650L, refreshed.remainingMinor)
    }

    @Test
    fun ensureDailyStatePreservesMonthEndSaved() = runTest {
        val settingsDao = FakeSettingsDao(
            SettingsEntity(
                defaultCurrency = "USD",
                timezone = "UTC",
                dailyBudgetMinor = 1000L,
                reportTimeMinutes = 1200,
            ),
        )
        val transactionDao = FakeTransactionDao { _, _ -> 1000L }
        val dailyDao = FakeDailyBudgetStateDao()
        val savingsDao = FakeSavingsEntryDao()
        val service = BudgetService(settingsDao, transactionDao, dailyDao, savingsDao, BudgetEngine())

        val date = LocalDate(2025, 1, 31)
        dailyDao.upsert(
            DailyBudgetStateEntity(
                date = date,
                dailyBudgetMinor = 1000L,
                carryMinor = 0L,
                remainingMinor = 1000L,
                monthlySavedMinor = 500L,
            ),
        )

        val refreshed = service.ensureDailyState(date, TimeZone.UTC)
        assertEquals(500L, refreshed.monthlySavedMinor)
    }

    @Test
    fun loopStartDatePreventsPhantomPreOnboardingCarry() = runTest {
        val startDate = LocalDate(2026, 11, 16)
        val settingsDao = FakeSettingsDao(
            SettingsEntity(
                defaultCurrency = "EUR",
                timezone = "UTC",
                dailyBudgetMinor = 1800L,
                reportTimeMinutes = 1200,
                monthlyCapMinor = 54_800L,
                loopStartDate = startDate,
            ),
        )
        val transactionDao = FakeTransactionDao { _, _ -> 0L }
        val dailyDao = FakeDailyBudgetStateDao()
        val savingsDao = FakeSavingsEntryDao()
        val service = BudgetService(settingsDao, transactionDao, dailyDao, savingsDao, BudgetEngine())

        val state = service.ensureDailyState(startDate, TimeZone.UTC)
        assertEquals(0L, state.carryMinor)
        assertEquals(1800L, state.remainingMinor)
    }

    @Test
    fun infuseCarryOverCreditsGoalsWithCascadeOverflow() = runTest {
        val startDate = LocalDate(2026, 11, 15)
        val settingsDao = FakeSettingsDao(
            SettingsEntity(
                defaultCurrency = "EUR",
                timezone = "UTC",
                dailyBudgetMinor = 1800L,
                reportTimeMinutes = 1200,
                loopStartDate = startDate,
            ),
        )
        val transactionDao = FakeTransactionDao { start, _ ->
            val date = start.toLocalDateTime(TimeZone.UTC).date
            if (date == startDate) 1000L else 0L
        }
        val dailyDao = FakeDailyBudgetStateDao()
        val savingsDao = FakeSavingsEntryDao()
        val goalDao = FakeSavingsGoalDao(
            mutableListOf(
                SavingsGoalEntity(id = 1L, name = "Card Paydown", targetMinor = 500L, allocatedMinor = 0L, kind = GoalKind.DEBT_PAYDOWN, sortOrder = 0),
                SavingsGoalEntity(id = 2L, name = "Move Fund", targetMinor = 5000L, allocatedMinor = 0L, kind = GoalKind.SAVINGS, sortOrder = 1),
            ),
        )
        val service = BudgetService(
            settingsDao, transactionDao, dailyDao, savingsDao, BudgetEngine(), goalDao,
        )

        val infused = service.infuseCarryOver(LocalDate(2026, 11, 16), TimeZone.UTC)
        assertEquals(800L, infused.amount.minor)
        assertEquals("Card Paydown", infused.creditedGoalName)
        assertEquals(500L, goalDao.getById(1L)?.allocatedMinor)
        assertEquals(300L, goalDao.getById(2L)?.allocatedMinor)
        assertEquals(1800L, dailyDao.getByDate(LocalDate(2026, 11, 16))?.remainingMinor)
    }

    @Test
    fun finalizeMissedMonthEndsClosesPriorMonthOnce() = runTest {
        val settingsDao = FakeSettingsDao(
            SettingsEntity(
                defaultCurrency = "EUR",
                timezone = "UTC",
                dailyBudgetMinor = 1800L,
                reportTimeMinutes = 1200,
                loopStartDate = LocalDate(2026, 10, 30),
            ),
        )
        val transactionDao = FakeTransactionDao { start, _ ->
            val d = start.toLocalDateTime(TimeZone.UTC).date
            if (d == LocalDate(2026, 10, 30)) 1000L else 1800L
        }
        val dailyDao = FakeDailyBudgetStateDao()
        val savingsDao = FakeSavingsEntryDao()
        val service = BudgetService(settingsDao, transactionDao, dailyDao, savingsDao, BudgetEngine())

        val firstClose = service.finalizeMissedMonthEnds(LocalDate(2026, 11, 5), TimeZone.UTC)
        assertNotNull(firstClose)
        assertEquals(LocalDate(2026, 10, 31), firstClose.date)
        assertEquals(800L, firstClose.savedMinor)

        val secondClose = service.finalizeMissedMonthEnds(LocalDate(2026, 11, 5), TimeZone.UTC)
        assertNull(secondClose)
        assertEquals(1, savingsDao.entries.count { it.source == SavingsSource.MONTH_END })
    }

    @Test
    fun loopMonthStatsComputesSpendCustomCapAndStreak() = runTest {
        val startDate = LocalDate(2026, 11, 14)
        val settingsDao = FakeSettingsDao(
            SettingsEntity(
                defaultCurrency = "EUR",
                timezone = "UTC",
                dailyBudgetMinor = 1800L,
                reportTimeMinutes = 1200,
                monthlyCapMinor = 54_800L,
                loopStartDate = startDate,
            ),
        )
        val txInstant = LocalDate(2026, 11, 15).atStartOfDayIn(TimeZone.UTC)
        val transactionDao = FakeTransactionDao(
            earliest = txInstant,
            sumDefaultProvider = { start, end ->
                val s = start.toLocalDateTime(TimeZone.UTC).date
                val e = end.toLocalDateTime(TimeZone.UTC).date
                if (LocalDate(2026, 11, 15) >= s && LocalDate(2026, 11, 15) < e) 1500L else 0L
            },
        )
        val dailyDao = FakeDailyBudgetStateDao()
        val savingsDao = FakeSavingsEntryDao()
        val service = BudgetService(settingsDao, transactionDao, dailyDao, savingsDao, BudgetEngine())

        val today = LocalDate(2026, 11, 16)
        service.ensureDailyState(today, TimeZone.UTC)
        val stats = service.getLoopMonthStats(today, TimeZone.UTC)
        assertEquals(1500L, stats.spentThisMonthMinor)
        assertEquals(54_800L, stats.monthlyCapMinor)
        assertEquals(3, stats.cleanStreakDays)
    }

    @Test
    fun customResetDayRollsAcrossCalendarMonthAndClosesOnCycleEnd() = runTest {
        val settingsDao = FakeSettingsDao(
            SettingsEntity(
                defaultCurrency = "EUR",
                timezone = "UTC",
                dailyBudgetMinor = 1800L,
                reportTimeMinutes = 1200,
                monthlyCapMinor = 54_800L,
                loopStartDate = LocalDate(2026, 11, 14),
                cycleResetDay = 14,
            ),
        )
        val transactionDao = FakeTransactionDao { start, _ ->
            val d = start.toLocalDateTime(TimeZone.UTC).date
            if (d == LocalDate(2026, 11, 30)) 1000L else 1800L
        }
        val dailyDao = FakeDailyBudgetStateDao()
        val savingsDao = FakeSavingsEntryDao()
        val service = BudgetService(settingsDao, transactionDao, dailyDao, savingsDao, BudgetEngine())

        // On calendar month end (Nov 30), surplus MUST continue carrying over under resetDay = 14
        assertNull(service.applyEndOfDay(LocalDate(2026, 11, 30), TimeZone.UTC))
        val dec1State = service.ensureDailyState(LocalDate(2026, 12, 1), TimeZone.UTC)
        assertEquals(800L, dec1State.carryMinor)

        // On cycle end (Dec 13), cycle closes and Dec 14 starts fresh at 0 carry
        val closed = service.applyEndOfDay(LocalDate(2026, 12, 13), TimeZone.UTC)
        assertNotNull(closed)
        assertEquals(LocalDate(2026, 12, 13), closed.date)
        assertEquals(800L, closed.savedMinor)

        val nextCycleDay1 = service.ensureDailyState(LocalDate(2026, 12, 14), TimeZone.UTC)
        assertEquals(0L, nextCycleDay1.carryMinor)

        // Missed cycle close on Dec 13 finalizes automatically if checked on Dec 15
        val savingsDao2 = FakeSavingsEntryDao()
        val dailyDao2 = FakeDailyBudgetStateDao()
        val service2 = BudgetService(settingsDao, transactionDao, dailyDao2, savingsDao2, BudgetEngine())
        val missed = service2.finalizeMissedMonthEnds(LocalDate(2026, 12, 15), TimeZone.UTC)
        assertNotNull(missed)
        assertEquals(LocalDate(2026, 12, 13), missed.date)
        assertEquals(800L, missed.savedMinor)
    }
}

private class FakeSettingsDao(
    private var settings: SettingsEntity?,
) : SettingsDao {
    override suspend fun get(): SettingsEntity? = settings

    override suspend fun upsert(settings: SettingsEntity) {
        this.settings = settings
    }
}

private class FakeTransactionDao(
    private val earliest: Instant? = null,
    private val sumDefaultProvider: (Instant, Instant) -> Long,
) : TransactionDao {
    override suspend fun getById(id: Long): TransactionEntity? = null

    override suspend fun listAll(): List<TransactionEntity> = emptyList()

    override suspend fun listBetween(start: Instant, endExclusive: Instant): List<TransactionEntity> = emptyList()

    override suspend fun sumDefaultBetween(start: Instant, endExclusive: Instant): Long {
        return sumDefaultProvider(start, endExclusive)
    }

    override suspend fun earliestDate(): Instant? = earliest

    override suspend fun insert(transaction: TransactionEntity): Long = 1L

    override suspend fun delete(transaction: TransactionEntity) {
    }
}

private class FakeDailyBudgetStateDao : DailyBudgetStateDao {
    private val states = mutableMapOf<LocalDate, DailyBudgetStateEntity>()

    override suspend fun getByDate(date: LocalDate): DailyBudgetStateEntity? = states[date]

    override suspend fun getLatest(): DailyBudgetStateEntity? {
        return states.values.maxByOrNull { it.date }
    }

    override suspend fun earliestDate(): LocalDate? = states.keys.minOrNull()

    override suspend fun upsert(state: DailyBudgetStateEntity) {
        states[state.date] = state
    }
}

private class FakeSavingsEntryDao : SavingsEntryDao {
    val entries = mutableListOf<SavingsEntryEntity>()

    override suspend fun list(): List<SavingsEntryEntity> = entries.toList()

    override suspend fun findBySourceAndDate(source: SavingsSource, date: LocalDate): SavingsEntryEntity? {
        return entries.firstOrNull { it.source == source && it.date == date }
    }

    override suspend fun latestBySource(source: SavingsSource): SavingsEntryEntity? {
        return entries.filter { it.source == source }.maxByOrNull { it.date }
    }

    override suspend fun insert(entry: SavingsEntryEntity): Long {
        entries.add(entry)
        return entries.size.toLong()
    }

    override suspend fun update(entry: SavingsEntryEntity) {
        val index = entries.indexOfFirst { it.id == entry.id }
        if (index >= 0) {
            entries[index] = entry
        }
    }

    override suspend fun delete(entry: SavingsEntryEntity) {
        entries.remove(entry)
    }

    override suspend fun sumTotal(): Long = entries.sumOf { it.amountMinor }
}

private class FakeSavingsGoalDao(
    private val items: MutableList<SavingsGoalEntity> = mutableListOf(),
) : SavingsGoalDao {
    override suspend fun listActive(): List<SavingsGoalEntity> =
        items.filter { !it.archived }.sortedWith(compareBy({ it.sortOrder }, { it.id }))

    override suspend fun listAll(): List<SavingsGoalEntity> =
        items.sortedWith(compareBy({ it.archived }, { it.sortOrder }, { it.id }))

    override suspend fun getById(id: Long): SavingsGoalEntity? = items.firstOrNull { it.id == id }

    override suspend fun nextSortOrder(): Int = (items.maxOfOrNull { it.sortOrder } ?: -1) + 1

    override suspend fun insert(goal: SavingsGoalEntity): Long {
        val nextId = (items.maxOfOrNull { it.id } ?: 0L) + 1L
        items.add(goal.copy(id = nextId))
        return nextId
    }

    override suspend fun update(goal: SavingsGoalEntity) {
        val idx = items.indexOfFirst { it.id == goal.id }
        if (idx >= 0) items[idx] = goal
    }

    override suspend fun delete(goal: SavingsGoalEntity) {
        items.removeAll { it.id == goal.id }
    }
}
