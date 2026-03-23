package com.passmanager.ui.onboarding

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.R
import com.passmanager.domain.usecase.SetupVaultUseCase
import com.passmanager.domain.usecase.UnlockWithPassphraseUseCase
import com.passmanager.ui.common.UserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val isLoading: Boolean = false,
    val error: UserMessage? = null,
    val isComplete: Boolean = false
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val setupVaultUseCase: SetupVaultUseCase,
    private val unlockWithPassphraseUseCase: UnlockWithPassphraseUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun createVault(passphrase: CharArray, confirm: CharArray) {
        if (!passphrase.contentEquals(confirm)) {
            passphrase.fill('\u0000')
            confirm.fill('\u0000')
            _uiState.value = _uiState.value.copy(
                error = UserMessage.Resource(R.string.onboarding_passphrase_mismatch)
            )
            return
        }
        if (passphrase.size < 8) {
            passphrase.fill('\u0000')
            confirm.fill('\u0000')
            _uiState.value = _uiState.value.copy(
                error = UserMessage.Resource(R.string.onboarding_passphrase_too_short)
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                setupVaultUseCase(passphrase)
                // Immediately unlock after setup
                unlockWithPassphraseUseCase(passphrase)
                _uiState.value = _uiState.value.copy(isLoading = false, isComplete = true)
            } catch (e: Exception) {
                runCatching {
                    Log.e("OnboardingViewModel", "Vault creation failed", e)
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = UserMessage.Resource(R.string.onboarding_vault_creation_failed)
                )
            } finally {
                passphrase.fill('\u0000')
                confirm.fill('\u0000')
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
