package com.passmanager.ui.theme

import androidx.compose.ui.graphics.Color
import com.passmanager.protocol.design.Palette

// Every value comes from [Palette], the shared token object in :protocol, so the phone and the
// desktop client render the same vault in the same colors. The `_LIFTED` tokens are deliberately
// not used here: they compensate for non-emissive LCD panels and belong to the desktop theme only.

// ── Dark scheme — teal/green primary, near-black canvas ──

val DarkPrimary = Color(Palette.DARK_PRIMARY)
val DarkOnPrimary = Color(Palette.DARK_ON_PRIMARY)
val DarkPrimaryContainer = Color(Palette.DARK_PRIMARY_CONTAINER)
val DarkOnPrimaryContainer = Color(Palette.DARK_ON_PRIMARY_CONTAINER)

val DarkSecondary = Color(Palette.DARK_SECONDARY)
val DarkOnSecondary = Color(Palette.DARK_ON_SECONDARY)
val DarkSecondaryContainer = Color(Palette.DARK_SECONDARY_CONTAINER)
val DarkOnSecondaryContainer = Color(Palette.DARK_ON_SECONDARY_CONTAINER)

val DarkTertiary = Color(Palette.DARK_TERTIARY)
val DarkOnTertiary = Color(Palette.DARK_ON_TERTIARY)
val DarkTertiaryContainer = Color(Palette.DARK_TERTIARY_CONTAINER)
val DarkOnTertiaryContainer = Color(Palette.DARK_ON_TERTIARY_CONTAINER)

val DarkError = Color(Palette.DARK_ERROR)
val DarkOnError = Color(Palette.DARK_ON_ERROR)
val DarkErrorContainer = Color(Palette.DARK_ERROR_CONTAINER)
val DarkOnErrorContainer = Color(Palette.DARK_ON_ERROR_CONTAINER)

val DarkBackground = Color(Palette.DARK_BACKGROUND)
val DarkOnBackground = Color(Palette.DARK_ON_BACKGROUND)
val DarkSurface = Color(Palette.DARK_SURFACE)
val DarkOnSurface = Color(Palette.DARK_ON_SURFACE)
val DarkSurfaceVariant = Color(Palette.DARK_SURFACE_VARIANT)
val DarkOnSurfaceVariant = Color(Palette.DARK_ON_SURFACE_VARIANT)
val DarkOutline = Color(Palette.DARK_OUTLINE)
val DarkOutlineVariant = Color(Palette.DARK_OUTLINE_VARIANT)
val DarkSurfaceDim = Color(Palette.DARK_SURFACE_DIM)
val DarkSurfaceBright = Color(Palette.DARK_SURFACE_BRIGHT)
val DarkInverseSurface = Color(Palette.DARK_INVERSE_SURFACE)
val DarkInverseOnSurface = Color(Palette.DARK_INVERSE_ON_SURFACE)
val DarkInversePrimary = Color(Palette.DARK_INVERSE_PRIMARY)
val DarkSurfaceContainerLowest = Color(Palette.DARK_SURFACE_CONTAINER_LOWEST)
val DarkSurfaceContainerLow = Color(Palette.DARK_SURFACE_CONTAINER_LOW)
val DarkSurfaceContainer = Color(Palette.DARK_SURFACE_CONTAINER)
val DarkSurfaceContainerHigh = Color(Palette.DARK_SURFACE_CONTAINER_HIGH)
val DarkSurfaceContainerHighest = Color(Palette.DARK_SURFACE_CONTAINER_HIGHEST)

// ── Semantic category tints (used across light/dark) ──

val CategoryLoginTint = Color(Palette.CATEGORY_LOGIN_TINT)
val CategoryCardTint = Color(Palette.CATEGORY_CARD_TINT)
val CategoryNoteTint = Color(Palette.CATEGORY_NOTE_TINT)
val CategoryIdentityTint = Color(Palette.CATEGORY_IDENTITY_TINT)
val CategoryBankTint = Color(Palette.CATEGORY_BANK_TINT)

val StrengthFairColor = Color(Palette.STRENGTH_FAIR)

// ── Light scheme — teal/green primary, off-white canvas ──

val LightPrimary = Color(Palette.LIGHT_PRIMARY)
val LightOnPrimary = Color(Palette.LIGHT_ON_PRIMARY)
val LightPrimaryContainer = Color(Palette.LIGHT_PRIMARY_CONTAINER)
val LightOnPrimaryContainer = Color(Palette.LIGHT_ON_PRIMARY_CONTAINER)

val LightSecondary = Color(Palette.LIGHT_SECONDARY)
val LightOnSecondary = Color(Palette.LIGHT_ON_SECONDARY)
val LightSecondaryContainer = Color(Palette.LIGHT_SECONDARY_CONTAINER)
val LightOnSecondaryContainer = Color(Palette.LIGHT_ON_SECONDARY_CONTAINER)

val LightTertiary = Color(Palette.LIGHT_TERTIARY)
val LightOnTertiary = Color(Palette.LIGHT_ON_TERTIARY)
val LightTertiaryContainer = Color(Palette.LIGHT_TERTIARY_CONTAINER)
val LightOnTertiaryContainer = Color(Palette.LIGHT_ON_TERTIARY_CONTAINER)

val LightError = Color(Palette.LIGHT_ERROR)
val LightOnError = Color(Palette.LIGHT_ON_ERROR)
val LightErrorContainer = Color(Palette.LIGHT_ERROR_CONTAINER)
val LightOnErrorContainer = Color(Palette.LIGHT_ON_ERROR_CONTAINER)

val LightBackground = Color(Palette.LIGHT_BACKGROUND)
val LightOnBackground = Color(Palette.LIGHT_ON_BACKGROUND)
val LightSurface = Color(Palette.LIGHT_SURFACE)
val LightOnSurface = Color(Palette.LIGHT_ON_SURFACE)
val LightSurfaceVariant = Color(Palette.LIGHT_SURFACE_VARIANT)
val LightOnSurfaceVariant = Color(Palette.LIGHT_ON_SURFACE_VARIANT)
val LightOutline = Color(Palette.LIGHT_OUTLINE)
val LightOutlineVariant = Color(Palette.LIGHT_OUTLINE_VARIANT)
val LightSurfaceDim = Color(Palette.LIGHT_SURFACE_DIM)
val LightSurfaceBright = Color(Palette.LIGHT_SURFACE_BRIGHT)
val LightInverseSurface = Color(Palette.LIGHT_INVERSE_SURFACE)
val LightInverseOnSurface = Color(Palette.LIGHT_INVERSE_ON_SURFACE)
val LightInversePrimary = Color(Palette.LIGHT_INVERSE_PRIMARY)
val LightSurfaceContainerLowest = Color(Palette.LIGHT_SURFACE_CONTAINER_LOWEST)
val LightSurfaceContainerLow = Color(Palette.LIGHT_SURFACE_CONTAINER_LOW)
val LightSurfaceContainer = Color(Palette.LIGHT_SURFACE_CONTAINER)
val LightSurfaceContainerHigh = Color(Palette.LIGHT_SURFACE_CONTAINER_HIGH)
val LightSurfaceContainerHighest = Color(Palette.LIGHT_SURFACE_CONTAINER_HIGHEST)
