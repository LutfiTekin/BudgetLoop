package t.l.budget.loop.platform

import androidx.compose.runtime.Composable
import t.l.budget.loop.data.db.BudgetLoopDatabase

@Composable
expect fun rememberPlatformContext(): PlatformContext

expect fun createDatabase(platformContext: PlatformContext): BudgetLoopDatabase
