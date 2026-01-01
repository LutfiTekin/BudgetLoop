package t.l.budget.loop.platform

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import java.util.Calendar

actual typealias PlatformContext = Context

actual class DailyResetScheduler actual constructor(
    private val platformContext: PlatformContext,
) {
    actual fun schedule() {
        val delayMillis = delayUntil(0, 0)
        val workRequest = PeriodicWorkRequestBuilder<DailyResetWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(platformContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest,
        )
    }

    actual fun cancel() {
        WorkManager.getInstance(platformContext).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        private const val WORK_NAME = "daily_reset"
    }
}

actual class DailyReportScheduler actual constructor(
    private val platformContext: PlatformContext,
) {
    actual fun schedule(reportTimeMinutes: Int) {
        val hour = reportTimeMinutes / 60
        val minute = reportTimeMinutes % 60
        val delayMillis = delayUntil(hour, minute)
        val workRequest = PeriodicWorkRequestBuilder<DailyReportWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(platformContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest,
        )
    }

    actual fun cancel() {
        WorkManager.getInstance(platformContext).cancelUniqueWork(WORK_NAME)
    }

    companion object {
        private const val WORK_NAME = "daily_report"
    }
}

class DailyResetWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return Result.success()
    }
}

class DailyReportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return Result.success()
    }
}

private fun delayUntil(hour: Int, minute: Int): Long {
    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= now) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }
    return calendar.timeInMillis - now
}
