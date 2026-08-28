package com.passmanager.crypto

/**
 * Hex helpers for tests only.
 *
 * Published test vectors are written in hex, and transcribing them into `byteArrayOf` with
 * signed decimal literals is both unreadable and a place to introduce a typo that looks
 * like a cryptographic bug. Keeping the vectors in the notation their specifications use
 * means a reader can compare them against the source document by eye.
 */
internal fun hex(value: String): ByteArray {
    require(value.length % 2 == 0) { "hex string has an odd length: ${value.length}" }
    return ByteArray(value.length / 2) { i ->
        ((digit(value[2 * i]) shl 4) or digit(value[2 * i + 1])).toByte()
    }
}

internal fun ByteArray.toHex(): String {
    val digits = "0123456789abcdef"
    return buildString(size * 2) {
        for (byte in this@toHex) {
            val value = byte.toInt() and 0xff
            append(digits[value shr 4])
            append(digits[value and 0xf])
        }
    }
}

/** A run of one repeated byte, which is how several RFC 9106 vectors state their inputs. */
internal fun repeatedByte(value: Int, size: Int): ByteArray = ByteArray(size) { value.toByte() }

private fun digit(character: Char): Int {
    val value = when (character) {
        in '0'..'9' -> character - '0'
        in 'a'..'f' -> character - 'a' + 10
        in 'A'..'F' -> character - 'A' + 10
        else -> -1
    }
    require(value >= 0) { "'$character' is not a hex digit" }
    return value
}
