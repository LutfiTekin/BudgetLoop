package t.l.budget.loop.domain.onboarding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import t.l.budget.loop.data.db.BankAccountDao
import t.l.budget.loop.data.db.BankAccountEntity
import t.l.budget.loop.data.db.CreditCardDao
import t.l.budget.loop.data.db.CreditCardEntity
import t.l.budget.loop.data.db.SettingsDao
import t.l.budget.loop.data.db.SettingsEntity
import t.l.budget.loop.money.Money
import t.l.budget.loop.money.normalizeCurrencyCode
import t.l.budget.loop.ui.onboarding.BankDraft
import t.l.budget.loop.ui.onboarding.CardDraft
import t.l.budget.loop.ui.onboarding.OnboardingData

sealed class OnboardingSaveResult {
    data object Success : OnboardingSaveResult()
    data class Error(val message: String) : OnboardingSaveResult()
}

class OnboardingRepository(
    private val settingsDao: SettingsDao,
    private val bankAccountDao: BankAccountDao,
    private val creditCardDao: CreditCardDao,
) {
    suspend fun load(): OnboardingData? {
        return withContext(Dispatchers.Default) {
            val settings = settingsDao.get() ?: return@withContext null
            val cards = creditCardDao.list().map {
                CardDraft(
                    name = it.name,
                    limit = Money.of(it.limitMinor, it.currency).format(withCurrency = false),
                    debt = Money.of(it.debtMinor, it.currency).format(withCurrency = false),
                    currency = it.currency,
                )
            }
            val banks = bankAccountDao.list().map {
                BankDraft(
                    name = it.name,
                    balance = Money.of(it.balanceMinor, it.currency).format(withCurrency = false),
                    currency = it.currency,
                )
            }
            OnboardingData(
                currency = settings.defaultCurrency,
                dailyBudget = Money.of(settings.dailyBudgetMinor, settings.defaultCurrency)
                    .format(withCurrency = false),
                cards = cards,
                banks = banks,
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

            data.cards.forEach { card ->
                val cardCurrency = normalizeCurrencyCode(card.currency)
                val limitMinor = parseMinor(card.limit, cardCurrency)
                    ?: return@withContext OnboardingSaveResult.Error(
                        "Enter a valid limit for ${card.name}.",
                    )
                val debtMinor = parseMinor(card.debt, cardCurrency)
                    ?: return@withContext OnboardingSaveResult.Error(
                        "Enter a valid debt for ${card.name}.",
                    )

                creditCardDao.upsert(
                    CreditCardEntity(
                        name = card.name.trim(),
                        limitMinor = limitMinor,
                        debtMinor = debtMinor,
                        currency = cardCurrency,
                    ),
                )
            }

            data.banks.forEach { bank ->
                val bankCurrency = normalizeCurrencyCode(bank.currency)
                val balanceMinor = parseMinor(bank.balance, bankCurrency)
                    ?: return@withContext OnboardingSaveResult.Error(
                        "Enter a valid balance for ${bank.name}.",
                    )

                bankAccountDao.upsert(
                    BankAccountEntity(
                        name = bank.name.trim(),
                        balanceMinor = balanceMinor,
                        currency = bankCurrency,
                    ),
                )
            }

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
