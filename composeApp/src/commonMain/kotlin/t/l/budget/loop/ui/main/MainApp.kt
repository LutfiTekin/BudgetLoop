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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import t.l.budget.loop.data.db.DailyBudgetStateDao
import t.l.budget.loop.data.db.SavingsEntryDao
import t.l.budget.loop.data.db.SettingsDao
import t.l.budget.loop.data.db.TransactionDao
import t.l.budget.loop.data.db.TransactionEntity
import t.l.budget.loop.domain.budget.BudgetService
import t.l.budget.loop.domain.transactions.TransactionService
import t.l.budget.loop.money.Money
import t.l.budget.loop.money.normalizeCurrencyCode
import t.l.budget.loop.ui.onboarding.OnboardingData
import t.l.budget.loop.ui.theme.GrayLavender
import t.l.budget.loop.ui.theme.MutedPurple
import t.l.budget.loop.ui.theme.SoftViolet
import kotlin.time.Clock

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
    settingsDao: SettingsDao,
    dailyBudgetStateDao: DailyBudgetStateDao,
    savingsEntryDao: SavingsEntryDao,
) {
    var screen by remember { mutableStateOf(MainScreen.TANK) }
    var showMonthlySummary by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    val savingsEntries = remember { mutableStateListOf<SavingsEntryDraft>() }
    val scope = rememberCoroutineScope()
    val transactionService = remember(transactionDao) { TransactionService(transactionDao) }
    val budgetService = remember(settingsDao, transactionDao, dailyBudgetStateDao, savingsEntryDao) {
        BudgetService(settingsDao, transactionDao, dailyBudgetStateDao, savingsEntryDao)
    }
    var transactions by remember { mutableStateOf<List<TransactionEntity>>(emptyList()) }
    val currency = normalizeCurrencyCode(onboardingData.currency.ifBlank { "USD" })
    val defaultDailyBudgetMinor = parseMinor(onboardingData.dailyBudget, currency) ?: 0L
    var dailyBudgetMinor by remember { mutableStateOf(defaultDailyBudgetMinor) }
    var carryMinor by remember { mutableStateOf(0L) }
    var remainingMinor by remember { mutableStateOf(defaultDailyBudgetMinor) }
    val dailyAllowanceMinor = dailyBudgetMinor + carryMinor
    val dailyAllowance = Money.of(dailyAllowanceMinor, currency)
    val remaining = Money.of(remainingMinor, currency)
    val timeZone = TimeZone.currentSystemDefault()

    suspend fun loadTransactions() {
        val today = currentLocalDate(timeZone)
        val state = budgetService.ensureDailyState(today, timeZone)
        dailyBudgetMinor = state.dailyBudgetMinor
        carryMinor = state.carryMinor
        remainingMinor = state.remainingMinor
        transactions = transactionDao.listAll()
    }

    LaunchedEffect(transactionDao) {
        loadTransactions()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (screen) {
            MainScreen.TANK -> TankScreen(
                dailyAllowance = dailyAllowance,
                remaining = remaining,
                transactions = transactions,
                timeZone = timeZone,
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
                dailyLimitMinor = dailyAllowanceMinor.coerceAtLeast(0L),
                onSave = { amountMinor, note ->
                    scope.launch {
                        val amount = Money.of(amountMinor, currency)
                        val nowInstant = nowInstant()
                        transactionService.addExpense(
                            amount = amount,
                            amountDefault = amount,
                            date = nowInstant,
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
    dailyAllowance: Money,
    remaining: Money,
    transactions: List<TransactionEntity>,
    timeZone: TimeZone,
    onShowMonthlySummary: () -> Unit,
) {
    val progress = if (dailyAllowance.minor <= 0L) {
        0f
    } else {
        (remaining.minor.toFloat() / dailyAllowance.minor.toFloat()).coerceIn(0f, 1f)
    }
    val fillColor = when {
        progress > 0.6f -> MutedPurple
        progress > 0.3f -> SoftViolet
        else -> GrayLavender
    }
    val baseColor = MaterialTheme.colorScheme.background

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
            timeZone = timeZone,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun TransactionHistory(
    transactions: List<TransactionEntity>,
    timeZone: TimeZone,
    modifier: Modifier = Modifier,
) {
    val today = currentLocalDate(timeZone)
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
                val dateLabel = formatTransactionDateLabel(transaction.date, today, timeZone)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            transaction.note?.ifBlank { "No note" } ?: "No note",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
    dailyLimitMinor: Long,
    onSave: (Long, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountMinor by remember { mutableStateOf(0L) }
    var note by remember { mutableStateOf("") }
    val canSave = amountMinor > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val cleanedNote = note.trim().ifBlank { null }
                    onSave(amountMinor, cleanedNote)
                    amountMinor = 0L
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
                CylindricalNumberPicker(
                    currentAmountMinor = amountMinor,
                    dailyLimitMinor = dailyLimitMinor,
                    currency = defaultCurrency,
                    onAmountChange = { amountMinor = it },
                    modifier = Modifier.fillMaxWidth(),
                )
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
                val date = currentLocalDate(TimeZone.currentSystemDefault()).toString()
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

private fun nowInstant(): Instant {
    val now = Clock.System.now()
    return Instant.fromEpochMilliseconds(now.toEpochMilliseconds())
}

private fun currentLocalDate(timeZone: TimeZone): LocalDate {
    return nowInstant().toLocalDateTime(timeZone).date
}

private fun parseMinor(amount: String, currency: String): Long? {
    val cleaned = amount.trim().replace(",", ".")
    val value = cleaned.toDoubleOrNull() ?: return null
    return Money.fromDecimal(value, currency).minor
}
