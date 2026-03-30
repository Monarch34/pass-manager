package com.passmanager.ui.desktop

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.R
import com.passmanager.protocol.PairingQrPayload
import com.passmanager.domain.usecase.ConnectToDesktopUseCase
import com.passmanager.domain.usecase.SendItemListToDesktopUseCase
import com.passmanager.domain.model.LockState
import com.passmanager.domain.model.PairingSessionState
import com.passmanager.domain.port.LockStateProvider
import com.passmanager.security.DesktopPairingSession
import com.passmanager.ui.common.AppLogger
import com.passmanager.ui.common.UserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.runtime.Immutable
import javax.inject.Inject

@Immutable
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
    private val lockStateProvider: LockStateProvider,
    private val requestHandler: DesktopRequestHandler
) : ViewModel() {

    private val _uiState = MutableStateFlow(DesktopLinkUiState())
    val uiState: StateFlow<DesktopLinkUiState> = _uiState.asStateFlow()

    private var processingQr = AtomicBoolean(false)

    /** Last emitted pairing state — used to detect transition into [PairingSessionState.Active] only. */
    private var previousPairingSessionState: PairingSessionState? = null

    init {
        observeSessionState()
        observeVaultLock()
    }

    private fun observeSessionState() {
        viewModelScope.launch {
            pairingSession.state.collect { sessionState ->
                _uiState.value = _uiState.value.copy(sessionState = sessionState)

                // When verification succeeds, send item list once per transition into Active (not on Active re-emissions).
                val becameActive =
                    sessionState is PairingSessionState.Active &&
                        previousPairingSessionState !is PairingSessionState.Active
                previousPairingSessionState = sessionState
                if (becameActive) {
                    viewModelScope.launch {
                        try {
                            sendItemListToDesktopUseCase()
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Failed to send item list", e)
                            // Don't disconnect — items can be re-requested by desktop
                        }
                    }
                }
            }
        }
    }

    private fun observeVaultLock() {
        viewModelScope.launch {
            lockStateProvider.lockState.collect { lockState ->
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
                requestHandler.startRequestLoop(viewModelScope)
                _uiState.value = _uiState.value.copy(isBusy = false)
            } catch (e: SerializationException) {
                AppLogger.e(TAG, "QR parsing failed", e)
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    error = UserMessage.Resource(R.string.desktop_error_invalid_qr)
                )
            } catch (e: com.passmanager.domain.exception.DesktopHandshakeException) {
                AppLogger.e(TAG, "Handshake failed", e)
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    error = UserMessage.Resource(R.string.desktop_error_session_expired)
                )
            } catch (e: ConnectException) {
                AppLogger.e(TAG, "Desktop not reachable", e)
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    error = UserMessage.Resource(R.string.desktop_error_unreachable)
                )
            } catch (e: SocketTimeoutException) {
                AppLogger.e(TAG, "Connection timed out", e)
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    error = UserMessage.Resource(R.string.desktop_error_timeout)
                )
            } catch (e: Exception) {
                AppLogger.e(TAG, "Pairing failed", e)
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    error = UserMessage.Resource(R.string.desktop_error_pairing_failed)
                )
            } finally {
                processingQr.set(false)
            }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            requestHandler.stopLoop()
            pairingSession.endSession(appContext.getString(R.string.desktop_session_user_disconnected))
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    override fun onCleared() {
        requestHandler.stopLoop()
        super.onCleared()
    }

    companion object {
        private const val TAG = "DesktopLinkVM"
    }
}
