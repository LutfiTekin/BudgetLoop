package t.l.budget.loop.domain.fx

class DefaultFxProvider : FxProvider {
    override suspend fun getRate(
        fromCurrency: String,
        toCurrency: String,
    ): FxRate? {
        return null
    }
}
