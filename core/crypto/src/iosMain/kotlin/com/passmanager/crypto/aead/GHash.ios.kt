package com.passmanager.crypto.aead

/**
 * GCM's authenticator: multiplication in GF(2^128), as specified in NIST SP 800-38D.
 *
 * This exists because Apple gives Kotlin no way to reach AES-GCM whole. CryptoKit has it
 * and is Swift-only, which Kotlin/Native cannot bind; CommonCrypto's GCM entry points are
 * not in the iOS SDK's public headers, and the platform bindings confirm it — the mode
 * constants stop at ECB, CBC, CFB, CTR and OFB. What is available is AES itself, so the
 * split is: **Apple's AES, this project's authenticator.** The cipher, which is the part
 * that is genuinely dangerous to implement in software, stays theirs.
 *
 * The implementation is branch-free. Every iteration touches the same words in the same
 * order and selects with an arithmetic-shift mask rather than an `if`, so neither the
 * subkey nor the data influences the timing. The obvious speedup — precomputing a table of
 * multiples of the subkey and looking up four bits at a time — is deliberately not used:
 * those lookups are indexed by secret-derived values, which is exactly the pattern that
 * produced practical cache-timing attacks against table-driven AES.
 *
 * The cost is roughly a hundred operations per byte, which is unremarkable for vault-sized
 * data and would be the wrong choice for a network stream.
 */
internal class GHash(subkey: ByteArray) {

    private val hHi = readLongBe(subkey, 0)
    private val hLo = readLongBe(subkey, 8)

    private var accumulatorHi = 0L
    private var accumulatorLo = 0L

    /**
     * Absorbs data, zero-padded up to a block boundary at the end of *this* call. GCM
     * requires the associated data and the ciphertext to be padded separately, which is why
     * this pads per call rather than buffering across calls.
     */
    fun update(data: ByteArray, offset: Int = 0, length: Int = data.size - offset) {
        var consumed = 0
        while (consumed + BlockSize <= length) {
            absorb(
                readLongBe(data, offset + consumed),
                readLongBe(data, offset + consumed + 8),
            )
            consumed += BlockSize
        }
        if (consumed < length) {
            var hi = 0L
            var lo = 0L
            for (i in 0 until 8) {
                hi = (hi shl 8) or byteAt(data, offset, consumed + i, length)
            }
            for (i in 8 until BlockSize) {
                lo = (lo shl 8) or byteAt(data, offset, consumed + i, length)
            }
            absorb(hi, lo)
        }
    }

    /**
     * The final block: the two lengths in bits. Without it, an attacker could move bytes
     * between the associated data and the ciphertext and the tag would not notice.
     */
    fun updateLengths(associatedDataBytes: Int, ciphertextBytes: Int) {
        absorb(associatedDataBytes.toLong() * 8, ciphertextBytes.toLong() * 8)
    }

    fun digest(): ByteArray {
        val out = ByteArray(BlockSize)
        writeLongBe(out, 0, accumulatorHi)
        writeLongBe(out, 8, accumulatorLo)
        return out
    }

    /** Y = (Y xor block) * H, by shift-and-add over the 128 bits of the left operand. */
    private fun absorb(blockHi: Long, blockLo: Long) {
        var xHi = accumulatorHi xor blockHi
        var xLo = accumulatorLo xor blockLo
        var productHi = 0L
        var productLo = 0L
        var multipleHi = hHi
        var multipleLo = hLo

        repeat(64) {
            // Arithmetic shift: all ones when the leading bit is set, all zeros otherwise.
            // Masking with it adds the current multiple or adds nothing, in the same time.
            val selected = xHi shr 63
            productHi = productHi xor (multipleHi and selected)
            productLo = productLo xor (multipleLo and selected)

            val overflow = -(multipleLo and 1L)
            multipleLo = (multipleLo ushr 1) or (multipleHi shl 63)
            multipleHi = (multipleHi ushr 1) xor (ReductionPolynomial and overflow)

            xHi = xHi shl 1
        }
        repeat(64) {
            val selected = xLo shr 63
            productHi = productHi xor (multipleHi and selected)
            productLo = productLo xor (multipleLo and selected)

            val overflow = -(multipleLo and 1L)
            multipleLo = (multipleLo ushr 1) or (multipleHi shl 63)
            multipleHi = (multipleHi ushr 1) xor (ReductionPolynomial and overflow)

            xLo = xLo shl 1
        }

        accumulatorHi = productHi
        accumulatorLo = productLo
    }

    private companion object {
        const val BlockSize = 16

        /**
         * x^128 + x^7 + x^2 + x + 1, in the bit order GCM uses, which puts it in the top
         * byte of the high word.
         */
        val ReductionPolynomial = 0xe100000000000000uL.toLong()
    }
}

private fun byteAt(data: ByteArray, offset: Int, index: Int, length: Int): Long =
    if (index < length) data[offset + index].toLong() and 0xff else 0L

private fun readLongBe(source: ByteArray, offset: Int): Long {
    var value = 0L
    for (i in 0 until 8) value = (value shl 8) or (source[offset + i].toLong() and 0xff)
    return value
}

private fun writeLongBe(target: ByteArray, offset: Int, value: Long) {
    for (i in 0 until 8) target[offset + i] = (value ushr (8 * (7 - i))).toByte()
}
