package com.passmanager.ui.item

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.R
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.usecase.DecryptItemUseCase
import com.passmanager.domain.usecase.SaveVaultItemUseCase
import com.passmanager.ui.item.CARD_NUMBER_DIGITS
import com.passmanager.ui.item.expiryFieldDigitsFromStored
import com.passmanager.ui.item.formatExpiryMmYy
import com.passmanager.ui.item.parseExpiryFieldDigits
import com.passmanager.ui.item.sanitizeExpiryFieldDigits
import com.passmanager.domain.repository.VaultRepository
import com.passmanager.domain.validation.BankPasswordValidator
import com.passmanager.domain.validation.BankPasswordViolation
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
    val isSaved: Boolean = false,
    val cardholderName: String = "",
    val cardNumber: String = "",
    val cardCvc: String = "",
    /** Expiry as up to four digits only (MMYY); slash is visual in the UI. */
    val cardExpiry: String = "",
    val previousPasswords: List<String> = emptyList(),
    val bankPasswordViolations: List<BankPasswordViolation> = emptyList()
)

@HiltViewModel
class AddEditItemViewModel @Inject constructor(
    private val saveVaultItemUseCase: SaveVaultItemUseCase,
    private val decryptItemUseCase: DecryptItemUseCase,
    private val vaultRepository: VaultRepository,
    private val bankPasswordValidator: BankPasswordValidator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemId: String? = savedStateHandle.get<String>("itemId")?.takeIf { it.isNotBlank() }
    private val initialCategory: String? =
        savedStateHandle.get<String>("initialCategory")?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(AddEditUiState())
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    init {
        when {
            itemId != null -> loadItem(itemId)
            initialCategory != null -> applyInitialCategory(initialCategory)
        }
    }

    private fun applyInitialCategory(raw: String) {
        val cat = ItemCategory.fromString(raw)
        val violations = if (cat == ItemCategory.BANK) {
            bankPasswordValidator.validate(_uiState.value.password, _uiState.value.previousPasswords)
        } else {
            emptyList()
        }
        _uiState.value = _uiState.value.copy(
            category = cat.name.lowercase(),
            bankPasswordViolations = violations
        )
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
                    isLoading = false,
                    cardholderName = decrypted.cardholderName,
                    cardNumber = decrypted.cardNumber,
                    cardCvc = decrypted.cardCvc,
                    cardExpiry = expiryFieldDigitsFromStored(decrypted.cardExpiry),
                    previousPasswords = decrypted.previousPasswords
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
    fun onPasswordChange(value: String) {
        val violations = if (_uiState.value.category.equals("bank", ignoreCase = true)) {
            bankPasswordValidator.validate(value, _uiState.value.previousPasswords)
        } else emptyList()
        _uiState.value = _uiState.value.copy(password = value, bankPasswordViolations = violations)
    }
    fun onNotesChange(value: String) { _uiState.value = _uiState.value.copy(notes = value) }
    fun onCategoryChange(value: String) {
        val violations = if (value.equals("bank", ignoreCase = true)) {
            bankPasswordValidator.validate(_uiState.value.password, _uiState.value.previousPasswords)
        } else emptyList()
        _uiState.value = _uiState.value.copy(category = value, bankPasswordViolations = violations)
    }
    fun onCardholderNameChange(value: String) { _uiState.value = _uiState.value.copy(cardholderName = value) }
    fun onCardNumberChange(value: String) {
        _uiState.value = _uiState.value.copy(cardNumber = value.filter { it.isDigit() }.take(CARD_NUMBER_DIGITS))
    }
    fun onCardCvcChange(value: String) {
        _uiState.value = _uiState.value.copy(cardCvc = value.filter { it.isDigit() }.take(4))
    }

    /** Keep digit-only content; the composable applies a visual transformation for the slash. */
    fun onCardExpiryChange(value: String) {
        _uiState.value = _uiState.value.copy(cardExpiry = sanitizeExpiryFieldDigits(value))
    }

    fun applyGeneratedPassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    fun save() {
        val state = _uiState.value
        val isCard = state.category.equals("card", ignoreCase = true)
        val isBank = state.category.equals("bank", ignoreCase = true)

        if (state.title.isBlank()) {
            _uiState.value = state.copy(error = UserMessage.Resource(R.string.item_title_required))
            return
        }
        val cardExpiryForSave = if (isCard) {
            val panDigits = state.cardNumber.filter { it.isDigit() }
            if (panDigits.length != CARD_NUMBER_DIGITS) {
                _uiState.value = state.copy(error = UserMessage.Resource(R.string.item_card_number_invalid_16))
                return
            }
            val expiry = parseExpiryFieldDigits(state.cardExpiry)
            if (expiry == null || expiry.first !in 1..12) {
                _uiState.value = state.copy(error = UserMessage.Resource(R.string.item_expiry_invalid))
                return
            }
            formatExpiryMmYy(expiry.first, expiry.second)
        } else {
            state.cardExpiry.trim()
        }

        if (!isCard) {
            if (state.password.isBlank()) {
                _uiState.value = state.copy(error = UserMessage.Resource(R.string.item_password_required))
                return
            }
        }
        if (isBank) {
            val violations = bankPasswordValidator.validate(state.password, state.previousPasswords)
            if (violations.isNotEmpty()) {
                _uiState.value = state.copy(
                    bankPasswordViolations = violations,
                    error = UserMessage.Resource(R.string.bank_password_invalid)
                )
                return
            }
        }

        val updatedPreviousPasswords = if (isBank && state.password.isNotBlank()) {
            (listOf(state.password) + state.previousPasswords).take(4)
        } else {
            state.previousPasswords
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
                    category = state.category,
                    cardholderName = state.cardholderName.trim(),
                    cardNumber = state.cardNumber.trim(),
                    cardCvc = state.cardCvc.trim(),
                    cardExpiry = cardExpiryForSave,
                    previousPasswords = updatedPreviousPasswords
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
