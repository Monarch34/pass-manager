package com.passmanager.desktop.server

import com.passmanager.desktop.crypto.EncryptedChannel
import com.passmanager.desktop.crypto.SensitiveByteArray
import com.passmanager.desktop.model.ItemSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

sealed interface DesktopSessionState {
    data object WaitingForPhone : DesktopSessionState
    data class VerifyingCode(
        val attemptsRemaining: Int = 3,
        val error: String? = null,
        /** 8-char hex fingerprint shown to user for out-of-band MITM verification. */
        val safetyNumber: String = ""
    ) : DesktopSessionState
    data class Connected(val safetyNumber: String) : DesktopSessionState
    data class Disconnected(val reason: String) : DesktopSessionState
}

/**
 * Manages desktop-side session lifecycle. Holds session key in a [SensitiveByteArray]
 * backed by DirectByteBuffer (W5 mitigation). Zeroes all material on disconnect.
 *
 * Thread-safety: all crypto state mutations synchronize on [lock].
 * The [cleanup] ↔ [close] distinction matters:
 *  - [cleanup]: resets session state for next pairing cycle (scope stays alive)
 *  - [close]:   app exit — cleanup + cancel scope permanently
 */
class DesktopSessionManager : Closeable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val cleaningUp = AtomicBoolean(false)

    private val _state = MutableStateFlow<DesktopSessionState>(DesktopSessionState.WaitingForPhone)
    val state: StateFlow<DesktopSessionState> = _state.asStateFlow()

    private val _items = MutableStateFlow<List<ItemSummary>>(emptyList())
    val items: StateFlow<List<ItemSummary>> = _items.asStateFlow()

    private val _clipboardStatus = MutableStateFlow<String?>(null)
    val clipboardStatus: StateFlow<String?> = _clipboardStatus.asStateFlow()

    private var sessionKey: SensitiveByteArray? = null
    var channel: EncryptedChannel? = null; private set

    /** Computed during handshake; included in VerifyingCode state for display on VerifyScreen. */
    private var pendingSafetyNumber: String = ""

    private var heartbeatJob: Job? = null
    private var inactivityJob: Job? = null

    @Volatile
    private var lastHeartbeatAckTime: Long = 0L

    // ---- Callbacks wired by Main.kt ----

    /** Called after session cleanup — triggers PairingServer.generateSession(). */
    var onSessionEnded: (() -> Unit)? = null

    /** Called when a heartbeat needs to be sent to the phone. */
    var onHeartbeatNeeded: (suspend () -> Unit)? = null

    // ---- Session key management ----

    fun setSessionKey(key: SensitiveByteArray) {
        synchronized(lock) {
            sessionKey?.close()
            sessionKey = key
        }
    }

    fun sessionKeyCopy(): ByteArray {
        synchronized(lock) {
            return sessionKey?.copyBytes()
                ?: throw IllegalStateException("No session key")
        }
    }

    fun setChannel(ch: EncryptedChannel) {
        synchronized(lock) {
            channel?.close()
            channel = ch
        }
    }

    fun setPendingSafetyNumber(sn: String) {
        pendingSafetyNumber = sn
    }

    // ---- Session lifecycle events ----

    fun onSessionConnected() {
        _state.value = DesktopSessionState.VerifyingCode(safetyNumber = pendingSafetyNumber)
        lastHeartbeatAckTime = System.currentTimeMillis()
    }

    fun onCodeVerified(safetyNumber: String) {
        _state.value = DesktopSessionState.Connected(safetyNumber)
        startHeartbeatMonitor()
        resetInactivityTimer()
    }

    fun onVerifyFailed(error: String, attemptsRemaining: Int) {
        if (attemptsRemaining <= 0) {
            onDisconnected("Verification failed: $error")
        } else {
            val currentSafetyNumber = (state.value as? DesktopSessionState.VerifyingCode)?.safetyNumber ?: ""
            _state.value = DesktopSessionState.VerifyingCode(attemptsRemaining, error, currentSafetyNumber)
        }
    }

    fun onItemsReceived(items: List<ItemSummary>) {
        _items.value = items
        resetInactivityTimer()
    }

    fun onPasswordReceived(itemId: String) {
        resetInactivityTimer()
    }

    fun onHeartbeatAckReceived() {
        lastHeartbeatAckTime = System.currentTimeMillis()
    }

    fun setClipboardStatus(status: String?) {
        _clipboardStatus.value = status
    }

    fun onDisconnected(reason: String) {
        _state.value = DesktopSessionState.Disconnected(reason)
        cleanup()
    }

    /**
     * Transition back to [WaitingForPhone] after generating a new session.
     * Called by Main.kt after [onSessionEnded] triggers [PairingServer.generateSession].
     */
    fun resetToWaiting() {
        _state.value = DesktopSessionState.WaitingForPhone
    }

    // ---- Cleanup & close ----

    /**
     * Resets session state (keys, channel, timers, items) but keeps the
     * coroutine scope alive so the next pairing cycle can use it.
     */
    private fun cleanup() {
        // compareAndSet prevents concurrent or repeated cleanup — flag is never reset,
        // acting as a one-shot latch for this session's lifecycle.
        if (!cleaningUp.compareAndSet(false, true)) return

        heartbeatJob?.cancel()
        inactivityJob?.cancel()
        heartbeatJob = null
        inactivityJob = null

        synchronized(lock) {
            channel?.close()
            channel = null

            sessionKey?.close()
            sessionKey = null
        }

        _items.value = emptyList()
        _clipboardStatus.value = null
        pendingSafetyNumber = ""

        // Reset the latch so the next pairing cycle can call cleanup again
        cleaningUp.set(false)

        onSessionEnded?.invoke()
    }

    /**
     * App exit: cleanup session state AND cancel the coroutine scope permanently.
     */
    override fun close() {
        cleanup()
        scope.cancel()
    }

    private fun CoroutineScope.cancel() {
        val job = coroutineContext[Job]
        job?.cancel()
    }

    // ---- Heartbeat & inactivity ----

    /**
     * Sends periodic heartbeats and monitors for ack responses.
     * If no ack within [HEARTBEAT_INTERVAL_MS] + [HEARTBEAT_GRACE_MS],
     * the session is considered dead.
     */
    private fun startHeartbeatMonitor() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS)
                try {
                    onHeartbeatNeeded?.invoke()
                } catch (_: Exception) {
                    onDisconnected("Heartbeat send failed")
                    break
                }
                delay(HEARTBEAT_GRACE_MS)
                val elapsed = System.currentTimeMillis() - lastHeartbeatAckTime
                if (elapsed > HEARTBEAT_INTERVAL_MS + HEARTBEAT_GRACE_MS) {
                    onDisconnected("Heartbeat timeout")
                    break
                }
            }
        }
    }

    private fun resetInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = scope.launch {
            delay(INACTIVITY_TIMEOUT_MS)
            onDisconnected("Inactivity timeout")
        }
    }

    companion object {
        const val HEARTBEAT_INTERVAL_MS = 15_000L
        const val HEARTBEAT_GRACE_MS = 5_000L
        const val INACTIVITY_TIMEOUT_MS = 5 * 60 * 1000L
    }
}
