package com.passmanager.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.BuildConfig
import com.passmanager.R
import com.passmanager.domain.port.AppSettingsDefaults
import com.passmanager.domain.port.AppSettingsPort
import com.passmanager.domain.usecase.ChangePassphraseUseCase
import com.passmanager.domain.usecase.SeedDemoVaultItemsUseCase
import com.passmanager.domain.exception.WrongPassphraseException
import com.passmanager.domain.model.LockState
import com.passmanager.domain.port.LockStateProvider
import com.passmanager.domain.port.BiometricLockPort
import com.passmanager.security.biometric.BiometricHelper
import com.passmanager.ui.common.AppLogger
import com.passmanager.ui.common.UserMessage
import coil.imageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import androidx.compose.runtime.Immutable
import javax.inject.Inject

/** One-shot error shown to the user in the Settings screen. */
sealed interface SettingsError {
    val message: UserMessage
    /** General biometric / setting error shown as a snackbar. */
    data class General(override val message: UserMessage) : SettingsError
    /** Passphrase change failure shown inline in the bottom sheet. */
    data class PassphraseChange(override val message: UserMessage) : SettingsError
}

@Immutable
data class SettingsUiState(
    val biometricEnabled: Boolean = false,
    val biometricAvailableOnDevice: Boolean = false,
    val autoLockSeconds: Int = AppSettingsDefaults.AUTO_LOCK_SECONDS,
    val useGoogleFavicons: Boolean = AppSettingsDefaults.USE_GOOGLE_FAVICONS,
    val error: SettingsError? = null,
    val showChangePassphraseSheet: Boolean = false,
    val isPassphraseChanging: Boolean = false,
    /** Debug: loading state for demo seed button. */
    val isSeedingDemo: Boolean = false,
    /** Debug: one-shot snackbar after demo seed (success or failure). */
    val seedDemoMessage: UserMessage? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val biometricLockPort: BiometricLockPort,
    private val appSettings: AppSettingsPort,
    private val biometricHelper: BiometricHelper,
    private val changePassphraseUseCase: ChangePassphraseUseCase,
    private val lockStateProvider: LockStateProvider,
    private val seedDemoVaultItemsUseCase: SeedDemoVaultItemsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _pendingBiometricCipherEvent = MutableSharedFlow<Cipher>(extraBufferCapacity = 1)
    val pendingBiometricCipherEvent: SharedFlow<Cipher> = _pendingBiometricCipherEvent

    init {
        viewModelScope.launch {
            val enrolled = biometricLockPort.isAvailable()
            val canUseBiometric = biometricHelper.canUseBiometric()
            _uiState.update {
                it.copy(
                    biometricEnabled = enrolled,
                    biometricAvailableOnDevice = canUseBiometric
                )
            }
        }
        appSettings.autoLockTimeoutSeconds
            .onEach { seconds -> _uiState.update { it.copy(autoLockSeconds = seconds) } }
            .launchIn(viewModelScope)
        appSettings.useGoogleFavicons
            .onEach { useGoogle -> _uiState.update { it.copy(useGoogleFavicons = useGoogle) } }
            .launchIn(viewModelScope)
    }

    fun toggleBiometric() {
        if (_uiState.value.biometricEnabled) disableBiometric() else prepareBiometricEnrollment()
    }

    private fun prepareBiometricEnrollment() {
        viewModelScope.launch {
            try {
                val cipher = biometricLockPort.prepareEnrollment()
                _pendingBiometricCipherEvent.emit(cipher)
            } catch (e: Exception) {
                AppLogger.e("SettingsViewModel", "Failed to prepare biometric", e)
                _uiState.update { it.copy(error = SettingsError.General(UserMessage.Resource(R.string.settings_error_biometric_prepare))) }
            }
        }
    }

    fun onBiometricEnrollmentSuccess(authenticatedCipher: Cipher) {
        viewModelScope.launch {
            try {
                biometricLockPort.completeEnrollment(authenticatedCipher)
                _uiState.update { it.copy(biometricEnabled = true) }
            } catch (e: Exception) {
                AppLogger.e("SettingsViewModel", "Failed to enable biometric", e)
                _uiState.update { it.copy(error = SettingsError.General(UserMessage.Resource(R.string.settings_error_biometric_enable))) }
            }
        }
    }

    private fun disableBiometric() {
        viewModelScope.launch {
            try {
                biometricLockPort.disable()
                _uiState.update { it.copy(biometricEnabled = false) }
            } catch (e: Exception) {
                AppLogger.e("SettingsViewModel", "Failed to disable biometric", e)
                _uiState.update { it.copy(error = SettingsError.General(UserMessage.Resource(R.string.settings_error_biometric_disable))) }
            }
        }
    }

    fun setAutoLockTimeout(seconds: Int) {
        viewModelScope.launch { appSettings.setAutoLockTimeout(seconds) }
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun setUseGoogleFavicons(enabled: Boolean) {
        viewModelScope.launch {
            appSettings.setUseGoogleFavicons(enabled)
            if (!enabled) {
                val loader = context.imageLoader
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
            }
        }
    }

    fun openChangePassphraseSheet() {
        _uiState.update { it.copy(showChangePassphraseSheet = true) }
    }

    fun dismissChangePassphraseSheet() {
        _uiState.update { it.copy(showChangePassphraseSheet = false, error = null) }
    }

    fun changePassphrase(current: CharArray, new: CharArray, confirm: CharArray) {
        if (!new.contentEquals(confirm)) {
            current.fill('\u0000')
            new.fill('\u0000')
            confirm.fill('\u0000')
            _uiState.update {
                it.copy(error = SettingsError.PassphraseChange(UserMessage.Resource(R.string.onboarding_passphrase_mismatch)))
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isPassphraseChanging = true, error = null) }
            try {
                changePassphraseUseCase(current, new)
                lockStateProvider.lock()
                _uiState.update { it.copy(isPassphraseChanging = false) }
            } catch (e: WrongPassphraseException) {
                _uiState.update {
                    it.copy(
                        isPassphraseChanging = false,
                        error = SettingsError.PassphraseChange(UserMessage.Resource(R.string.settings_wrong_current_passphrase))
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("SettingsViewModel", "Failed to change passphrase", e)
                _uiState.update {
                    it.copy(
                        isPassphraseChanging = false,
                        error = SettingsError.PassphraseChange(UserMessage.Resource(R.string.settings_passphrase_change_failed))
                    )
                }
            } finally {
                current.fill('\u0000')
                new.fill('\u0000')
                confirm.fill('\u0000')
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Debug only: inserts 6 demo items per category. No-op in release builds. */
    fun seedDemoVaultItems() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            if (lockStateProvider.lockState.value !is LockState.Unlocked) {
                _uiState.update {
                    it.copy(seedDemoMessage = UserMessage.Resource(R.string.settings_debug_seed_demo_locked))
                }
                return@launch
            }
            _uiState.update { it.copy(isSeedingDemo = true, seedDemoMessage = null) }
            try {
                val added = seedDemoVaultItemsUseCase()
                _uiState.update {
                    it.copy(
                        isSeedingDemo = false,
                        seedDemoMessage = UserMessage.Resource(R.string.settings_debug_seed_demo_done, added)
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("SettingsViewModel", "Demo seed failed", e)
                _uiState.update {
                    it.copy(
                        isSeedingDemo = false,
                        seedDemoMessage = UserMessage.Resource(R.string.settings_debug_seed_demo_failed)
                    )
                }
            }
        }
    }

    fun clearSeedDemoMessage() {
        _uiState.update { it.copy(seedDemoMessage = null) }
    }
}
