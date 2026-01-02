package t.l.budget.loop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import t.l.budget.loop.ui.onboarding.OnboardingData
import t.l.budget.loop.ui.onboarding.OnboardingFlow
import t.l.budget.loop.ui.main.MainApp
import t.l.budget.loop.domain.onboarding.OnboardingRepository
import t.l.budget.loop.domain.onboarding.OnboardingSaveResult
import t.l.budget.loop.platform.createDatabase
import t.l.budget.loop.platform.rememberPlatformContext

@Composable
@Preview
fun App() {
    MaterialTheme {
        val platformContext = rememberPlatformContext()
        val database = remember(platformContext) { createDatabase(platformContext) }
        val repository = remember(database) {
            OnboardingRepository(
                settingsDao = database.settingsDao(),
            )
        }
        var appState by remember { mutableStateOf<AppState>(AppState.Loading) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            val loaded = repository.load()
            appState = if (loaded == null) AppState.Onboarding else AppState.Ready(loaded)
        }

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (val state = appState) {
                AppState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("Loading...")
                    }
                }
                AppState.Onboarding -> {
                    OnboardingFlow(
                        onFinished = { data ->
                            scope.launch {
                                when (val result = repository.save(
                                    data = data,
                                    timezone = TimeZone.currentSystemDefault(),
                                )) {
                                    is OnboardingSaveResult.Success -> {
                                        errorMessage = null
                                        appState = AppState.Ready(data)
                                    }
                                    is OnboardingSaveResult.Error -> {
                                        errorMessage = result.message
                                    }
                                }
                            }
                        },
                        errorMessage = errorMessage,
                    )
                }
                is AppState.Ready -> {
                    MainApp(onboardingData = state.data)
                }
            }
        }
    }
}

private sealed class AppState {
    data object Loading : AppState()
    data object Onboarding : AppState()
    data class Ready(val data: OnboardingData) : AppState()
}
