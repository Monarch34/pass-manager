package com.passmanager.crypto.kdf

import com.passmanager.crypto.mac.platformHmacSha256
import com.passmanager.crypto.wipe

/**
 * HKDF with SHA-256 (RFC 5869): both steps, extract then expand.
 *
 * This is not a substitute for [argon2id] and never takes a passphrase. Argon2 exists to
 * make guessing a low-entropy secret expensive; HKDF exists to turn one high-entropy secret
 * into several independent ones cheaply. Feeding a passphrase to HKDF would produce a key
 * an attacker can guess at the speed of a single hash.
 *
 * The point of `info` is domain separation. Two keys derived from the same vault key with
 * different `info` strings are independent, so a flaw that exposes one — a content key
 * recovered from a chosen-ciphertext attack, say — leaves the others intact. Every caller
 * passes a distinct, versioned label.
 *
 * @param salt optional and not secret. RFC 5869 substitutes a block of zeros when it is
 *   absent, which is what happens here, so callers must not read an empty salt as a way to
 *   skip the extract step.
 */
fun hkdfSha256(
    inputKeyMaterial: ByteArray,
    salt: ByteArray,
    info: ByteArray,
    length: Int,
): ByteArray {
    require(length in 1..MaxOutputLength) {
        "requested $length bytes; HKDF-SHA-256 produces 1..$MaxOutputLength"
    }

    val pseudoRandomKey =
        platformHmacSha256(if (salt.isEmpty()) ByteArray(HashLength) else salt, inputKeyMaterial)

    val out = ByteArray(length)
    var previous = ByteArray(0)
    var written = 0
    var counter = 1
    while (written < length) {
        // T(n) = HMAC(PRK, T(n-1) || info || n). Chaining each block into the next is what
        // stops the output being 255 independent one-block derivations.
        val input = ByteArray(previous.size + info.size + 1)
        previous.copyInto(input)
        info.copyInto(input, previous.size)
        input[input.size - 1] = counter.toByte()

        previous.wipe()
        previous = platformHmacSha256(pseudoRandomKey, input)
        input.wipe()

        val take = minOf(HashLength, length - written)
        previous.copyInto(out, written, 0, take)
        written += take
        counter++
    }

    previous.wipe()
    pseudoRandomKey.wipe()
    return out
}

private const val HashLength = 32

/** RFC 5869's ceiling: the block counter is a single byte, so 255 blocks is all there is. */
private const val MaxOutputLength = 255 * HashLength
