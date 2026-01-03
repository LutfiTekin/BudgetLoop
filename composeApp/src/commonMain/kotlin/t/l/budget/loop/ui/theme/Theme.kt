package t.l.budget.loop.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val DeepViolet = Color(0xFF2E2645)
val DarkViolet = Color(0xFF3A2F5E)

val MutedPurple = Color(0xFF8F5FA6)
val SoftViolet = Color(0xFFB894C6)
val GrayLavender = Color(0xFF9E8FB3)

val PaleBeige = Color(0xFFF1E6D8)

val CoolLavenderGray = Color(0xFFC6BED6)
val DisabledLavender = Color(0xFF6E638A)

val AppColorScheme = darkColorScheme(
    primary = MutedPurple,
    secondary = SoftViolet,
    tertiary = GrayLavender,
    background = DeepViolet,
    surface = DarkViolet,
    surfaceVariant = DarkViolet,
    onPrimary = PaleBeige,
    onSecondary = PaleBeige,
    onTertiary = PaleBeige,
    onBackground = CoolLavenderGray,
    onSurface = CoolLavenderGray,
    onSurfaceVariant = CoolLavenderGray,
    outline = DisabledLavender,
)
