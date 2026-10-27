package ir.v2rayng.compose.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val AccentOptions = listOf(
    "Green" to Color(0xFF2BD48A),
    "Blue" to Color(0xFF60A5FA),
    "Purple" to Color(0xFFB39BF5),
    "Orange" to Color(0xFFFFA94D),
    "Red" to Color(0xFFEC6E6E),
    "Yellow" to Color(0xFFF4D27A),
    "Cyan" to Color(0xFF7FD8E8),
    "Pink" to Color(0xFFE689C3),
)

/** Semantic status colors shared by the status pill, connect ring, ping chips and log badges. */
object StatusColors {
    val Idle = Color(0xFF9BA8B4)
    val Connecting = Color(0xFFFFB24A)
    val Connected = Color(0xFF2BD48A)
    val Error = Color(0xFFFF7C7C)
    val Up = Color(0xFFEC6E6E)
    val Down = Color(0xFF7FD8E8)
}

private val NightColors = darkColorScheme(
    primary = Color(0xFF2BD48A),
    onPrimary = Color(0xFF06271B),
    primaryContainer = Color(0xFF0B4A33),
    onPrimaryContainer = Color(0xFF9BF3CB),
    secondary = Color(0xFF8AB7F7),
    onSecondary = Color(0xFF0A2347),
    tertiary = Color(0xFFFFB24A),
    onTertiary = Color(0xFF3A2400),
    background = Color(0xFF090D13),
    onBackground = Color(0xFFE3EBF2),
    surface = Color(0xFF101722),
    onSurface = Color(0xFFE3EBF2),
    surfaceVariant = Color(0xFF182231),
    onSurfaceVariant = Color(0xFF9BA8B4),
    surfaceContainerLowest = Color(0xFF070A0F),
    surfaceContainerLow = Color(0xFF0D131C),
    surfaceContainer = Color(0xFF111924),
    surfaceContainerHigh = Color(0xFF16202D),
    surfaceContainerHighest = Color(0xFF1C2836),
    outline = Color(0xFF2C3847),
    outlineVariant = Color(0xFF212C3A),
    error = Color(0xFFFF7C7C),
    onError = Color(0xFF3A0A0A),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFC2C2),
)

private val DayColors = lightColorScheme(
    primary = Color(0xFF0E9E68),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F0D7),
    onPrimaryContainer = Color(0xFF04301F),
    secondary = Color(0xFF2F5EA8),
    onSecondary = Color.White,
    tertiary = Color(0xFFB4630A),
    onTertiary = Color.White,
    background = Color(0xFFF5F4F0),
    onBackground = Color(0xFF1C2026),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C2026),
    surfaceVariant = Color(0xFFECEAE5),
    onSurfaceVariant = Color(0xFF4C525A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAF9F6),
    surfaceContainer = Color(0xFFF2F1ED),
    surfaceContainerHigh = Color(0xFFECEAE5),
    surfaceContainerHighest = Color(0xFFE5E3DE),
    outline = Color(0xFFD3D0CA),
    outlineVariant = Color(0xFFE3E0DA),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

private fun appTypography(): Typography {
    val t = Typography()
    return t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
        labelMedium = t.labelMedium.copy(letterSpacing = 0.3.sp),
    )
}

@Composable
fun V2rayNGTheme(dark: Boolean = true, accent: Int = 0, custom: Long = -1L, content: @Composable () -> Unit) {
    val a = if (custom >= 0L) {
        Color(custom)
    } else {
        AccentOptions[((accent % AccentOptions.size) + AccentOptions.size) % AccentOptions.size].second
    }
    val base = if (dark) NightColors else DayColors
    val onA = if (a.luminance() >= 0.55f) Color(0xFF06281C) else Color.White
    // containers follow the chosen accent instead of staying green whatever the user picked
    val container = a.copy(alpha = if (dark) 0.22f else 0.18f).compositeOver(base.surface)
    val onContainer = if (dark) lerp(a.copy(alpha = 1f), Color.White, 0.35f) else Color(0xFF14181D)
    MaterialTheme(
        colorScheme = base.copy(
            primary = a,
            onPrimary = onA,
            primaryContainer = container,
            onPrimaryContainer = onContainer,
            secondaryContainer = container,
            onSecondaryContainer = onContainer,
            surfaceTint = a,
        ),
        typography = appTypography(),
        shapes = AppShapes,
        content = content,
    )
}
