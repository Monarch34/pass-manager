package com.passmanager.domain.validation

enum class PasswordStrength { WEAK, FAIR, GOOD, STRONG }

object PasswordStrengthEvaluator {

    fun evaluate(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength.WEAK
        var score = 0
        if (password.length >= 8)                                          score++
        if (password.length >= 14)                                         score++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() })                                 score++
        if (password.any { !it.isLetterOrDigit() })                        score++
        return when {
            score <= 1 -> PasswordStrength.WEAK
            score == 2 -> PasswordStrength.FAIR
            score == 3 -> PasswordStrength.GOOD
            else       -> PasswordStrength.STRONG
        }
    }
}
