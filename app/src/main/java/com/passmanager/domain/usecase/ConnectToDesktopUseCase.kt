package com.passmanager.domain.usecase

import com.passmanager.crypto.channel.PairingQrPayload
import com.passmanager.crypto.channel.PROTOCOL_V1
import com.passmanager.security.DesktopPairingSession
import com.passmanager.security.PairingSessionState
import javax.inject.Inject

/**
 * Orchestrates the desktop connection flow:
 * 1. Parse QR → 2. ECDH key exchange → 3. HKDF derivation → 4. Open WebSocket
 *
 * After success the [DesktopPairingSession] is in [PairingSessionState.Verifying]
 * and awaits the user to enter the 6-digit code on the desktop.
 */
class ConnectToDesktopUseCase @Inject constructor(
    private val session: DesktopPairingSession,
) {
    suspend operator fun invoke(qrPayload: PairingQrPayload): Result<Unit> = runCatching {
        require(qrPayload.v <= PROTOCOL_V1) {
            "This QR code requires a newer version of PassManager. Please update the app."
        }
        require(qrPayload.v == PROTOCOL_V1) { "Unrecognised QR code format" }
        require(qrPayload.token.isNotBlank()) { "Invalid QR payload" }
        val startResult = session.startPairing(qrPayload)
        try {
            session.completePairing(
                channel = startResult.channel,
                pairingClient = startResult.client
            )
        } catch (e: Exception) {
            if (session.state.value is PairingSessionState.Pairing) {
                session.abortPairing(e.message ?: "Pairing failed")
            }
            throw e
        }
    }
}
