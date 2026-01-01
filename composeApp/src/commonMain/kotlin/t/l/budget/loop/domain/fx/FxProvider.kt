package t.l.budget.loop.domain.fx

import kotlinx.datetime.Instant
import t.l.budget.loop.data.db.FxRateSource

data class FxRate(
    val fromCurrency: String,
    val toCurrency: String,
    val rate: Double,
    val asOf: Instant,
    val source: FxRateSource,
)

interface FxProvider {
    suspend fun getRate(
        fromCurrency: String,
        toCurrency: String,
    ): FxRate?
}
