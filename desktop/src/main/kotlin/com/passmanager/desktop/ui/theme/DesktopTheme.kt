package com.passmanager.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

// ── Android-matching color palette ──────────────────────────────────────

// Category tints (shared across themes)
val CategoryLoginTint = Color(0xFF0D9488)
val CategoryCardTint = Color(0xFFB45309)
val CategoryNoteTint = Color(0xFF7C3AED)
val CategoryIdentityTint = Color(0xFF0284C7)

// Desktop LCD screens render darker than phone OLED — tones are lifted
// slightly for better contrast and readability on non-emissive displays.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF0D9488),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFFA5B4FC),
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF3730A3),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF422006),
    tertiaryContainer = Color(0xFF92400E),
    onTertiaryContainer = Color(0xFFFEF3C7),
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF7F1D1D),
    errorContainer = Color(0xFF991B1B),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Color(0xFF111827),           // lifted from 0x0C0F14
    onBackground = Color(0xFFE8ECF2),
    surface = Color(0xFF1A2033),              // lifted from 0x141820
    onSurface = Color(0xFFE8ECF2),
    surfaceVariant = Color(0xFF252D3C),       // lifted from 0x1E2430
    onSurfaceVariant = Color(0xFFA0A8B8),
    outline = Color(0xFF4A5268),              // lifted from 0x3B4254
    outlineVariant = Color(0xFF2A3040),
    surfaceDim = Color(0xFF111827),
    surfaceBright = Color(0xFF2A3040),
    inverseSurface = Color(0xFFE8ECF2),
    inverseOnSurface = Color(0xFF1A1E28),
    inversePrimary = Color(0xFF0D7A70),
    surfaceContainerLowest = Color(0xFF0C1018),
    surfaceContainerLow = Color(0xFF141B2A),
    surfaceContainer = Color(0xFF1A2233),
    surfaceContainerHigh = Color(0xFF283040), // lifted from 0x1C2230 (fixes near-black digit boxes)
    surfaceContainerHighest = Color(0xFF303848), // lifted from 0x24293A
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0D7A70),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF00312C),
    secondary = Color(0xFF4338CA),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Color(0xFFB45309),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF422006),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    background = Color(0xFFF8FAFF),           // slightly cooler white
    onBackground = Color(0xFF111318),
    surface = Color.White,
    onSurface = Color(0xFF111318),
    surfaceVariant = Color(0xFFE4E8F0),
    onSurfaceVariant = Color(0xFF3E4450),
    outline = Color(0xFF6E7688),
    outlineVariant = Color(0xFFC8CDD8),
    surfaceDim = Color(0xFFD8DCE6),
    surfaceBright = Color(0xFFF8FAFF),
    inverseSurface = Color(0xFF1A1E28),
    inverseOnSurface = Color(0xFFEFF1F6),
    inversePrimary = Color(0xFF5EEAD4),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F2F8),
    surfaceContainer = Color(0xFFEAECF4),
    surfaceContainerHigh = Color(0xFFE4E7EF),
    surfaceContainerHighest = Color(0xFFDDE1EA),
)

// ── Typography with Poppins ─────────────────────────────────────────────

private val PoppinsFamily = try {
    FontFamily(
        Font(resource = "fonts/Poppins-Regular.ttf", weight = FontWeight.Normal),
        Font(resource = "fonts/Poppins-Medium.ttf", weight = FontWeight.Medium),
        Font(resource = "fonts/Poppins-SemiBold.ttf", weight = FontWeight.SemiBold),
        Font(resource = "fonts/Poppins-Bold.ttf", weight = FontWeight.Bold),
    )
} catch (_: Exception) {
    // Fallback if font files are not bundled yet
    FontFamily.Default
}

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
)

// ── Shapes (matching Android) ───────────────────────────────────────────

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// ── Theme composable ────────────────────────────────────────────────────

@Composable
fun PassManagerDesktopTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
