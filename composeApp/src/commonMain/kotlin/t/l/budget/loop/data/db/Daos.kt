package t.l.budget.loop.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

@Dao
interface SettingsDao {
    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun get(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: SettingsEntity)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    suspend fun listAll(): List<TransactionEntity>

    @Query(
        "SELECT * FROM transactions " +
            "WHERE date >= :start AND date < :endExclusive " +
            "ORDER BY date ASC",
    )
    suspend fun listBetween(
        start: Instant,
        endExclusive: Instant,
    ): List<TransactionEntity>

    @Query(
        "SELECT COALESCE(SUM(amountDefaultMinor), 0) FROM transactions " +
            "WHERE date >= :start AND date < :endExclusive",
    )
    suspend fun sumDefaultBetween(
        start: Instant,
        endExclusive: Instant,
    ): Long

    @Query("SELECT MIN(date) FROM transactions")
    suspend fun earliestDate(): Instant?

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Delete
    suspend fun delete(transaction: TransactionEntity)
}

@Dao
interface FxRateDao {
    @Query(
        "SELECT * FROM fx_rates " +
            "WHERE fromCurrency = :fromCurrency AND toCurrency = :toCurrency AND source = :source " +
            "ORDER BY asOf DESC LIMIT 1",
    )
    suspend fun getRate(
        fromCurrency: String,
        toCurrency: String,
        source: FxRateSource,
    ): FxRateEntity?

    @Query(
        "SELECT * FROM fx_rates " +
            "WHERE fromCurrency = :fromCurrency AND toCurrency = :toCurrency " +
            "ORDER BY asOf DESC LIMIT 1",
    )
    suspend fun getLatest(
        fromCurrency: String,
        toCurrency: String,
    ): FxRateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rate: FxRateEntity): Long
}

@Dao
interface DailyBudgetStateDao {
    @Query("SELECT * FROM daily_budget_state WHERE date = :date")
    suspend fun getByDate(date: LocalDate): DailyBudgetStateEntity?

    @Query("SELECT * FROM daily_budget_state ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(): DailyBudgetStateEntity?

    @Query("SELECT MIN(date) FROM daily_budget_state")
    suspend fun earliestDate(): LocalDate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: DailyBudgetStateEntity)
}

@Dao
interface SavingsEntryDao {
    @Query("SELECT * FROM savings_entries ORDER BY date DESC, id DESC")
    suspend fun list(): List<SavingsEntryEntity>

    @Query("SELECT * FROM savings_entries WHERE source = :source AND date = :date LIMIT 1")
    suspend fun findBySourceAndDate(source: SavingsSource, date: LocalDate): SavingsEntryEntity?

    @Query("SELECT * FROM savings_entries WHERE source = :source ORDER BY date DESC, id DESC LIMIT 1")
    suspend fun latestBySource(source: SavingsSource): SavingsEntryEntity?

    @Insert
    suspend fun insert(entry: SavingsEntryEntity): Long

    @Update
    suspend fun update(entry: SavingsEntryEntity)

    @Delete
    suspend fun delete(entry: SavingsEntryEntity)

    @Query("SELECT COALESCE(SUM(amountMinor), 0) FROM savings_entries")
    suspend fun sumTotal(): Long
}

@Dao
interface SavingsGoalDao {
    @Query("SELECT * FROM savings_goals WHERE archived = 0 ORDER BY sortOrder ASC, id ASC")
    suspend fun listActive(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals ORDER BY archived ASC, sortOrder ASC, id ASC")
    suspend fun listAll(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE id = :id")
    suspend fun getById(id: Long): SavingsGoalEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM savings_goals")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(goal: SavingsGoalEntity): Long

    @Update
    suspend fun update(goal: SavingsGoalEntity)

    @Delete
    suspend fun delete(goal: SavingsGoalEntity)
}
