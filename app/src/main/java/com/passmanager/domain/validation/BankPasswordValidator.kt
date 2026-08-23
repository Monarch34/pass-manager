package com.passmanager.domain.validation

import javax.inject.Inject

sealed class BankPasswordViolation {
    data object TooShort : BankPasswordViolation()
    data object TooLong : BankPasswordViolation()
    data object MissingUppercase : BankPasswordViolation()
    data object MissingLowercase : BankPasswordViolation()
    data object MissingDigit : BankPasswordViolation()
    data object ConsecutiveSequence : BankPasswordViolation()
    data object RepeatingCharacters : BankPasswordViolation()
    data object ReusedPassword : BankPasswordViolation()
}

/**
 * The numbers behind [BankPasswordValidator], named so that producers of bank passwords —
 * the password generator, the rule indicator, the save-time history trim — stay in step with
 * the checker instead of repeating literals.
 */
object BankPasswordRules {
    const val MIN_LENGTH = 6
    const val MAX_LENGTH = 12

    /** How many retired passwords a new bank password is checked against. */
    const val HISTORY_SIZE = 4
}

class BankPasswordValidator @Inject constructor() {

    /**
     * @param previousPasswords passwords this record no longer uses. The password currently
     *   stored on the record must not be in this list, otherwise re-saving an untouched item
     *   would fail with [BankPasswordViolation.ReusedPassword].
     */
    fun validate(
        password: String,
        previousPasswords: List<String> = emptyList()
    ): List<BankPasswordViolation> {
        if (password.isEmpty()) return emptyList()

        val allDigits = password.all { it.isDigit() }
        if (allDigits) {
            return when {
                password.length < BankPasswordRules.MIN_LENGTH -> listOf(BankPasswordViolation.TooShort)
                password.length == BankPasswordRules.MIN_LENGTH -> buildList {
                    if (hasConsecutiveSequence(password)) add(BankPasswordViolation.ConsecutiveSequence)
                    if (hasRepeatingCharacters(password)) add(BankPasswordViolation.RepeatingCharacters)
                    if (isReused(password, previousPasswords)) add(BankPasswordViolation.ReusedPassword)
                }
                else -> validateComplexPassword(password, previousPasswords)
            }
        }
        return validateComplexPassword(password, previousPasswords)
    }

    private fun validateComplexPassword(
        password: String,
        previousPasswords: List<String>
    ): List<BankPasswordViolation> = buildList {
        if (password.length < BankPasswordRules.MIN_LENGTH) add(BankPasswordViolation.TooShort)
        if (password.length > BankPasswordRules.MAX_LENGTH) add(BankPasswordViolation.TooLong)
        if (password.none { it.isUpperCase() }) add(BankPasswordViolation.MissingUppercase)
        if (password.none { it.isLowerCase() }) add(BankPasswordViolation.MissingLowercase)
        if (password.none { it.isDigit() }) add(BankPasswordViolation.MissingDigit)
        if (hasConsecutiveSequence(password)) add(BankPasswordViolation.ConsecutiveSequence)
        if (hasRepeatingCharacters(password)) add(BankPasswordViolation.RepeatingCharacters)
        if (isReused(password, previousPasswords)) add(BankPasswordViolation.ReusedPassword)
    }

    private fun isReused(password: String, previousPasswords: List<String>): Boolean =
        previousPasswords.take(BankPasswordRules.HISTORY_SIZE).any { it == password }

    private fun hasConsecutiveSequence(password: String): Boolean {
        if (password.length < 3) return false
        for (i in 0..password.length - 3) {
            val a = password[i]
            val b = password[i + 1]
            val c = password[i + 2]
            val diff = b - a
            if (diff == c - b && (diff == 1 || diff == -1)) return true
        }
        return false
    }

    private fun hasRepeatingCharacters(password: String): Boolean {
        if (password.length < 3) return false
        for (i in 0..password.length - 3) {
            if (password[i] == password[i + 1] && password[i + 1] == password[i + 2]) return true
        }
        return false
    }
}
