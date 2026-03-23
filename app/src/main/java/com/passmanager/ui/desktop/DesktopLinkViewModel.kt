package com.passmanager.ui.desktop

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.R
import com.passmanager.crypto.channel.PairingQrPayload
import com.passmanager.crypto.channel.SecureRequest
import com.passmanager.crypto.channel.SecureResponse
import com.passmanager.domain.usecase.ConnectToDesktopUseCase
import com.passmanager.domain.usecase.SendItemListToDesktopUseCase
import com.passmanager.domain.usecase.SendPasswordToDesktopUseCase
import com.passmanager.security.DesktopPairingSession
import com.passmanager.security.LockState
import com.passmanager.security.PairingSessionState
import com.passmanager.security.VaultLockManager
import com.passmanager.ui.common.UserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.inject.Inject

data class DesktopLinkUiState(
    val vaultUnlocked: Boolean = false,
    val sessionState: PairingSessionState = PairingSessionState.Idle,
    val isScanning: Boolean = false,
    val isBusy: Boolean = false,
    val error: UserMessage? = null
)

@HiltViewModel
class DesktopLinkViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val pairingSession: DesktopPairingSession,
    private val connectToDesktopUseCase: ConnectToDesktopUseCase,
    private val sendItemListToDesktopUseCase: SendItemListToDesktopUseCase,
    private val sendPasswordToDesktopUseCase: SendPasswordToDesktopUseCase,
    private val vaultLockManager: VaultLockManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DesktopLinkUiState())
    val uiState: StateFlow<DesktopLinkUiState> = _uiState.asStateFlow()

    private var requestLoopJob: Job? = null
    private var processingQr = java.util.concurrent.atomic.AtomicBoolean(false)

    init {
        observeSessionState()
        observeVaultLock()
        createNotificationChannel()
    }

    private fun observeSessionState() {
        viewModelScope.launch {
            pairingSession.state.collect { sessionState ->
                _uiState.value = _uiState.value.copy(sessionState = sessionState)

                // When verification succeeds and session becomes Active, send item list
                if (sessionState is PairingSessionState.Active) {
                    viewModelScope.launch {
                        try {
                            sendItemListToDesktopUseCase()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to send item list", e)
                            // Don't disconnect — items can be re-requested by desktop
                        }
                    }
                }
            }
        }
    }

    private fun observeVaultLock() {
        viewModelScope.launch {
            vaultLockManager.lockState.collect { lockState ->
                val unlocked = lockState is LockState.Unlocked
                _uiState.value = _uiState.value.copy(vaultUnlocked = unlocked)
                if (!unlocked && pairingSession.state.value is PairingSessionState.Active) {
                    pairingSession.endSession(appContext.getString(R.string.desktop_session_vault_locked))
                }
            }
        }
    }

    fun startScanning() {
        pairingSession.resetToIdleIfTerminal()
        _uiState.value = _uiState.value.copy(isScanning = true, error = null)
    }

    fun stopScanning() {
        _uiState.value = _uiState.value.copy(isScanning = false)
    }

    fun onQrCodeScanned(rawValue: String) {
        // Guard against duplicate ML Kit callbacks firing before UI recomposes
        if (!processingQr.compareAndSet(false, true)) return
        _uiState.value = _uiState.value.copy(isScanning = false, isBusy = true, error = null)
        viewModelScope.launch {
            try {
                val payload = Json.decodeFromString<PairingQrPayload>(rawValue)
                connectToDesktopUseCase(payload).getOrThrow()
                // Session is now in Verifying state — start request loop to listen for Verify
                startRequestLoop()
                _uiState.value = _uiState.value.copy(isBusy = false)
            } catch (e: SerializationException) {
                Log.e(TAG, "QR parsing failed", e)
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    error = UserMessage.Resource(R.string.desktop_error_invalid_qr)
                )
            } catch (e: com.passmanager.domain.usecase.DesktopHandshakeException) {
                Log.e(TAG, "Handshake failed", e)
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    error = UserMessage.Resource(R.string.desktop_error_session_expired)
                )
            } catch (e: ConnectException) {
                Log.e(TAG, "Desktop not reachable", e)
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    error = UserMessage.Resource(R.string.desktop_error_unreachable)
                )
            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "Connection timed out", e)
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    error = UserMessage.Resource(R.string.desktop_error_timeout)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Pairing failed", e)
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    error = UserMessage.Resource(
                        R.string.desktop_error_connection_failed_with_reason,
                        e.message ?: appContext.getString(R.string.error_unknown)
                    )
                )
            } finally {
                processingQr.set(false)
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            requestLoopJob?.cancel()
            pairingSession.endSession(appContext.getString(R.string.desktop_session_user_disconnected))
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun startRequestLoop() {
        requestLoopJob?.cancel()
        requestLoopJob = viewModelScope.launch {
            while (isActive) {
                val request = pairingSession.receiveSecureRequest() ?: break
                handleDesktopRequest(request)
            }
        }
    }

    private suspend fun handleDesktopRequest(request: SecureRequest) {
        val state = pairingSession.state.value
        val requestAllowed = when (state) {
            is PairingSessionState.Active -> true
            is PairingSessionState.Verifying ->
                request is SecureRequest.Verify ||
                    request is SecureRequest.Heartbeat ||
                    request is SecureRequest.Disconnect
            else ->
                request is SecureRequest.Heartbeat ||
                    request is SecureRequest.Disconnect
        }
        if (!requestAllowed) {
            sendErrorSafely(
                appContext.getString(R.string.desktop_error_verification_required),
                "rejecting disallowed request ${request::class.simpleName}"
            )
            return
        }

        when (request) {
            is SecureRequest.GetPassword -> {
                try {
                    val title = sendPasswordToDesktopUseCase(request.itemId)
                    showPasswordNotification(title)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send password", e)
                    sendErrorSafely(
                        appContext.getString(
                            R.string.desktop_error_password_retrieval_failed,
                            (e.message ?: appContext.getString(R.string.error_unknown))
                        ),
                        "sending password retrieval error"
                    )
                }
            }
            is SecureRequest.ListItems -> {
                sendItemListToDesktopUseCase()
            }
            is SecureRequest.Heartbeat -> {
                pairingSession.respondToHeartbeat()
            }
            is SecureRequest.Disconnect -> {
                pairingSession.endSession(appContext.getString(R.string.desktop_session_desktop_disconnected))
            }
            is SecureRequest.Verify -> {
                pairingSession.handleVerifyRequest(request.code)
            }
        }
    }

    private suspend fun sendErrorSafely(message: String, context: String) {
        try {
            pairingSession.sendSecure(SecureResponse.Error(message))
        } catch (sendError: Exception) {
            Log.w(TAG, "Failed $context", sendError)
        }
    }

    private fun showPasswordNotification(itemTitle: String) {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(appContext.getString(R.string.desktop_link_title))
            .setContentText(
                appContext.getString(R.string.desktop_notification_password_sent, itemTitle)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID_BASE + (System.currentTimeMillis() % 1000).toInt(), notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.desktop_pairing_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = appContext.getString(R.string.desktop_pairing_channel_desc)
            }
            nm.createNotificationChannel(channel)
        }
    }

    override fun onCleared() {
        requestLoopJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val TAG = "DesktopLinkVM"
        private const val CHANNEL_ID = "desktop_pairing"
        private const val NOTIFICATION_ID_BASE = 39000
    }
}
