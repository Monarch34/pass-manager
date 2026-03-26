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

class BankPasswordValidator @Inject constructor() {

    fun validate(
        password: String,
        previousPasswords: List<String> = emptyList()
    ): List<BankPasswordViolation> {
        if (password.isEmpty()) return emptyList()

        val allDigits = password.all { it.isDigit() }
        if (allDigits) {
            return when {
                password.length < 6 -> listOf(BankPasswordViolation.TooShort)
                password.length == 6 -> buildList {
                    if (previousPasswords.take(4).contains(password)) {
                        add(BankPasswordViolation.ReusedPassword)
                    }
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
        if (password.length < 6) add(BankPasswordViolation.TooShort)
        if (password.length > 12) add(BankPasswordViolation.TooLong)
        if (password.none { it.isUpperCase() }) add(BankPasswordViolation.MissingUppercase)
        if (password.none { it.isLowerCase() }) add(BankPasswordViolation.MissingLowercase)
        if (password.none { it.isDigit() }) add(BankPasswordViolation.MissingDigit)
        if (hasConsecutiveSequence(password)) add(BankPasswordViolation.ConsecutiveSequence)
        if (hasRepeatingCharacters(password)) add(BankPasswordViolation.RepeatingCharacters)
        if (previousPasswords.take(4).any { it == password }) {
            add(BankPasswordViolation.ReusedPassword)
        }
    }

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
