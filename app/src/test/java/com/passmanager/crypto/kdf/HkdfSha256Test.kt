package com.passmanager.crypto.kdf

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HkdfSha256Test {

    @Test
    fun `derive produces 32 bytes by default`() {
        val key = HkdfSha256.derive(
            ikm = ByteArray(32) { it.toByte() },
            salt = ByteArray(16) { (it + 100).toByte() },
            info = "test".toByteArray()
        )
        assertEquals(32, key.size)
        key.fill(0)
    }

    @Test
    fun `same inputs produce same output`() {
        val ikm = ByteArray(32) { it.toByte() }
        val salt = "salt".toByteArray()
        val info = "info".toByteArray()

        val a = HkdfSha256.derive(ikm, salt, info)
        val b = HkdfSha256.derive(ikm, salt, info)
        assertArrayEquals(a, b)
        a.fill(0)
        b.fill(0)
    }

    @Test
    fun `different salt produces different key`() {
        val ikm = ByteArray(32) { it.toByte() }
        val info = "info".toByteArray()

        val a = HkdfSha256.derive(ikm, "salt1".toByteArray(), info)
        val b = HkdfSha256.derive(ikm, "salt2".toByteArray(), info)
        assertNotEquals(a.toList(), b.toList())
        a.fill(0)
        b.fill(0)
    }

    @Test
    fun `different info produces different key`() {
        val ikm = ByteArray(32) { it.toByte() }
        val salt = "salt".toByteArray()

        val a = HkdfSha256.derive(ikm, salt, "info1".toByteArray())
        val b = HkdfSha256.derive(ikm, salt, "info2".toByteArray())
        assertNotEquals(a.toList(), b.toList())
        a.fill(0)
        b.fill(0)
    }

    @Test
    fun `custom output length`() {
        val key = HkdfSha256.derive(
            ikm = ByteArray(32) { it.toByte() },
            salt = "salt".toByteArray(),
            info = "info".toByteArray(),
            length = 64
        )
        assertEquals(64, key.size)
        key.fill(0)
    }

    @Test
    fun `empty salt uses zero-filled hash-length salt`() {
        val key = HkdfSha256.derive(
            ikm = ByteArray(32) { it.toByte() },
            salt = ByteArray(0),
            info = "test".toByteArray()
        )
        assertEquals(32, key.size)
        key.fill(0)
    }

    // RFC 5869 Test Vector #1
    @Test
    fun `rfc5869 test vector 1`() {
        val ikm = hexToBytes("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val salt = hexToBytes("000102030405060708090a0b0c")
        val info = hexToBytes("f0f1f2f3f4f5f6f7f8f9")
        val expected = hexToBytes(
            "3cb25f25faacd57a90434f64d0362f2a" +
            "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
            "34007208d5b887185865"
        )

        val result = HkdfSha256.derive(ikm, salt, info, length = 42)
        assertArrayEquals(expected, result)
        result.fill(0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `reject zero-length output`() {
        HkdfSha256.derive(
            ikm = ByteArray(32),
            salt = ByteArray(0),
            info = ByteArray(0),
            length = 0
        )
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val bytes = ByteArray(len / 2)
        for (i in bytes.indices) {
            bytes[i] = ((Character.digit(hex[i * 2], 16) shl 4) +
                Character.digit(hex[i * 2 + 1], 16)).toByte()
        }
        return bytes
    }
}
