package com.passmanager.ui.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.R
import com.passmanager.domain.port.AppSettingsDefaults
import com.passmanager.domain.port.AppSettingsPort
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.ItemPayload
import com.passmanager.domain.usecase.DecryptItemUseCase
import com.passmanager.domain.usecase.DeleteVaultItemByIdUseCase
import com.passmanager.domain.usecase.ObserveVaultItemByIdUseCase
import com.passmanager.ui.common.AppLogger
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
import androidx.compose.runtime.Immutable
import javax.inject.Inject

@Immutable
data class ViewItemUiState(
    val payload: ItemPayload? = null,
    val isLoading: Boolean = true,
    val error: UserMessage? = null,
    val isDeleted: Boolean = false,
    val passwordVisible: Boolean = false,
    val useGoogleFavicons: Boolean = AppSettingsDefaults.USE_GOOGLE_FAVICONS
) {
    /** Convenience derived from the sealed payload type — no string tag needed. */
    val category: ItemCategory get() = payload?.category ?: ItemCategory.LOGIN
}

@HiltViewModel
class ViewItemViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val decryptItemUseCase: DecryptItemUseCase,
    private val observeVaultItemByIdUseCase: ObserveVaultItemByIdUseCase,
    private val deleteVaultItemByIdUseCase: DeleteVaultItemByIdUseCase,
    private val appSettings: AppSettingsPort
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewItemUiState())
    val uiState: StateFlow<ViewItemUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var currentItemId: String
        get() = savedStateHandle.get<String>("itemId") ?: ""
        set(value) {
            savedStateHandle["itemId"] = value
        }

    init {
        appSettings.useGoogleFavicons
            .onEach { useGoogle -> _uiState.update { it.copy(useGoogleFavicons = useGoogle) } }
            .launchIn(viewModelScope)

        val restoredId = currentItemId
        if (restoredId.isNotBlank()) {
            loadForItem(restoredId)
        }
    }

    fun loadForItem(itemId: String) {
        if (itemId.isBlank()) return
        if (itemId == currentItemId && observeJob?.isActive == true) return
        currentItemId = itemId
        observeJob?.cancel()
        _uiState.value = ViewItemUiState(isLoading = true, passwordVisible = false)
        observeJob = viewModelScope.launch {
            observeVaultItemByIdUseCase(itemId).collect { vaultItem ->
                if (vaultItem == null) {
                    _uiState.update {
                        it.copy(isLoading = false, error = UserMessage.Resource(R.string.item_not_found), payload = null)
                    }
                    return@collect
                }
                try {
                    val decrypted = decryptItemUseCase(vaultItem)
                    _uiState.update {
                        it.copy(payload = decrypted, isLoading = false, error = null)
                    }
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(isLoading = false, error = UserMessage.Resource(R.string.item_decrypt_failed))
                    }
                }
            }
        }
    }

    fun togglePasswordVisible() {
        _uiState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun delete() {
        viewModelScope.launch {
            try {
                deleteVaultItemByIdUseCase(currentItemId)
                _uiState.update { it.copy(isDeleted = true) }
            } catch (e: Exception) {
                AppLogger.e("ViewItemViewModel", "Failed to delete item", e)
                _uiState.update { it.copy(error = UserMessage.Resource(R.string.item_delete_failed)) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        observeJob?.cancel()
    }
}
