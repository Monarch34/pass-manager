package com.passmanager.ui.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.R
import com.passmanager.data.preferences.AppPreferences
import com.passmanager.crypto.keystore.AndroidKeystoreManager
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.domain.usecase.ChangePassphraseUseCase
import com.passmanager.domain.usecase.EnableBiometricUseCase
import com.passmanager.domain.usecase.WrongPassphraseException
import com.passmanager.security.VaultLockManager
import com.passmanager.security.biometric.BiometricHelper
import com.passmanager.ui.common.UserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.crypto.Cipher
import javax.inject.Inject

data class SettingsUiState(
    val biometricEnabled: Boolean = false,
    val biometricAvailableOnDevice: Boolean = false,
    val autoLockSeconds: Int = AppPreferences.DEFAULT_AUTO_LOCK_SECONDS,
    val error: UserMessage? = null,
    val showChangePassphraseSheet: Boolean = false,
    val isPassphraseChanging: Boolean = false,
    val passphraseChangeError: UserMessage? = null,
    val vaultLocked: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val metadataRepository: MetadataRepository,
    private val enableBiometricUseCase: EnableBiometricUseCase,
    private val appPreferences: AppPreferences,
    private val biometricHelper: BiometricHelper,
    private val changePassphraseUseCase: ChangePassphraseUseCase,
    private val vaultLockManager: VaultLockManager,
    private val keystoreManager: AndroidKeystoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _pendingBiometricCipherEvent = MutableSharedFlow<Cipher>(extraBufferCapacity = 1)
    val pendingBiometricCipherEvent: SharedFlow<Cipher> = _pendingBiometricCipherEvent

    init {
        viewModelScope.launch {
            val metadata = metadataRepository.get()
            val canUseBiometric = biometricHelper.canUseBiometric()
            _uiState.value = _uiState.value.copy(
                biometricEnabled = metadata?.biometricEnabled == true,
                biometricAvailableOnDevice = canUseBiometric
            )
        }
        appPreferences.autoLockTimeoutSeconds
            .onEach { seconds -> _uiState.update { it.copy(autoLockSeconds = seconds) } }
            .launchIn(viewModelScope)
    }

    fun toggleBiometric() {
        val currentlyEnabled = _uiState.value.biometricEnabled
        if (currentlyEnabled) {
            disableBiometric()
        } else {
            prepareBiometricEnrollment()
        }
    }

    private fun prepareBiometricEnrollment() {
        viewModelScope.launch {
            try {
                val cipher = enableBiometricUseCase.prepareEnrollment()
                _pendingBiometricCipherEvent.emit(cipher)
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to prepare biometric", e)
                _uiState.value = _uiState.value.copy(
                    error = UserMessage.Resource(R.string.settings_error_biometric_prepare)
                )
            }
        }
    }

    fun onBiometricEnrollmentSuccess(authenticatedCipher: Cipher) {
        viewModelScope.launch {
            try {
                enableBiometricUseCase.completeEnrollment(authenticatedCipher)
                _uiState.value = _uiState.value.copy(biometricEnabled = true)
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to enable biometric", e)
                _uiState.value = _uiState.value.copy(
                    error = UserMessage.Resource(R.string.settings_error_biometric_enable)
                )
            }
        }
    }

    private fun disableBiometric() {
        viewModelScope.launch {
            try {
                metadataRepository.disableBiometric()
                keystoreManager.deleteBiometricKey()
                _uiState.value = _uiState.value.copy(biometricEnabled = false)
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to disable biometric", e)
                _uiState.value = _uiState.value.copy(
                    error = UserMessage.Resource(R.string.settings_error_biometric_disable)
                )
            }
        }
    }

    fun setAutoLockTimeout(seconds: Int) {
        viewModelScope.launch {
            appPreferences.setAutoLockTimeout(seconds)
        }
    }

    fun openChangePassphraseSheet() {
        _uiState.value = _uiState.value.copy(showChangePassphraseSheet = true)
    }

    fun dismissChangePassphraseSheet() {
        _uiState.value = _uiState.value.copy(
            showChangePassphraseSheet = false,
            passphraseChangeError = null
        )
    }

    fun changePassphrase(current: CharArray, new: CharArray, confirm: CharArray) {
        if (!new.contentEquals(confirm)) {
            _uiState.value = _uiState.value.copy(
                passphraseChangeError = UserMessage.Resource(R.string.onboarding_passphrase_mismatch)
            )
            current.fill('\u0000')
            new.fill('\u0000')
            confirm.fill('\u0000')
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPassphraseChanging = true, passphraseChangeError = null)
            try {
                changePassphraseUseCase(current, new)
                vaultLockManager.lock()
                _uiState.value = _uiState.value.copy(isPassphraseChanging = false, vaultLocked = true)
            } catch (e: WrongPassphraseException) {
                _uiState.value = _uiState.value.copy(
                    isPassphraseChanging = false,
                    passphraseChangeError = UserMessage.Resource(R.string.settings_wrong_current_passphrase)
                )
            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Failed to change passphrase", e)
                _uiState.value = _uiState.value.copy(
                    isPassphraseChanging = false,
                    passphraseChangeError = UserMessage.Resource(R.string.settings_passphrase_change_failed)
                )
            } finally {
                current.fill('\u0000')
                new.fill('\u0000')
                confirm.fill('\u0000')
            }
        }
    }

    fun clearPassphraseChangeError() {
        _uiState.value = _uiState.value.copy(passphraseChangeError = null)
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
