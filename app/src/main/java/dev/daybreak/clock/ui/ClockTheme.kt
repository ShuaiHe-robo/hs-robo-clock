package dev.daybreak.clock.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.daybreak.clock.R

// Georgia-compatible, locally bundled under the SIL Open Font License.
val ClockNumerals = FontFamily(Font(R.font.gelasio_regular, FontWeight.Normal))
// Bundle the Chinese face as well: a Latin serif alone falls back to system sans.
val ClockChinese = FontFamily(Font(R.font.wenkai_regular, FontWeight.Normal))

private val Dark = darkColorScheme(
    primary = Color(0xFF9BD5BA), onPrimary = Color(0xFF112C21), primaryContainer = Color(0xFF263E33), onPrimaryContainer = Color(0xFFC5EAD6),
    secondary = Color(0xFF9BD5BA), onSecondary = Color(0xFF112C21), secondaryContainer = Color(0xFF263E33), onSecondaryContainer = Color(0xFFC5EAD6),
    background = Color(0xFF111716), onBackground = Color(0xFFEAF1ED),
    surface = Color(0xFF111716), onSurface = Color(0xFFEAF1ED), surfaceVariant = Color(0xFF202A25), onSurfaceVariant = Color(0xFFB3C2B9),
    surfaceContainer = Color(0xFF19221E), surfaceContainerLow = Color(0xFF19221E), surfaceContainerHigh = Color(0xFF232E28),
    surfaceContainerHighest = Color(0xFF2B3830), surfaceDim = Color(0xFF111716), surfaceBright = Color(0xFF303E34), surfaceTint = Color(0xFF9BD5BA),
    outline = Color(0xFF728279), outlineVariant = Color(0xFF35453B), error = Color(0xFFFFB4AB)
)
private val Light = lightColorScheme(
    primary = Color(0xFF28664A), onPrimary = Color.White, primaryContainer = Color(0xFFD8EFDF), onPrimaryContainer = Color(0xFF193A29),
    secondary = Color(0xFF28664A), onSecondary = Color.White, secondaryContainer = Color(0xFFD8EFDF), onSecondaryContainer = Color(0xFF193A29),
    background = Color(0xFFF4F7F4), onBackground = Color(0xFF17251C), surface = Color(0xFFF4F7F4), onSurface = Color(0xFF17251C),
    surfaceVariant = Color(0xFFE5EDE6), onSurfaceVariant = Color(0xFF4D5E51),
    surfaceContainer = Color(0xFFE9F0EA), surfaceContainerLow = Color(0xFFFFFFFF), surfaceContainerHigh = Color(0xFFE0EAE1),
    surfaceContainerHighest = Color(0xFFD8E4DA), surfaceDim = Color(0xFFD1DDD3), surfaceBright = Color(0xFFF4F7F4), surfaceTint = Color(0xFF28664A),
    outline = Color(0xFF6B7C70), outlineVariant = Color(0xFFC3D2C7)
)
// Morning mint colors, Kai Chinese text, and warm-paper serif time numerals.
@Composable
fun ClockTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light,
        typography = Typography(
            displayLarge = TextStyle(fontFamily = ClockChinese, fontWeight = FontWeight.Normal, fontSize = 80.sp, lineHeight = 96.sp),
            displayMedium = TextStyle(fontFamily = ClockChinese, fontSize = 45.sp, lineHeight = 52.sp),
            displaySmall = TextStyle(fontFamily = ClockChinese, fontSize = 36.sp, lineHeight = 44.sp),
            headlineLarge = TextStyle(fontFamily = ClockChinese, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 40.sp),
            headlineMedium = TextStyle(fontFamily = ClockChinese, fontSize = 28.sp, lineHeight = 36.sp),
            headlineSmall = TextStyle(fontFamily = ClockChinese, fontSize = 24.sp, lineHeight = 32.sp),
            titleLarge = TextStyle(fontFamily = ClockChinese, fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 30.sp),
            titleMedium = TextStyle(fontFamily = ClockChinese, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
            titleSmall = TextStyle(fontFamily = ClockChinese, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
            bodyLarge = TextStyle(fontFamily = ClockChinese, fontSize = 16.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontFamily = ClockChinese, fontSize = 14.sp, lineHeight = 22.sp),
            bodySmall = TextStyle(fontFamily = ClockChinese, fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontFamily = ClockChinese, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
            labelMedium = TextStyle(fontFamily = ClockChinese, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
            labelSmall = TextStyle(fontFamily = ClockChinese, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
        ), content = content)
}
