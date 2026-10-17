package ir.v2rayng.compose.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
fun V2rayNGTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NightColors, typography = Typography(), content = content)
}
