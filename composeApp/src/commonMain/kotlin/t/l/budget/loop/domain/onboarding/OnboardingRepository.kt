package t.l.budget.loop.domain.onboarding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
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

    suspend fun save(
        data: OnboardingData,
        timezone: TimeZone,
        reportTimeMinutes: Int = DEFAULT_REPORT_TIME_MINUTES,
    ): OnboardingSaveResult {
        return withContext(Dispatchers.Default) {
            val currency = normalizeCurrencyCode(data.currency)
            val dailyBudgetMinor = parseMinor(data.dailyBudget, currency)
                ?: return@withContext OnboardingSaveResult.Error("Enter a valid daily budget amount.")

            settingsDao.upsert(
                SettingsEntity(
                    defaultCurrency = currency,
                    timezone = timezone.id,
                    dailyBudgetMinor = dailyBudgetMinor,
                    reportTimeMinutes = reportTimeMinutes,
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