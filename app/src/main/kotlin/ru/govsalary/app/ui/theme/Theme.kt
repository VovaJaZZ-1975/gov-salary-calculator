package ru.govsalary.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Все цвета приложения объявлены ТОЛЬКО здесь (мастер-промпт Фазы 4, п.21: "цвета должны быть
 * централизованы в Theme, не прописывать цвета хаотично по всему проекту"). Экраны используют
 * исключительно MaterialTheme.colorScheme.* — ни одного Color(0x...) вне этого файла.
 * Палитра: тёмно-синий / белый / светло-серый / золотистые акценты.
 */
private val DeepNavy = Color(0xFF0D2547)
private val NavySurfaceDark = Color(0xFF13294B)
private val Gold = Color(0xFFD4AF37)
private val GoldDim = Color(0xFFB8944B)
private val LightGray = Color(0xFFF2F3F5)
private val MidGray = Color(0xFFCBD2DA)
private val White = Color(0xFFFFFFFF)
private val ErrorRed = Color(0xFFB3261E)
private val SuccessGreen = Color(0xFF2E7D32)

val LightColors = lightColorScheme(
    primary = DeepNavy,
    onPrimary = White,
    primaryContainer = LightGray,
    onPrimaryContainer = DeepNavy,
    secondary = Gold,
    onSecondary = DeepNavy,
    secondaryContainer = Color(0xFFFCEFC7),
    onSecondaryContainer = Color(0xFF4A3B00),
    background = White,
    onBackground = DeepNavy,
    surface = White,
    onSurface = DeepNavy,
    surfaceVariant = LightGray,
    onSurfaceVariant = Color(0xFF44474A),
    outline = MidGray,
    error = ErrorRed,
    onError = White,
)

val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = DeepNavy,
    primaryContainer = NavySurfaceDark,
    onPrimaryContainer = Gold,
    secondary = GoldDim,
    onSecondary = DeepNavy,
    background = Color(0xFF0A1930),
    onBackground = White,
    surface = NavySurfaceDark,
    onSurface = White,
    surfaceVariant = Color(0xFF1B3355),
    onSurfaceVariant = MidGray,
    outline = Color(0xFF3C557C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

/** Семантический цвет "прогресс/успех" — вынесен отдельно, т.к. Material3 ColorScheme его не предоставляет. */
val ColorScheme_Success: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF8BD68B) else SuccessGreen

val AppTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 18.sp),
)

enum class AppThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun GovSalaryTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
