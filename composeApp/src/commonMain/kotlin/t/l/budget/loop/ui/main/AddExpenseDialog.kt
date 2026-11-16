package t.l.budget.loop.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import t.l.budget.loop.domain.fx.ManualFxProvider
import t.l.budget.loop.money.CurrencyMeta
import t.l.budget.loop.money.Money
import t.l.budget.loop.money.normalizeCurrencyCode
import kotlin.math.roundToLong

internal data class NewExpenseInput(
    val amount: Money,
    val amountDefault: Money,
    val note: String?,
    val category: String?,
    val date: LocalDate,
)

@Composable
internal fun AddExpenseDialog(
    defaultCurrency: String,
    dailyLimitMinor: Long,
    fxProvider: ManualFxProvider,
    today: LocalDate,
    onSave: suspend (NewExpenseInput) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountMinor by remember { mutableStateOf(0L) }
    var manualEntryMode by remember { mutableStateOf(false) }
    var manualAmountText by remember { mutableStateOf("") }
    var selectedCurrencyText by remember { mutableStateOf(defaultCurrency) }
    var fxRateText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var categoryText by remember { mutableStateOf("") }
    var markOffLoop by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(today) }
    var saving by remember { mutableStateOf(false) }
    var pendingSave by remember { mutableStateOf<NewExpenseInput?>(null) }

    val activeCurrency = remember(selectedCurrencyText, defaultCurrency) {
        val cleaned = selectedCurrencyText.trim().uppercase()
        if (cleaned.length == 3) cleaned else defaultCurrency
    }
    val needsFx = activeCurrency != defaultCurrency

    LaunchedEffect(activeCurrency, defaultCurrency) {
        if (activeCurrency == defaultCurrency) {
            fxRateText = ""
            return@LaunchedEffect
        }
        val cached = fxProvider.getRate(activeCurrency, defaultCurrency)
        fxRateText = cached?.rate?.toString().orEmpty()
    }

    LaunchedEffect(pendingSave) {
        val payload = pendingSave ?: return@LaunchedEffect
        saving = true
        try {
            if (needsFx) {
                val parsedRate = fxRateText.trim().replace(",", ".").toDoubleOrNull()
                if (parsedRate != null && parsedRate > 0.0) {
                    fxProvider.upsertRate(activeCurrency, defaultCurrency, parsedRate)
                }
            }
            onSave(payload)
            onDismiss()
        } finally {
            saving = false
            pendingSave = null
        }
    }

    val effectiveMinor: Long? = if (manualEntryMode) {
        parseInputMinor(manualAmountText, activeCurrency)
    } else {
        amountMinor
    }
    val parsedFxRate: Double? = if (!needsFx) {
        1.0
    } else {
        fxRateText.trim().replace(",", ".").toDoubleOrNull()?.takeIf { it > 0.0 }
    }
    val defaultAmountPreview: Money? = remember(effectiveMinor, activeCurrency, defaultCurrency, parsedFxRate) {
        val minor = effectiveMinor ?: return@remember null
        val rate = parsedFxRate ?: return@remember null
        if (!needsFx) {
            Money.of(minor, defaultCurrency)
        } else {
            convertMinorWithRate(minor, activeCurrency, defaultCurrency, rate)
        }
    }

    val canSave = !saving &&
        effectiveMinor != null &&
        effectiveMinor > 0L &&
        defaultAmountPreview != null &&
        selectedCurrencyText.trim().length == 3

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val minor = effectiveMinor ?: return@Button
                    val defaultMoney = defaultAmountPreview ?: return@Button
                    val cleanedCategory = buildExpenseTag(categoryText, markOffLoop)
                    pendingSave = NewExpenseInput(
                        amount = Money.of(minor, normalizeCurrencyCode(activeCurrency)),
                        amountDefault = defaultMoney,
                        note = note.trim().ifBlank { null },
                        category = cleanedCategory,
                        date = selectedDate,
                    )
                },
                enabled = canSave,
            ) {
                Text(if (saving) "Saving..." else "Save expense")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, enabled = !saving) {
                Text("Cancel")
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Add Expense")
                TextButton(
                    onClick = {
                        if (!manualEntryMode && amountMinor > 0L) {
                            manualAmountText = Money.of(amountMinor, activeCurrency).format(withCurrency = false)
                        }
                        manualEntryMode = !manualEntryMode
                    },
                ) {
                    Text(if (manualEntryMode) "Use Dial" else "Type Amount")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (manualEntryMode) {
                    TextField(
                        value = manualAmountText,
                        onValueChange = { manualAmountText = it },
                        label = { Text("Amount ($activeCurrency)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    CylindricalNumberPicker(
                        currentAmountMinor = amountMinor,
                        softLimitMinor = if (needsFx) 0L else dailyLimitMinor,
                        currency = activeCurrency,
                        onAmountChange = { amountMinor = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = selectedCurrencyText,
                        onValueChange = { selectedCurrencyText = it.uppercase().take(3) },
                        label = { Text("Currency") },
                        singleLine = true,
                        modifier = Modifier.weight(0.35f),
                    )
                    TextField(
                        value = categoryText,
                        onValueChange = { categoryText = it },
                        label = { Text("Tag (optional)") },
                        singleLine = true,
                        modifier = Modifier.weight(0.65f),
                    )
                }

                if (needsFx) {
                    TextField(
                        value = fxRateText,
                        onValueChange = { fxRateText = it },
                        label = { Text("FX Rate: 1 $activeCurrency in $defaultCurrency") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (defaultAmountPreview != null && (effectiveMinor ?: 0L) > 0L) {
                        Text(
                            text = "Converted: ${defaultAmountPreview.format()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note (optional)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showDatePicker = !showDatePicker }) {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = "Select Date",
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = markOffLoop,
                        onCheckedChange = { markOffLoop = it },
                    )
                    Text(
                        text = "Flag as unplanned / off-loop spend",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (showDatePicker) {
                    TransactionDatePicker(
                        date = selectedDate,
                        onDateChange = { selectedDate = it },
                        maxDate = today,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                    )
                }
            }
        },
    )
}

private fun buildExpenseTag(rawTag: String, offLoop: Boolean): String? {
    val trimmed = rawTag.trim()
    return when {
        offLoop && trimmed.isNotEmpty() -> "Off-Loop · $trimmed"
        offLoop -> "Off-Loop"
        trimmed.isNotEmpty() -> trimmed
        else -> null
    }
}

private fun parseInputMinor(amount: String, currency: String): Long? {
    val cleaned = amount.trim().replace(",", ".")
    val value = cleaned.toDoubleOrNull() ?: return null
    return Money.fromDecimal(value, currency).minor
}

private fun convertMinorWithRate(
    minor: Long,
    fromCurrency: String,
    toCurrency: String,
    rate: Double,
): Money {
    val fromDigits = CurrencyMeta.fractionDigits(fromCurrency)
    val toDigits = CurrencyMeta.fractionDigits(toCurrency)
    val fromFactor = decimalFactor(fromDigits).toDouble()
    val toFactor = decimalFactor(toDigits).toDouble()
    val major = minor.toDouble() / fromFactor
    val convertedMinor = (major * rate * toFactor).roundToLong()
    return Money.of(convertedMinor, toCurrency)
}

private fun decimalFactor(digits: Int): Long {
    var res = 1L
    repeat(digits) { res *= 10L }
    return res
}
