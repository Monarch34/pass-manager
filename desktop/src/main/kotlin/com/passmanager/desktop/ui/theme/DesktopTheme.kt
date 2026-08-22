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
import com.passmanager.protocol.design.Palette

// ── Android-matching color palette ──────────────────────────────────────
//
// Every value comes from [Palette], the shared token object in :protocol, so the phone and the
// desktop cannot drift apart. Nothing here is a literal.

// Category tints (shared across themes)
val CategoryLoginTint = Color(Palette.CATEGORY_LOGIN_TINT)
val CategoryCardTint = Color(Palette.CATEGORY_CARD_TINT)
val CategoryNoteTint = Color(Palette.CATEGORY_NOTE_TINT)
val CategoryIdentityTint = Color(Palette.CATEGORY_IDENTITY_TINT)
val CategoryBankTint = Color(Palette.CATEGORY_BANK_TINT)

// Desktop LCD screens render darker than phone OLED — tones are lifted
// slightly for better contrast and readability on non-emissive displays.
// That lift now lives in the shared palette as the `_LIFTED` tokens; this
// scheme picks one wherever it exists and the shared token otherwise.
private val DarkColors = darkColorScheme(
    primary = Color(Palette.DARK_PRIMARY),
    onPrimary = Color(Palette.DARK_ON_PRIMARY),
    primaryContainer = Color(Palette.DARK_PRIMARY_CONTAINER),
    onPrimaryContainer = Color(Palette.DARK_ON_PRIMARY_CONTAINER),
    secondary = Color(Palette.DARK_SECONDARY),
    onSecondary = Color(Palette.DARK_ON_SECONDARY),
    secondaryContainer = Color(Palette.DARK_SECONDARY_CONTAINER),
    onSecondaryContainer = Color(Palette.DARK_ON_SECONDARY_CONTAINER),
    tertiary = Color(Palette.DARK_TERTIARY),
    onTertiary = Color(Palette.DARK_ON_TERTIARY),
    tertiaryContainer = Color(Palette.DARK_TERTIARY_CONTAINER),
    onTertiaryContainer = Color(Palette.DARK_ON_TERTIARY_CONTAINER),
    error = Color(Palette.DARK_ERROR),
    onError = Color(Palette.DARK_ON_ERROR),
    errorContainer = Color(Palette.DARK_ERROR_CONTAINER),
    onErrorContainer = Color(Palette.DARK_ON_ERROR_CONTAINER),
    background = Color(Palette.DARK_BACKGROUND_LIFTED),
    onBackground = Color(Palette.DARK_ON_BACKGROUND),
    surface = Color(Palette.DARK_SURFACE_LIFTED),
    onSurface = Color(Palette.DARK_ON_SURFACE),
    surfaceVariant = Color(Palette.DARK_SURFACE_VARIANT_LIFTED),
    onSurfaceVariant = Color(Palette.DARK_ON_SURFACE_VARIANT),
    outline = Color(Palette.DARK_OUTLINE_LIFTED),
    outlineVariant = Color(Palette.DARK_OUTLINE_VARIANT),
    surfaceDim = Color(Palette.DARK_SURFACE_DIM_LIFTED),
    surfaceBright = Color(Palette.DARK_SURFACE_BRIGHT),
    inverseSurface = Color(Palette.DARK_INVERSE_SURFACE),
    inverseOnSurface = Color(Palette.DARK_INVERSE_ON_SURFACE),
    inversePrimary = Color(Palette.DARK_INVERSE_PRIMARY),
    surfaceContainerLowest = Color(Palette.DARK_SURFACE_CONTAINER_LOWEST_LIFTED),
    surfaceContainerLow = Color(Palette.DARK_SURFACE_CONTAINER_LOW_LIFTED),
    surfaceContainer = Color(Palette.DARK_SURFACE_CONTAINER_LIFTED),
    surfaceContainerHigh = Color(Palette.DARK_SURFACE_CONTAINER_HIGH_LIFTED),
    surfaceContainerHighest = Color(Palette.DARK_SURFACE_CONTAINER_HIGHEST_LIFTED),
)

// Mirror of the dark lift: on LCD the off-white canvas reads as grey, so the
// light scheme takes the brighter, slightly cooler `_LIFTED` canvas tones.
private val LightColors = lightColorScheme(
    primary = Color(Palette.LIGHT_PRIMARY),
    onPrimary = Color(Palette.LIGHT_ON_PRIMARY),
    primaryContainer = Color(Palette.LIGHT_PRIMARY_CONTAINER),
    onPrimaryContainer = Color(Palette.LIGHT_ON_PRIMARY_CONTAINER),
    secondary = Color(Palette.LIGHT_SECONDARY),
    onSecondary = Color(Palette.LIGHT_ON_SECONDARY),
    secondaryContainer = Color(Palette.LIGHT_SECONDARY_CONTAINER),
    onSecondaryContainer = Color(Palette.LIGHT_ON_SECONDARY_CONTAINER),
    tertiary = Color(Palette.LIGHT_TERTIARY),
    onTertiary = Color(Palette.LIGHT_ON_TERTIARY),
    tertiaryContainer = Color(Palette.LIGHT_TERTIARY_CONTAINER),
    onTertiaryContainer = Color(Palette.LIGHT_ON_TERTIARY_CONTAINER),
    error = Color(Palette.LIGHT_ERROR),
    onError = Color(Palette.LIGHT_ON_ERROR),
    errorContainer = Color(Palette.LIGHT_ERROR_CONTAINER),
    onErrorContainer = Color(Palette.LIGHT_ON_ERROR_CONTAINER),
    background = Color(Palette.LIGHT_BACKGROUND_LIFTED),
    onBackground = Color(Palette.LIGHT_ON_BACKGROUND),
    surface = Color(Palette.LIGHT_SURFACE),
    onSurface = Color(Palette.LIGHT_ON_SURFACE),
    surfaceVariant = Color(Palette.LIGHT_SURFACE_VARIANT),
    onSurfaceVariant = Color(Palette.LIGHT_ON_SURFACE_VARIANT),
    outline = Color(Palette.LIGHT_OUTLINE),
    outlineVariant = Color(Palette.LIGHT_OUTLINE_VARIANT),
    surfaceDim = Color(Palette.LIGHT_SURFACE_DIM),
    surfaceBright = Color(Palette.LIGHT_SURFACE_BRIGHT_LIFTED),
    inverseSurface = Color(Palette.LIGHT_INVERSE_SURFACE),
    inverseOnSurface = Color(Palette.LIGHT_INVERSE_ON_SURFACE),
    inversePrimary = Color(Palette.LIGHT_INVERSE_PRIMARY),
    surfaceContainerLowest = Color(Palette.LIGHT_SURFACE_CONTAINER_LOWEST),
    surfaceContainerLow = Color(Palette.LIGHT_SURFACE_CONTAINER_LOW),
    surfaceContainer = Color(Palette.LIGHT_SURFACE_CONTAINER),
    surfaceContainerHigh = Color(Palette.LIGHT_SURFACE_CONTAINER_HIGH),
    surfaceContainerHighest = Color(Palette.LIGHT_SURFACE_CONTAINER_HIGHEST),
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
