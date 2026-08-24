package com.passmanager.desktop.preferences

import java.util.prefs.Preferences

/**
 * User preferences persisted with [java.util.prefs] (OS-backed, no extra deps).
 */
object DesktopPreferences {
    private const val NODE = "com.passmanager.desktop"
    private const val KEY_USE_GOOGLE_FAVICONS = "use_google_favicons"
    private const val KEY_USE_DARK_THEME = "use_dark_theme"

    /** When false (default), no icon request is made at all; when true, one request to t0.gstatic.com. */
    fun getUseGoogleFavicons(): Boolean =
        Preferences.userRoot().node(NODE).getBoolean(KEY_USE_GOOGLE_FAVICONS, false)

    fun setUseGoogleFavicons(value: Boolean) {
        Preferences.userRoot().node(NODE).putBoolean(KEY_USE_GOOGLE_FAVICONS, value)
    }

    /** When true (default), the app starts in the dark scheme. */
    fun getUseDarkTheme(): Boolean =
        Preferences.userRoot().node(NODE).getBoolean(KEY_USE_DARK_THEME, true)

    fun setUseDarkTheme(value: Boolean) {
        Preferences.userRoot().node(NODE).putBoolean(KEY_USE_DARK_THEME, value)
    }
}
