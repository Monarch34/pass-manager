package com.passmanager.domain.model

/**
 * UI-facing state for desktop pairing (no crypto types). Emitted by [com.passmanager.security.DesktopPairingSession].
 */
sealed interface PairingSessionState {
    data object Idle : PairingSessionState
    data object Pairing : PairingSessionState
    data class Verifying(
        val code: String,
        val desktopIp: String,
        val attemptsRemaining: Int = MAX_VERIFY_ATTEMPTS,
        val expiresAtMs: Long = 0L,
        /** 8-char hex fingerprint of both public keys. User should verify this matches the desktop. */
        val safetyNumber: String = ""
    ) : PairingSessionState {
        companion object {
            const val MAX_VERIFY_ATTEMPTS = 3
        }
    }
    data class Active(
        val desktopIp: String,
        val passwordsSent: Int,
        val lastItemTitle: String?
    ) : PairingSessionState
    data class Ended(val reason: String) : PairingSessionState
    data class Error(val message: String) : PairingSessionState
}
