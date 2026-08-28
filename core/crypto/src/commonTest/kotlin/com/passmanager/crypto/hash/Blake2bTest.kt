package com.passmanager.crypto.hash

import com.passmanager.crypto.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * BLAKE2b against published digests.
 *
 * These are diagnostics rather than the real gate: Argon2's own vectors exercise BLAKE2b
 * far harder than any of this, and a broken hash cannot produce a correct Argon2 tag. What
 * they buy is a *localised* failure. If the RFC 9106 vector breaks and these pass, the fault
 * is somewhere in Argon2; if these break too, it is here, and that is a much smaller place
 * to look.
 */
class Blake2bTest {

    @Test
    fun `hashes the empty input`() {
        assertEquals(
            "786a02f742015903c6c6fd852552d272912f4740e15847618a86e217f71f5419" +
                "d25e1031afee585313896444934eb04b903a685b1448b755d56f701afe9be2ce",
            Blake2b(64).digest().toHex(),
        )
    }

    @Test
    fun `hashes the RFC 7693 vector for abc`() {
        assertEquals(
            "ba80a53f981c4d0d6a2797b69f12f6e94c212f14685ac4b74b12bb6fdbffa2d1" +
                "7d87c5392aab792dc252d5de4533cc9518d38aa8dbf1925ab92386edd4009923",
            Blake2b(64).update("abc".encodeToByteArray()).digest().toHex(),
        )
    }

    /**
     * The digest length is folded into the initial state, so a shorter digest is not a
     * truncation of a longer one. Reading the first 32 bytes of BLAKE2b-512 and calling it
     * BLAKE2b-256 is a real and easy mistake, and Argon2 depends on it not being made.
     */
    @Test
    fun `a shorter digest is not a prefix of a longer one`() {
        val short = Blake2b(32).update("abc".encodeToByteArray()).digest().toHex()
        val long = Blake2b(64).update("abc".encodeToByteArray()).digest().toHex()
        assertEquals(64, short.length)
        assertTrue(!long.startsWith(short), "BLAKE2b-256 came out as a prefix of BLAKE2b-512")
    }

    /**
     * The absorbing loop must not compress a full buffer until it knows more data follows,
     * because the final block is compressed with a different flag. Feeding exactly one
     * block, and one block plus a byte, is where an off-by-one in that rule shows up.
     */
    @Test
    fun `chunking the input does not change the digest`() {
        val input = ByteArray(Blake2b.BlockSize * 3 + 7) { (it * 31 and 0xff).toByte() }
        val whole = Blake2b(64).update(input).digest().toHex()
        for (chunk in intArrayOf(1, 63, 127, 128, 129, 200)) {
            val streamed = Blake2b(64)
            var offset = 0
            while (offset < input.size) {
                val take = minOf(chunk, input.size - offset)
                streamed.update(input, offset, take)
                offset += take
            }
            assertEquals(whole, streamed.digest().toHex(), "chunked into $chunk-byte updates")
        }
    }

    @Test
    fun `exactly one block is hashed as a final block`() {
        val input = ByteArray(Blake2b.BlockSize) { 0x42 }
        assertEquals(
            Blake2b(64).update(input).digest().toHex(),
            Blake2b(64).update(input, 0, 64).update(input, 64, 64).digest().toHex(),
        )
    }

    @Test
    fun `rejects digest sizes outside the supported range`() {
        assertFailsWith<IllegalArgumentException> { Blake2b(0) }
        assertFailsWith<IllegalArgumentException> { Blake2b(65) }
    }
}
