package t.l.budget.loop.platform

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

@Composable
actual fun rememberHapticFeedbackManager(): HapticFeedbackManager {
    val view = LocalView.current
    return remember(view) { AndroidHapticFeedbackManager(view) }
}

private class AndroidHapticFeedbackManager(
    private val view: View,
) : HapticFeedbackManager {
    override fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}
