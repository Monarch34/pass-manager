package com.passmanager.ui.item

import com.passmanager.domain.validation.CARD_NUMBER_DIGITS
import com.passmanager.domain.validation.cardPanDigits
import com.passmanager.domain.validation.isCardPanAcceptableForSave
import com.passmanager.domain.validation.parseExpiryFieldDigits

/** Maps to string resources in Compose; keeps PAN UX rules in one place. */
sealed class CardPanSupportingHint {
    data object None : CardPanSupportingHint()
    data object FifteenDigitAmex : CardPanSupportingHint()
    data class Progress(val digitCount: Int, val treatAsError: Boolean) : CardPanSupportingHint()
    data object RequiredWhenHint : CardPanSupportingHint()
}

data class CardPanFieldUiState(
    val panDigits: String,
    val isFieldError: Boolean,
    val supportingHint: CardPanSupportingHint,
)

fun cardPanFieldUiState(cardNumberRaw: String, showValidationHints: Boolean): CardPanFieldUiState {
    val panDigits = cardPanDigits(cardNumberRaw)
    val acceptable = isCardPanAcceptableForSave(panDigits)
    val isFieldError =
        (panDigits.isNotEmpty() && !acceptable) || (showValidationHints && panDigits.isEmpty())
    val supportingHint = when {
        panDigits.length == 15 -> CardPanSupportingHint.FifteenDigitAmex
        panDigits.isNotEmpty() && !acceptable ->
            CardPanSupportingHint.Progress(panDigits.length, treatAsError = true)
        panDigits.isNotEmpty() ->
            CardPanSupportingHint.Progress(panDigits.length, treatAsError = false)
        showValidationHints -> CardPanSupportingHint.RequiredWhenHint
        else -> CardPanSupportingHint.None
    }
    return CardPanFieldUiState(panDigits, isFieldError, supportingHint)
}

data class CardExpiryFieldUiState(
    val isFieldError: Boolean,
    val showInvalidSupporting: Boolean,
    val showRequiredSupporting: Boolean,
)

fun cardExpiryFieldUiState(
    expiryDigits: String,
    showValidationHints: Boolean,
): CardExpiryFieldUiState {
    val mmPartial =
        if (expiryDigits.length >= 2) expiryDigits.take(2).toIntOrNull() else null
    val expiryMonthOutOfRange = mmPartial != null && mmPartial !in 1..12
    val expiryParseOk = parseExpiryFieldDigits(expiryDigits) != null
    val expiryHasError =
        expiryMonthOutOfRange || (expiryDigits.length == 4 && !expiryParseOk)
    val isFieldError =
        expiryHasError || (showValidationHints && expiryDigits.isEmpty())
    return CardExpiryFieldUiState(
        isFieldError = isFieldError,
        showInvalidSupporting = expiryHasError,
        showRequiredSupporting = showValidationHints && expiryDigits.isEmpty() && !expiryHasError,
    )
}
