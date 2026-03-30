package com.passmanager.domain.usecase

import com.passmanager.domain.port.DesktopPairingPort
import com.passmanager.protocol.PROTOCOL_V1
import com.passmanager.protocol.PairingQrPayload
import javax.inject.Inject

class ConnectToDesktopUseCase @Inject constructor(
    private val session: DesktopPairingPort,
) {
    suspend operator fun invoke(qrPayload: PairingQrPayload): Result<Unit> = runCatching {
        require(qrPayload.v <= PROTOCOL_V1) {
            "This QR code requires a newer version of PassManager. Please update the app."
        }
        require(qrPayload.v >= PROTOCOL_V1) { "Unrecognised QR code format — QR version too old." }
        require(qrPayload.token.isNotBlank()) { "Invalid QR payload" }
        try {
            session.connectAndPair(qrPayload)
        } catch (e: Exception) {
            if (session.pairingState.value is com.passmanager.domain.model.PairingSessionState.Pairing) {
                session.abortPairing(e.message ?: "Pairing failed")
            }
            throw e
        }
    }
}
