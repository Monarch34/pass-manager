package com.passmanager.domain.port

import com.passmanager.protocol.PairingQrPayload
import com.passmanager.protocol.SecureResponse
import kotlinx.coroutines.flow.StateFlow
import com.passmanager.domain.model.PairingSessionState

interface DesktopPairingPort {
    val pairingState: StateFlow<PairingSessionState>
    val isPairing: StateFlow<Boolean>
    suspend fun connectAndPair(qrPayload: PairingQrPayload)
    fun abortPairing(reason: String)
    fun canSendPassword(): Boolean
    suspend fun sendSecure(response: SecureResponse)
    fun recordPasswordSent(itemTitle: String)
}
