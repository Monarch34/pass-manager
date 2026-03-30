package com.passmanager.domain.validation

/** Standard 16-digit PAN groups of 4. */
const val CARD_NUMBER_DIGITS = 16

private const val MIN_EXPIRY_YEAR_FULL = 2000
private const val MAX_EXPIRY_YEAR_FULL = 2100

/**
 * Parses card expiry:
 * - **MM/YY** (4 digits): month 1–12, year as 20YY
 * - **MM/YYYY** (6 digits): accepted for data saved earlier; month 1–12, full year in range
 */
fun parseExpiryMmYyToMonthYear(expiry: String): Pair<Int, Int>? {
    val digits = expiry.trim().filter { it.isDigit() }
    if (digits.length >= 6) {
        val mm = digits.substring(0, 2).toIntOrNull() ?: return null
        val yyyy = digits.substring(2, 6).toIntOrNull() ?: return null
        if (mm !in 1..12) return null
        if (yyyy !in MIN_EXPIRY_YEAR_FULL..MAX_EXPIRY_YEAR_FULL) return null
        return mm to yyyy
    }
    if (digits.length >= 4) {
        val mm = digits.substring(0, 2).toIntOrNull() ?: return null
        val yy = digits.substring(2, 4).toIntOrNull() ?: return null
        if (mm !in 1..12) return null
        return mm to (2000 + yy)
    }
    return null
}

/** Storage and display: MM/YY ([yearFull] last two digits). */
fun formatExpiryMmYy(month: Int, yearFull: Int): String {
    require(month in 1..12)
    return "%02d/%02d".format(month, yearFull % 100)
}

/** Expiry field raw value: digits only (max four); UI may insert a visual slash. */
fun sanitizeExpiryFieldDigits(value: String): String = value.filter { it.isDigit() }.take(4)

private fun expiryDigitsToSlashForm(fieldDigits: String): String {
    val digits = sanitizeExpiryFieldDigits(fieldDigits)
    return when {
        digits.length <= 2 -> digits
        else -> digits.substring(0, 2) + "/" + digits.substring(2)
    }
}

/** Parses a complete MM/YY from the digit-only field value. */
fun parseExpiryFieldDigits(fieldDigits: String): Pair<Int, Int>? =
    parseExpiryMmYyToMonthYear(expiryDigitsToSlashForm(fieldDigits))

/** Digit-only string to show in the expiry field when loading from the vault. */
fun expiryFieldDigitsFromStored(stored: String): String {
    val parsed = parseExpiryMmYyToMonthYear(stored) ?: return sanitizeExpiryFieldDigits(stored)
    return "%02d%02d".format(parsed.first, parsed.second % 100)
}
