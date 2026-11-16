package t.l.budget.loop.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.room.Room
import t.l.budget.loop.data.db.BudgetLoopDatabase

@Composable
actual fun rememberPlatformContext(): PlatformContext = LocalContext.current.applicationContext

actual fun createDatabase(platformContext: PlatformContext): BudgetLoopDatabase {
    return Room.databaseBuilder(
        platformContext,
        BudgetLoopDatabase::class.java,
        DATABASE_NAME,
    )
        .addMigrations(BudgetLoopDatabase.MIGRATION_1_2)
        .build()
}

private const val DATABASE_NAME = "budgetloop.db"
