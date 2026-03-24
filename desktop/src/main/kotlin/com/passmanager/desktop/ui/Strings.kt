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
    const val PAIR_LISTENING = "Ready — scan the QR code with your phone"

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

    // Site icons (vault browser — mirrors Android Settings → Rich site icons)
    const val FAVICON_SECTION_TITLE = "Site icons"
    const val FAVICON_SECTION_HINT = "Choose how the desktop loads website icons next to entries."
    const val FAVICON_MODE_PRIVATE_LABEL = "Private"
    const val FAVICON_MODE_GOOGLE_LABEL = "Rich (Google)"
    const val FAVICON_MODE_PRIVATE_EXPLAINER =
        "Only your PC contacts each website for favicon.ico. No third party sees which sites you use. Some icons may be missing."
    const val FAVICON_MODE_GOOGLE_EXPLAINER =
        "Uses Google’s favicon service first for clearer icons. The site address is sent to Google."

    /** One-line hints for the compact footer control. */
    const val FAVICON_COMPACT_PRIVATE_LINE =
        "Direct favicon only — private. Some icons may be missing."
    const val FAVICON_COMPACT_GOOGLE_LINE =
        "Google helper for clearer icons; site address goes to Google."

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
