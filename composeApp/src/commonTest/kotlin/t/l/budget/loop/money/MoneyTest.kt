package t.l.budget.loop.money

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyTest {
    @Test
    fun formatTwoDecimals() {
        val money = Money.of(12345, "usd")
        assertEquals("123.45 USD", money.format())
    }

    @Test
    fun formatZeroDecimals() {
        val money = Money.of(500, "JPY")
        assertEquals("500 JPY", money.format())
    }

    @Test
    fun roundToMinor() {
        val money = Money.fromDecimal(12.346, "USD")
        assertEquals(1235, money.minor)
    }
}
