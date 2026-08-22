package com.passmanager.protocol.design

/**
 * Single source of truth for the app color palette, shared by :app and desktop.
 *
 * The protocol module already holds the code both applications are forced to agree on — the wire
 * format is one such contract, and the palette is another: a phone and a desktop showing the same
 * vault must show it in the same colors. Values are plain ARGB [Long] constants so this module stays
 * dependency-free pure JVM; each UI layer wraps them in its own color type (`Color(...)`).
 */
object Palette {

    // ── Dark scheme — teal/green primary, near-black canvas ──

    const val DARK_PRIMARY: Long = 0xFF5EEAD4
    const val DARK_ON_PRIMARY: Long = 0xFF003731
    const val DARK_PRIMARY_CONTAINER: Long = 0xFF0D9488
    const val DARK_ON_PRIMARY_CONTAINER: Long = 0xFFCCFBF1

    const val DARK_SECONDARY: Long = 0xFFA5B4FC
    const val DARK_ON_SECONDARY: Long = 0xFF1E1B4B
    const val DARK_SECONDARY_CONTAINER: Long = 0xFF3730A3
    const val DARK_ON_SECONDARY_CONTAINER: Long = 0xFFE0E7FF

    const val DARK_TERTIARY: Long = 0xFFFBBF24
    const val DARK_ON_TERTIARY: Long = 0xFF422006
    const val DARK_TERTIARY_CONTAINER: Long = 0xFF92400E
    const val DARK_ON_TERTIARY_CONTAINER: Long = 0xFFFEF3C7

    const val DARK_ERROR: Long = 0xFFFCA5A5
    const val DARK_ON_ERROR: Long = 0xFF7F1D1D
    const val DARK_ERROR_CONTAINER: Long = 0xFF991B1B
    const val DARK_ON_ERROR_CONTAINER: Long = 0xFFFEE2E2

    const val DARK_BACKGROUND: Long = 0xFF0C0F14
    const val DARK_ON_BACKGROUND: Long = 0xFFE8ECF2
    const val DARK_SURFACE: Long = 0xFF141820
    const val DARK_ON_SURFACE: Long = 0xFFE8ECF2
    const val DARK_SURFACE_VARIANT: Long = 0xFF1E2430
    const val DARK_ON_SURFACE_VARIANT: Long = 0xFFA0A8B8
    const val DARK_OUTLINE: Long = 0xFF3B4254
    const val DARK_OUTLINE_VARIANT: Long = 0xFF2A3040
    const val DARK_SURFACE_DIM: Long = 0xFF0C0F14
    const val DARK_SURFACE_BRIGHT: Long = 0xFF2A3040
    const val DARK_INVERSE_SURFACE: Long = 0xFFE8ECF2
    const val DARK_INVERSE_ON_SURFACE: Long = 0xFF1A1E28
    const val DARK_INVERSE_PRIMARY: Long = 0xFF0D7A70
    const val DARK_SURFACE_CONTAINER_LOWEST: Long = 0xFF080B0F
    const val DARK_SURFACE_CONTAINER_LOW: Long = 0xFF101420
    const val DARK_SURFACE_CONTAINER: Long = 0xFF161B26
    const val DARK_SURFACE_CONTAINER_HIGH: Long = 0xFF1C2230
    const val DARK_SURFACE_CONTAINER_HIGHEST: Long = 0xFF24293A

    // ── Dark scheme, lifted variant — for non-emissive (LCD) desktop displays ──
    //
    // Phone OLED renders near-black as true black, so the tones above separate cleanly there.
    // Desktop LCD panels crush the same tones into one muddy dark, so the desktop theme picks the
    // `_LIFTED` token wherever one exists and falls back to the shared token otherwise. Only the
    // low-luminance surfaces need lifting; accents, text and inverse tones are identical on both.

    /** Lifted from [DARK_BACKGROUND]: the app canvas is the largest crushed area on LCD. */
    const val DARK_BACKGROUND_LIFTED: Long = 0xFF111827

    /** Lifted from [DARK_SURFACE]: keeps cards readable against the lifted background. */
    const val DARK_SURFACE_LIFTED: Long = 0xFF1A2033

    /** Lifted from [DARK_SURFACE_VARIANT]: preserves the surface/variant step after the lift. */
    const val DARK_SURFACE_VARIANT_LIFTED: Long = 0xFF252D3C

    /** Lifted from [DARK_OUTLINE]: hairline borders disappear on LCD at the shared value. */
    const val DARK_OUTLINE_LIFTED: Long = 0xFF4A5268

    /** Lifted from [DARK_SURFACE_DIM]: tracks [DARK_BACKGROUND_LIFTED], as the shared pair does. */
    const val DARK_SURFACE_DIM_LIFTED: Long = 0xFF111827

    /** Lifted from [DARK_SURFACE_CONTAINER_LOWEST]: keeps the lowest step above pure black. */
    const val DARK_SURFACE_CONTAINER_LOWEST_LIFTED: Long = 0xFF0C1018

    /** Lifted from [DARK_SURFACE_CONTAINER_LOW]: keeps the container ladder evenly spaced. */
    const val DARK_SURFACE_CONTAINER_LOW_LIFTED: Long = 0xFF141B2A

    /** Lifted from [DARK_SURFACE_CONTAINER]: keeps the container ladder evenly spaced. */
    const val DARK_SURFACE_CONTAINER_LIFTED: Long = 0xFF1A2233

    /** Lifted from [DARK_SURFACE_CONTAINER_HIGH]: fixes near-black digit boxes on LCD. */
    const val DARK_SURFACE_CONTAINER_HIGH_LIFTED: Long = 0xFF283040

    /** Lifted from [DARK_SURFACE_CONTAINER_HIGHEST]: keeps the top step above the `HIGH` lift. */
    const val DARK_SURFACE_CONTAINER_HIGHEST_LIFTED: Long = 0xFF303848

    // ── Semantic category tints (used across light/dark) ──

    const val CATEGORY_LOGIN_TINT: Long = 0xFF0D9488
    const val CATEGORY_CARD_TINT: Long = 0xFFB45309
    const val CATEGORY_NOTE_TINT: Long = 0xFF7C3AED
    const val CATEGORY_IDENTITY_TINT: Long = 0xFF0284C7
    const val CATEGORY_BANK_TINT: Long = 0xFF059669

    const val STRENGTH_FAIR: Long = 0xFFFF8F00

    // ── Light scheme — teal/green primary, off-white canvas ──

    const val LIGHT_PRIMARY: Long = 0xFF0D7A70
    const val LIGHT_ON_PRIMARY: Long = 0xFFFFFFFF
    const val LIGHT_PRIMARY_CONTAINER: Long = 0xFFCCFBF1
    const val LIGHT_ON_PRIMARY_CONTAINER: Long = 0xFF00312C

    const val LIGHT_SECONDARY: Long = 0xFF4338CA
    const val LIGHT_ON_SECONDARY: Long = 0xFFFFFFFF
    const val LIGHT_SECONDARY_CONTAINER: Long = 0xFFE0E7FF
    const val LIGHT_ON_SECONDARY_CONTAINER: Long = 0xFF1E1B4B

    const val LIGHT_TERTIARY: Long = 0xFFB45309
    const val LIGHT_ON_TERTIARY: Long = 0xFFFFFFFF
    const val LIGHT_TERTIARY_CONTAINER: Long = 0xFFFEF3C7
    const val LIGHT_ON_TERTIARY_CONTAINER: Long = 0xFF422006

    const val LIGHT_ERROR: Long = 0xFFDC2626
    const val LIGHT_ON_ERROR: Long = 0xFFFFFFFF
    const val LIGHT_ERROR_CONTAINER: Long = 0xFFFEE2E2
    const val LIGHT_ON_ERROR_CONTAINER: Long = 0xFF7F1D1D

    const val LIGHT_BACKGROUND: Long = 0xFFF6F8FC
    const val LIGHT_ON_BACKGROUND: Long = 0xFF111318
    const val LIGHT_SURFACE: Long = 0xFFFFFFFF
    const val LIGHT_ON_SURFACE: Long = 0xFF111318
    const val LIGHT_SURFACE_VARIANT: Long = 0xFFE4E8F0
    const val LIGHT_ON_SURFACE_VARIANT: Long = 0xFF3E4450
    const val LIGHT_OUTLINE: Long = 0xFF6E7688
    const val LIGHT_OUTLINE_VARIANT: Long = 0xFFC8CDD8
    const val LIGHT_SURFACE_DIM: Long = 0xFFD8DCE6
    const val LIGHT_SURFACE_BRIGHT: Long = 0xFFF6F8FC
    const val LIGHT_INVERSE_SURFACE: Long = 0xFF1A1E28
    const val LIGHT_INVERSE_ON_SURFACE: Long = 0xFFEFF1F6
    const val LIGHT_INVERSE_PRIMARY: Long = 0xFF5EEAD4
    const val LIGHT_SURFACE_CONTAINER_LOWEST: Long = 0xFFFFFFFF
    const val LIGHT_SURFACE_CONTAINER_LOW: Long = 0xFFF0F2F8
    const val LIGHT_SURFACE_CONTAINER: Long = 0xFFEAEDF4
    const val LIGHT_SURFACE_CONTAINER_HIGH: Long = 0xFFE4E7EF
    const val LIGHT_SURFACE_CONTAINER_HIGHEST: Long = 0xFFDDE1EA

    // ── Light scheme, lifted variant — for non-emissive (LCD) desktop displays ──
    //
    // Same intent as the dark lifts, mirrored: on LCD the off-white canvas reads as grey, so the
    // desktop theme brightens (and slightly cools) it. Only the canvas tones move; paper-white
    // surfaces and the container ladder are identical on both platforms.

    /** Lifted from [LIGHT_BACKGROUND]: a brighter, cooler white so the canvas is not grey on LCD. */
    const val LIGHT_BACKGROUND_LIFTED: Long = 0xFFF8FAFF

    /** Lifted from [LIGHT_SURFACE_BRIGHT]: tracks [LIGHT_BACKGROUND_LIFTED], as the shared pair does. */
    const val LIGHT_SURFACE_BRIGHT_LIFTED: Long = 0xFFF8FAFF
}
