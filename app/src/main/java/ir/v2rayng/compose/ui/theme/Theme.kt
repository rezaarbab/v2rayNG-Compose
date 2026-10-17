package ir.v2rayng.compose.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val AccentOptions = listOf(
    "سبز" to Color(0xFF2BD48A),
    "آبی" to Color(0xFF60A5FA),
    "بنفش" to Color(0xFFB39BF5),
    "نارنجی" to Color(0xFFFFA94D),
)

private val NightColors = darkColorScheme(
    primary = Color(0xFF2BD48A),
    onPrimary = Color(0xFF06271B),
    primaryContainer = Color(0xFF0B4A33),
    onPrimaryContainer = Color(0xFF9BF3CB),
    secondary = Color(0xFF8AB7F7),
    onSecondary = Color(0xFF0A2347),
    tertiary = Color(0xFFFFB24A),
    onTertiary = Color(0xFF3A2400),
    background = Color(0xFF0B0F16),
    onBackground = Color(0xFFE3EBF2),
    surface = Color(0xFF111823),
    onSurface = Color(0xFFE3EBF2),
    surfaceVariant = Color(0xFF1A2433),
    onSurfaceVariant = Color(0xFF9BA8B4),
    outline = Color(0xFF2C3847),
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
    background = Color(0xFFF7F5F1),
    onBackground = Color(0xFF20242A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF20242A),
    surfaceVariant = Color(0xFFE9E7E2),
    onSurfaceVariant = Color(0xFF4C525A),
    outline = Color(0xFFD6D3CD),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

@Composable
fun V2rayNGTheme(dark: Boolean = true, accent: Int = 0, content: @Composable () -> Unit) {
    val a = AccentOptions[((accent % AccentOptions.size) + AccentOptions.size) % AccentOptions.size].second
    val base = if (dark) NightColors else DayColors
    val onA = if (a.luminance() >= 0.55f) Color(0xFF06281C) else Color.White
    MaterialTheme(
        colorScheme = base.copy(primary = a, onPrimary = onA),
        typography = Typography(),
        content = content,
    )
}
