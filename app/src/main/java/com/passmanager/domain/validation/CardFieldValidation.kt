package com.passmanager.domain.validation

/** Card number field: digits only. */
fun cardPanDigits(cardNumberRaw: String): String = cardNumberRaw.filter { it.isDigit() }

/** True when PAN is exactly [CARD_NUMBER_DIGITS] digits (save gate). */
fun isCardPanAcceptableForSave(panDigits: String): Boolean =
    panDigits.length == CARD_NUMBER_DIGITS

/**
 * True when the digit-only expiry field parses to MM/YY with month 1–12.
 * Shared by [AddEditItemSaveValidator] and card form UI alignment.
 */
fun isCardExpiryAcceptableForSave(expiryFieldDigits: String): Boolean {
    val expiry = parseExpiryFieldDigits(expiryFieldDigits) ?: return false
    return expiry.first in 1..12
}

fun cardCvcIsWeak(cvcRaw: String): Boolean {
    val cvcDigits = cvcRaw.filter { it.isDigit() }
    return cvcDigits.isNotEmpty() && cvcDigits.length < 3
}

