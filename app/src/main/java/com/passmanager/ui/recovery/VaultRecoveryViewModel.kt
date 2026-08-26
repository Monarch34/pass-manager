package com.passmanager.ui.recovery

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.R
import com.passmanager.domain.usecase.ResetVaultUseCase
import com.passmanager.ui.common.AppLogger
import com.passmanager.ui.common.UserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class VaultRecoveryUiState(
    val typedConfirmation: String = "",
    val isResetting: Boolean = false,
    val isReset: Boolean = false,
    val error: UserMessage? = null
)

/**
 * The only exit from a vault whose device key is permanently gone.
 *
 * Restoring a `.pmvault` backup needs an unlocked vault to import into, so a user holding a
 * perfectly good backup still cannot use it while an unopenable vault occupies the app. Erasing
 * that vault is what unblocks them — which is also why it is behind a typed confirmation rather
 * than a button anyone can hit by reflex on a screen they landed on by accident.
 */
@HiltViewModel
class VaultRecoveryViewModel @Inject constructor(
    private val resetVaultUseCase: ResetVaultUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultRecoveryUiState())
    val uiState: StateFlow<VaultRecoveryUiState> = _uiState.asStateFlow()

    fun onConfirmationChanged(value: String) {
        _uiState.update { it.copy(typedConfirmation = value, error = null) }
    }

    /**
     * @param requiredWord the localized confirmation word the screen displayed. Compared with the
     *   user's input after trimming — matching is case-sensitive on purpose, so the word has to be
     *   read and reproduced rather than half-typed.
     */
    fun resetVault(requiredWord: String) {
        if (_uiState.value.typedConfirmation.trim() != requiredWord) return
        viewModelScope.launch {
            _uiState.update { it.copy(isResetting = true, error = null) }
            try {
                resetVaultUseCase()
                _uiState.update { it.copy(isResetting = false, isReset = true) }
            } catch (e: Exception) {
                AppLogger.e("VaultRecoveryViewModel", "Vault reset failed", e)
                _uiState.update {
                    it.copy(
                        isResetting = false,
                        error = UserMessage.Resource(R.string.recovery_failed)
                    )
                }
            }
        }
    }
}
