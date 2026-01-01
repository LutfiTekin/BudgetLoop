package t.l.budget.loop.platform

expect abstract class PlatformContext

expect class DailyResetScheduler(platformContext: PlatformContext) {
    fun schedule()
    fun cancel()
}

expect class DailyReportScheduler(platformContext: PlatformContext) {
    fun schedule(reportTimeMinutes: Int)
    fun cancel()
}
