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
    const val DISCONNECT = "Disconnect"
    const val WAITING_FOR_ITEMS = "Waiting for vault items\u2026"
    const val NOT_CONNECTED = "Not connected"

    // Theme (action = destination after tap)
    const val SWITCH_TO_LIGHT_THEME = "Switch to light theme"
    const val SWITCH_TO_DARK_THEME = "Switch to dark theme"

    // Status messages
    const val ERROR_GENERIC = "Something went wrong. Please try again."
    const val RATE_LIMITED = "Too many requests — please wait a moment."

    // Formatting helpers
    fun serverAddress(ip: String, port: Int) = "Server: $ip:$port"
    fun attemptsRemaining(count: Int) = "$count attempts remaining"
    fun itemCount(count: Int) = "$count items"
    fun copyPasswordFor(title: String) = "Copy password for $title"
}
