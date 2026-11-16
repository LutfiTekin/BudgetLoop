package t.l.budget.loop.platform

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import t.l.budget.loop.domain.budget.BudgetService
import t.l.budget.loop.money.Money
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.time.Clock

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
        val normalized = reportTimeMinutes.coerceIn(0, 23 * 60 + 59)
        val hour = normalized / 60
        val minute = normalized % 60
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
        val db = createDatabase(applicationContext)
        return try {
            val settings = db.settingsDao().get() ?: return Result.success()
            val tz = runCatching { TimeZone.of(settings.timezone) }.getOrDefault(TimeZone.currentSystemDefault())
            val today = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
                .toLocalDateTime(tz).date
            val service = BudgetService(
                settingsDao = db.settingsDao(),
                transactionDao = db.transactionDao(),
                dailyBudgetStateDao = db.dailyBudgetStateDao(),
                savingsEntryDao = db.savingsEntryDao(),
                savingsGoalDao = db.savingsGoalDao(),
            )
            service.finalizeMissedMonthEnds(today, tz)
            service.ensureDailyState(today, tz)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            db.close()
        }
    }
}

class DailyReportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val db = createDatabase(applicationContext)
        return try {
            val summaryText = run {
                val settings = db.settingsDao().get() ?: return@run "Open BudgetLoop to review today's loop."
                val tz = runCatching { TimeZone.of(settings.timezone) }.getOrDefault(TimeZone.currentSystemDefault())
                val today = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
                    .toLocalDateTime(tz).date
                val service = BudgetService(
                    settingsDao = db.settingsDao(),
                    transactionDao = db.transactionDao(),
                    dailyBudgetStateDao = db.dailyBudgetStateDao(),
                    savingsEntryDao = db.savingsEntryDao(),
                    savingsGoalDao = db.savingsGoalDao(),
                )
                val state = service.ensureDailyState(today, tz)
                val stats = service.getLoopMonthStats(today, tz)
                val remText = Money.of(state.remainingMinor, settings.defaultCurrency).format()
                val spentText = Money.of(stats.spentThisMonthMinor, settings.defaultCurrency).format()
                val capText = Money.of(stats.monthlyCapMinor, settings.defaultCurrency).format()
                "Remaining: $remText · Month: $spentText / $capText · Streak: ${stats.cleanStreakDays}d"
            }
            showDailyReportNotification(applicationContext, summaryText)
            Result.success()
        } catch (_: Exception) {
            Result.success()
        } finally {
            db.close()
        }
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

private fun showDailyReportNotification(context: Context, body: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
    }
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "daily_report"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Daily Loop Reports",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }
    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("Daily Loop Status")
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setAutoCancel(true)
        .build()
    manager.notify(1001, notification)
}
