package t.l.budget.loop.platform

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // iOS does not use an Android system back button.
}
