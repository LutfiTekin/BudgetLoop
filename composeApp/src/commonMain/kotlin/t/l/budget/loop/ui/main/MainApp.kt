package t.l.budget.loop.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import t.l.budget.loop.money.Money
import t.l.budget.loop.money.normalizeCurrencyCode
import t.l.budget.loop.ui.onboarding.CardDraft
import t.l.budget.loop.ui.onboarding.OnboardingData
import kotlin.time.Clock

private enum class MainScreen {
    TANK,
    EXPENSE,
    CORRECTION,
    SAVINGS,
}

data class SavingsEntryDraft(
    val amount: String,
    val note: String,
    val date: String,
)

@Composable
fun MainApp(
    onboardingData: OnboardingData,
) {
    var screen by remember { mutableStateOf(MainScreen.TANK) }
    var showMonthlySummary by remember { mutableStateOf(false) }
    val savingsEntries = remember { mutableStateListOf<SavingsEntryDraft>() }
    val currency = normalizeCurrencyCode(onboardingData.currency.ifBlank { "USD" })

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (screen) {
                MainScreen.TANK -> TankScreen(
                    dailyBudget = Money.of(10000, currency),
                    remaining = Money.of(4250, currency),
                    netStatus = Money.of(120000, currency),
                    onShowMonthlySummary = { showMonthlySummary = true },
                )
                MainScreen.EXPENSE -> AddExpenseScreen(
                    defaultCurrency = currency,
                    cards = onboardingData.cards,
                )
                MainScreen.CORRECTION -> CorrectionScreen(defaultCurrency = currency)
                MainScreen.SAVINGS -> SavingsTrackerScreen(
                    defaultCurrency = currency,
                    entries = savingsEntries,
                )
            }
            if (showMonthlySummary) {
                MonthlySummaryDialog(
                    savedAmount = Money.of(7800, currency),
                    onDismiss = { showMonthlySummary = false },
                )
            }
        }
        MainNav(
            current = screen,
            onSelect = { screen = it },
        )
    }
}

@Composable
private fun MainNav(
    current: MainScreen,
    onSelect: (MainScreen) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NavButton(
            label = "Tank",
            selected = current == MainScreen.TANK,
            onClick = { onSelect(MainScreen.TANK) },
        )
        NavButton(
            label = "Expense",
            selected = current == MainScreen.EXPENSE,
            onClick = { onSelect(MainScreen.EXPENSE) },
        )
        NavButton(
            label = "Correct",
            selected = current == MainScreen.CORRECTION,
            onClick = { onSelect(MainScreen.CORRECTION) },
        )
        NavButton(
            label = "Savings",
            selected = current == MainScreen.SAVINGS,
            onClick = { onSelect(MainScreen.SAVINGS) },
        )
    }
}

@Composable
private fun RowScope.NavButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.weight(1f)) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) {
            Text(label)
        }
    }
}

@Composable
private fun TankScreen(
    dailyBudget: Money,
    remaining: Money,
    netStatus: Money,
    onShowMonthlySummary: () -> Unit,
) {
    val progress = if (dailyBudget.minor <= 0L) {
        0f
    } else {
        (remaining.minor.toFloat() / dailyBudget.minor.toFloat()).coerceIn(0f, 1f)
    }
    val fillColor = when {
        progress > 0.6f -> Color(0xFF2E8B57)
        progress > 0.3f -> Color(0xFFF0C75E)
        else -> Color(0xFFCC4E4E)
    }
    val baseColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(baseColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(progress)
                .align(Alignment.BottomCenter)
                .background(fillColor),
        )
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Daily Remaining", style = MaterialTheme.typography.titleSmall)
            Text(remaining.format(), style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Net Status", style = MaterialTheme.typography.titleSmall)
            Text(netStatus.format(), style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onShowMonthlySummary) {
                Text("Monthly summary")
            }
        }
    }
}

@Composable
private fun AddExpenseScreen(
    defaultCurrency: String,
    cards: List<CardDraft>,
) {
    var amount by remember { mutableStateOf("") }
    var cardName by remember { mutableStateOf(cards.firstOrNull()?.name ?: "") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Add Expense", style = MaterialTheme.typography.titleLarge)
        TextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount ($defaultCurrency)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )
        TextField(
            value = cardName,
            onValueChange = { cardName = it },
            label = { Text("Card") },
            singleLine = true,
        )
        TextField(
            value = category,
            onValueChange = { category = it },
            label = { Text("Category (optional)") },
            singleLine = true,
        )
        TextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note (optional)") },
            singleLine = true,
        )
        Button(
            onClick = {
                amount = ""
                category = ""
                note = ""
            },
            enabled = amount.isNotBlank() && cardName.isNotBlank(),
        ) {
            Text("Save expense")
        }
        if (cards.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("Cards on file:", style = MaterialTheme.typography.titleSmall)
            cards.forEach { card -> Text("- ${card.name} (${card.currency})") }
        }
    }
}

@Composable
private fun CorrectionScreen(
    defaultCurrency: String,
) {
    var bankName by remember { mutableStateOf("") }
    var bankBalance by remember { mutableStateOf("") }
    var cardName by remember { mutableStateOf("") }
    var cardDebt by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Correction Mode", style = MaterialTheme.typography.titleLarge)
        Text("Adjust bank balance")
        TextField(
            value = bankName,
            onValueChange = { bankName = it },
            label = { Text("Bank name") },
            singleLine = true,
        )
        TextField(
            value = bankBalance,
            onValueChange = { bankBalance = it },
            label = { Text("New balance ($defaultCurrency)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )
        Button(
            onClick = {
                bankName = ""
                bankBalance = ""
            },
            enabled = bankName.isNotBlank() && bankBalance.isNotBlank(),
        ) {
            Text("Apply bank adjustment")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text("Adjust card debt")
        TextField(
            value = cardName,
            onValueChange = { cardName = it },
            label = { Text("Card name") },
            singleLine = true,
        )
        TextField(
            value = cardDebt,
            onValueChange = { cardDebt = it },
            label = { Text("New debt ($defaultCurrency)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )
        Button(
            onClick = {
                cardName = ""
                cardDebt = ""
            },
            enabled = cardName.isNotBlank() && cardDebt.isNotBlank(),
        ) {
            Text("Apply card adjustment")
        }
    }
}

@Composable
private fun SavingsTrackerScreen(
    defaultCurrency: String,
    entries: MutableList<SavingsEntryDraft>,
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Savings Tracker", style = MaterialTheme.typography.titleLarge)
        TextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount ($defaultCurrency)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )
        TextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note (optional)") },
            singleLine = true,
        )
        Button(
            onClick = {
                val date = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
                entries.add(SavingsEntryDraft(amount = amount, note = note, date = date))
                amount = ""
                note = ""
            },
            enabled = amount.isNotBlank(),
        ) {
            Text("Add saving")
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (entries.isEmpty()) {
            Text("No savings yet.")
        } else {
            entries.forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("${entry.amount} $defaultCurrency")
                        Text(entry.date, style = MaterialTheme.typography.bodySmall)
                        if (entry.note.isNotBlank()) {
                            Text(entry.note, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(onClick = { entries.remove(entry) }) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlySummaryDialog(
    savedAmount: Money,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got it")
            }
        },
        title = { Text("Monthly Summary") },
        text = { Text("You saved ${savedAmount.format()} this month.") },
    )
}
