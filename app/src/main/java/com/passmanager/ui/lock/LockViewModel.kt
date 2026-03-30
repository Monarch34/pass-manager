package com.passmanager.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.R
import com.passmanager.domain.usecase.UnlockWithPassphraseUseCase
import com.passmanager.domain.exception.WrongPassphraseException
import com.passmanager.domain.model.LockState
import com.passmanager.domain.port.LockStateProvider
import com.passmanager.domain.exception.BiometricKeyInvalidatedException
import com.passmanager.domain.port.BiometricLockPort
import com.passmanager.security.biometric.BiometricHelper
import com.passmanager.ui.common.AppLogger
import com.passmanager.ui.common.UserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
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

@Immutable
data class LockUiState(
    val isLoading: Boolean = false,
    val error: UserMessage? = null,
    val shouldShakePassphraseField: Boolean = false,
    val isUnlocked: Boolean = false,
    val biometricAvailable: Boolean = false
)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val unlockWithPassphraseUseCase: UnlockWithPassphraseUseCase,
    private val biometricLockPort: BiometricLockPort,
    private val lockStateProvider: LockStateProvider,
    private val biometricHelper: BiometricHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    private val _biometricCipherEvent = MutableSharedFlow<Cipher>(extraBufferCapacity = 1)
    val biometricCipherEvent: SharedFlow<Cipher> = _biometricCipherEvent

    init {
        checkBiometricAvailability()
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
                _uiState.update { it.copy(isLoading = false, isUnlocked = true) }
            } catch (e: Exception) {
                AppLogger.e("LockViewModel", "Biometric unlock failed", e)
                _uiState.update {
                    it.copy(isLoading = false, error = UserMessage.Resource(R.string.lock_biometric_unlock_failed))
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, shouldShakePassphraseField = false) }
    }

    fun onPassphraseShakeConsumed() {
        _uiState.update { it.copy(shouldShakePassphraseField = false) }
    }
}
