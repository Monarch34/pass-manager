package com.passmanager.domain.validation

import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.ItemPayload
import javax.inject.Inject

/** Flat snapshot of add/edit form fields used for save gating and payload build. */
data class AddEditSaveSnapshot(
    val title: String,
    val category: ItemCategory,
    val username: String,
    val address: String,
    val password: String,
    val cardholderName: String,
    val cardNumber: String,
    val cardCvc: String,
    val cardExpiry: String,
    val accountNumber: String,
    val bankName: String,
    val bankPassword: String,
    /**
     * Bank password this record was loaded with — empty for a new record. Keeping it apart from
     * [previousPasswords] is what lets an unchanged password stay valid while a true rollback to
     * a retired password is still rejected.
     */
    val originalBankPassword: String,
    /** Retired bank passwords only; never contains [bankPassword] or [originalBankPassword]. */
    val previousPasswords: List<String>,
    val bankPasswordViolations: List<BankPasswordViolation>,
    val notes: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String,
    val identityAddress: String,
    val company: String,
)

sealed class AddEditSaveFailure {
    data object TitleRequired : AddEditSaveFailure()
    data object CardPanInvalid : AddEditSaveFailure()
    data object CardExpiryInvalid : AddEditSaveFailure()
    data object PasswordRequired : AddEditSaveFailure()
    data class BankInvalid(val violations: List<BankPasswordViolation>) : AddEditSaveFailure()
}

sealed class AddEditSaveOutcome {
    data class Success(val payload: ItemPayload) : AddEditSaveOutcome()
    data class Failure(val error: AddEditSaveFailure) : AddEditSaveOutcome()
}

/**
 * Single place for “can save” and save-time validation + [ItemPayload] construction.
 */
class AddEditItemSaveValidator @Inject constructor(
    private val bankPasswordValidator: BankPasswordValidator,
) {

    fun canSave(s: AddEditSaveSnapshot): Boolean = evaluateFailure(s) == null

    fun prepareSave(s: AddEditSaveSnapshot, itemId: String?): AddEditSaveOutcome {
        evaluateFailure(s)?.let { return AddEditSaveOutcome.Failure(it) }
        val id = itemId ?: ""
        val payload = when (s.category) {
            ItemCategory.CARD -> {
                val expiry = parseExpiryFieldDigits(s.cardExpiry)!!
                ItemPayload.Card(
                    id = id,
                    title = s.title.trim(),
                    notes = s.notes.trim(),
                    cardholderName = s.cardholderName.trim(),
                    cardNumber = s.cardNumber.trim(),
                    cardCvc = s.cardCvc.trim(),
                    cardExpiry = formatExpiryMmYy(expiry.first, expiry.second),
                )
            }
            ItemCategory.BANK -> {
                val violations = bankPasswordValidator.validate(s.bankPassword, s.previousPasswords)
                if (violations.isNotEmpty()) {
                    return AddEditSaveOutcome.Failure(AddEditSaveFailure.BankInvalid(violations))
                }
                ItemPayload.Bank(
                    id = id,
                    title = s.title.trim(),
                    notes = s.notes.trim(),
                    accountNumber = s.accountNumber.trim(),
                    bankName = s.bankName.trim(),
                    password = s.bankPassword,
                    previousPasswords = retiredBankPasswords(s),
                )
            }
            ItemCategory.NOTE -> ItemPayload.SecureNote(
                id = id,
                title = s.title.trim(),
                notes = s.notes.trim(),
            )
            ItemCategory.IDENTITY -> ItemPayload.Identity(
                id = id,
                title = s.title.trim(),
                notes = s.notes.trim(),
                firstName = s.firstName.trim(),
                lastName = s.lastName.trim(),
                email = s.email.trim(),
                phone = s.phone.trim(),
                address = s.identityAddress.trim(),
                company = s.company.trim(),
            )
            else -> ItemPayload.Login(
                id = id,
                title = s.title.trim(),
                notes = s.notes.trim(),
                username = s.username.trim(),
                address = s.address.trim(),
                password = s.password,
            )
        }
        return AddEditSaveOutcome.Success(payload)
    }

    /**
     * The first reason this form cannot be saved, or `null` when it can. Public so the screen can
     * tell the user *why* the save button is disabled instead of leaving it dead.
     */
    fun evaluateFailure(s: AddEditSaveSnapshot): AddEditSaveFailure? {
        if (s.title.isBlank()) return AddEditSaveFailure.TitleRequired
        return when (s.category) {
            ItemCategory.CARD -> {
                val panDigits = cardPanDigits(s.cardNumber)
                if (!isCardPanAcceptableForSave(panDigits)) return AddEditSaveFailure.CardPanInvalid
                if (!isCardExpiryAcceptableForSave(s.cardExpiry)) {
                    return AddEditSaveFailure.CardExpiryInvalid
                }
                null
            }
            ItemCategory.BANK -> {
                if (s.bankPassword.isBlank()) return AddEditSaveFailure.PasswordRequired
                val violations = bankPasswordValidator.validate(s.bankPassword, s.previousPasswords)
                if (violations.isNotEmpty()) return AddEditSaveFailure.BankInvalid(violations)
                null
            }
            ItemCategory.NOTE, ItemCategory.IDENTITY -> null
            else -> {
                if (s.password.isBlank()) return AddEditSaveFailure.PasswordRequired
                null
            }
        }
    }

    /**
     * History written back with the record: the passwords it no longer uses.
     *
     * The password being saved is deliberately left out — it is already stored in
     * [ItemPayload.Bank.password], and repeating it here would make the record fail its own
     * [BankPasswordViolation.ReusedPassword] check the next time it is opened for editing.
     * A password only joins the history once it is actually replaced, which is what keeps a
     * rollback to an earlier password blocked.
     */
    private fun retiredBankPasswords(s: AddEditSaveSnapshot): List<String> {
        if (s.bankPassword == s.originalBankPassword) return s.previousPasswords
        return (listOf(s.originalBankPassword) + s.previousPasswords)
            .filter { it.isNotEmpty() }
            .distinct()
            .take(BankPasswordRules.HISTORY_SIZE)
    }
}
