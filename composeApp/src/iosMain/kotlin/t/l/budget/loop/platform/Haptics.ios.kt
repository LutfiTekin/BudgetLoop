package t.l.budget.loop.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyleLight

@Composable
actual fun rememberHapticFeedbackManager(): HapticFeedbackManager {
    return remember { IosHapticFeedbackManager() }
}

private class IosHapticFeedbackManager : HapticFeedbackManager {
    private val generator = UIImpactFeedbackGenerator(UIImpactFeedbackStyleLight)

    override fun tick() {
        generator.prepare()
        generator.impactOccurred()
    }
}
