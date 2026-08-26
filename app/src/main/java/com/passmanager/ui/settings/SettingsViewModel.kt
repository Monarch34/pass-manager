package com.passmanager.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.BuildConfig
import com.passmanager.R
import com.passmanager.domain.port.AppSettingsDefaults
import com.passmanager.domain.port.AppSettingsPort
import com.passmanager.domain.usecase.ChangePassphraseUseCase
import com.passmanager.domain.usecase.ExportVaultUseCase
import com.passmanager.domain.usecase.ImportPlan
import com.passmanager.domain.usecase.ImportVaultUseCase
import com.passmanager.domain.usecase.SeedDemoVaultItemsUseCase
import com.passmanager.domain.exception.PmVaultAuthenticationException
import com.passmanager.domain.exception.PmVaultException
import com.passmanager.domain.exception.PmVaultInvalidParametersException
import com.passmanager.domain.exception.PmVaultMalformedException
import com.passmanager.domain.exception.PmVaultUnsupportedVersionException
import com.passmanager.domain.exception.WrongPassphraseException
import com.passmanager.domain.model.LockState
import com.passmanager.domain.port.LockStateProvider
import com.passmanager.domain.port.BiometricLockPort
import com.passmanager.domain.port.VaultFilePort
import com.passmanager.domain.validation.PasswordStrength
import com.passmanager.domain.validation.PasswordStrengthEvaluator
import com.passmanager.security.biometric.BiometricHelper
import com.passmanager.ui.common.AppLogger
import com.passmanager.ui.common.UserMessage
import coil.imageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/** The modal step the export/import flow is currently on, if any. */
sealed interface VaultTransferDialog {
    /** Asked only once the vault is unlocked — never before the picker opens. */
    data class ExportPassphrase(val uri: String, val error: UserMessage? = null) : VaultTransferDialog
    data class ImportPassphrase(val uri: String, val error: UserMessage? = null) : VaultTransferDialog
    /** The "N new, M will be overwritten" review `docs/FORMAT.md` requires before applying. */
    data class ImportSummary(
        val newCount: Int,
        val overwriteCount: Int,
        val skippedCount: Int,
        val overwrittenTitles: List<String>
    ) : VaultTransferDialog
}

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
    /** Epoch millis of the last successful export, or null if the vault has never been exported. */
    val lastExportAtMs: Long? = null,
    val transferDialog: VaultTransferDialog? = null,
    val isTransferBusy: Boolean = false,
    /** One-shot snackbar after an export or import finishes. */
    val transferMessage: UserMessage? = null,
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
    private val seedDemoVaultItemsUseCase: SeedDemoVaultItemsUseCase,
    private val exportVaultUseCase: ExportVaultUseCase,
    private val importVaultUseCase: ImportVaultUseCase,
    private val vaultFilePort: VaultFilePort,
    private val transferRequests: VaultTransferRequests
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /**
     * Decrypted vault content held between the summary dialog and the user's decision. Dropped on
     * every exit from the flow — dismissal included — so it never outlives the dialog.
     */
    private var pendingImportPlan: ImportPlan? = null

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
        appSettings.lastExportAtMs
            .onEach { at -> _uiState.update { it.copy(lastExportAtMs = at) } }
            .launchIn(viewModelScope)
        // A document picked before an auto-lock lands here after the unlock, because locking
        // destroys this ViewModel along with the rest of the main graph.
        transferRequests.pending
            .onEach { pending ->
                if (pending != null && lockStateProvider.lockState.value is LockState.Unlocked) {
                    transferRequests.clear()
                    openPassphraseDialog(pending.kind, pending.uri)
                }
            }
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

    /** Enrollment could not complete; the switch must not look enabled after a failed prompt. */
    fun onBiometricEnrollmentError(message: String) {
        _uiState.update {
            it.copy(biometricEnabled = false, error = SettingsError.General(UserMessage.Plain(message)))
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
            // Cleared in both directions, so turning the setting back on retries the domains that
            // failed while it was last on instead of inheriting a stale session of misses.
            com.passmanager.ui.components.clearFaviconMissDomains()
            if (!enabled) {
                // DiskCache.clear() is a synchronous file-tree delete and viewModelScope is
                // Main.immediate, so leaving it on the calling thread put blocking I/O on the UI
                // thread at the exact moment a privacy-conscious user flips the switch.
                withContext(Dispatchers.IO) {
                    val loader = context.imageLoader
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()
                }
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

    // ── Vault export / import ────────────────────────

    /** The picker returned a destination. */
    fun onExportFileChosen(uri: String) = onTransferFileChosen(VaultTransferKind.EXPORT, uri)

    /** The picker returned a `.pmvault` file to read. */
    fun onImportFileChosen(uri: String) = onTransferFileChosen(VaultTransferKind.IMPORT, uri)

    private fun onTransferFileChosen(kind: VaultTransferKind, uri: String) {
        if (lockStateProvider.lockState.value !is LockState.Unlocked) {
            // The picker backgrounded the app long enough for auto-lock to fire. Hold the document
            // and pick the flow back up on the other side of the lock screen.
            transferRequests.stash(kind, uri)
            return
        }
        openPassphraseDialog(kind, uri)
    }

    private fun openPassphraseDialog(kind: VaultTransferKind, uri: String) {
        val dialog = when (kind) {
            VaultTransferKind.EXPORT -> VaultTransferDialog.ExportPassphrase(uri)
            VaultTransferKind.IMPORT -> VaultTransferDialog.ImportPassphrase(uri)
        }
        _uiState.update { it.copy(transferDialog = dialog) }
    }

    fun dismissTransferDialog() {
        pendingImportPlan = null
        _uiState.update { it.copy(transferDialog = null) }
    }

    fun clearTransferMessage() {
        _uiState.update { it.copy(transferMessage = null) }
    }

    /**
     * Writes the encrypted backup. The passphrase must match its confirmation and clear the
     * [PasswordStrength.GOOD] floor — a backup file is offline, so a weak passphrase on it is
     * offline-crackable forever.
     */
    fun exportVault(passphrase: CharArray, confirm: CharArray) {
        val uri = (_uiState.value.transferDialog as? VaultTransferDialog.ExportPassphrase)?.uri
        if (uri == null) {
            passphrase.fill('\u0000')
            confirm.fill('\u0000')
            return
        }
        if (!passphrase.contentEquals(confirm)) {
            passphrase.fill('\u0000')
            confirm.fill('\u0000')
            showExportError(uri, R.string.onboarding_passphrase_mismatch)
            return
        }
        // JVM String is immutable and not zeroable — accepted residual; the Compose text field
        // already holds this text as a String.
        if (PasswordStrengthEvaluator.evaluate(String(passphrase)) < PasswordStrength.GOOD) {
            passphrase.fill('\u0000')
            confirm.fill('\u0000')
            showExportError(uri, R.string.settings_export_passphrase_weak)
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isTransferBusy = true) }
            try {
                val bytes = exportVaultUseCase(passphrase)
                try {
                    vaultFilePort.write(uri, bytes)
                } finally {
                    bytes.fill(0)
                }
                appSettings.setLastExportAt(System.currentTimeMillis())
                _uiState.update {
                    it.copy(
                        isTransferBusy = false,
                        transferDialog = null,
                        transferMessage = UserMessage.Resource(R.string.settings_export_done)
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("SettingsViewModel", "Vault export failed", e)
                _uiState.update {
                    it.copy(
                        isTransferBusy = false,
                        transferDialog = null,
                        transferMessage = UserMessage.Resource(R.string.settings_export_failed)
                    )
                }
            } finally {
                passphrase.fill('\u0000')
                confirm.fill('\u0000')
            }
        }
    }

    /** Validates, derives and decrypts the picked file, then shows the merge summary. */
    fun prepareImport(passphrase: CharArray) {
        val uri = (_uiState.value.transferDialog as? VaultTransferDialog.ImportPassphrase)?.uri
        if (uri == null) {
            passphrase.fill('\u0000')
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isTransferBusy = true) }
            try {
                val bytes = vaultFilePort.read(uri)
                val plan = try {
                    importVaultUseCase.plan(bytes, passphrase)
                } finally {
                    bytes.fill(0)
                }
                pendingImportPlan = plan
                _uiState.update {
                    it.copy(
                        isTransferBusy = false,
                        transferDialog = VaultTransferDialog.ImportSummary(
                            newCount = plan.insertCount,
                            overwriteCount = plan.overwriteCount,
                            skippedCount = plan.skippedCount,
                            overwrittenTitles = plan.overwrittenTitles
                        )
                    )
                }
            } catch (e: Exception) {
                if (e !is PmVaultException) {
                    AppLogger.e("SettingsViewModel", "Vault import failed", e)
                }
                _uiState.update {
                    it.copy(
                        isTransferBusy = false,
                        transferDialog = VaultTransferDialog.ImportPassphrase(
                            uri = uri,
                            error = UserMessage.Resource(importErrorMessage(e))
                        )
                    )
                }
            } finally {
                passphrase.fill('\u0000')
            }
        }
    }

    /** @param addOnly skips every overwrite, adding only ids the vault does not have yet. */
    fun applyImport(addOnly: Boolean) {
        val plan = pendingImportPlan
        if (plan == null) {
            dismissTransferDialog()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isTransferBusy = true) }
            try {
                val result = importVaultUseCase.apply(plan, addOnly = addOnly)
                _uiState.update {
                    it.copy(
                        isTransferBusy = false,
                        transferDialog = null,
                        transferMessage = UserMessage.Resource(
                            R.string.settings_import_done,
                            result.inserted,
                            result.overwritten
                        )
                    )
                }
            } catch (e: Exception) {
                AppLogger.e("SettingsViewModel", "Applying vault import failed", e)
                _uiState.update {
                    it.copy(
                        isTransferBusy = false,
                        transferDialog = null,
                        transferMessage = UserMessage.Resource(R.string.settings_import_failed)
                    )
                }
            } finally {
                pendingImportPlan = null
            }
        }
    }

    private fun showExportError(uri: String, messageResId: Int) {
        _uiState.update {
            it.copy(
                transferDialog = VaultTransferDialog.ExportPassphrase(
                    uri = uri,
                    error = UserMessage.Resource(messageResId)
                )
            )
        }
    }

    private fun importErrorMessage(e: Exception): Int = when (e) {
        is PmVaultAuthenticationException -> R.string.settings_import_wrong_passphrase
        is PmVaultUnsupportedVersionException -> R.string.settings_import_unsupported_version
        is PmVaultInvalidParametersException,
        is PmVaultMalformedException -> R.string.settings_import_invalid_file
        else -> R.string.settings_import_failed
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
