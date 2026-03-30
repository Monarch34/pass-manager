package com.passmanager.domain.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.passmanager.ui.item.CardPanSupportingHint
import com.passmanager.ui.item.cardExpiryFieldUiState
import com.passmanager.ui.item.cardPanFieldUiState

class CardFieldValidationTest {

    @Test
    fun isCardPanAcceptableForSave_onlySixteenDigits() {
        assertFalse(isCardPanAcceptableForSave(""))
        assertFalse(isCardPanAcceptableForSave("123456789012345"))
        assertTrue(isCardPanAcceptableForSave("1234567890123456"))
        assertFalse(isCardPanAcceptableForSave("12345678901234567"))
    }

    @Test
    fun cardPanDigits_filtersNonDigits() {
        assertEquals("4111111111111111", cardPanDigits("4111 1111 1111 1111"))
    }

    @Test
    fun isCardExpiryAcceptableForSave_validMmYy() {
        assertTrue(isCardExpiryAcceptableForSave("1228"))
        assertFalse(isCardExpiryAcceptableForSave("1328"))
        assertFalse(isCardExpiryAcceptableForSave("12"))
        assertFalse(isCardExpiryAcceptableForSave(""))
    }

    @Test
    fun cardPanFieldUiState_fifteenDigits_amexHint() {
        val fifteen = "123456789012345"
        val ui = cardPanFieldUiState(fifteen, showValidationHints = false)
        assertTrue(ui.supportingHint is CardPanSupportingHint.FifteenDigitAmex)
        assertTrue(ui.isFieldError)
    }

    @Test
    fun cardPanFieldUiState_sixteenDigits_neutralProgress() {
        val ui = cardPanFieldUiState("1234567890123456", showValidationHints = false)
        val h = ui.supportingHint as CardPanSupportingHint.Progress
        assertEquals(16, h.digitCount)
        assertFalse(h.treatAsError)
        assertFalse(ui.isFieldError)
    }

    @Test
    fun cardExpiryFieldUiState_emptyWithHints() {
        val ui = cardExpiryFieldUiState("", showValidationHints = true)
        assertTrue(ui.isFieldError)
        assertFalse(ui.showInvalidSupporting)
        assertTrue(ui.showRequiredSupporting)
    }

    @Test
    fun cardCvcIsWeak_incompleteNonEmpty() {
        assertFalse(cardCvcIsWeak(""))
        assertFalse(cardCvcIsWeak("123"))
        assertTrue(cardCvcIsWeak("12"))
    }
}
