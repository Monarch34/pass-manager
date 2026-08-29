package com.passmanager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The palette, carried over from version 1 unchanged.
 *
 * The restyle changed structure, not colour: the same teal on the same cool near-neutral
 * canvas. A vault that looks like a different application after an update is a vault people
 * trust less, and there is nothing wrong with these values.
 *
 * Dynamic colour is deliberately not used. A password manager whose category stripes and
 * warning states are re-tinted from the user's wallpaper loses the one thing those colours
 * are for, which is telling one kind of entry from another at a glance.
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF0D7A70),
    onPrimary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0E7FF),
    error = Color(0xFFDC2626),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF111318),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111318),
    surfaceVariant = Color(0xFFE4E8F0),
    onSurfaceVariant = Color(0xFF3E4450),
    outline = Color(0xFF6E7688),
    outlineVariant = Color(0xFFC8CDD8),
    surfaceContainer = Color(0xFFEAEDF4),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF003731),
    secondaryContainer = Color(0xFF3730A3),
    error = Color(0xFFFCA5A5),
    background = Color(0xFF0C0F14),
    onBackground = Color(0xFFE8ECF2),
    surface = Color(0xFF141820),
    onSurface = Color(0xFFE8ECF2),
    surfaceVariant = Color(0xFF1E2430),
    onSurfaceVariant = Color(0xFFA0A8B8),
    outline = Color(0xFF3B4254),
    outlineVariant = Color(0xFF2A3040),
    surfaceContainer = Color(0xFF161B26),
)

@Composable
fun PassManagerTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Nothing here touches the window. `enableEdgeToEdge()` in the activity already fits the
    // system bars and picks contrasting icons from the same system dark-mode setting this
    // composable reads, so doing it again here was one behaviour written twice — and it
    // required reaching for the Activity through LocalContext, which is exactly the cast
    // Compose now tells you not to make.
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, content = content)
}

// ── The shapes the design names directly ────────────────────────────────────
// Material's shape roles exist so a component library can style itself. These are the sizes
// the design speaks in, and a screen that wants "the card radius" should say so rather than
// reach for whichever Material role happens to equal it today.

/** The grouped block every screen builds its content out of. */
val CardShape = RoundedCornerShape(20.dp)

/** Tighter than a card, so a field inside one still reads as a field. */
val FieldShape = RoundedCornerShape(12.dp)

/** Anything whose ends are fully round: buttons and the search bar. */
val PillShape = RoundedCornerShape(100.dp)

/** Category filter chips — square-ish, so they do not compete with the pill buttons. */
val ChipShape = RoundedCornerShape(8.dp)
