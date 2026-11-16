package t.l.budget.loop.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import t.l.budget.loop.data.db.SettingsEntity
import t.l.budget.loop.domain.budget.BudgetEngine
import t.l.budget.loop.domain.onboarding.OnboardingRepository
import t.l.budget.loop.domain.onboarding.OnboardingSaveResult
import t.l.budget.loop.money.Money
import t.l.budget.loop.platform.DailyReportScheduler
import t.l.budget.loop.platform.DailyResetScheduler
import t.l.budget.loop.platform.PlatformContext
import t.l.budget.loop.ui.onboarding.OnboardingData
import kotlin.time.Clock

@Composable
internal fun SettingsScreen(
    settings: SettingsEntity?,
    repository: OnboardingRepository,
    platformContext: PlatformContext,
    onSettingsUpdated: suspend () -> Unit,
) {
    val initialCurrency = settings?.defaultCurrency ?: "USD"
    val initialDaily = settings?.let {
        Money.of(it.dailyBudgetMinor, it.defaultCurrency).format(withCurrency = false)
    }.orEmpty()
    val initialCap = settings?.monthlyCapMinor?.let {
        Money.of(it, initialCurrency).format(withCurrency = false)
    }.orEmpty()
    val initialReportMinutes = settings?.reportTimeMinutes ?: OnboardingRepository.DEFAULT_REPORT_TIME_MINUTES
    val initialResetDay = (settings?.cycleResetDay ?: 1).toString()

    var currency by remember(settings) { mutableStateOf(initialCurrency) }
    var dailyBudget by remember(settings) { mutableStateOf(initialDaily) }
    var cycleResetDay by remember(settings) { mutableStateOf(initialResetDay) }
    var useCustomMonthlyCap by remember(settings) { mutableStateOf(settings?.monthlyCapMinor != null) }
    var monthlyCap by remember(settings) { mutableStateOf(initialCap) }
    var reportHour by remember(settings) { mutableStateOf((initialReportMinutes / 60).toString().padStart(2, '0')) }
    var reportMinute by remember(settings) { mutableStateOf((initialReportMinutes % 60).toString().padStart(2, '0')) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val currentTimeZone = TimeZone.currentSystemDefault()
    val engine = remember { BudgetEngine() }
    val today = remember {
        Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
            .toLocalDateTime(currentTimeZone).date
    }
    val previewCycle = remember(cycleResetDay) {
        val day = cycleResetDay.toIntOrNull()?.takeIf { it in 1..31 } ?: (settings?.cycleResetDay ?: 1)
        engine.currentCycle(today, day)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .padding(bottom = 84.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Loop Settings", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "All loop limits and cycle dates are stored locally and update your loop immediately.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TextField(
            value = currency,
            onValueChange = { currency = it.uppercase().take(3) },
            label = { Text("Default Currency (ISO 3-letter)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        TextField(
            value = dailyBudget,
            onValueChange = { dailyBudget = it },
            label = { Text("Daily Loop Budget") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        TextField(
            value = cycleResetDay,
            onValueChange = { cycleResetDay = it.filter(Char::isDigit).take(2) },
            label = { Text("Monthly Cycle Reset Day (1-31)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Cycle window: ${previewCycle.start} -> ${previewCycle.endInclusive} (surplus closes after end date)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = useCustomMonthlyCap,
                onCheckedChange = { useCustomMonthlyCap = it },
            )
            Text(
                text = "Override cycle spending cap (otherwise Daily x days in cycle)",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        if (useCustomMonthlyCap) {
            TextField(
                value = monthlyCap,
                onValueChange = { monthlyCap = it },
                label = { Text("Custom Cycle Cap ($currency)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextField(
                value = reportHour,
                onValueChange = { reportHour = it.filter(Char::isDigit).take(2) },
                label = { Text("Report Hour (0-23)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextField(
                value = reportMinute,
                onValueChange = { reportMinute = it.filter(Char::isDigit).take(2) },
                label = { Text("Minute (0-59)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = "Active timezone: ${currentTimeZone.id}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val currentStatus = statusMessage
        if (currentStatus != null) {
            Text(
                text = currentStatus,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
            )
        }

        Button(
            onClick = {
                val parsedResetDay = cycleResetDay.toIntOrNull()
                if (parsedResetDay == null || parsedResetDay !in 1..31) {
                    isError = true
                    statusMessage = "Enter a monthly reset day between 1 and 31."
                    return@Button
                }
                val hour = reportHour.toIntOrNull()?.coerceIn(0, 23) ?: 20
                val minute = reportMinute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                val totalReportMinutes = hour * 60 + minute
                val capArgument = if (useCustomMonthlyCap) monthlyCap else ""
                if (useCustomMonthlyCap && monthlyCap.isBlank()) {
                    isError = true
                    statusMessage = "Enter a cycle cap or disable the override."
                    return@Button
                }
                scope.launch {
                    when (
                        val result = repository.save(
                            data = OnboardingData(currency = currency, dailyBudget = dailyBudget),
                            timezone = currentTimeZone,
                            reportTimeMinutes = totalReportMinutes,
                            monthlyCapInput = capArgument,
                            cycleResetDay = parsedResetDay,
                        )
                    ) {
                        is OnboardingSaveResult.Success -> {
                            DailyResetScheduler(platformContext).schedule()
                            DailyReportScheduler(platformContext).schedule(totalReportMinutes)
                            onSettingsUpdated()
                            isError = false
                            statusMessage = "Settings saved & cycle reset day updated."
                        }
                        is OnboardingSaveResult.Error -> {
                            isError = true
                            statusMessage = result.message
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Settings")
        }
    }
}
