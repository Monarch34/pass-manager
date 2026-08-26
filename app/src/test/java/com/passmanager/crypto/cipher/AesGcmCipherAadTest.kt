package com.passmanager.crypto.cipher

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.AEADBadTagException

/**
 * The AAD overloads exist so `.pmvault` can bind its header into the body's tag. What matters is
 * not that matching AAD round-trips, but that *every* mismatch fails the tag check.
 */
class AesGcmCipherAadTest {

    private val cipher = AesGcmCipher()
    private val key = ByteArray(32) { it.toByte() }
    private val plaintext = "vault body".toByteArray()
    private val aad = "PMVT header bytes".toByteArray()

    @Test
    fun `round-trips with matching aad`() {
        val encrypted = cipher.encrypt(plaintext, key, aad)
        assertArrayEquals(plaintext, cipher.decrypt(encrypted, key, aad))
    }

    @Test
    fun `tampered aad fails the tag check`() {
        val encrypted = cipher.encrypt(plaintext, key, aad)
        val tampered = aad.copyOf().also { it[0] = (it[0] + 1).toByte() }

        assertThrows(AEADBadTagException::class.java) {
            cipher.decrypt(encrypted, key, tampered)
        }
    }

    @Test
    fun `aad of a different length fails the tag check`() {
        val encrypted = cipher.encrypt(plaintext, key, aad)

        assertThrows(AEADBadTagException::class.java) {
            cipher.decrypt(encrypted, key, aad + 0x20)
        }
    }

    @Test
    fun `omitting the aad on decrypt fails the tag check`() {
        val encrypted = cipher.encrypt(plaintext, key, aad)

        assertThrows(AEADBadTagException::class.java) {
            cipher.decrypt(encrypted, key)
        }
    }

    @Test
    fun `supplying an aad the ciphertext was not sealed with fails the tag check`() {
        val encrypted = cipher.encrypt(plaintext, key)

        assertThrows(AEADBadTagException::class.java) {
            cipher.decrypt(encrypted, key, aad)
        }
    }

    @Test
    fun `wrong key fails even with matching aad`() {
        val encrypted = cipher.encrypt(plaintext, key, aad)
        val otherKey = ByteArray(32) { (it + 1).toByte() }

        assertThrows(AEADBadTagException::class.java) {
            cipher.decrypt(encrypted, otherKey, aad)
        }
    }

    @Test
    fun `aad overload still produces a fresh 12-byte iv per call`() {
        val first = cipher.encrypt(plaintext, key, aad)
        val second = cipher.encrypt(plaintext, key, aad)

        assert(first.iv.size == 12)
        assert(!first.iv.contentEquals(second.iv))
    }

    @Test
    fun `the aad-free overloads are unchanged`() {
        val encrypted = cipher.encrypt(plaintext, key)
        assertArrayEquals(plaintext, cipher.decrypt(encrypted, key))
    }
}
