package t.l.budget.loop.platform

actual class PlatformContext

actual class DailyResetScheduler actual constructor(
    private val platformContext: PlatformContext,
) {
    actual fun schedule() {
        // TODO: wire BGTaskScheduler registration and scheduling.
    }

    actual fun cancel() {
    }
}

actual class DailyReportScheduler actual constructor(
    private val platformContext: PlatformContext,
) {
    actual fun schedule(reportTimeMinutes: Int) {
        // TODO: wire BGTaskScheduler registration and scheduling.
    }

    actual fun cancel() {
    }
}
