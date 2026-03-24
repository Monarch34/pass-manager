package com.passmanager.desktop.preferences

import java.util.prefs.Preferences

/**
 * User preferences persisted with [java.util.prefs] (OS-backed, no extra deps).
 */
object DesktopPreferences {
    private const val NODE = "com.passmanager.desktop"
    private const val KEY_USE_GOOGLE_FAVICONS = "use_google_favicons"

    /** When false (default), only `https://domain/favicon.ico` is fetched — no third party. */
    fun getUseGoogleFavicons(): Boolean =
        Preferences.userRoot().node(NODE).getBoolean(KEY_USE_GOOGLE_FAVICONS, false)

    fun setUseGoogleFavicons(value: Boolean) {
        Preferences.userRoot().node(NODE).putBoolean(KEY_USE_GOOGLE_FAVICONS, value)
    }
}
