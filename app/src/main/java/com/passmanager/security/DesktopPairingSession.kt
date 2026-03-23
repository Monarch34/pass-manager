package com.passmanager.security

import android.util.Base64
import android.util.Log
import com.passmanager.agent.DesktopPairingClient
import com.passmanager.crypto.channel.EncryptedChannel
import com.passmanager.crypto.channel.PairingQrPayload
import com.passmanager.crypto.channel.SecureRequest
import com.passmanager.crypto.channel.SecureResponse
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
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

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

/**
 * Manages the lifecycle of a single ephemeral desktop pairing session.
 * Holds all transient crypto state (keypairs, session key, channel) in memory.
 * Everything is zeroed on session end.
 *
 * W1 mitigation: rate limits password requests (max [MAX_PW_PER_SESSION] per session,
 * min [PW_COOLDOWN_MS] between requests).
 */
@Singleton
class DesktopPairingSession @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<PairingSessionState>(PairingSessionState.Idle)
    val state: StateFlow<PairingSessionState> = _state.asStateFlow()

    private var keyExchange: X25519KeyExchange? = null
    private var sessionKey: SensitiveByteArray? = null
    private var channel: EncryptedChannel? = null
    private var client: DesktopPairingClient? = null

    private var inactivityJob: Job? = null
    private var sessionJob: Job? = null
    private var verifyTimeoutJob: Job? = null

    private var pendingSafetyNumber: String = ""

    private var passwordsSentThisSession = 0
    private var lastPasswordRequestTimeMs = 0L
    private var lastItemTitle: String? = null

    // Exposed for the pairing flow to read
    var desktopIp: String? = null; private set
    var desktopPort: Int = 0; private set

    /**
     * Phase 1: Parse QR payload, generate ephemeral keypair, perform ECDH handshake.
     * Returns the channel and client for Phase 2.
     */
    suspend fun startPairing(qrPayload: PairingQrPayload): PairingStartResult {
        cleanup()
        _state.value = PairingSessionState.Pairing

        desktopIp = qrPayload.ip
        desktopPort = qrPayload.port

        val kx = X25519KeyExchange()
        keyExchange = kx

        val pairingClient = DesktopPairingClient()
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

            // ECDH → shared secret → HKDF → session key
            val sharedSecret = kx.deriveSharedSecret(desktopPubBytes)
            kx.close() // zero private key immediately
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

            sessionKey = SensitiveByteArray.directFrom(derivedKey) // zeroes derivedKey

            val ch = EncryptedChannel(
                sessionKey = sessionKey!!.copyBytes(), // EncryptedChannel zeros this copy immediately
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

    /**
     * Phase 2: Open WebSocket, generate 6-digit verification code, transition to
     * [PairingSessionState.Verifying]. The code is displayed on the phone for the
     * user to type on the desktop. Actual session activation happens when
     * [handleVerifyRequest] receives the correct code.
     */
    suspend fun completePairing(
        channel: EncryptedChannel,
        pairingClient: DesktopPairingClient,
    ) {
        val ip = desktopIp ?: throw IllegalStateException("No desktop IP")
        val port = desktopPort

        val verified = CompletableDeferred<Unit>()

        // Generate random 6-digit code
        val code = String.format("%06d", SecureRandom().nextInt(1_000_000))

        sessionJob = scope.launch {
            try {
                pairingClient.runSecureSession(ip, port, channel) {
                    // WebSocket is open — transition to Verifying and signal caller
                    val expiresAt = System.currentTimeMillis() + VERIFY_CODE_TIMEOUT_MS
                    _state.value = PairingSessionState.Verifying(
                        code = code,
                        desktopIp = ip,
                        attemptsRemaining = PairingSessionState.Verifying.MAX_VERIFY_ATTEMPTS,
                        expiresAtMs = expiresAt,
                        safetyNumber = pendingSafetyNumber
                    )
                    // Start code expiry timer
                    verifyTimeoutJob = scope.launch {
                        delay(VERIFY_CODE_TIMEOUT_MS)
                        if (_state.value is PairingSessionState.Verifying) {
                            try {
                                sendSecure(SecureResponse.VerifyFailed("Code expired", 0))
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to send verify timeout", e)
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

        // Auto-end session if the WebSocket closes unexpectedly
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

    /**
     * Handles a verification code attempt from the desktop.
     * Returns true if the code matches and session is now Active.
     */
    suspend fun handleVerifyRequest(receivedCode: String): Boolean {
        val currentState = _state.value
        if (currentState !is PairingSessionState.Verifying) return false

        if (receivedCode == currentState.code) {
            sendSecure(SecureResponse.VerifyOk(safetyNumber = pendingSafetyNumber))

            passwordsSentThisSession = 0
            lastPasswordRequestTimeMs = 0L
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

    /**
     * Clears terminal UI states so the user can scan again without staying on Error/Ended.
     */
    fun resetToIdleIfTerminal() {
        when (_state.value) {
            is PairingSessionState.Error, is PairingSessionState.Ended -> {
                _state.value = PairingSessionState.Idle
            }
            else -> Unit
        }
    }

    /**
     * Resets crypto and UI state after a failed pairing attempt (stuck in [PairingSessionState.Pairing]).
     */
    fun abortPairing(reason: String) {
        sessionJob?.cancel()
        inactivityJob?.cancel()
        verifyTimeoutJob?.cancel()
        sessionJob = null
        inactivityJob = null
        verifyTimeoutJob = null
        _state.value = PairingSessionState.Error(reason)
        cleanup()
    }

    fun canSendPassword(): Boolean {
        if (_state.value !is PairingSessionState.Active) return false
        if (passwordsSentThisSession >= MAX_PW_PER_SESSION) return false
        val now = System.currentTimeMillis()
        if (now - lastPasswordRequestTimeMs < PW_COOLDOWN_MS) return false
        return true
    }

    fun recordPasswordSent(itemTitle: String) {
        passwordsSentThisSession++
        lastPasswordRequestTimeMs = System.currentTimeMillis()
        lastItemTitle = itemTitle
        resetInactivityTimer()

        val ip = desktopIp ?: return
        _state.value = PairingSessionState.Active(
            desktopIp = ip,
            passwordsSent = passwordsSentThisSession,
            lastItemTitle = lastItemTitle
        )

        if (passwordsSentThisSession >= MAX_PW_PER_SESSION) {
            scope.launch { endSession("Session password limit reached") }
        }
    }

    suspend fun sendSecure(response: SecureResponse) {
        client?.sendSecure(response)
    }

    suspend fun receiveSecureRequest(): SecureRequest? {
        return try {
            client?.receiveSecureRequest()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to receive secure request", e)
            null
        }
    }

    suspend fun endSession(reason: String = "User disconnected") {
        _state.value = PairingSessionState.Ended(reason)
        try {
            client?.sendSecure(SecureResponse.DisconnectAck)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send disconnect ack", e)
        }
        cleanup()
    }

    private fun cleanup() {
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

        // Capture client reference before nulling to avoid race condition
        val clientRef = client
        client = null
        if (clientRef != null) {
            scope.launch {
                try {
                    clientRef.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to close desktop client", e)
                }
            }
        }

        passwordsSentThisSession = 0
        lastPasswordRequestTimeMs = 0L
        lastItemTitle = null
        pendingSafetyNumber = ""
        desktopIp = null
        desktopPort = 0
    }

    /**
     * Responds to a heartbeat request from the desktop.
     * Resets the inactivity timer (heartbeat proves the desktop is alive).
     */
    suspend fun respondToHeartbeat() {
        resetInactivityTimer()
        sendSecure(SecureResponse.HeartbeatAck(ts = System.currentTimeMillis()))
    }

    private fun resetInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = scope.launch {
            delay(INACTIVITY_TIMEOUT_MS)
            endSession("Inactivity timeout")
        }
    }

    /**
     * Deterministic ordering: compare byte-by-byte (lexicographic) so the same
     * two keys always produce the same salt regardless of JVM run or hash collisions.
     * contentHashCode() is NOT used — it is non-deterministic across JVM restarts
     * and prone to collisions.
     */
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
     * SHA-256(min(a,b) || max(a,b)) → first 4 bytes → 8 uppercase hex chars.
     * Shown on both phone and desktop so the user can visually confirm no MITM occurred.
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

    companion object {
        private const val TAG = "DesktopPairingSession"
        private val SESSION_INFO = "passmanager-v1".toByteArray()
        const val INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L
        const val VERIFY_CODE_TIMEOUT_MS = 15_000L
        const val MAX_PW_PER_SESSION = 20
        const val PW_COOLDOWN_MS = 10_000L
    }
}

data class PairingStartResult(
    val channel: EncryptedChannel,
    val client: DesktopPairingClient
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}
