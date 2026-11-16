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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import t.l.budget.loop.data.db.DailyBudgetStateDao
import t.l.budget.loop.data.db.FxRateDao
import t.l.budget.loop.data.db.GoalKind
import t.l.budget.loop.data.db.SavingsEntryDao
import t.l.budget.loop.data.db.SavingsEntryEntity
import t.l.budget.loop.data.db.SavingsGoalDao
import t.l.budget.loop.data.db.SavingsGoalEntity
import t.l.budget.loop.data.db.SettingsDao
import t.l.budget.loop.data.db.SettingsEntity
import t.l.budget.loop.data.db.TransactionDao
import t.l.budget.loop.data.db.TransactionEntity
import t.l.budget.loop.domain.budget.BudgetService
import t.l.budget.loop.domain.budget.InfuseResult
import t.l.budget.loop.domain.budget.LoopMonthStats
import t.l.budget.loop.domain.budget.MonthEndResult
import t.l.budget.loop.domain.fx.ManualFxProvider
import t.l.budget.loop.domain.onboarding.OnboardingRepository
import t.l.budget.loop.domain.savings.SavingsGoalService
import t.l.budget.loop.domain.savings.SavingsService
import t.l.budget.loop.domain.transactions.TransactionService
import t.l.budget.loop.money.CurrencyMeta
import t.l.budget.loop.money.Money
import t.l.budget.loop.money.normalizeCurrencyCode
import t.l.budget.loop.platform.DailyReportScheduler
import t.l.budget.loop.platform.DailyResetScheduler
import t.l.budget.loop.platform.PlatformBackHandler
import t.l.budget.loop.platform.PlatformContext
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
    VAULT,
    SETTINGS,
}

private enum class BankingState {
    IDLE,
    FILLING,
    SUCCESS_DRAINING,
}

@Composable
fun MainApp(
    onboardingData: OnboardingData,
    platformContext: PlatformContext,
    transactionDao: TransactionDao,
    settingsDao: SettingsDao,
    dailyBudgetStateDao: DailyBudgetStateDao,
    savingsEntryDao: SavingsEntryDao,
    savingsGoalDao: SavingsGoalDao,
    fxRateDao: FxRateDao,
) {
    var screen by remember { mutableStateOf(MainScreen.TANK) }
    var showMonthlySummary by remember { mutableStateOf(false) }
    var showAddExpenseDialog by remember { mutableStateOf(false) }
    var isBanking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    PlatformBackHandler(enabled = screen != MainScreen.TANK) {
        screen = MainScreen.TANK
    }

    val transactionService = remember(transactionDao) { TransactionService(transactionDao) }
    val budgetService = remember(settingsDao, transactionDao, dailyBudgetStateDao, savingsEntryDao, savingsGoalDao) {
        BudgetService(
            settingsDao = settingsDao,
            transactionDao = transactionDao,
            dailyBudgetStateDao = dailyBudgetStateDao,
            savingsEntryDao = savingsEntryDao,
            savingsGoalDao = savingsGoalDao,
        )
    }
    val savingsService = remember(savingsEntryDao, savingsGoalDao) {
        SavingsService(savingsEntryDao, savingsGoalDao)
    }
    val goalService = remember(savingsGoalDao) { SavingsGoalService(savingsGoalDao) }
    val onboardingRepository = remember(settingsDao) { OnboardingRepository(settingsDao) }
    val fxProvider = remember(fxRateDao) { ManualFxProvider(fxRateDao) }

    var settings by remember { mutableStateOf<SettingsEntity?>(null) }
    var transactions by remember { mutableStateOf<List<TransactionEntity>>(emptyList()) }
    var goals by remember { mutableStateOf<List<SavingsGoalEntity>>(emptyList()) }
    var savingsEntries by remember { mutableStateOf<List<SavingsEntryEntity>>(emptyList()) }
    var newlyClosedMonthResult by remember { mutableStateOf<MonthEndResult?>(null) }

    val initialCurrency = normalizeCurrencyCode(onboardingData.currency.ifBlank { "USD" })
    val initialDailyMinor = parseMinor(onboardingData.dailyBudget, initialCurrency) ?: 0L
    var currency by remember { mutableStateOf(initialCurrency) }
    var dailyBudgetMinor by remember { mutableStateOf(initialDailyMinor) }
    var carryMinor by remember { mutableStateOf(0L) }
    var remainingMinor by remember { mutableStateOf(initialDailyMinor) }
    var totalSaved by remember { mutableStateOf(Money.of(0L, initialCurrency)) }
    var monthStats by remember { mutableStateOf<LoopMonthStats?>(null) }

    val activeTimeZone = remember(settings?.timezone) {
        val id = settings?.timezone
        if (id.isNullOrBlank()) {
            TimeZone.currentSystemDefault()
        } else {
            runCatching { TimeZone.of(id) }.getOrDefault(TimeZone.currentSystemDefault())
        }
    }

    suspend fun loadData() {
        val loadedSettings = settingsDao.get()
        settings = loadedSettings
        val resolvedTz = loadedSettings?.timezone
            ?.let { runCatching { TimeZone.of(it) }.getOrNull() }
            ?: TimeZone.currentSystemDefault()
        val resolvedCurrency = normalizeCurrencyCode(
            (loadedSettings?.defaultCurrency ?: onboardingData.currency).ifBlank { "USD" },
        )
        currency = resolvedCurrency

        val today = currentLocalDate(resolvedTz)
        val missedResult = budgetService.finalizeMissedMonthEnds(today, resolvedTz)
        if (missedResult != null) {
            newlyClosedMonthResult = missedResult
            showMonthlySummary = true
        }

        val state = budgetService.ensureDailyState(today, resolvedTz)
        dailyBudgetMinor = state.dailyBudgetMinor
        carryMinor = state.carryMinor
        remainingMinor = state.remainingMinor
        transactions = transactionDao.listAll()
        goals = goalService.listActiveGoals()
        savingsEntries = savingsService.listEntries()
        totalSaved = budgetService.getTotalSaved()
        monthStats = budgetService.getLoopMonthStats(today, resolvedTz)
    }

    LaunchedEffect(transactionDao, settingsDao) {
        loadData()
        val activeSettings = settingsDao.get()
        DailyResetScheduler(platformContext).schedule()
        DailyReportScheduler(platformContext).schedule(
            activeSettings?.reportTimeMinutes ?: OnboardingRepository.DEFAULT_REPORT_TIME_MINUTES,
        )
    }

    val dailyAllowanceMinor = dailyBudgetMinor + carryMinor
    val dailyBudget = Money.of(dailyBudgetMinor, currency)
    val remaining = Money.of(remainingMinor, currency)
    val carryAmount = Money.of(carryMinor, currency)
    val activeGoalName = goals.firstOrNull { !it.archived && it.allocatedMinor < it.targetMinor }?.name
        ?: goals.firstOrNull { !it.archived }?.name

    suspend fun infuseCarryOver(): InfuseResult {
        val today = currentLocalDate(activeTimeZone)
        val infused = budgetService.infuseCarryOver(today, activeTimeZone)
        loadData()
        return infused
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (screen) {
            MainScreen.TANK -> TankScreen(
                dailyBudget = dailyBudget,
                remaining = remaining,
                carryAmount = carryAmount,
                activeGoalName = activeGoalName,
                monthStats = monthStats,
                transactions = transactions,
                timeZone = activeTimeZone,
                onInfuseCarryOver = { infuseCarryOver() },
                onBankingStateChange = { isBanking = it },
                onShowMonthlySummary = { showMonthlySummary = true },
                onDeleteTransaction = { tx ->
                    scope.launch {
                        transactionService.deleteExpense(tx.id)
                        loadData()
                    }
                },
            )

            MainScreen.VAULT -> VaultAndGoalsScreen(
                defaultCurrency = currency,
                totalSaved = totalSaved,
                goals = goals,
                entries = savingsEntries,
                onAddGoal = { name, targetMinor, initialMinor, kind, targetDate ->
                    scope.launch {
                        goalService.createGoal(name, targetMinor, initialMinor, kind, targetDate)
                        loadData()
                    }
                },
                onUpdateGoal = { updated ->
                    scope.launch {
                        goalService.updateGoal(updated)
                        loadData()
                    }
                },
                onMoveGoal = { goalId, delta ->
                    scope.launch {
                        goalService.moveGoal(goalId, delta)
                        loadData()
                    }
                },
                onDeleteGoal = { goal ->
                    scope.launch {
                        goalService.deleteGoal(goal)
                        loadData()
                    }
                },
                onAddManualEntry = { amountMinor, note, goalId ->
                    scope.launch {
                        val today = currentLocalDate(activeTimeZone)
                        savingsService.addManualEntry(
                            amount = Money.of(amountMinor, currency),
                            date = today,
                            note = note,
                            goalId = goalId,
                        )
                        loadData()
                    }
                },
                onDeleteEntry = { entry ->
                    scope.launch {
                        savingsService.deleteEntry(entry)
                        loadData()
                    }
                },
            )

            MainScreen.SETTINGS -> SettingsScreen(
                settings = settings,
                repository = onboardingRepository,
                platformContext = platformContext,
                onSettingsUpdated = { loadData() },
            )
        }

        if (showMonthlySummary) {
            MonthlySummaryDialog(
                currency = currency,
                closedMonthResult = newlyClosedMonthResult ?: monthStats?.latestClosedMonthResult,
                monthStats = monthStats,
                totalSaved = totalSaved,
                onDismiss = {
                    newlyClosedMonthResult = null
                    showMonthlySummary = false
                },
            )
        }

        if (showAddExpenseDialog) {
            AddExpenseDialog(
                defaultCurrency = currency,
                dailyLimitMinor = remainingMinor.coerceAtLeast(0L),
                fxProvider = fxProvider,
                today = currentLocalDate(activeTimeZone),
                onSave = { input ->
                    val instant = input.date.atStartOfDayIn(activeTimeZone)
                    transactionService.addExpense(
                        amount = input.amount,
                        amountDefault = input.amountDefault,
                        date = instant,
                        note = input.note,
                        category = input.category,
                    )
                    loadData()
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
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .safeDrawingPadding(),
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (expanded) {
            ExtendedFloatingActionButton(
                onClick = {
                    expanded = false
                    onAddExpense()
                },
                content = { Text("Add expense") },
            )
            if (current != MainScreen.TANK) {
                ExtendedFloatingActionButton(
                    onClick = {
                        expanded = false
                        onNavigate(MainScreen.TANK)
                    },
                    content = { Text("Tank") },
                )
            }
            if (current != MainScreen.VAULT) {
                ExtendedFloatingActionButton(
                    onClick = {
                        expanded = false
                        onNavigate(MainScreen.VAULT)
                    },
                    content = { Text("Goals & Vault") },
                )
            }
            if (current != MainScreen.SETTINGS) {
                ExtendedFloatingActionButton(
                    onClick = {
                        expanded = false
                        onNavigate(MainScreen.SETTINGS)
                    },
                    content = { Text("Settings") },
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
    activeGoalName: String?,
    monthStats: LoopMonthStats?,
    transactions: List<TransactionEntity>,
    timeZone: TimeZone,
    onInfuseCarryOver: suspend () -> InfuseResult,
    onBankingStateChange: (Boolean) -> Unit,
    onShowMonthlySummary: () -> Unit,
    onDeleteTransaction: (TransactionEntity) -> Unit,
) {
    val baseColor = MaterialTheme.colorScheme.background
    val haptics = rememberHapticFeedbackManager()
    val onInfuseState by rememberUpdatedState(onInfuseCarryOver)
    val onBankingStateChangeState by rememberUpdatedState(onBankingStateChange)
    val latestCarryAmount by rememberUpdatedState(carryAmount)
    val latestRemaining by rememberUpdatedState(remaining)
    val bankScope = rememberCoroutineScope()
    val bankProgress = remember { Animatable(0f) }
    var bankingState by remember { mutableStateOf(BankingState.IDLE) }
    var infuseResult by remember { mutableStateOf<InfuseResult?>(null) }
    var lockedCarryAmount by remember { mutableStateOf<Money?>(null) }
    var lockedRemaining by remember { mutableStateOf<Money?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    val activeCarryAmount by remember {
        derivedStateOf {
            if (bankingState == BankingState.IDLE) latestCarryAmount else lockedCarryAmount ?: latestCarryAmount
        }
    }
    val activeRemaining by remember {
        derivedStateOf {
            if (bankingState == BankingState.IDLE) latestRemaining else lockedRemaining ?: latestRemaining
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
    val focusProgress by remember { derivedStateOf { bankProgress.value.coerceIn(0f, 1f) } }
    val isBankingActive by remember { derivedStateOf { focusProgress > 0f || bankingState != BankingState.IDLE } }
    val previewCarryMinorExact by remember {
        derivedStateOf { activeCarryAmount.minor.toDouble() * focusProgress }
    }
    val previewCarryMinor by remember { derivedStateOf { previewCarryMinorExact.roundToLong() } }
    val contentAlpha by remember { derivedStateOf { (1f - focusProgress).coerceIn(0f, 1f) } }
    val displayAmountMinorExact by remember {
        derivedStateOf {
            if (bankingState == BankingState.FILLING) previewCarryMinorExact else activeCarryAmount.minor.toDouble()
        }
    }
    val displayAmountMinor by remember { derivedStateOf { displayAmountMinorExact.roundToLong() } }
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
        displayRemainingMinor < 0L -> MaterialTheme.colorScheme.error.copy(alpha = 0.28f)
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
        if (bankingState != BankingState.FILLING) return@LaunchedEffect
        while (isActive && bankingState == BankingState.FILLING) {
            val intervalMs = ((1f - focusProgress) * 120f + 30f).roundToLong()
            haptics.tick()
            delay(intervalMs.coerceAtLeast(20L))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(baseColor),
    ) {
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
                                    val result = onInfuseState()
                                    infuseResult = result
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
                .safeDrawingPadding()
                .alpha(contentAlpha),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (displayRemainingMinor < 0L) "Loop Deficit" else "Daily Remaining",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (displayRemainingMinor < 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = displayRemaining.format(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (displayRemainingMinor < 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
                )

                if (monthStats != null) {
                    val spentMoney = Money.of(monthStats.spentThisMonthMinor, dailyBudget.currency).format()
                    val capMoney = Money.of(monthStats.monthlyCapMinor, dailyBudget.currency).format()
                    val overCap = monthStats.spentThisMonthMinor > monthStats.monthlyCapMinor
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusChip(label = "Streak: ${monthStats.cleanStreakDays}d", isWarning = false)
                        StatusChip(label = "Month: $spentMoney / $capMoney", isWarning = overCap)
                    }
                }

                if (canBank) {
                    val targetSuffix = activeGoalName?.let { " -> $it" } ?: " to Vault"
                    Text(
                        text = "Residuum ${activeCarryAmount.format()} · Hold tank to infuse$targetSuffix",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(onClick = onShowMonthlySummary) {
                    Text("Monthly summary")
                }
            }

            if (!isBankingActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 196.dp, bottom = 92.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    TransactionHistory(
                        transactions = transactions,
                        defaultCurrency = dailyBudget.currency,
                        timeZone = timeZone,
                        onDeleteTransaction = onDeleteTransaction,
                    )
                }
            }
        }

        WaveOverlay(
            progress = focusProgress,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxSize(),
        )

        if (bankingState != BankingState.IDLE) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = activeGoalName?.let { "Infusing into $it" } ?: "Infusing into Vault",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                BankingAmountDial(
                    amountMinor = displayAmountMinor,
                    maxMinor = activeCarryAmount.minor,
                    currency = activeCarryAmount.currency,
                    textStyle = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        val resolvedResult = infuseResult
        if (showSuccessDialog && resolvedResult != null) {
            AlertDialog(
                onDismissRequest = { showSuccessDialog = false },
                confirmButton = {
                    Button(onClick = { showSuccessDialog = false }) {
                        Text("OK")
                    }
                },
                title = { Text("Residuum Infused!") },
                text = {
                    val dest = resolvedResult.creditedGoalName?.let { " into \"$it\"" } ?: " to your Vault"
                    Text("You banked ${resolvedResult.amount.format()}$dest.")
                },
            )
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    isWarning: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (isWarning) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isWarning) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun TransactionHistory(
    transactions: List<TransactionEntity>,
    defaultCurrency: String,
    timeZone: TimeZone,
    onDeleteTransaction: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = currentLocalDate(timeZone)
    var pendingDelete by remember { mutableStateOf<TransactionEntity?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth(0.88f)
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
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(transactions, key = { it.id }) { transaction ->
                    val dateLabel = formatTransactionDateLabel(transaction.date, today, timeZone)
                    val meta = buildString {
                        append(dateLabel)
                        if (!transaction.category.isNullOrBlank()) append(" · ${transaction.category}")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = transaction.note?.ifBlank { null }
                                    ?: transaction.category?.ifBlank { null }
                                    ?: "Expense",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = Money.of(transaction.amountDefaultMinor, defaultCurrency).format(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (normalizeCurrencyCode(transaction.currency) != normalizeCurrencyCode(defaultCurrency)) {
                                Text(
                                    text = Money.of(transaction.amountMinor, transaction.currency).format(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { pendingDelete = transaction }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete expense")
                        }
                    }
                }
            }
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTransaction(toDelete)
                        pendingDelete = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
            title = { Text("Delete expense?") },
            text = {
                val amountText = Money.of(toDelete.amountDefaultMinor, defaultCurrency).format()
                Text("Remove $amountText${toDelete.note?.let { " ($it)" }.orEmpty()} and recalculate your loop?")
            },
        )
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
private fun MonthlySummaryDialog(
    currency: String,
    closedMonthResult: MonthEndResult?,
    monthStats: LoopMonthStats?,
    totalSaved: Money,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Got it")
            }
        },
        title = { Text("Loop Summary") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (closedMonthResult != null) {
                    val resultMoney = Money.of(closedMonthResult.savedMinor, currency)
                    val headline = if (resultMoney.minor >= 0L) {
                        "Last closed month (${closedMonthResult.date}): saved ${resultMoney.format()}."
                    } else {
                        "Last closed month (${closedMonthResult.date}): overspent by ${resultMoney.abs().format()}."
                    }
                    Text(headline, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        text = "No completed month-end closed yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (monthStats != null) {
                    val spent = Money.of(monthStats.spentThisMonthMinor, currency).format()
                    val cap = Money.of(monthStats.monthlyCapMinor, currency).format()
                    val headroom = Money.of(monthStats.monthlyCapMinor - monthStats.spentThisMonthMinor, currency).format()
                    Text("Current month spent: $spent / $cap")
                    Text("Remaining monthly headroom: $headroom")
                    Text("Current clean streak: ${monthStats.cleanStreakDays} days")
                }

                Text("Total Vault Saved: ${totalSaved.format()}")
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
