package t.l.budget.loop.money

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

typealias CurrencyCode = String

fun normalizeCurrencyCode(code: CurrencyCode): CurrencyCode {
    val normalized = code.trim().uppercase()
    require(normalized.length == 3) { "Currency code must be 3 letters." }
    return normalized
}

data class Money(val minor: Long, val currency: CurrencyCode) {
    init {
        require(currency.isNotBlank()) { "Currency is required." }
    }

    fun isZero(): Boolean = minor == 0L

    fun isNegative(): Boolean = minor < 0L

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return Money(minor + other.minor, currency)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return Money(minor - other.minor, currency)
    }

    operator fun unaryMinus(): Money = Money(-minor, currency)

    fun abs(): Money = Money(kotlin.math.abs(minor), currency)

    fun format(withCurrency: Boolean = true): String {
        return MoneyFormatter.format(this, withCurrency)
    }

    private fun requireSameCurrency(other: Money) {
        val left = normalizeCurrencyCode(currency)
        val right = normalizeCurrencyCode(other.currency)
        require(left == right) { "Currency mismatch: $left vs $right" }
    }

    companion object {
        fun of(minor: Long, currency: CurrencyCode): Money {
            return Money(minor, normalizeCurrencyCode(currency))
        }

        fun fromDecimal(amount: Double, currency: CurrencyCode): Money {
            val normalized = normalizeCurrencyCode(currency)
            return Money(MoneyRounding.toMinor(amount, normalized), normalized)
        }
    }
}

object CurrencyMeta {
    private val zeroDecimals = setOf("BIF", "CLP", "JPY", "KRW", "PYG", "VND")
    private val threeDecimals = setOf("BHD", "JOD", "KWD", "OMR", "TND")

    fun fractionDigits(currency: CurrencyCode): Int {
        val normalized = normalizeCurrencyCode(currency)
        return when (normalized) {
            in zeroDecimals -> 0
            in threeDecimals -> 3
            else -> 2
        }
    }
}

object MoneyRounding {
    fun toMinor(amount: Double, currency: CurrencyCode): Long {
        val fraction = CurrencyMeta.fractionDigits(currency)
        val factor = 10.0.pow(fraction.toDouble())
        return (amount * factor).roundToLong()
    }
}

object MoneyFormatter {
    fun format(money: Money, withCurrency: Boolean = true): String {
        val fraction = CurrencyMeta.fractionDigits(money.currency)
        val factor = pow10(fraction)
        val absMinor = abs(money.minor)
        val major = if (factor == 0L) absMinor else absMinor / factor
        val minorPart = if (factor == 0L) 0L else absMinor % factor
        val amount = if (fraction == 0) {
            major.toString()
        } else {
            val minorText = minorPart.toString().padStart(fraction, '0')
            "$major.$minorText"
        }
        val sign = if (money.minor < 0) "-" else ""
        val currency = normalizeCurrencyCode(money.currency)
        return if (withCurrency) "$sign$amount $currency" else "$sign$amount"
    }

    private fun pow10(exp: Int): Long {
        var result = 1L
        repeat(exp) { result *= 10L }
        return result
    }
}
