package t.l.budget.loop.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.atStartOfDayIn
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
import t.l.budget.loop.money.CurrencyMeta
import t.l.budget.loop.money.Money
import t.l.budget.loop.money.normalizeCurrencyCode
import t.l.budget.loop.platform.rememberHapticFeedbackManager
import t.l.budget.loop.ui.onboarding.OnboardingData
import t.l.budget.loop.ui.theme.GrayLavender
import t.l.budget.loop.ui.theme.MutedPurple
import t.l.budget.loop.ui.theme.SoftViolet
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.time.Clock

private enum class MainScreen {
    TANK,
    SAVINGS,
}

private enum class BankingState {
    IDLE,
    FILLING,
    SUCCESS_DRAINING,
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
    var isBanking by remember { mutableStateOf(false) }
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
    val dailyBudget = Money.of(dailyBudgetMinor, currency)
    val remaining = Money.of(remainingMinor, currency)
    val carryAmount = Money.of(carryMinor, currency)
    val timeZone = TimeZone.currentSystemDefault()

    suspend fun loadTransactions() {
        val today = currentLocalDate(timeZone)
        val state = budgetService.ensureDailyState(today, timeZone)
        dailyBudgetMinor = state.dailyBudgetMinor
        carryMinor = state.carryMinor
        remainingMinor = state.remainingMinor
        remainingMinor = state.remainingMinor
        transactions = transactionDao.listAll()
    }

    var totalSaved by remember { mutableStateOf(Money.of(0, currency)) }

    suspend fun loadData() {
        loadTransactions()
        totalSaved = budgetService.getTotalSaved()
    }

    LaunchedEffect(transactionDao) {
        loadData()
    }

    suspend fun bankCarryOver(): Money {
        val today = currentLocalDate(timeZone)
        val banked = budgetService.bankCarryOver(today, timeZone)
        loadData()
        return banked
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (screen) {
            MainScreen.TANK -> TankScreen(
                dailyBudget = dailyBudget,
                remaining = remaining,
                carryAmount = carryAmount,
                transactions = transactions,
                timeZone = timeZone,
                onBankCarryOver = { bankCarryOver() },
                onBankingStateChange = { isBanking = it },
                onShowMonthlySummary = { showMonthlySummary = true },
            )
            MainScreen.SAVINGS -> SavingsTrackerScreen(
                defaultCurrency = currency,
                totalSaved = totalSaved,
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
                onSave = { amountMinor, note, date ->
                    scope.launch {
                        val amount = Money.of(amountMinor, currency)
                        val transactionInstant = date.atStartOfDayIn(timeZone)
                        transactionService.addExpense(
                            amount = amount,
                            amountDefault = amount,
                            date = transactionInstant,
                            note = note,
                            category = DefaultExpenseCategory,
                        )
                        loadData()
                    }
                },
                onDismiss = { showAddExpenseDialog = false },
            )
        }
        if (!(screen == MainScreen.TANK && isBanking)) {
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
    carryAmount: Money,
    transactions: List<TransactionEntity>,
    timeZone: TimeZone,
    onBankCarryOver: suspend () -> Money,
    onBankingStateChange: (Boolean) -> Unit,
    onShowMonthlySummary: () -> Unit,
) {
    val baseColor = MaterialTheme.colorScheme.background
    val haptics = rememberHapticFeedbackManager()
    val onBankCarryOverState by rememberUpdatedState(onBankCarryOver)
    val onBankingStateChangeState by rememberUpdatedState(onBankingStateChange)
    val latestCarryAmount by rememberUpdatedState(carryAmount)
    val latestRemaining by rememberUpdatedState(remaining)
    val bankScope = rememberCoroutineScope()
    val bankProgress = remember { Animatable(0f) }
    var bankingState by remember { mutableStateOf(BankingState.IDLE) }
    var bankedAmount by remember { mutableStateOf<Money?>(null) }
    var lockedCarryAmount by remember { mutableStateOf<Money?>(null) }
    var lockedRemaining by remember { mutableStateOf<Money?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    val activeCarryAmount by remember {
        derivedStateOf {
            if (bankingState == BankingState.IDLE) {
                latestCarryAmount
            } else {
                lockedCarryAmount ?: latestCarryAmount
            }
        }
    }
    val activeRemaining by remember {
        derivedStateOf {
            if (bankingState == BankingState.IDLE) {
                latestRemaining
            } else {
                lockedRemaining ?: latestRemaining
            }
        }
    }
    val canBank by remember {
        derivedStateOf { activeCarryAmount.minor > 0L && activeRemaining.minor >= dailyBudget.minor }
    }
    val requiredDurationMs by remember {
        derivedStateOf {
            val fractionDigits = CurrencyMeta.fractionDigits(activeCarryAmount.currency)
            val factor = pow10(fractionDigits).toDouble()
            val amountMajor = activeCarryAmount.minor.toDouble() / factor
            val extraSeconds = max(0.0, (amountMajor - 5.0) / 5.0)
            val durationMs = (5000.0 + extraSeconds * 1000.0).roundToLong()
            min(durationMs, 20_000L)
        }
    }
    val focusProgress by remember {
        derivedStateOf { bankProgress.value.coerceIn(0f, 1f) }
    }
    val isBankingActive by remember {
        derivedStateOf { focusProgress > 0f || bankingState != BankingState.IDLE }
    }
    val previewCarryMinorExact by remember {
        derivedStateOf { activeCarryAmount.minor.toDouble() * focusProgress }
    }
    val previewCarryMinor by remember {
        derivedStateOf { previewCarryMinorExact.roundToLong() }
    }
    val contentAlpha by remember {
        derivedStateOf { (1f - focusProgress).coerceIn(0f, 1f) }
    }
    val displayAmountMinorExact by remember {
        derivedStateOf {
            if (bankingState == BankingState.FILLING) previewCarryMinorExact else activeCarryAmount.minor.toDouble()
        }
    }
    val displayAmountMinor by remember {
        derivedStateOf { displayAmountMinorExact.roundToLong() }
    }
    val displayRemainingMinor by remember {
        derivedStateOf {
            val baseRemainingMinor = activeRemaining.minor
            val deltaMinor = when (bankingState) {
                BankingState.FILLING -> previewCarryMinor
                BankingState.SUCCESS_DRAINING -> activeCarryAmount.minor
                BankingState.IDLE -> if (focusProgress > 0f) previewCarryMinor else 0L
            }
            baseRemainingMinor - deltaMinor
        }
    }
    val displayRemaining = Money.of(displayRemainingMinor, activeRemaining.currency)
    val progress by remember {
        derivedStateOf {
            if (dailyBudget.minor <= 0L) {
                0f
            } else {
                (displayRemainingMinor.toFloat() / dailyBudget.minor.toFloat()).coerceIn(0f, 1f)
            }
        }
    }
    val fillColor = when {
        progress > 0.6f -> MutedPurple
        progress > 0.3f -> SoftViolet
        else -> GrayLavender
    }

    LaunchedEffect(canBank) {
        if (!canBank && bankingState == BankingState.IDLE) {
            bankingState = BankingState.IDLE
            bankProgress.snapTo(0f)
            lockedCarryAmount = null
            lockedRemaining = null
        }
    }

    LaunchedEffect(bankingState) {
        if (bankingState == BankingState.IDLE) {
            lockedCarryAmount = null
            lockedRemaining = null
        }
    }

    LaunchedEffect(isBankingActive) {
        onBankingStateChangeState(isBankingActive)
    }

    LaunchedEffect(bankingState) {
        if (bankingState != BankingState.FILLING) {
            return@LaunchedEffect
        }
        while (isActive && bankingState == BankingState.FILLING) {
            val intervalMs = ((1f - focusProgress) * 120f + 30f).roundToLong()
            haptics.tick()
            delay(intervalMs.coerceAtLeast(20L))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(baseColor)
    ) {
        // Gesture Detector Layer (Background)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(canBank, requiredDurationMs) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        if (!canBank || bankingState != BankingState.IDLE) return@awaitEachGesture
                        lockedCarryAmount = latestCarryAmount
                        lockedRemaining = latestRemaining
                        bankingState = BankingState.FILLING
                        val fillJob = bankScope.launch {
                            try {
                                val startProgress = bankProgress.value.coerceIn(0f, 1f)
                                val remainingDurationMs = (
                                    requiredDurationMs.toDouble() * (1.0 - startProgress.toDouble())
                                ).roundToLong().coerceAtLeast(1L)
                                bankProgress.animateTo(
                                    1f,
                                    animationSpec = tween(
                                        durationMillis = remainingDurationMs.toInt(),
                                        easing = LinearEasing,
                                    ),
                                )
                                if (bankProgress.value >= 1f && bankingState == BankingState.FILLING) {
                                    val banked = onBankCarryOverState()
                                    bankedAmount = banked
                                    bankingState = BankingState.SUCCESS_DRAINING
                                    bankProgress.animateTo(
                                        0f,
                                        animationSpec = tween(
                                            durationMillis = 1000,
                                            easing = LinearEasing,
                                        ),
                                    )
                                    bankingState = BankingState.IDLE
                                    showSuccessDialog = true
                                }
                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                bankingState = BankingState.IDLE
                                bankProgress.snapTo(0f)
                            }
                        }
                        waitForUpOrCancellation()
                        if (bankingState == BankingState.FILLING && bankProgress.value < 1f) {
                            fillJob.cancel()
                            bankingState = BankingState.IDLE
                            bankScope.launch {
                                bankProgress.animateTo(
                                    0f,
                                    animationSpec = tween(
                                        durationMillis = 3000,
                                        easing = LinearEasing,
                                    ),
                                )
                            }
                        }
                        down.consume()
                    }
                },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(progress)
                .align(Alignment.BottomCenter)
                .background(fillColor),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(contentAlpha),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Daily Remaining", style = MaterialTheme.typography.titleSmall)
                Text(displayRemaining.format(), style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onShowMonthlySummary) {
                    Text("Monthly summary")
                }
            }
            if (!isBankingActive) {
                TransactionHistory(
                    transactions = transactions,
                    timeZone = timeZone,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        WaveOverlay(
            progress = focusProgress,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize(),
        )
        if (bankingState != BankingState.IDLE) {
            BankingAmountDial(
                amountMinor = displayAmountMinor,
                maxMinor = activeCarryAmount.minor,
                currency = activeCarryAmount.currency,
                textStyle = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        val resolvedBankedAmount = bankedAmount
        if (showSuccessDialog && resolvedBankedAmount != null) {
            AlertDialog(
                onDismissRequest = { showSuccessDialog = false },
                confirmButton = {
                    Button(onClick = { showSuccessDialog = false }) {
                        Text("OK")
                    }
                },
                title = { Text("Banked!") },
                text = {
                    Text("You successfully banked ${resolvedBankedAmount.format()}.")
                },
            )
        }
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
            .heightIn(max = 400.dp)
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
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(transactions) { transaction ->
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
}

@Composable
private fun BankingAmountDial(
    amountMinor: Long,
    maxMinor: Long,
    currency: String,
    textStyle: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val fractionDigits = CurrencyMeta.fractionDigits(currency)
    val factorLong = pow10(fractionDigits)
    val absAmountMinor = abs(amountMinor)
    val limitMinor = maxMinor.coerceAtLeast(0L)

    val limitMinorInt = limitMinor.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val absAmountMinorInt = absAmountMinor.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    val limitMajorInt = (limitMinor / factorLong).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    val dialStyle = textStyle.copy(color = color)
    val currencyStyle = if (textStyle.fontSize.isUnspecified) {
        textStyle
    } else {
        textStyle.copy(fontSize = textStyle.fontSize * 0.6f)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (amountMinor < 0) {
            Text("-", style = dialStyle)
        }
        
        val majorValue = (absAmountMinor / factorLong).toInt().coerceIn(0, limitMajorInt)
        PhysicsWheelPicker(
            range = 0..limitMajorInt,
            value = majorValue,
            onValueChange = {},
            textStyle = dialStyle,
            enabled = false,
            itemHeight = 56.dp,
            modifier = Modifier.width(140.dp),
        )
        
        if (fractionDigits > 0) {
            Text(".", style = dialStyle)
            PhysicsWheelPicker(
                range = 0..limitMinorInt,
                value = absAmountMinorInt,
                onValueChange = {},
                textStyle = dialStyle,
                enabled = false,
                animateOnValueChange = false,
                itemHeight = 56.dp,
                modifier = Modifier.width((56 * fractionDigits).dp),
                label = { (it % factorLong).toString().padStart(fractionDigits, '0') },
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = normalizeCurrencyCode(currency),
            style = currencyStyle,
            color = color,
        )
    }
}

@Composable
private fun WaveOverlay(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val infiniteTransition = rememberInfiniteTransition()
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI.toFloat()),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                easing = LinearEasing,
            ),
        ),
    )

    Canvas(modifier = modifier) {
        if (clampedProgress <= 0f) return@Canvas
        val baseY = size.height * (1f - clampedProgress)
        val amplitude = size.height * 0.02f
        val waveLength = size.width * 0.75f
        val frequency = if (waveLength == 0f) 0f else (2f * PI.toFloat()) / waveLength
        val step = (size.width / 32f).coerceAtLeast(6f)

        val path = Path()
        path.moveTo(0f, size.height)
        path.lineTo(size.width, size.height)
        path.lineTo(size.width, baseY)

        var x = size.width
        while (x >= 0f) {
            val y = baseY + amplitude * sin((frequency * x + phase).toDouble()).toFloat()
            path.lineTo(x, y)
            x -= step
        }
        path.close()
        drawPath(path, color)
    }
}

@Composable
private fun AddExpenseDialog(
    defaultCurrency: String,
    dailyLimitMinor: Long,
    onSave: (Long, String?, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var amountMinor by remember { mutableStateOf(0L) }
    var note by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(currentLocalDate(TimeZone.currentSystemDefault())) }

    val canSave = amountMinor > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val cleanedNote = note.trim().ifBlank { null }
                    onSave(amountMinor, cleanedNote, selectedDate)
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
                            contentDescription = "Select Date"
                        )
                    }
                }
                
                if (showDatePicker) {
                    val maxDate = currentLocalDate(TimeZone.currentSystemDefault())
                    TransactionDatePicker(
                        date = selectedDate,
                        onDateChange = { selectedDate = it },
                        maxDate = maxDate,
                        modifier = Modifier.fillMaxWidth().height(140.dp), // Increased height slightly
                    )
                }
            }
        },
    )
}

@Composable
private fun SavingsTrackerScreen(
    defaultCurrency: String,
    totalSaved: Money,
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
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Total Saved", style = MaterialTheme.typography.labelMedium)
                Text(totalSaved.format(), style = MaterialTheme.typography.displayMedium)
            }
        }
        
        Text("Add Entry (Draft)", style = MaterialTheme.typography.titleMedium)
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

private fun pow10(exp: Int): Long {
    var result = 1L
    repeat(exp) { result *= 10L }
    return result
}
