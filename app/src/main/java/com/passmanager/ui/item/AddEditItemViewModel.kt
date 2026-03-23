package com.passmanager.ui.item

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.R
import com.passmanager.domain.usecase.DecryptItemUseCase
import com.passmanager.domain.usecase.SaveVaultItemUseCase
import com.passmanager.domain.repository.VaultRepository
import com.passmanager.ui.common.UserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddEditUiState(
    val title: String = "",
    val username: String = "",
    val address: String = "",
    val password: String = "",
    val notes: String = "",
    val category: String = "login",
    val isLoading: Boolean = false,
    val error: UserMessage? = null,
    val isSaved: Boolean = false
)

@HiltViewModel
class AddEditItemViewModel @Inject constructor(
    private val saveVaultItemUseCase: SaveVaultItemUseCase,
    private val decryptItemUseCase: DecryptItemUseCase,
    private val vaultRepository: VaultRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemId: String? = savedStateHandle["itemId"]

    private val _uiState = MutableStateFlow(AddEditUiState())
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    init {
        itemId?.let { loadItem(it) }
    }

    private fun loadItem(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val item = vaultRepository.getById(id) ?: return@launch
                val decrypted = decryptItemUseCase(item)
                _uiState.value = _uiState.value.copy(
                    title = decrypted.title,
                    username = decrypted.username,
                    address = decrypted.address,
                    password = decrypted.password,
                    notes = decrypted.notes,
                    category = item.category,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = UserMessage.Resource(R.string.item_load_failed)
                )
            }
        }
    }

    fun onTitleChange(value: String) { _uiState.value = _uiState.value.copy(title = value) }
    fun onUsernameChange(value: String) { _uiState.value = _uiState.value.copy(username = value) }
    fun onAddressChange(value: String) { _uiState.value = _uiState.value.copy(address = value) }
    fun onPasswordChange(value: String) { _uiState.value = _uiState.value.copy(password = value) }
    fun onNotesChange(value: String) { _uiState.value = _uiState.value.copy(notes = value) }
    fun onCategoryChange(value: String) { _uiState.value = _uiState.value.copy(category = value) }

    fun applyGeneratedPassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.value = state.copy(error = UserMessage.Resource(R.string.item_title_required))
            return
        }
        if (state.password.isBlank()) {
            _uiState.value = state.copy(error = UserMessage.Resource(R.string.item_password_required))
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                saveVaultItemUseCase(
                    id = itemId,
                    title = state.title.trim(),
                    username = state.username.trim(),
                    address = state.address.trim(),
                    password = state.password,
                    notes = state.notes.trim(),
                    category = state.category
                )
                _uiState.value = _uiState.value.copy(isLoading = false, isSaved = true)
            } catch (e: Exception) {
                Log.e("AddEditItemViewModel", "Save failed", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = UserMessage.Resource(R.string.item_save_failed)
                )
            }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    /** Clears [AddEditUiState.isSaved] after a successful save so the bottom sheet can reopen. */
    fun clearSavedFlag() {
        if (_uiState.value.isSaved) {
            _uiState.value = _uiState.value.copy(isSaved = false)
        }
    }

    /** Clears form state when opening the add-item sheet (ViewModel scoped to vault list entry). */
    fun resetForNewItem() {
        if (itemId != null) return
        _uiState.value = AddEditUiState()
    }
}
