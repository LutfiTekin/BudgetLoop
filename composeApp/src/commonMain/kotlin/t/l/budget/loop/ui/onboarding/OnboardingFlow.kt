package t.l.budget.loop.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

data class CardDraft(
    val name: String,
    val limit: String,
    val debt: String,
    val currency: String,
)

data class BankDraft(
    val name: String,
    val balance: String,
    val currency: String,
)

data class OnboardingData(
    val currency: String,
    val dailyBudget: String,
    val cards: List<CardDraft>,
    val banks: List<BankDraft>,
)

private enum class OnboardingStep {
    WELCOME,
    CURRENCY,
    DAILY_BUDGET,
    CARDS,
    BANKS,
    DONE,
}

@Composable
fun OnboardingFlow(
    onFinished: (OnboardingData) -> Unit,
    errorMessage: String? = null,
) {
    var step by remember { mutableStateOf(OnboardingStep.WELCOME) }
    var currency by remember { mutableStateOf("") }
    var dailyBudget by remember { mutableStateOf("") }
    val cards = remember { mutableStateListOf<CardDraft>() }
    val banks = remember { mutableStateListOf<BankDraft>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
            )
        }
        when (step) {
            OnboardingStep.WELCOME -> WelcomeStep(
                onStart = { step = OnboardingStep.CURRENCY },
            )
            OnboardingStep.CURRENCY -> CurrencyStep(
                currency = currency,
                onCurrencyChange = { currency = it.uppercase() },
                onNext = { step = OnboardingStep.DAILY_BUDGET },
            )
            OnboardingStep.DAILY_BUDGET -> DailyBudgetStep(
                dailyBudget = dailyBudget,
                onBudgetChange = { dailyBudget = it },
                onBack = { step = OnboardingStep.CURRENCY },
                onNext = { step = OnboardingStep.CARDS },
            )
            OnboardingStep.CARDS -> CardsStep(
                defaultCurrency = currency,
                cards = cards,
                onBack = { step = OnboardingStep.DAILY_BUDGET },
                onNext = { step = OnboardingStep.BANKS },
            )
            OnboardingStep.BANKS -> BanksStep(
                defaultCurrency = currency,
                banks = banks,
                onBack = { step = OnboardingStep.CARDS },
                onNext = { step = OnboardingStep.DONE },
            )
            OnboardingStep.DONE -> DoneStep(
                currency = currency,
                dailyBudget = dailyBudget,
                cardCount = cards.size,
                bankCount = banks.size,
                onFinish = {
                    onFinished(
                        OnboardingData(
                            currency = currency,
                            dailyBudget = dailyBudget,
                            cards = cards.toList(),
                            banks = banks.toList(),
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun WelcomeStep(
    onStart: () -> Unit,
) {
    Text("Budget Loop", style = MaterialTheme.typography.headlineMedium)
    Text("Let's start the Loop.")
    Spacer(modifier = Modifier.height(8.dp))
    Button(onClick = onStart) {
        Text("Start")
    }
}

@Composable
private fun CurrencyStep(
    currency: String,
    onCurrencyChange: (String) -> Unit,
    onNext: () -> Unit,
) {
    Text("Default Currency", style = MaterialTheme.typography.titleLarge)
    TextField(
        value = currency,
        onValueChange = onCurrencyChange,
        label = { Text("Currency (e.g., USD)") },
        singleLine = true,
    )
    Button(
        onClick = onNext,
        enabled = currency.length == 3,
    ) {
        Text("Continue")
    }
}

@Composable
private fun DailyBudgetStep(
    dailyBudget: String,
    onBudgetChange: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Text("Daily Budget", style = MaterialTheme.typography.titleLarge)
    TextField(
        value = dailyBudget,
        onValueChange = onBudgetChange,
        label = { Text("Amount") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
    StepActions(
        onBack = onBack,
        onNext = onNext,
        nextEnabled = dailyBudget.isNotBlank(),
    )
}

@Composable
private fun CardsStep(
    defaultCurrency: String,
    cards: MutableList<CardDraft>,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }
    var debt by remember { mutableStateOf("") }
    var currency by remember(defaultCurrency) { mutableStateOf(defaultCurrency) }

    Text("Add Credit Cards", style = MaterialTheme.typography.titleLarge)
    TextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Card name") },
        singleLine = true,
    )
    TextField(
        value = limit,
        onValueChange = { limit = it },
        label = { Text("Limit") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
    TextField(
        value = debt,
        onValueChange = { debt = it },
        label = { Text("Current debt") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
    TextField(
        value = currency,
        onValueChange = { currency = it.uppercase() },
        label = { Text("Currency") },
        singleLine = true,
    )
    Button(
        onClick = {
            cards.add(CardDraft(name, limit, debt, currency))
            name = ""
            limit = ""
            debt = ""
            currency = defaultCurrency
        },
        enabled = name.isNotBlank() && limit.isNotBlank() && debt.isNotBlank() && currency.length == 3,
    ) {
        Text("Add card")
    }

    if (cards.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text("Cards added:", style = MaterialTheme.typography.titleSmall)
        cards.forEach { card ->
            Text("- ${card.name} (${card.currency})")
        }
    }

    StepActions(onBack = onBack, onNext = onNext, nextEnabled = true)
}

@Composable
private fun BanksStep(
    defaultCurrency: String,
    banks: MutableList<BankDraft>,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var currency by remember(defaultCurrency) { mutableStateOf(defaultCurrency) }

    Text("Add Bank Accounts (Optional)", style = MaterialTheme.typography.titleLarge)
    TextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Bank name") },
        singleLine = true,
    )
    TextField(
        value = balance,
        onValueChange = { balance = it },
        label = { Text("Balance") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
    )
    TextField(
        value = currency,
        onValueChange = { currency = it.uppercase() },
        label = { Text("Currency") },
        singleLine = true,
    )
    Button(
        onClick = {
            banks.add(BankDraft(name, balance, currency))
            name = ""
            balance = ""
            currency = defaultCurrency
        },
        enabled = name.isNotBlank() && balance.isNotBlank() && currency.length == 3,
    ) {
        Text("Add bank")
    }

    if (banks.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text("Banks added:", style = MaterialTheme.typography.titleSmall)
        banks.forEach { bank ->
            Text("- ${bank.name} (${bank.currency})")
        }
    }

    StepActions(onBack = onBack, onNext = onNext, nextEnabled = true)
}

@Composable
private fun DoneStep(
    currency: String,
    dailyBudget: String,
    cardCount: Int,
    bankCount: Int,
    onFinish: () -> Unit,
) {
    Text("Ready to Loop", style = MaterialTheme.typography.titleLarge)
    Text("Currency: $currency")
    Text("Daily budget: $dailyBudget")
    Text("Cards: $cardCount")
    Text("Banks: $bankCount")
    Spacer(modifier = Modifier.height(8.dp))
    Button(onClick = onFinish) {
        Text("Finish")
    }
}

@Composable
private fun StepActions(
    onBack: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onBack) {
            Text("Back")
        }
        Button(
            onClick = onNext,
            enabled = nextEnabled,
        ) {
            Text("Continue")
        }
    }
}
