package com.passmanager.domain.usecase

import java.security.SecureRandom
import javax.inject.Inject

class GeneratePasswordUseCase @Inject constructor() {

    private val secureRandom = SecureRandom()

    companion object {
        const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
        const val DIGITS = "0123456789"
        const val SYMBOLS = "!@#\$%^&*()-_=+[]{}|;:,.<>?"
    }

    operator fun invoke(
        length: Int = 20,
        includeUppercase: Boolean = true,
        includeLowercase: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true
    ): String {
        val charPool = buildString {
            if (includeUppercase) append(UPPERCASE)
            if (includeLowercase) append(LOWERCASE)
            if (includeDigits) append(DIGITS)
            if (includeSymbols) append(SYMBOLS)
        }

        require(charPool.isNotEmpty()) { "At least one character set must be selected" }
        require(length in 8..128) { "Password length must be between 8 and 128" }

        // Ensure at least one character from each selected category
        val required = buildList {
            if (includeUppercase) add(UPPERCASE[secureRandom.nextInt(UPPERCASE.length)])
            if (includeLowercase) add(LOWERCASE[secureRandom.nextInt(LOWERCASE.length)])
            if (includeDigits) add(DIGITS[secureRandom.nextInt(DIGITS.length)])
            if (includeSymbols) add(SYMBOLS[secureRandom.nextInt(SYMBOLS.length)])
        }

        val remaining = (required.size until length).map {
            charPool[secureRandom.nextInt(charPool.length)]
        }

        val combined = (required + remaining).toMutableList()
        // Fisher-Yates shuffle using SecureRandom
        for (i in combined.indices.reversed()) {
            val j = secureRandom.nextInt(i + 1)
            val temp = combined[i]
            combined[i] = combined[j]
            combined[j] = temp
        }

        return combined.joinToString("")
    }
}
