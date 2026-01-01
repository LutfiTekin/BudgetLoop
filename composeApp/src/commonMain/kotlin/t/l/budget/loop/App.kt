package t.l.budget.loop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import org.jetbrains.compose.ui.tooling.preview.Preview
import t.l.budget.loop.ui.onboarding.OnboardingData
import t.l.budget.loop.ui.onboarding.OnboardingFlow
import t.l.budget.loop.ui.main.MainApp

@Composable
@Preview
fun App() {
    MaterialTheme {
        var onboardingData by remember { mutableStateOf<OnboardingData?>(null) }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (onboardingData == null) {
                OnboardingFlow(
                    onFinished = { data -> onboardingData = data },
                )
            } else {
                MainApp(onboardingData = onboardingData!!)
            }
        }
    }
}
