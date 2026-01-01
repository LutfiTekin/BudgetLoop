package t.l.budget.loop.domain.budget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import t.l.budget.loop.data.db.DailyBudgetStateDao
import t.l.budget.loop.data.db.DailyBudgetStateEntity
import t.l.budget.loop.data.db.SavingsEntryDao
import t.l.budget.loop.data.db.SavingsEntryEntity
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
        val transactionDao = FakeTransactionDao(sumDefault = 0L)
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
    fun carryUsesPreviousRemainingWhenNotMonthEnd() = runTest {
        val settingsDao = FakeSettingsDao(
            SettingsEntity(
                defaultCurrency = "USD",
                timezone = "UTC",
                dailyBudgetMinor = 1000L,
                reportTimeMinutes = 1200,
            ),
        )
        val transactionDao = FakeTransactionDao(sumDefault = 0L)
        val dailyDao = FakeDailyBudgetStateDao()
        val savingsDao = FakeSavingsEntryDao()
        val service = BudgetService(settingsDao, transactionDao, dailyDao, savingsDao, BudgetEngine())

        val previousDate = LocalDate(2025, 3, 15)
        dailyDao.upsert(
            DailyBudgetStateEntity(
                date = previousDate,
                dailyBudgetMinor = 1000L,
                carryMinor = 0L,
                remainingMinor = 220L,
                monthlySavedMinor = 0L,
            ),
        )

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
        val transactionDao = FakeTransactionDao(sumDefault = 200L)
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
    private val sumDefault: Long,
) : TransactionDao {
    override suspend fun getById(id: Long): TransactionEntity? = null

    override suspend fun listByCard(cardId: Long): List<TransactionEntity> = emptyList()

    override suspend fun listBetween(start: Instant, endExclusive: Instant): List<TransactionEntity> = emptyList()

    override suspend fun sumDefaultBetween(start: Instant, endExclusive: Instant): Long = sumDefault

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

    override suspend fun upsert(state: DailyBudgetStateEntity) {
        states[state.date] = state
    }
}

private class FakeSavingsEntryDao : SavingsEntryDao {
    val entries = mutableListOf<SavingsEntryEntity>()

    override suspend fun list(): List<SavingsEntryEntity> = entries.toList()

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
}
