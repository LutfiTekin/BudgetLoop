package t.l.budget.loop.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

@ConstructedBy(BudgetLoopDatabaseConstructor::class)
@Database(
    entities = [
        SettingsEntity::class,
        TransactionEntity::class,
        FxRateEntity::class,
        DailyBudgetStateEntity::class,
        SavingsEntryEntity::class,
        SavingsGoalEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(RoomConverters::class)
abstract class BudgetLoopDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun transactionDao(): TransactionDao
    abstract fun fxRateDao(): FxRateDao
    abstract fun dailyBudgetStateDao(): DailyBudgetStateDao
    abstract fun savingsEntryDao(): SavingsEntryDao
    abstract fun savingsGoalDao(): SavingsGoalDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE settings ADD COLUMN monthlyCapMinor INTEGER")
                connection.execSQL("ALTER TABLE settings ADD COLUMN loopStartDate INTEGER")
                connection.execSQL("ALTER TABLE savings_entries ADD COLUMN goalId INTEGER")
                connection.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS savings_goals (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        targetMinor INTEGER NOT NULL,
                        allocatedMinor INTEGER NOT NULL DEFAULT 0,
                        kind TEXT NOT NULL,
                        targetDate INTEGER,
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        archived INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent(),
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_savings_goals_sortOrder ON savings_goals (sortOrder)",
                )
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_savings_goals_archived ON savings_goals (archived)",
                )
            }
        }
    }
}

expect object BudgetLoopDatabaseConstructor : RoomDatabaseConstructor<BudgetLoopDatabase>