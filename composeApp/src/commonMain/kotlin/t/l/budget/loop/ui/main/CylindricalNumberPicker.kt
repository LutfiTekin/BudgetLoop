package t.l.budget.loop.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import t.l.budget.loop.money.CurrencyMeta
import t.l.budget.loop.money.Money

@Composable
internal fun CylindricalNumberPicker(
    currentAmountMinor: Long,
    dailyLimitMinor: Long,
    currency: String,
    onAmountChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fractionDigits = CurrencyMeta.fractionDigits(currency)
    val factorLong = pow10(fractionDigits)
    val factorInt = factorLong.toInt()
    val limitMinor = dailyLimitMinor.coerceAtLeast(0L)
    val majorLimit = (limitMinor / factorLong).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val minorLimit = (limitMinor % factorLong).toInt()
    val initialMajor = (currentAmountMinor / factorLong).toInt().coerceIn(0, majorLimit)
    val initialMinor = (currentAmountMinor % factorLong).toInt()
        .coerceIn(0, if (majorLimit == 0) minorLimit else factorInt - 1)
    var major by remember(majorLimit, factorLong) { mutableStateOf(initialMajor) }
    var minor by remember(majorLimit, minorLimit, factorLong) { mutableStateOf(initialMinor) }
    val minorMax by remember {
        derivedStateOf {
            if (fractionDigits == 0) 0 else if (major >= majorLimit) minorLimit else factorInt - 1
        }
    }
    val amountMinor by remember {
        derivedStateOf { major.toLong() * factorLong + minor.toLong() }
    }
    val baseColor = MaterialTheme.colorScheme.onSurface
    val warningColor = MaterialTheme.colorScheme.error
    val onAmountChangeState by rememberUpdatedState(onAmountChange)
    val amountColor by remember(baseColor, warningColor, dailyLimitMinor) {
        derivedStateOf {
            val limit = dailyLimitMinor
            val ratio = if (limit <= 0L) {
                if (amountMinor > 0L) 1f else 0f
            } else {
                ((amountMinor - limit).toFloat() / limit.toFloat()).coerceIn(0f, 1f)
            }
            lerp(baseColor, warningColor, ratio)
        }
    }

    LaunchedEffect(currentAmountMinor, majorLimit, minorLimit, factorLong) {
        val targetMajor = (currentAmountMinor / factorLong).toInt().coerceIn(0, majorLimit)
        val targetMinor = (currentAmountMinor % factorLong).toInt()
            .coerceIn(0, if (targetMajor >= majorLimit) minorLimit else factorInt - 1)
        if (targetMajor != major) {
            major = targetMajor
        }
        if (targetMinor != minor) {
            minor = targetMinor
        }
    }

    LaunchedEffect(minorMax) {
        if (minor > minorMax) {
            minor = minorMax
        }
    }

    LaunchedEffect(amountMinor) {
        onAmountChangeState(amountMinor)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = Money.of(amountMinor, currency).format(),
            style = MaterialTheme.typography.headlineMedium,
            color = amountColor,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhysicsWheelPicker(
                range = 0..majorLimit,
                value = major,
                onValueChange = { major = it },
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.width(140.dp),
            )
            if (fractionDigits > 0) {
                Text(".", style = MaterialTheme.typography.titleLarge)
                PhysicsWheelPicker(
                    range = 0..minorMax,
                    value = minor,
                    onValueChange = { minor = it },
                    textStyle = MaterialTheme.typography.titleLarge,
                    label = { value ->
                        value.toString().padStart(fractionDigits, '0')
                    },
                    modifier = Modifier.width(90.dp),
                    scrollMultiplier = 2.5f,
                )
            }
        }
        Text(
            text = "Max ${Money.of(limitMinor, currency).format()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

private fun pow10(exp: Int): Long {
    var result = 1L
    repeat(exp) { result *= 10L }
    return result
}
