package com.passmanager.desktop.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals

class HkdfSha256Test {

    @Suppress("RemoveExplicitTypeArguments")
    private fun hex(s: String): ByteArray =
        s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /**
     * RFC 5869 Test Case 1 — known input/output vector.
     * https://www.rfc-editor.org/rfc/rfc5869#appendix-A.1
     */
    @Test
    fun `RFC 5869 test case 1`() {
        val ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val salt = hex("000102030405060708090a0b0c")
        val info = hex("f0f1f2f3f4f5f6f7f8f9")
        val expectedOkm = hex(
            "3cb25f25faacd57a90434f64d0362f2a" +
            "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
            "34007208d5b887185865"
        )

        val result = HkdfSha256.derive(ikm, salt, info, length = 42)
        assertContentEquals(expectedOkm, result)
    }

    /**
     * When salt is empty, HKDF-Extract should use a zero-filled key of hash length.
     * Verify by comparing against explicit zero salt.
     */
    @Test
    fun `empty salt uses zero-filled key`() {
        val ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val info = "test-info".toByteArray()

        val withEmptySalt = HkdfSha256.derive(ikm, ByteArray(0), info, length = 32)
        val withZeroSalt = HkdfSha256.derive(ikm, ByteArray(32), info, length = 32)

        assertContentEquals(withZeroSalt, withEmptySalt)
    }
}
