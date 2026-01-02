package t.l.budget.loop.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters

@ConstructedBy(BudgetLoopDatabaseConstructor::class)
@Database(
    entities = [
        SettingsEntity::class,
        TransactionEntity::class,
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
    abstract fun transactionDao(): TransactionDao
    abstract fun fxRateDao(): FxRateDao
    abstract fun dailyBudgetStateDao(): DailyBudgetStateDao
    abstract fun savingsEntryDao(): SavingsEntryDao
}

expect object BudgetLoopDatabaseConstructor : RoomDatabaseConstructor<BudgetLoopDatabase>