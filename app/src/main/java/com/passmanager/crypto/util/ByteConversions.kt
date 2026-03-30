package com.passmanager.crypto.util

import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction

fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean =
    if (this == null && other == null) true
    else if (this == null || other == null) false
    else contentEquals(other)

/**
 * Converts a [CharArray] to UTF-8 bytes without creating an intermediate [String],
 * so the caller retains the ability to zero the source array.
 */
fun CharArray.toUtf8Bytes(): ByteArray {
    val charBuffer = CharBuffer.wrap(this)
    val encoder = Charsets.UTF_8.newEncoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    val byteBuffer = encoder.encode(charBuffer)
    try {
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        return bytes
    } finally {
        if (byteBuffer.hasArray()) {
            byteBuffer.array().fill(0)
        }
    }
}
