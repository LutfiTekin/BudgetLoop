package t.l.budget.loop.domain.fx

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import t.l.budget.loop.data.db.FxRateDao
import t.l.budget.loop.data.db.FxRateEntity
import t.l.budget.loop.data.db.FxRateSource
import t.l.budget.loop.money.normalizeCurrencyCode

class ManualFxProvider(
    private val fxRateDao: FxRateDao,
    private val clock: Clock = Clock.System,
) : FxProvider {
    override suspend fun getRate(
        fromCurrency: String,
        toCurrency: String,
    ): FxRate? {
        val from = normalizeCurrencyCode(fromCurrency)
        val to = normalizeCurrencyCode(toCurrency)
        val entity = fxRateDao.getRate(from, to, FxRateSource.MANUAL) ?: return null
        return entity.toDomain()
    }

    suspend fun upsertRate(
        fromCurrency: String,
        toCurrency: String,
        rate: Double,
        asOf: Instant = clock.now(),
    ): FxRate {
        val from = normalizeCurrencyCode(fromCurrency)
        val to = normalizeCurrencyCode(toCurrency)
        val entity = FxRateEntity(
            fromCurrency = from,
            toCurrency = to,
            rate = rate,
            asOf = asOf,
            source = FxRateSource.MANUAL,
        )
        fxRateDao.upsert(entity)
        return entity.toDomain()
    }

    private fun FxRateEntity.toDomain(): FxRate {
        return FxRate(
            fromCurrency = fromCurrency,
            toCurrency = toCurrency,
            rate = rate,
            asOf = asOf,
            source = source,
        )
    }
}
