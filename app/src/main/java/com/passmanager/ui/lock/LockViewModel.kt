package com.passmanager.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.R
import com.passmanager.domain.usecase.UnlockWithPassphraseUseCase
import com.passmanager.domain.exception.WrongPassphraseException
import com.passmanager.domain.model.LockState
import com.passmanager.domain.port.LockStateProvider
import com.passmanager.domain.exception.BiometricKeyInvalidatedException
import com.passmanager.domain.exception.DeviceKeyLostException
import com.passmanager.domain.exception.DeviceKeyUnavailableException
import com.passmanager.domain.exception.UnlockThrottledException
import com.passmanager.domain.port.BiometricLockPort
import com.passmanager.domain.usecase.UnlockThrottle
import com.passmanager.security.biometric.BiometricHelper
import com.passmanager.ui.common.AppLogger
import com.passmanager.ui.common.UserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable
import javax.crypto.Cipher
import javax.inject.Inject
import kotlin.math.ceil

@Immutable
data class LockUiState(
    val isLoading: Boolean = false,
    val error: UserMessage? = null,
    val shouldShakePassphraseField: Boolean = false,
    val isUnlocked: Boolean = false,
    val biometricAvailable: Boolean = false,
    /**
     * The device key that seals this vault is permanently gone. Distinct from [error] because it
     * is not a message the user can act on by trying again — it routes to the recovery screen.
     */
    val deviceKeyLost: Boolean = false,
    /**
     * Seconds left on the back-pressure delay after repeated failures; 0 when unlocking is
     * allowed. Shown as a countdown so the wait reads as a deliberate limit rather than a
     * frozen screen.
     */
    val lockoutRemainingSeconds: Int = 0
)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val unlockWithPassphraseUseCase: UnlockWithPassphraseUseCase,
    private val biometricLockPort: BiometricLockPort,
    private val lockStateProvider: LockStateProvider,
    private val biometricHelper: BiometricHelper,
    private val unlockThrottle: UnlockThrottle
) : ViewModel() {

    private val _uiState = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    private val _biometricCipherEvent = MutableSharedFlow<Cipher>(extraBufferCapacity = 1)
    val biometricCipherEvent: SharedFlow<Cipher> = _biometricCipherEvent

    private var countdownJob: Job? = null

    init {
        checkBiometricAvailability()
        // A lockout survives the screen being closed and reopened, so it has to be read back
        // rather than only tracked in memory from the failure that caused it.
        viewModelScope.launch { startCountdown(unlockThrottle.remainingLockoutMs()) }
    }

    /**
     * Ticks the visible countdown down to zero. Purely cosmetic - the real gate is re-checked in
     * the use case on every attempt, so a cancelled or stale timer cannot let an attempt through
     * early.
     */
    private fun startCountdown(remainingMs: Long) {
        countdownJob?.cancel()
        if (remainingMs <= 0L) {
            _uiState.update { it.copy(lockoutRemainingSeconds = 0) }
            return
        }
        countdownJob = viewModelScope.launch {
            var remaining = ceil(remainingMs / 1000.0).toInt()
            while (remaining > 0) {
                _uiState.update { it.copy(lockoutRemainingSeconds = remaining) }
                delay(1_000)
                remaining--
            }
            _uiState.update { it.copy(lockoutRemainingSeconds = 0) }
        }
    }

    private fun checkBiometricAvailability() {
        viewModelScope.launch {
            val locked = lockStateProvider.lockState.value !is LockState.Unlocked
            val available = locked && biometricLockPort.isAvailable()
            _uiState.update { it.copy(biometricAvailable = available) }
        }
    }

    fun unlockWithPassphrase(passphrase: CharArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, shouldShakePassphraseField = false) }
            try {
                unlockWithPassphraseUseCase(passphrase)
                _uiState.update { it.copy(isLoading = false, isUnlocked = true) }
            } catch (e: WrongPassphraseException) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = UserMessage.Resource(R.string.lock_wrong_passphrase),
                        shouldShakePassphraseField = true
                    )
                }
                startCountdown(unlockThrottle.remainingLockoutMs())
            } catch (e: UnlockThrottledException) {
                _uiState.update { it.copy(isLoading = false) }
                startCountdown(e.remainingMs)
            } catch (e: DeviceKeyLostException) {
                // The only failure that earns the recovery screen. Everything else — including
                // every other Keystore complaint — is a retry, because the recovery screen's only
                // exit is erasing the vault and no transient fault is worth that.
                AppLogger.e("LockViewModel", "Device key permanently lost", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        deviceKeyLost = true,
                        error = UserMessage.Resource(R.string.lock_device_key_lost)
                    )
                }
            } catch (e: DeviceKeyUnavailableException) {
                AppLogger.e("LockViewModel", "Device key temporarily unavailable", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = UserMessage.Resource(R.string.lock_device_key_unavailable)
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("LockViewModel", "Passphrase unlock failed", e)
                _uiState.update {
                    it.copy(isLoading = false, error = UserMessage.Resource(R.string.lock_unlock_failed))
                }
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }

    fun prepareBiometricCipher() {
        viewModelScope.launch {
            try {
                val cipher = biometricLockPort.createAuthCipher()
                _biometricCipherEvent.emit(cipher)
            } catch (e: BiometricKeyInvalidatedException) {
                AppLogger.e("LockViewModel", "Biometric key invalidated", e)
                _uiState.update {
                    it.copy(
                        error = UserMessage.Resource(R.string.error_biometric_invalidated),
                        biometricAvailable = false
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("LockViewModel", "Biometric cipher preparation failed", e)
                _uiState.update { it.copy(error = UserMessage.Resource(R.string.lock_biometric_unlock_failed)) }
            }
        }
    }

    fun onBiometricSuccess(authenticatedCipher: Cipher) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                biometricLockPort.unlock(authenticatedCipher)
                unlockThrottle.clear()
                countdownJob?.cancel()
                _uiState.update { it.copy(isLoading = false, isUnlocked = true, lockoutRemainingSeconds = 0) }
            } catch (e: Exception) {
                AppLogger.e("LockViewModel", "Biometric unlock failed", e)
                _uiState.update {
                    it.copy(isLoading = false, error = UserMessage.Resource(R.string.lock_biometric_unlock_failed))
                }
            }
        }
    }

    /**
     * The system prompt is gone by the time this runs, so without surfacing the reason the screen
     * would just sit there and the user would have no idea why nothing opened. The text comes from
     * BiometricPrompt itself (already localised by the platform), so it is passed through as-is.
     */
    fun onBiometricError(message: String) {
        _uiState.update { it.copy(isLoading = false, error = UserMessage.Plain(message)) }
    }

    /** Soft failure: the prompt stays open for another try, so only the hint is refreshed. */
    fun onBiometricFail() {
        _uiState.update { it.copy(error = UserMessage.Resource(R.string.lock_biometric_not_recognised)) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, shouldShakePassphraseField = false) }
    }

    fun onPassphraseShakeConsumed() {
        _uiState.update { it.copy(shouldShakePassphraseField = false) }
    }
}
