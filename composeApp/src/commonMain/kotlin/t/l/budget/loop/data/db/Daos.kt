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
interface BankAccountDao {
    @Query("SELECT * FROM bank_accounts ORDER BY name")
    suspend fun list(): List<BankAccountEntity>

    @Query("SELECT * FROM bank_accounts WHERE id = :id")
    suspend fun getById(id: Long): BankAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(account: BankAccountEntity): Long

    @Delete
    suspend fun delete(account: BankAccountEntity)
}

@Dao
interface CreditCardDao {
    @Query("SELECT * FROM credit_cards ORDER BY name")
    suspend fun list(): List<CreditCardEntity>

    @Query("SELECT * FROM credit_cards WHERE id = :id")
    suspend fun getById(id: Long): CreditCardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: CreditCardEntity): Long

    @Delete
    suspend fun delete(card: CreditCardEntity)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE cardId = :cardId ORDER BY date DESC")
    suspend fun listByCard(cardId: Long): List<TransactionEntity>

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

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Delete
    suspend fun delete(transaction: TransactionEntity)
}

@Dao
interface BalanceAdjustmentDao {
    @Query(
        "SELECT * FROM balance_adjustments " +
            "WHERE accountType = :accountType AND accountId = :accountId " +
            "ORDER BY date DESC",
    )
    suspend fun listForAccount(
        accountType: AccountType,
        accountId: Long,
    ): List<BalanceAdjustmentEntity>

    @Insert
    suspend fun insert(adjustment: BalanceAdjustmentEntity): Long
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: DailyBudgetStateEntity)
}

@Dao
interface SavingsEntryDao {
    @Query("SELECT * FROM savings_entries ORDER BY date DESC")
    suspend fun list(): List<SavingsEntryEntity>

    @Insert
    suspend fun insert(entry: SavingsEntryEntity): Long

    @Update
    suspend fun update(entry: SavingsEntryEntity)

    @Delete
    suspend fun delete(entry: SavingsEntryEntity)
}
