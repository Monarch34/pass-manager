package com.passmanager.security

import android.util.Base64
import com.passmanager.BuildConfig
import com.passmanager.agent.DesktopPairingClient
import com.passmanager.crypto.channel.EncryptedChannel
import com.passmanager.domain.model.DesktopPairingConstants
import com.passmanager.domain.model.PairingSessionState
import com.passmanager.domain.port.DesktopPairingPort
import com.passmanager.protocol.PairingQrPayload
import com.passmanager.protocol.SecureRequest
import com.passmanager.protocol.SecureResponse
import com.passmanager.crypto.ecdh.X25519KeyExchange
import com.passmanager.crypto.kdf.HkdfSha256
import com.passmanager.crypto.util.SensitiveByteArray
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

import io.ktor.client.HttpClient

/**
 * Manages the lifecycle of a single ephemeral desktop pairing session.
 * Holds all transient crypto state (keypairs, session key, channel) in memory.
 * Everything is zeroed on session end.
 *
 * W1 mitigation: rate limits password requests (see [DesktopPairingConstants.MAX_PW_PER_SESSION],
 * [DesktopPairingConstants.PW_COOLDOWN_MS]).
 *
 * Desktop-initiated vault list refreshes are limited by [DesktopPairingConstants.VAULT_LIST_COOLDOWN_MS]
 * so the phone is not spammed; the initial list push after verification is not gated by this.
 */
@Singleton
class DesktopPairingSession @Inject constructor(
    private val clientProvider: Provider<DesktopPairingClient>
) : DesktopPairingPort {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<PairingSessionState>(PairingSessionState.Idle)
    val state: StateFlow<PairingSessionState> = _state.asStateFlow()

    private val sessionLock = Any()

    private var keyExchange: X25519KeyExchange? = null
    private var sessionKey: SensitiveByteArray? = null
    private var channel: EncryptedChannel? = null
    private var client: DesktopPairingClient? = null

    private var inactivityJob: Job? = null
    private var sessionJob: Job? = null
    private var verifyTimeoutJob: Job? = null

    private var pendingSafetyNumber: String = ""
    private var lastItemTitle: String? = null
    var desktopIp: String? = null; private set
    var desktopPort: Int = 0; private set

    private val rateLimiter = DesktopSessionRateLimiter(
        maxPasswords = DesktopPairingConstants.MAX_PW_PER_SESSION,
        passwordCooldownMs = DesktopPairingConstants.PW_COOLDOWN_MS,
        vaultListCooldownMs = DesktopPairingConstants.VAULT_LIST_COOLDOWN_MS
    )

    override val pairingState: StateFlow<PairingSessionState>
        get() = state

    override val isPairing: StateFlow<Boolean> = _state
        .map { it is PairingSessionState.Pairing }
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = _state.value is PairingSessionState.Pairing
        )

    override suspend fun connectAndPair(qrPayload: PairingQrPayload) {
        val result = startPairing(qrPayload)
        completePairing(result.channel, result.client)
    }

    suspend fun startPairing(qrPayload: PairingQrPayload): PairingStartResult {
        synchronized(sessionLock) { cleanupLocked() }
        _state.value = PairingSessionState.Pairing

        desktopIp = qrPayload.ip
        desktopPort = qrPayload.port

        val kx = X25519KeyExchange()
        keyExchange = kx

        val pairingClient = clientProvider.get()
        client = pairingClient

        val phonePubBytes = kx.publicKeyBytes.copyOf()
        val phonePubBase64 = Base64.encodeToString(phonePubBytes, Base64.NO_WRAP)
        val desktopPubBytes = Base64.decode(qrPayload.pub, Base64.NO_WRAP)
        require(desktopPubBytes.size == 32) {
            "Desktop public key must be 32 bytes, got ${desktopPubBytes.size}"
        }

        return try {
            pairingClient.handshake(
                qrPayload.ip,
                qrPayload.port,
                phonePubBase64,
                qrPayload.token
            )

            val sharedSecret = kx.deriveSharedSecret(desktopPubBytes)
            kx.close()
            keyExchange = null

            val salt = combinePubKeys(phonePubBytes, desktopPubBytes)
            pendingSafetyNumber = computeSafetyNumber(phonePubBytes, desktopPubBytes)
            val derivedKey = HkdfSha256.derive(
                ikm = sharedSecret,
                salt = salt,
                info = SESSION_INFO,
                length = 32
            )
            sharedSecret.fill(0)

            sessionKey = SensitiveByteArray.directFrom(derivedKey)

            val ch = EncryptedChannel(
                sessionKey = sessionKey!!.copyBytes(),
                sendDirection = EncryptedChannel.Direction.PHONE_TO_DESKTOP
            )
            channel = ch

            PairingStartResult(
                channel = ch,
                client = pairingClient
            )
        } catch (e: Exception) {
            abortPairing(e.message ?: "Handshake failed")
            throw e
        } finally {
            phonePubBytes.fill(0)
        }
    }

    suspend fun completePairing(
        channel: EncryptedChannel,
        pairingClient: DesktopPairingClient,
    ) {
        val ip = desktopIp ?: throw IllegalStateException("No desktop IP")
        val port = desktopPort

        val verified = CompletableDeferred<Unit>()

        val code = String.format("%06d", secureRandom.nextInt(1_000_000))

        sessionJob = scope.launch {
            try {
                pairingClient.runSecureSession(ip, port, channel) {
                    val expiresAt = System.currentTimeMillis() + DesktopPairingConstants.VERIFY_CODE_TIMEOUT_MS
                    _state.value = PairingSessionState.Verifying(
                        code = code,
                        desktopIp = ip,
                        attemptsRemaining = PairingSessionState.Verifying.MAX_VERIFY_ATTEMPTS,
                        expiresAtMs = expiresAt,
                        safetyNumber = pendingSafetyNumber
                    )
                    verifyTimeoutJob = scope.launch {
                        delay(DesktopPairingConstants.VERIFY_CODE_TIMEOUT_MS)
                        if (_state.value is PairingSessionState.Verifying) {
                            try {
                                sendSecure(SecureResponse.VerifyFailed("Code expired", 0))
                            } catch (e: Exception) {
                                logDebug("Failed to send verify timeout", e)
                            }
                            endSession("Verification code expired")
                        }
                    }
                    verified.complete(Unit)
                }
            } catch (e: Exception) {
                if (!verified.isCompleted) {
                    verified.completeExceptionally(e)
                }
            }
        }

        try {
            verified.await()
        } catch (e: Exception) {
            sessionJob?.cancel()
            abortPairing(e.message ?: "WebSocket failed")
            throw e
        }

        sessionJob?.invokeOnCompletion { cause ->
            val currentState = _state.value
            if (currentState is PairingSessionState.Active ||
                currentState is PairingSessionState.Verifying
            ) {
                scope.launch {
                    endSession(cause?.message ?: "Connection closed")
                }
            }
        }
    }

    suspend fun handleVerifyRequest(receivedCode: String): Boolean {
        val currentState = _state.value
        if (currentState !is PairingSessionState.Verifying) return false

        if (receivedCode == currentState.code) {
            sendSecure(SecureResponse.VerifyOk(safetyNumber = pendingSafetyNumber))

            rateLimiter.reset()
            lastItemTitle = null

            _state.value = PairingSessionState.Active(
                desktopIp = currentState.desktopIp,
                passwordsSent = 0,
                lastItemTitle = null
            )

            verifyTimeoutJob?.cancel()
            resetInactivityTimer()
            return true
        } else {
            val remaining = currentState.attemptsRemaining - 1
            if (remaining <= 0) {
                sendSecure(SecureResponse.VerifyFailed("Too many attempts", 0))
                endSession("Verification failed: too many attempts")
                return false
            }
            sendSecure(SecureResponse.VerifyFailed("Incorrect code", remaining))
            _state.value = currentState.copy(attemptsRemaining = remaining)
            return false
        }
    }

    fun resetToIdleIfTerminal() {
        when (_state.value) {
            is PairingSessionState.Error, is PairingSessionState.Ended -> {
                _state.value = PairingSessionState.Idle
            }
            else -> Unit
        }
    }

    override fun abortPairing(reason: String) {
        _state.value = PairingSessionState.Error(reason)
        synchronized(sessionLock) { cleanupLocked() }
    }

    override fun canSendPassword(): Boolean {
        if (_state.value !is PairingSessionState.Active) return false
        return rateLimiter.canSendPassword()
    }

    fun canAcceptVaultListRequestFromDesktop(): Boolean {
        if (_state.value !is PairingSessionState.Active) return false
        return rateLimiter.canAcceptVaultListRequest()
    }

    fun recordVaultListRequestFromDesktop() {
        rateLimiter.recordVaultListRequest()
        resetInactivityTimer()
    }

    override fun recordPasswordSent(itemTitle: String) {
        val sent = rateLimiter.recordPasswordSent()
        lastItemTitle = itemTitle
        resetInactivityTimer()

        val ip = desktopIp ?: return
        _state.value = PairingSessionState.Active(
            desktopIp = ip,
            passwordsSent = sent,
            lastItemTitle = lastItemTitle
        )

        if (sent >= DesktopPairingConstants.MAX_PW_PER_SESSION) {
            scope.launch { endSession("Session password limit reached") }
        }
    }

    sealed interface ReceiveResult {
        data class Success(val request: SecureRequest) : ReceiveResult
        data object ConnectionClosed : ReceiveResult
        data class Error(val cause: Exception) : ReceiveResult
    }

    override suspend fun sendSecure(response: SecureResponse) {
        client?.sendSecure(response)
    }

    suspend fun receiveSecureRequest(): ReceiveResult {
        return try {
            val request = client?.receiveSecureRequest()
            if (request != null) ReceiveResult.Success(request)
            else ReceiveResult.ConnectionClosed
        } catch (e: Exception) {
            logDebug("Failed to receive secure request", e)
            ReceiveResult.Error(e)
        }
    }

    suspend fun endSession(reason: String = "User disconnected") {
        _state.value = PairingSessionState.Ended(reason)
        val clientRef = synchronized(sessionLock) { client }
        try {
            clientRef?.sendSecure(SecureResponse.DisconnectAck)
        } catch (e: Exception) {
            logDebug("Failed to send disconnect ack", e)
        }
        synchronized(sessionLock) { cleanupLocked() }
    }

    private fun cleanupLocked() {
        inactivityJob?.cancel()
        sessionJob?.cancel()
        verifyTimeoutJob?.cancel()

        inactivityJob = null
        sessionJob = null
        verifyTimeoutJob = null

        channel?.close()
        channel = null

        sessionKey?.close()
        sessionKey = null

        keyExchange?.close()
        keyExchange = null

        val clientRef = client
        client = null
        if (clientRef != null) {
            scope.launch {
                try {
                    withTimeout(CLIENT_CLOSE_TIMEOUT_MS) { clientRef.close() }
                } catch (e: Exception) {
                    logDebug("Failed to close desktop client", e)
                }
            }
        }

        rateLimiter.reset()
        lastItemTitle = null
        pendingSafetyNumber = ""
        desktopIp = null
        desktopPort = 0
    }

    suspend fun respondToHeartbeat() {
        resetInactivityTimer()
        sendSecure(SecureResponse.HeartbeatAck(ts = System.currentTimeMillis()))
    }

    private fun resetInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = scope.launch {
            delay(DesktopPairingConstants.INACTIVITY_TIMEOUT_MS)
            endSession("Inactivity timeout")
        }
    }

    private fun combinePubKeys(a: ByteArray, b: ByteArray): ByteArray {
        val combined = ByteArray(a.size + b.size)
        val aFirst = compareArrays(a, b) <= 0
        val first = if (aFirst) a else b
        val second = if (aFirst) b else a
        System.arraycopy(first, 0, combined, 0, first.size)
        System.arraycopy(second, 0, combined, first.size, second.size)
        return combined
    }

    /**
     * 32-bit fingerprint (8 hex chars). Sufficient for LAN-only pairing where
     * the QR code is the primary trust anchor; this is a secondary visual check.
     * Not comparable to Signal-grade safety numbers (which use 60+ digits).
     */
    private fun computeSafetyNumber(phonePub: ByteArray, desktopPub: ByteArray): String {
        val combined = combinePubKeys(phonePub, desktopPub)
        val digest = MessageDigest.getInstance("SHA-256").digest(combined)
        return digest.take(4).joinToString("") { "%02X".format(it) }
    }

    private fun compareArrays(a: ByteArray, b: ByteArray): Int {
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (diff != 0) return diff
        }
        return a.size - b.size
    }

    private fun logDebug(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            android.util.Log.w(TAG, message, throwable)
        }
    }

    companion object {
        private const val TAG = "DesktopPairingSession"
        private const val CLIENT_CLOSE_TIMEOUT_MS = 5_000L
        private val SESSION_INFO = "passmanager-v1".toByteArray()
        private val secureRandom = SecureRandom()
    }
}

class PairingStartResult(
    val channel: EncryptedChannel,
    val client: DesktopPairingClient
)
