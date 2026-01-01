package t.l.budget.loop.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        SettingsEntity::class,
        BankAccountEntity::class,
        CreditCardEntity::class,
        TransactionEntity::class,
        BalanceAdjustmentEntity::class,
        FxRateEntity::class,
        DailyBudgetStateEntity::class,
        SavingsEntryEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class BudgetLoopDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun bankAccountDao(): BankAccountDao
    abstract fun creditCardDao(): CreditCardDao
    abstract fun transactionDao(): TransactionDao
    abstract fun balanceAdjustmentDao(): BalanceAdjustmentDao
    abstract fun fxRateDao(): FxRateDao
    abstract fun dailyBudgetStateDao(): DailyBudgetStateDao
    abstract fun savingsEntryDao(): SavingsEntryDao
}
