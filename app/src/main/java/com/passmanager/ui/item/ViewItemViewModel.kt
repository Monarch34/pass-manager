package com.passmanager.ui.item

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.R
import com.passmanager.data.preferences.AppPreferences
import com.passmanager.domain.model.DecryptedVaultItem
import com.passmanager.domain.usecase.DecryptItemUseCase
import com.passmanager.domain.repository.VaultRepository
import com.passmanager.ui.common.UserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ViewItemUiState(
    val item: DecryptedVaultItem? = null,
    /** From [VaultItem.category] (metadata), not inside encrypted blob. */
    val category: String = "login",
    val isLoading: Boolean = true,
    val error: UserMessage? = null,
    val isDeleted: Boolean = false,
    val passwordVisible: Boolean = false,
    val useGoogleFavicons: Boolean = AppPreferences.DEFAULT_USE_GOOGLE_FAVICONS
)

@HiltViewModel
class ViewItemViewModel @Inject constructor(
    private val decryptItemUseCase: DecryptItemUseCase,
    private val vaultRepository: VaultRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewItemUiState())
    val uiState: StateFlow<ViewItemUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var currentItemId: String = ""

    init {
        appPreferences.useGoogleFavicons
            .onEach { useGoogle -> _uiState.update { it.copy(useGoogleFavicons = useGoogle) } }
            .launchIn(viewModelScope)
    }

    fun loadForItem(itemId: String) {
        if (itemId.isBlank()) return
        if (itemId == currentItemId && observeJob?.isActive == true) return
        currentItemId = itemId
        observeJob?.cancel()
        _uiState.value = ViewItemUiState(isLoading = true, passwordVisible = false)
        observeJob = viewModelScope.launch {
            vaultRepository.observeById(itemId).collect { vaultItem ->
                if (vaultItem == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = UserMessage.Resource(R.string.item_not_found),
                        item = null
                    )
                    return@collect
                }
                try {
                    val decrypted = decryptItemUseCase(vaultItem)
                    _uiState.value = _uiState.value.copy(
                        item = decrypted,
                        category = vaultItem.category,
                        isLoading = false,
                        error = null
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = UserMessage.Resource(R.string.item_decrypt_failed)
                    )
                }
            }
        }
    }

    fun togglePasswordVisible() {
        _uiState.value = _uiState.value.copy(passwordVisible = !_uiState.value.passwordVisible)
    }

    fun delete() {
        viewModelScope.launch {
            try {
                vaultRepository.deleteById(currentItemId)
                _uiState.value = _uiState.value.copy(isDeleted = true)
            } catch (e: Exception) {
                Log.e("ViewItemViewModel", "Failed to delete item", e)
                _uiState.value = _uiState.value.copy(
                    error = UserMessage.Resource(R.string.item_delete_failed)
                )
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    override fun onCleared() {
        super.onCleared()
        observeJob?.cancel()
        _uiState.value = _uiState.value.copy(item = null)
    }
}
