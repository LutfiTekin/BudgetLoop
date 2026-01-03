package t.l.budget.loop.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import t.l.budget.loop.data.db.TransactionDao
import t.l.budget.loop.data.db.TransactionEntity
import t.l.budget.loop.domain.transactions.TransactionService
import t.l.budget.loop.money.Money
import t.l.budget.loop.money.normalizeCurrencyCode
import t.l.budget.loop.ui.onboarding.OnboardingData
import kotlin.time.Clock.*

private enum class MainScreen {
    TANK,
    SAVINGS,
}

private const val DefaultExpenseCategory = "Food"

data class SavingsEntryDraft(
    val amount: String,
    val note: String,
    val date: String,
)

@Composable
fun MainApp(
    onboardingData: OnboardingData,
    transactionDao: TransactionDao,
) {
    var screen by remember { mutableStateOf(MainScreen.TANK) }
    var showMonthlySummary by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    val savingsEntries = remember { mutableStateListOf<SavingsEntryDraft>() }
    val scope = rememberCoroutineScope()
    val transactionService = remember(transactionDao) { TransactionService(transactionDao) }
    var transactions by remember { mutableStateOf<List<TransactionEntity>>(emptyList()) }
    var spentTodayMinor by remember { mutableStateOf(0L) }
    val currency = normalizeCurrencyCode(onboardingData.currency.ifBlank { "USD" })
    val dailyBudgetMinor = parseMinor(onboardingData.dailyBudget, currency) ?: 0L
    val dailyBudget = Money.of(dailyBudgetMinor, currency)
    val remaining = Money.of(dailyBudget.minor - spentTodayMinor, currency)
    val timeZone = TimeZone.currentSystemDefault()

    suspend fun loadTransactions() {
        val today = System.todayIn(timeZone)
        val start = today.atStartOfDayIn(timeZone)
        val endExclusive = today.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone)
        spentTodayMinor = transactionDao.sumDefaultBetween(start, endExclusive)
        transactions = transactionDao.listAll()
    }

    LaunchedEffect(transactionDao) {
        loadTransactions()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (screen) {
            MainScreen.TANK -> TankScreen(
                dailyBudget = dailyBudget,
                remaining = remaining,
                transactions = transactions,
                onShowMonthlySummary = { showMonthlySummary = true },
            )
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
        if (showAddExpenseDialog) {
            AddExpenseDialog(
                defaultCurrency = currency,
                onSave = { amountMinor, note ->
                    scope.launch {
                        val amount = Money.of(amountMinor, currency)
                        transactionService.addExpense(
                            amount = amount,
                            amountDefault = amount,
                            date = System.now(),
                            note = note,
                            category = DefaultExpenseCategory,
                        )
                        loadTransactions()
                    }
                },
                onDismiss = { showAddExpenseDialog = false },
            )
        }
        FabMenu(
            current = screen,
            onNavigate = { target ->
                screen = target
                if (target != MainScreen.TANK) {
                    showMonthlySummary = false
                }
            },
            onAddExpense = {
                showMonthlySummary = false
                showAddExpenseDialog = true
            },
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
private fun FabMenu(
    current: MainScreen,
    onNavigate: (MainScreen) -> Unit,
    onAddExpense: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(20.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (expanded) {
            ExtendedFloatingActionButton(
                onClick = {
                    expanded = false
                    onAddExpense()
                },
                content = { Text("Add expense") },
            )
            if (current != MainScreen.SAVINGS) {
                ExtendedFloatingActionButton(
                    onClick = {
                        expanded = false
                        onNavigate(MainScreen.SAVINGS)
                    },
                    content = { Text("Savings") },
                )
            }
            if (current != MainScreen.TANK) {
                ExtendedFloatingActionButton(
                    onClick = {
                        expanded = false
                        onNavigate(MainScreen.TANK)
                    },
                    content = { Text("Tank") },
                )
            }
        }
        FloatingActionButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "x" else "+")
        }
    }
}

@Composable
private fun TankScreen(
    dailyBudget: Money,
    remaining: Money,
    transactions: List<TransactionEntity>,
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
            OutlinedButton(onClick = onShowMonthlySummary) {
                Text("Monthly summary")
            }
        }
        TransactionHistory(
            transactions = transactions,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun TransactionHistory(
    transactions: List<TransactionEntity>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth(0.85f)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                MaterialTheme.shapes.medium,
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Transaction history", style = MaterialTheme.typography.titleSmall)
        if (transactions.isEmpty()) {
            Text("No transactions yet.", style = MaterialTheme.typography.bodySmall)
        } else {
            transactions.forEach { transaction ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        transaction.note?.ifBlank { "No note" } ?: "No note",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        Money.of(transaction.amountMinor, transaction.currency).format(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddExpenseDialog(
    defaultCurrency: String,
    onSave: (Long, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val amountMinor = parseMinor(amount, defaultCurrency)
    val canSave = amountMinor != null
    val showAmountError = amount.isNotBlank() && amountMinor == null

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val minor = amountMinor ?: return@Button
                    val cleanedNote = note.trim().ifBlank { null }
                    onSave(minor, cleanedNote)
                    amount = ""
                    note = ""
                    onDismiss()
                },
                enabled = canSave,
            ) {
                Text("Save expense")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text("Add Expense") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount ($defaultCurrency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                if (showAmountError) {
                    Text(
                        "Enter a valid amount.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                )
            }
        },
    )
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
                val date = System.todayIn(TimeZone.currentSystemDefault()).toString()
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
        text = {
            if (savedAmount.minor >= 0) {
                Text("You saved ${savedAmount.format()} this month.")
            } else {
                Text("You overspent by ${savedAmount.abs().format()} this month.")
            }
        },
    )
}

private fun parseMinor(amount: String, currency: String): Long? {
    val cleaned = amount.trim().replace(",", ".")
    val value = cleaned.toDoubleOrNull() ?: return null
    return Money.fromDecimal(value, currency).minor
}
