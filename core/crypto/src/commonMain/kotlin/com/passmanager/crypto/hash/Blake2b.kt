package com.passmanager.crypto.hash

/**
 * BLAKE2b (RFC 7693), unkeyed, with a caller-chosen digest length of 1..64 bytes.
 *
 * This is the one hash in the module that is not taken from the platform, because no
 * platform offers it: BLAKE2b exists here only because Argon2 is defined in terms of it.
 * It is `internal` for the same reason — nothing outside this module should be reaching for
 * a hash function, and everything that needs one has a named construction to call instead.
 *
 * The state is streaming rather than a single `hash(bytes)` call. Argon2's initial hash
 * covers eleven separate values including the password, and building one concatenated
 * array first would mean an extra copy of the password on the heap that nothing can then
 * find to erase.
 */
internal class Blake2b(private val digestSize: Int) {

    private val h = LongArray(8)
    private val block = ByteArray(BlockSize)
    private var blockFilled = 0

    /**
     * Bytes absorbed so far. RFC 7693 specifies a 128-bit counter; the high half is always
     * zero here, since the longest input this module ever hashes is a 1 KiB Argon2 block.
     */
    private var counter = 0L

    /** Reused across compressions so that hashing allocates nothing per block. */
    private val v = LongArray(16)
    private val m = LongArray(16)

    init {
        require(digestSize in 1..MaxDigestSize) {
            "BLAKE2b digest size $digestSize is outside 1..$MaxDigestSize"
        }
        Iv.copyInto(h)
        // Parameter block, folded into h[0]: digest length, key length (zero: unkeyed),
        // fanout 1 and depth 1 for sequential hashing.
        h[0] = h[0] xor 0x0101_0000L xor digestSize.toLong()
    }

    fun update(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Blake2b {
        var read = offset
        var remaining = length
        while (remaining > 0) {
            // Compressed only once it is known that more data follows, because the final
            // block must be compressed with the last-block flag set and there is no way to
            // tell in advance which block that will be.
            if (blockFilled == BlockSize) {
                counter += BlockSize
                compress(last = false)
                blockFilled = 0
            }
            val take = minOf(BlockSize - blockFilled, remaining)
            data.copyInto(block, blockFilled, read, read + take)
            blockFilled += take
            read += take
            remaining -= take
        }
        return this
    }

    /**
     * Finishes the hash. The instance is spent afterwards; call it once.
     */
    fun digest(): ByteArray {
        counter += blockFilled.toLong()
        block.fill(0, blockFilled, BlockSize)
        compress(last = true)
        val out = ByteArray(digestSize)
        for (i in 0 until digestSize) {
            out[i] = (h[i / 8] ushr (8 * (i % 8))).toByte()
        }
        return out
    }

    private fun compress(last: Boolean) {
        for (i in 0 until 16) {
            var word = 0L
            for (b in 7 downTo 0) {
                word = (word shl 8) or (block[i * 8 + b].toLong() and 0xff)
            }
            m[i] = word
        }

        for (i in 0 until 8) v[i] = h[i]
        for (i in 0 until 8) v[8 + i] = Iv[i]
        v[12] = v[12] xor counter
        // v[13] would take the counter's high half, which is always zero here.
        if (last) v[14] = v[14].inv()

        for (round in 0 until Rounds) {
            // Twelve rounds over ten permutations: the last two reuse the first two.
            val s = Sigma[round % Sigma.size]
            mix(0, 4, 8, 12, m[s[0]], m[s[1]])
            mix(1, 5, 9, 13, m[s[2]], m[s[3]])
            mix(2, 6, 10, 14, m[s[4]], m[s[5]])
            mix(3, 7, 11, 15, m[s[6]], m[s[7]])
            mix(0, 5, 10, 15, m[s[8]], m[s[9]])
            mix(1, 6, 11, 12, m[s[10]], m[s[11]])
            mix(2, 7, 8, 13, m[s[12]], m[s[13]])
            mix(3, 4, 9, 14, m[s[14]], m[s[15]])
        }

        for (i in 0 until 8) h[i] = h[i] xor v[i] xor v[8 + i]
    }

    private fun mix(a: Int, b: Int, c: Int, d: Int, x: Long, y: Long) {
        v[a] = v[a] + v[b] + x
        v[d] = (v[d] xor v[a]).rotateRight(32)
        v[c] = v[c] + v[d]
        v[b] = (v[b] xor v[c]).rotateRight(24)
        v[a] = v[a] + v[b] + y
        v[d] = (v[d] xor v[a]).rotateRight(16)
        v[c] = v[c] + v[d]
        v[b] = (v[b] xor v[c]).rotateRight(63)
    }

    companion object {
        const val BlockSize = 128
        const val MaxDigestSize = 64

        private const val Rounds = 12

        /** The SHA-512 initialisation vector, which BLAKE2b shares. */
        private val Iv = longArrayOf(
            0x6a09e667f3bcc908uL.toLong(), 0xbb67ae8584caa73buL.toLong(),
            0x3c6ef372fe94f82buL.toLong(), 0xa54ff53a5f1d36f1uL.toLong(),
            0x510e527fade682d1uL.toLong(), 0x9b05688c2b3e6c1fuL.toLong(),
            0x1f83d9abfb41bd6buL.toLong(), 0x5be0cd19137e2179uL.toLong(),
        )

        private val Sigma = arrayOf(
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15),
            intArrayOf(14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3),
            intArrayOf(11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4),
            intArrayOf(7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8),
            intArrayOf(9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13),
            intArrayOf(2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9),
            intArrayOf(12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11),
            intArrayOf(13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10),
            intArrayOf(6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5),
            intArrayOf(10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0),
        )
    }
}
