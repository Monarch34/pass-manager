package com.passmanager.crypto.kdf

import com.passmanager.crypto.hex
import com.passmanager.crypto.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/** HKDF-SHA-256 against RFC 5869's own test cases. */
class HkdfTest {

    /**
     * RFC 5869 appendix A.1: salt, info, and 42 bytes of output — more than one hash block,
     * so it also covers the chaining that makes T(2) depend on T(1).
     */
    @Test
    fun `RFC 5869 test case 1`() {
        assertEquals(
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                "34007208d5b887185865",
            hkdfSha256(
                inputKeyMaterial = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
                salt = hex("000102030405060708090a0b0c"),
                info = hex("f0f1f2f3f4f5f6f7f8f9"),
                length = 42,
            ).toHex(),
        )
    }

    /**
     * RFC 5869 appendix A.3: no salt and no info. The absent salt becomes a block of zeros
     * rather than being skipped, and this vector is the only thing that proves it — an
     * implementation that skipped the extract step entirely would still return 42 plausible
     * bytes.
     */
    @Test
    fun `RFC 5869 test case 3`() {
        assertEquals(
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d" +
                "9d201395faa4b61a96c8",
            hkdfSha256(
                inputKeyMaterial = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b"),
                salt = ByteArray(0),
                info = ByteArray(0),
                length = 42,
            ).toHex(),
        )
    }

    /**
     * The reason HKDF is here at all: two keys derived from one secret under different
     * labels must be unrelated, so that losing one does not imply the other.
     */
    @Test
    fun `different info produces unrelated output`() {
        val secret = hex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f")
        val salt = hex("a1a2a3a4")
        val content = hkdfSha256(secret, salt, "content".encodeToByteArray(), 32).toHex()
        val index = hkdfSha256(secret, salt, "index".encodeToByteArray(), 32).toHex()
        assertNotEquals(content, index)
    }

    /**
     * Output is produced in 32-byte blocks and truncated, so the boundaries are where a
     * counter or a copy length goes wrong. The full range is also the specification's
     * ceiling: the block counter is one byte.
     */
    @Test
    fun `lengths across the block boundaries are prefixes of one another`() {
        val secret = hex("00112233445566778899aabbccddeeff")
        val longest = hkdfSha256(secret, ByteArray(0), ByteArray(0), 255 * 32).toHex()
        for (length in intArrayOf(1, 31, 32, 33, 64, 65, 255 * 32)) {
            val derived = hkdfSha256(secret, ByteArray(0), ByteArray(0), length)
            assertEquals(length, derived.size)
            assertEquals(longest.substring(0, length * 2), derived.toHex(), "length $length")
        }
    }

    @Test
    fun `rejects lengths outside the specified range`() {
        val secret = hex("00112233")
        assertFailsWith<IllegalArgumentException> {
            hkdfSha256(secret, ByteArray(0), ByteArray(0), 0)
        }
        assertFailsWith<IllegalArgumentException> {
            hkdfSha256(secret, ByteArray(0), ByteArray(0), 255 * 32 + 1)
        }
    }
}
