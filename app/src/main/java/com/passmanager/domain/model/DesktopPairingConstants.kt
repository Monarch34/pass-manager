package com.passmanager.domain.model

/** Limits and timeouts shared by [com.passmanager.security.DesktopPairingSession] and desktop link UI. */
object DesktopPairingConstants {
    const val INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L
    const val VERIFY_CODE_TIMEOUT_MS = 15_000L
    const val MAX_PW_PER_SESSION = 20
    const val PW_COOLDOWN_MS = 10_000L
    const val VAULT_LIST_COOLDOWN_MS = 2_000L
}
