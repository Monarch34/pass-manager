package com.passmanager.desktop.ui

/**
 * Centralized string constants for the desktop app.
 * Keeps all user-visible text in one place for consistency and future i18n.
 */
object Strings {
    // PairScreen
    const val APP_TITLE = "PassManager Desktop"
    const val PAIR_INSTRUCTION = "Scan this QR code with your phone to connect"
    const val PAIR_WAITING = "Waiting for phone\u2026"

    // VerifyScreen
    const val VERIFY_TITLE = "Verify Connection"
    const val VERIFY_INSTRUCTION = "Enter the 6-digit code shown on your phone"
    const val VERIFY_CANCEL = "Cancel"
    const val SAFETY_NUMBER_LABEL = "Security code"
    const val SAFETY_NUMBER_HINT = "Verify this matches your phone before entering the code"

    // VaultBrowserScreen
    const val CONNECTED = "Connected"
    const val REFRESH_VAULT_LIST = "Refresh vault list from phone"
    const val DISCONNECT = "Disconnect"
    const val WAITING_FOR_ITEMS = "Waiting for vault items\u2026"
    const val NOT_CONNECTED = "Not connected"

    // Theme (action = destination after tap)
    const val SWITCH_TO_LIGHT_THEME = "Switch to light theme"
    const val SWITCH_TO_DARK_THEME = "Switch to dark theme"

    // Site icons — identical behaviour to Android: Off makes no network request at all, On makes
    // exactly one, to t0.gstatic.com, and no other host is ever contacted. The explainers below are
    // word-for-word the Android settings_site_icons_subtitle_off / _on strings.
    const val FAVICON_SECTION_TITLE = "Site icons"
    const val FAVICON_SECTION_HINT = "Choose whether website icons are downloaded next to entries."
    const val FAVICON_MODE_OFF_LABEL = "Off"
    const val FAVICON_MODE_ON_LABEL = "On (Google)"
    const val FAVICON_MODE_OFF_EXPLAINER =
        "Off: no icon is downloaded and no network request is made. Entries show their category icon."
    const val FAVICON_MODE_ON_EXPLAINER =
        "On: the site address is sent to Google’s icon service (t0.gstatic.com), which returns the icon. No other host is contacted, and redirects away from it are refused."

    /** One-line hints for the compact footer control. */
    const val FAVICON_COMPACT_OFF_LINE =
        "No icons downloaded — no network request."
    const val FAVICON_COMPACT_ON_LINE =
        "Icons from t0.gstatic.com — the site address is sent to Google."

    // Accessibility labels. They live here, not inline at the call site, so the i18n sweep this
    // object exists to enable does not miss the two strings a screen reader actually announces.
    const val LOGO_CONTENT_DESCRIPTION = "PassManager logo"
    const val QR_CONTENT_DESCRIPTION = "QR code for pairing"

    // Status messages
    const val ERROR_GENERIC = "Something went wrong. Please try again."
    /** Fallback when the phone sends an empty rate-limit message. */
    const val RATE_LIMITED_FALLBACK = "Please wait a moment before trying again."

    // Formatting helpers
    fun serverAddress(ip: String, port: Int) = "Server: $ip:$port"
    fun attemptsRemaining(count: Int) = "$count attempts remaining"
    fun itemCount(count: Int) = "$count items"
    fun copyPasswordFor(title: String) = "Copy password for $title"
}
