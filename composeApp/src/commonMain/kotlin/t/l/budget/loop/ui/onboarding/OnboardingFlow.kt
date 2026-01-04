package t.l.budget.loop.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

data class OnboardingData(
    val currency: String,
    val dailyBudget: String,
)

private enum class OnboardingStep {
    WELCOME,
    CURRENCY,
    DAILY_BUDGET,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeContentPadding()
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
                onNext = { step = OnboardingStep.DONE },
            )
            OnboardingStep.DONE -> DoneStep(
                currency = currency,
                dailyBudget = dailyBudget,
                onFinish = {
                    onFinished(
                        OnboardingData(
                            currency = currency,
                            dailyBudget = dailyBudget,
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
private fun DoneStep(
    currency: String,
    dailyBudget: String,
    onFinish: () -> Unit,
) {
    Text("Ready to Loop", style = MaterialTheme.typography.titleLarge)
    Text("Currency: $currency")
    Text("Daily budget: $dailyBudget")
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