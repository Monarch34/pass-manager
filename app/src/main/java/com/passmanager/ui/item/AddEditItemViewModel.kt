package com.passmanager.ui.item

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.R
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.ItemPayload
import com.passmanager.domain.usecase.DecryptItemUseCase
import com.passmanager.domain.usecase.GetVaultItemByIdUseCase
import com.passmanager.domain.usecase.SaveVaultItemUseCase
import com.passmanager.domain.validation.AddEditItemSaveValidator
import com.passmanager.domain.validation.AddEditSaveFailure
import com.passmanager.domain.validation.AddEditSaveOutcome
import com.passmanager.domain.validation.AddEditSaveSnapshot
import com.passmanager.domain.validation.BankPasswordValidator
import com.passmanager.domain.validation.BankPasswordViolation
import com.passmanager.domain.validation.CARD_NUMBER_DIGITS
import com.passmanager.domain.validation.expiryFieldDigitsFromStored
import com.passmanager.domain.validation.sanitizeExpiryFieldDigits
import com.passmanager.navigation.Screen
import com.passmanager.ui.common.AppLogger
import com.passmanager.ui.common.UserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI form state — intentionally flat so users can switch categories without
 * losing typed data. The typed fields are converted to a type-safe
 * [ItemPayload] only at save time.
 */
@Immutable
data class AddEditUiState(
    // ── Shared ──
    val title: String = "",
    val notes: String = "",
    val category: ItemCategory = ItemCategory.LOGIN,
    val isLoading: Boolean = false,
    val error: UserMessage? = null,
    val isSaved: Boolean = false,
    /** Derived in [AddEditItemViewModel] via [AddEditItemSaveValidator.evaluateFailure]. */
    val canSave: Boolean = false,
    /** Why [canSave] is false — shown next to the disabled save button. Null when it is true. */
    val saveBlockedReason: AddEditSaveFailure? = null,
    /** True once the form differs from what was loaded; gates the “discard changes?” prompt. */
    val isDirty: Boolean = false,

    // ── Login fields ──
    val username: String = "",
    val address: String = "",
    val password: String = "",

    // ── Card fields ──
    val cardholderName: String = "",
    val cardNumber: String = "",
    val cardCvc: String = "",
    /** Expiry as up to four digits only (MMYY); slash is visual in the UI. */
    val cardExpiry: String = "",

    // ── Bank fields ──
    val accountNumber: String = "",
    val bankName: String = "",
    val bankPassword: String = "",
    /** Bank password this record was loaded with; empty while adding a new record. */
    val originalBankPassword: String = "",
    /** Retired bank passwords only — the record's current password is never in here. */
    val previousPasswords: List<String> = emptyList(),
    val bankPasswordViolations: List<BankPasswordViolation> = emptyList(),

    // ── Identity fields ──
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val identityAddress: String = "",
    val company: String = ""
) {
    val isCard: Boolean get() = category == ItemCategory.CARD
    val isBank: Boolean get() = category == ItemCategory.BANK
    val isNote: Boolean get() = category == ItemCategory.NOTE
    val isIdentity: Boolean get() = category == ItemCategory.IDENTITY
}

private fun AddEditUiState.toSaveSnapshot() = AddEditSaveSnapshot(
    title = title,
    category = category,
    username = username,
    address = address,
    password = password,
    cardholderName = cardholderName,
    cardNumber = cardNumber,
    cardCvc = cardCvc,
    cardExpiry = cardExpiry,
    accountNumber = accountNumber,
    bankName = bankName,
    bankPassword = bankPassword,
    originalBankPassword = originalBankPassword,
    previousPasswords = previousPasswords,
    bankPasswordViolations = bankPasswordViolations,
    notes = notes,
    firstName = firstName,
    lastName = lastName,
    email = email,
    phone = phone,
    identityAddress = identityAddress,
    company = company,
)

/**
 * Drops transient and derived fields so two form states can be compared on user-entered content
 * alone. Backs [AddEditUiState.isDirty], and stays correct as new content fields are added.
 */
private fun AddEditUiState.contentSignature() = copy(
    isLoading = false,
    error = null,
    isSaved = false,
    canSave = false,
    saveBlockedReason = null,
    isDirty = false,
    bankPasswordViolations = emptyList(),
)

@HiltViewModel
class AddEditItemViewModel @Inject constructor(
    private val saveVaultItemUseCase: SaveVaultItemUseCase,
    private val decryptItemUseCase: DecryptItemUseCase,
    private val getVaultItemByIdUseCase: GetVaultItemByIdUseCase,
    private val bankPasswordValidator: BankPasswordValidator,
    private val addEditItemSaveValidator: AddEditItemSaveValidator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Route uses [Screen.AddEditItem.NEW_ITEM_ROUTE_ID] for add flow; it is not a vault row id. */
    private val itemId: String? = savedStateHandle.get<String>("itemId")
        ?.takeIf { it.isNotBlank() && it != Screen.AddEditItem.NEW_ITEM_ROUTE_ID }
    private val initialCategory: String? =
        savedStateHandle.get<String>("initialCategory")?.takeIf { it.isNotBlank() }

    private val initialForm = AddEditUiState()
    private val _form = MutableStateFlow(initialForm)

    /**
     * The form as the user first saw it: empty for a new record, the decrypted record for an edit.
     * Everything typed after this point counts as an unsaved change.
     */
    private var baseline: AddEditUiState = initialForm

    val uiState: StateFlow<AddEditUiState> = _form
        .map { s -> s.withDerivedFlags() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000L),
            initialForm.withDerivedFlags()
        )

    /** Recomputes [AddEditUiState.canSave], [AddEditUiState.saveBlockedReason] and dirty state. */
    private fun AddEditUiState.withDerivedFlags(): AddEditUiState {
        val failure = addEditItemSaveValidator.evaluateFailure(toSaveSnapshot())
        return copy(
            canSave = failure == null,
            saveBlockedReason = failure,
            isDirty = contentSignature() != baseline.contentSignature()
        )
    }

    init {
        when {
            itemId != null -> loadItem(itemId)
            initialCategory != null -> applyInitialCategory(initialCategory)
        }
    }

    // ── Load from vault ──────────────────────────────

    private fun applyInitialCategory(raw: String) {
        val cat = ItemCategory.fromString(raw)
        _form.update {
            val violations = if (cat == ItemCategory.BANK) {
                bankPasswordValidator.validate(it.bankPassword, it.previousPasswords)
            } else emptyList()
            it.copy(category = cat, bankPasswordViolations = violations)
        }
        // Preselecting a category is navigation, not user input: it must not arm the discard prompt.
        baseline = _form.value
    }

    private fun loadItem(id: String) {
        viewModelScope.launch {
            _form.update { it.copy(isLoading = true) }
            try {
                val item = getVaultItemByIdUseCase(id)
                if (item == null) {
                    _form.update {
                        it.copy(isLoading = false, error = UserMessage.Resource(R.string.item_not_found))
                    }
                    return@launch
                }
                val loaded = fromPayload(payload = decryptItemUseCase(item))
                baseline = loaded
                _form.update { loaded }
            } catch (e: Exception) {
                _form.update { it.copy(isLoading = false, error = UserMessage.Resource(R.string.item_load_failed)) }
            }
        }
    }

    /** Maps a sealed [ItemPayload] into the flat form state. */
    private fun fromPayload(payload: ItemPayload): AddEditUiState = when (payload) {
        is ItemPayload.Login -> AddEditUiState(
            title = payload.title, notes = payload.notes, category = ItemCategory.LOGIN,
            username = payload.username, address = payload.address, password = payload.password
        )
        is ItemPayload.Card -> AddEditUiState(
            title = payload.title, notes = payload.notes, category = ItemCategory.CARD,
            cardholderName = payload.cardholderName, cardNumber = payload.cardNumber,
            cardCvc = payload.cardCvc, cardExpiry = expiryFieldDigitsFromStored(payload.cardExpiry)
        )
        is ItemPayload.Bank -> AddEditUiState(
            title = payload.title, notes = payload.notes, category = ItemCategory.BANK,
            accountNumber = payload.accountNumber, bankName = payload.bankName,
            bankPassword = payload.password, originalBankPassword = payload.password,
            // Records written before the history split kept the current password as entry 0.
            // Dropping it here is what stops the record failing its own reuse check on reopen.
            // Only the leading entry is dropped: a match further down is a genuinely retired
            // password the user happens to have returned to, and removing it would quietly make
            // that old password reusable again.
            previousPasswords = payload.previousPasswords.let {
                if (it.firstOrNull() == payload.password) it.drop(1) else it
            }
        )
        is ItemPayload.SecureNote -> AddEditUiState(
            title = payload.title, notes = payload.notes, category = ItemCategory.NOTE
        )
        is ItemPayload.Identity -> AddEditUiState(
            title = payload.title, notes = payload.notes, category = ItemCategory.IDENTITY,
            firstName = payload.firstName, lastName = payload.lastName,
            email = payload.email, phone = payload.phone,
            identityAddress = payload.address, company = payload.company
        )
    }

    // ── Field change handlers ────────────────────────

    fun onTitleChange(value: String) { _form.update { it.copy(title = value) } }
    fun onNotesChange(value: String) { _form.update { it.copy(notes = value) } }
    fun onCategoryChange(category: ItemCategory) {
        _form.update {
            val violations = if (category == ItemCategory.BANK) {
                bankPasswordValidator.validate(it.bankPassword, it.previousPasswords)
            } else emptyList()
            it.copy(category = category, bankPasswordViolations = violations)
        }
    }

    // Login
    fun onUsernameChange(value: String) { _form.update { it.copy(username = value) } }
    fun onAddressChange(value: String) { _form.update { it.copy(address = value) } }
    fun onPasswordChange(value: String) { _form.update { it.copy(password = value) } }

    // Card
    fun onCardholderNameChange(value: String) { _form.update { it.copy(cardholderName = value) } }
    fun onCardNumberChange(value: String) {
        _form.update { it.copy(cardNumber = value.filter { c -> c.isDigit() }.take(CARD_NUMBER_DIGITS)) }
    }
    fun onCardCvcChange(value: String) {
        _form.update { it.copy(cardCvc = value.filter { c -> c.isDigit() }.take(4)) }
    }
    fun onCardExpiryChange(value: String) {
        _form.update { it.copy(cardExpiry = sanitizeExpiryFieldDigits(value)) }
    }

    // Bank
    fun onAccountNumberChange(value: String) { _form.update { it.copy(accountNumber = value) } }
    fun onBankNameChange(value: String) { _form.update { it.copy(bankName = value) } }
    fun onBankPasswordChange(value: String) {
        _form.update {
            val violations = bankPasswordValidator.validate(value, it.previousPasswords)
            it.copy(bankPassword = value, bankPasswordViolations = violations)
        }
    }

    // Identity
    fun onFirstNameChange(value: String) { _form.update { it.copy(firstName = value) } }
    fun onLastNameChange(value: String) { _form.update { it.copy(lastName = value) } }
    fun onEmailChange(value: String) { _form.update { it.copy(email = value) } }
    fun onPhoneChange(value: String) { _form.update { it.copy(phone = value) } }
    fun onIdentityAddressChange(value: String) { _form.update { it.copy(identityAddress = value) } }
    fun onCompanyChange(value: String) { _form.update { it.copy(company = value) } }

    // ── Password generator handoff ───────────────────

    /**
     * Category whose password rules the generator must respect when opened from this form.
     * Read by the navigation layer, so it stays correct even while nothing collects [uiState].
     */
    val generatorCategory: ItemCategory get() = _form.value.category

    /** The caller owns [password] and wipes it afterwards, so this never mutates the array. */
    fun applyGeneratedPassword(password: CharArray) {
        val value = String(password)
        _form.update {
            when (it.category) {
                ItemCategory.BANK -> {
                    val violations = bankPasswordValidator.validate(value, it.previousPasswords)
                    it.copy(bankPassword = value, bankPasswordViolations = violations)
                }
                else -> it.copy(password = value)
            }
        }
    }

    // ── Save ─────────────────────────────────────────

    fun save() {
        val state = _form.value
        when (val outcome = addEditItemSaveValidator.prepareSave(state.toSaveSnapshot(), itemId)) {
            is AddEditSaveOutcome.Failure -> applySaveFailure(outcome.error)
            is AddEditSaveOutcome.Success -> persistPayload(outcome.payload)
        }
    }

    private fun applySaveFailure(error: AddEditSaveFailure) {
        val msg = when (error) {
            AddEditSaveFailure.TitleRequired -> UserMessage.Resource(R.string.item_title_required)
            AddEditSaveFailure.CardPanInvalid -> UserMessage.Resource(R.string.item_card_number_invalid_16)
            AddEditSaveFailure.CardExpiryInvalid -> UserMessage.Resource(R.string.item_expiry_invalid)
            AddEditSaveFailure.PasswordRequired -> UserMessage.Resource(R.string.item_password_required)
            is AddEditSaveFailure.BankInvalid -> UserMessage.Resource(R.string.bank_password_invalid)
        }
        _form.update {
            when (error) {
                is AddEditSaveFailure.BankInvalid ->
                    it.copy(bankPasswordViolations = error.violations, error = msg)
                else -> it.copy(error = msg)
            }
        }
    }

    private fun persistPayload(payload: ItemPayload) {
        viewModelScope.launch {
            _form.update { it.copy(isLoading = true, error = null) }
            try {
                saveVaultItemUseCase(payload = payload, existingId = itemId)
                // What is on screen is now what is stored, so leaving must not prompt to discard.
                // The baseline has to move BEFORE the emission: isDirty is derived in the map on
                // _form, so assigning it afterwards leaves the flag stale until the next edit.
                // originalBankPassword moves with it, otherwise a second save without leaving the
                // screen would retire the password that was just stored as if it were the old one.
                val saved = _form.value.copy(
                    isLoading = false,
                    isSaved = true,
                    originalBankPassword = _form.value.bankPassword
                )
                baseline = saved
                _form.value = saved
            } catch (e: Exception) {
                AppLogger.e("AddEditItemViewModel", "Save failed", e)
                _form.update { it.copy(isLoading = false, error = UserMessage.Resource(R.string.item_save_failed)) }
            }
        }
    }

    fun clearError() { _form.update { it.copy(error = null) } }

    fun clearSavedFlag() {
        _form.update { if (it.isSaved) it.copy(isSaved = false) else it }
    }

    fun resetForNewItem() {
        if (itemId != null) return
        _form.value = initialForm
        baseline = initialForm
    }
}
