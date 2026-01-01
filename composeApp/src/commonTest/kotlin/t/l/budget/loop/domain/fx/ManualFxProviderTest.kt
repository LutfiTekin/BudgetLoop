package t.l.budget.loop.domain.fx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import t.l.budget.loop.data.db.FxRateDao
import t.l.budget.loop.data.db.FxRateEntity
import t.l.budget.loop.data.db.FxRateSource

class ManualFxProviderTest {
    @Test
    fun returnsNullWhenMissing() = runTest {
        val dao = FakeFxRateDao()
        val provider = ManualFxProvider(dao)
        assertNull(provider.getRate("USD", "EUR"))
    }

    @Test
    fun storesAndReturnsManualRate() = runTest {
        val dao = FakeFxRateDao()
        val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
        val provider = ManualFxProvider(dao, clock = FixedClock(now))

        val stored = provider.upsertRate("usd", "eur", 0.91)
        val fetched = provider.getRate("USD", "EUR")

        requireNotNull(fetched)
        assertEquals("USD", fetched.fromCurrency)
        assertEquals("EUR", fetched.toCurrency)
        assertEquals(0.91, fetched.rate)
        assertEquals(now, stored.asOf)
    }
}

private class FakeFxRateDao : FxRateDao {
    private val rates = mutableMapOf<String, FxRateEntity>()

    override suspend fun getRate(
        fromCurrency: String,
        toCurrency: String,
        source: FxRateSource,
    ): FxRateEntity? {
        return rates[key(fromCurrency, toCurrency, source)]
    }

    override suspend fun getLatest(fromCurrency: String, toCurrency: String): FxRateEntity? {
        return rates.values
            .filter { it.fromCurrency == fromCurrency && it.toCurrency == toCurrency }
            .maxByOrNull { it.asOf }
    }

    override suspend fun upsert(rate: FxRateEntity): Long {
        rates[key(rate.fromCurrency, rate.toCurrency, rate.source)] = rate
        return 1L
    }

    private fun key(from: String, to: String, source: FxRateSource): String {
        return "$from|$to|${source.name}"
    }
}

private class FixedClock(
    private val instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}
