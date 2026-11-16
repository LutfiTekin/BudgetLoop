package t.l.budget.loop.domain.onboarding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import t.l.budget.loop.data.db.SettingsDao
import t.l.budget.loop.data.db.SettingsEntity
import t.l.budget.loop.money.Money
import t.l.budget.loop.money.normalizeCurrencyCode
import t.l.budget.loop.ui.onboarding.OnboardingData

sealed class OnboardingSaveResult {
    data object Success : OnboardingSaveResult()
    data class Error(val message: String) : OnboardingSaveResult()
}

class OnboardingRepository(
    private val settingsDao: SettingsDao,
) {
    suspend fun load(): OnboardingData? {
        return withContext(Dispatchers.Default) {
            val settings = settingsDao.get() ?: return@withContext null
            OnboardingData(
                currency = settings.defaultCurrency,
                dailyBudget = Money.of(settings.dailyBudgetMinor, settings.defaultCurrency)
                    .format(withCurrency = false),
            )
        }
    }

    suspend fun loadSettings(): SettingsEntity? {
        return withContext(Dispatchers.Default) { settingsDao.get() }
    }

    suspend fun save(
        data: OnboardingData,
        timezone: TimeZone,
        reportTimeMinutes: Int = DEFAULT_REPORT_TIME_MINUTES,
        monthlyCapInput: String? = null,
        cycleResetDay: Int? = null,
    ): OnboardingSaveResult {
        return withContext(Dispatchers.Default) {
            val currency = runCatching { normalizeCurrencyCode(data.currency) }.getOrNull()
                ?: return@withContext OnboardingSaveResult.Error("Enter a valid 3-letter currency code.")
            val dailyBudgetMinor = parseMinor(data.dailyBudget, currency)
                ?: return@withContext OnboardingSaveResult.Error("Enter a valid daily budget amount.")
            if (dailyBudgetMinor <= 0L) {
                return@withContext OnboardingSaveResult.Error("Daily budget must be greater than zero.")
            }

            val parsedCapMinor = if (monthlyCapInput.isNullOrBlank()) {
                null
            } else {
                val cap = parseMinor(monthlyCapInput, currency)
                    ?: return@withContext OnboardingSaveResult.Error("Enter a valid monthly cap amount.")
                if (cap <= 0L) {
                    return@withContext OnboardingSaveResult.Error("Monthly cap must be greater than zero.")
                }
                cap
            }

            if (cycleResetDay != null && cycleResetDay !in 1..31) {
                return@withContext OnboardingSaveResult.Error("Reset day must be between 1 and 31.")
            }

            val existing = settingsDao.get()
            val today = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
                .toLocalDateTime(timezone).date
            val loopStartDate: LocalDate = existing?.loopStartDate ?: today
            val resolvedResetDay = cycleResetDay ?: existing?.cycleResetDay ?: 1

            settingsDao.upsert(
                SettingsEntity(
                    defaultCurrency = currency,
                    timezone = timezone.id,
                    dailyBudgetMinor = dailyBudgetMinor,
                    reportTimeMinutes = reportTimeMinutes.coerceIn(0, 23 * 60 + 59),
                    monthlyCapMinor = parsedCapMinor ?: existing?.monthlyCapMinor?.takeIf { monthlyCapInput == null },
                    loopStartDate = loopStartDate,
                    cycleResetDay = resolvedResetDay,
                ),
            )

            OnboardingSaveResult.Success
        }
    }

    private fun parseMinor(amount: String, currency: String): Long? {
        val cleaned = amount.trim().replace(",", ".")
        val value = cleaned.toDoubleOrNull() ?: return null
        return Money.fromDecimal(value, currency).minor
    }

    companion object {
        const val DEFAULT_REPORT_TIME_MINUTES = 20 * 60
    }
}