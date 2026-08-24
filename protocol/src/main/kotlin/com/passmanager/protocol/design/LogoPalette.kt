package com.passmanager.protocol.design

/**
 * Single source of truth for the vault shield logo colors, shared by :app and desktop.
 *
 * The protocol module already holds the code both applications are forced to agree on, and the app's
 * own mark is one of those things: the Android vector drawable and the desktop AWT renderer draw the
 * same shield and must draw it in the same colors. Values are plain ARGB [Long] constants, always
 * fully opaque, so this module stays dependency-free pure JVM.
 *
 * The plate behind the shield is not part of this object, but it is not free either: it is pinned to
 * [Palette.LIGHT_PRIMARY_CONTAINER] in both themes on both platforms (mirrored on Android as
 * `@color/ic_launcher_primary_container`). A theme-following plate was tried and abandoned — the
 * shield art below is fixed light-scheme colour, so on the dark scheme's `primaryContainer` the
 * outer shield measured 1.22:1 against its own backdrop and the mark all but vanished. Against the
 * pinned mint it measures 4.05:1 ([TEAL_LIGHT]) and 5.43:1 ([TEAL_DARK]), and the launcher icon, the
 * notification chip and both in-app marks become literally the same lockup.
 */
object LogoPalette {

    /** Left half of the cap, outer shield, top V and center lock. */
    const val TEAL_DARK: Long = 0xFF1A6D68

    /** Right half of the cap, outer shield, top V and center lock. */
    const val TEAL_LIGHT: Long = 0xFF21837D

    /** Left half of the inner shield. */
    const val INNER_LEFT: Long = 0xFFC8E6DA

    /** Right half of the inner shield. */
    const val INNER_RIGHT: Long = 0xFFDDF0E6

    /** Left circuit traces and their endpoint dots. */
    const val CIRCUIT_LEFT: Long = 0xFFA5D4C4

    /** Right circuit traces and their endpoint dots. */
    const val CIRCUIT_RIGHT: Long = 0xFFBCE0D4
}
