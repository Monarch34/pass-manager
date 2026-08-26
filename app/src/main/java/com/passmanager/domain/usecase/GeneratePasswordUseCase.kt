package com.passmanager.domain.usecase

import java.security.SecureRandom
import javax.inject.Inject
import kotlin.math.ln
import kotlin.math.roundToInt

class GeneratePasswordUseCase @Inject constructor() {

    private val secureRandom = SecureRandom()

    companion object {
        const val UPPERCASE = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        const val LOWERCASE = "abcdefghijklmnopqrstuvwxyz"
        const val DIGITS = "0123456789"
        const val SYMBOLS = "!@#\$%^&*()-_=+[]{}|;:,.<>?"

        /**
         * Size of the alphabet [invoke] actually draws from.
         *
         * Derived from the same constants rather than restated as numbers. The displayed entropy
         * used to hard-code 32 for the symbol class while only 26 symbols were ever drawn, which
         * is the failure mode a single source of truth removes: change [SYMBOLS] and the reported
         * strength follows it.
         */
        fun poolSize(
            includeUppercase: Boolean = true,
            includeLowercase: Boolean = true,
            includeDigits: Boolean = true,
            includeSymbols: Boolean = true
        ): Int =
            (if (includeUppercase) UPPERCASE.length else 0) +
                (if (includeLowercase) LOWERCASE.length else 0) +
                (if (includeDigits) DIGITS.length else 0) +
                (if (includeSymbols) SYMBOLS.length else 0)

        /**
         * `round(length * log2(poolSize))`, per `docs/IOS_PARITY.md`.
         *
         * Rounded, not truncated. Truncation happens to bias downward, which is the safe
         * direction, but it was paired with an inflated pool size and the two together reported
         * more strength than the generator delivers. A password manager may never overstate that.
         */
        fun entropyBits(
            length: Int,
            includeUppercase: Boolean = true,
            includeLowercase: Boolean = true,
            includeDigits: Boolean = true,
            includeSymbols: Boolean = true
        ): Int {
            val pool = poolSize(includeUppercase, includeLowercase, includeDigits, includeSymbols)
            if (pool <= 1 || length <= 0) return 0
            return (length * (ln(pool.toDouble()) / ln(2.0))).roundToInt()
        }
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
        for (i in combined.indices.reversed()) {
            val j = secureRandom.nextInt(i + 1)
            val temp = combined[i]
            combined[i] = combined[j]
            combined[j] = temp
        }

        return combined.joinToString("")
    }
}
