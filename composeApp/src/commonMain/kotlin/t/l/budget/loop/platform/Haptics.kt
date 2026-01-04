package t.l.budget.loop.platform

import androidx.compose.runtime.Composable

interface HapticFeedbackManager {
    fun tick()
}

@Composable
expect fun rememberHapticFeedbackManager(): HapticFeedbackManager
