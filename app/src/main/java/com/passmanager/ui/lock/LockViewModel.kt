package com.passmanager.ui.lock

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.R
import com.passmanager.crypto.keystore.AndroidKeystoreManager
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.domain.usecase.BiometricKeyInvalidatedException
import com.passmanager.domain.usecase.UnlockWithBiometricUseCase
import com.passmanager.domain.usecase.UnlockWithPassphraseUseCase
import com.passmanager.domain.usecase.WrongPassphraseException
import com.passmanager.security.LockState
import com.passmanager.security.VaultLockManager
import com.passmanager.security.biometric.BiometricHelper
import com.passmanager.ui.common.UserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

data class LockUiState(
    val isLoading: Boolean = false,
    val error: UserMessage? = null,
    val shouldShakePassphraseField: Boolean = false,
    val isUnlocked: Boolean = false,
    val biometricAvailable: Boolean = false,
    val biometricCipher: Cipher? = null
)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val unlockWithPassphraseUseCase: UnlockWithPassphraseUseCase,
    private val unlockWithBiometricUseCase: UnlockWithBiometricUseCase,
    private val metadataRepository: MetadataRepository,
    private val vaultLockManager: VaultLockManager,
    private val keystoreManager: AndroidKeystoreManager,
    private val biometricHelper: BiometricHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    private val _biometricCipherEvent = MutableSharedFlow<Cipher>(extraBufferCapacity = 1)
    val biometricCipherEvent: SharedFlow<Cipher> = _biometricCipherEvent

    val lockState: StateFlow<LockState> = vaultLockManager.lockState

    init {
        checkBiometricAvailability()
    }

    private fun checkBiometricAvailability() {
        viewModelScope.launch {
            val metadata = metadataRepository.get()
            val enabledInDb = metadata?.biometricEnabled == true
            val hasWrapped = metadata?.biometricWrappedKey != null
            val hasKeystoreKey = keystoreManager.hasBiometricKey()
            val deviceOk = biometricHelper.canUseBiometric()
            val locked = vaultLockManager.lockState.value !is LockState.Unlocked
            _uiState.value = _uiState.value.copy(
                biometricAvailable = locked &&
                    enabledInDb &&
                    hasWrapped &&
                    hasKeystoreKey &&
                    deviceOk
            )
        }
    }

    fun unlockWithPassphrase(passphrase: CharArray) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                shouldShakePassphraseField = false
            )
            try {
                unlockWithPassphraseUseCase(passphrase)
                _uiState.value = _uiState.value.copy(isLoading = false, isUnlocked = true)
            } catch (e: WrongPassphraseException) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = UserMessage.Resource(R.string.lock_wrong_passphrase),
                    shouldShakePassphraseField = true
                )
            } catch (e: Exception) {
                Log.e("LockViewModel", "Passphrase unlock failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = UserMessage.Resource(R.string.lock_unlock_failed)
                )
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }

    fun prepareBiometricCipher() {
        viewModelScope.launch {
            try {
                val cipher = unlockWithBiometricUseCase.createAuthCipher()
                _biometricCipherEvent.emit(cipher)
            } catch (e: BiometricKeyInvalidatedException) {
                Log.e("LockViewModel", "Biometric key invalidated", e)
                _uiState.value = _uiState.value.copy(
                    error = UserMessage.Resource(R.string.error_biometric_invalidated),
                    biometricAvailable = false
                )
            }
        }
    }

    fun onBiometricSuccess(authenticatedCipher: Cipher) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                unlockWithBiometricUseCase(authenticatedCipher)
                _uiState.value = _uiState.value.copy(isLoading = false, isUnlocked = true)
            } catch (e: Exception) {
                Log.e("LockViewModel", "Biometric unlock failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = UserMessage.Resource(R.string.lock_biometric_unlock_failed)
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null, shouldShakePassphraseField = false)
    }

    fun onPassphraseShakeConsumed() {
        _uiState.value = _uiState.value.copy(shouldShakePassphraseField = false)
    }
}
