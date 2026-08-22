package com.passmanager.protocol.design

/**
 * Single source of truth for the vault shield logo colors, shared by :app and desktop.
 *
 * The protocol module already holds the code both applications are forced to agree on, and the app's
 * own mark is one of those things: the Android vector drawable and the desktop AWT renderer draw the
 * same shield and must draw it in the same colors. Values are plain ARGB [Long] constants, always
 * fully opaque, so this module stays dependency-free pure JVM.
 *
 * The plate behind the shield is not part of this object — it is the scheme's `primaryContainer`
 * ([Palette.LIGHT_PRIMARY_CONTAINER] / [Palette.DARK_PRIMARY_CONTAINER]), so it follows the theme
 * while the shield art itself stays identical in light and dark.
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
