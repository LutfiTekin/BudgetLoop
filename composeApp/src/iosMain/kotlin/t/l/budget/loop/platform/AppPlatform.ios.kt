package t.l.budget.loop.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import t.l.budget.loop.data.db.BudgetLoopDatabase
import t.l.budget.loop.data.db.BudgetLoopDatabaseConstructor

class IosPlatformContext : PlatformContext()

@Composable
actual fun rememberPlatformContext(): PlatformContext = remember { IosPlatformContext() }

actual fun createDatabase(platformContext: PlatformContext): BudgetLoopDatabase {
    return Room.databaseBuilder(
        name = DATABASE_NAME,
        factory = BudgetLoopDatabaseConstructor::initialize,
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(kotlinx.coroutines.Dispatchers.Default)
        .build()
}

private const val DATABASE_NAME = "budgetloop.db"
