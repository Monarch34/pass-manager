package com.passmanager.crypto.key

import com.passmanager.crypto.Secret
import com.passmanager.crypto.aead.AesGcm
import com.passmanager.crypto.kdf.Argon2Parameters
import com.passmanager.crypto.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * The two-key model.
 *
 * Cheap parameters throughout: what is under test is the wrapping, not Argon2, and Argon2
 * has its own vectors. Using the shipped cost here would add seconds to every run on every
 * target to re-derive a key that is already proven correct.
 */
class VaultKeysTest {

    private val cheap = Argon2Parameters(memoryKib = 32, iterations = 1, parallelism = 1)

    @Test
    fun `a wrapped key comes back`() {
        val salt = VaultKeys.newSalt()
        val kek = VaultKeys.deriveKeyEncryptionKey(Secret.copyOfUtf8("open sesame"), salt, cheap)
        val vaultKey = VaultKeys.newVaultKey()

        val wrapped = VaultKeys.wrap(kek, vaultKey)
        assertEquals(VaultKeys.WrappedSize, wrapped.size)
        assertEquals(vaultKey.copyBytes().toHex(), VaultKeys.unwrap(kek, wrapped)?.copyBytes()?.toHex())
    }

    @Test
    fun `the wrong passphrase yields nothing`() {
        val salt = VaultKeys.newSalt()
        val wrapped = VaultKeys.wrap(
            VaultKeys.deriveKeyEncryptionKey(Secret.copyOfUtf8("right"), salt, cheap),
            VaultKeys.newVaultKey(),
        )
        val wrong = VaultKeys.deriveKeyEncryptionKey(Secret.copyOfUtf8("wrong"), salt, cheap)
        assertNull(VaultKeys.unwrap(wrong, wrapped))
    }

    /**
     * The salt is what stops one derivation being reusable against another vault. The same
     * passphrase under a different salt has to produce a different key, or every vault in a
     * stolen backup falls to one guess.
     */
    @Test
    fun `the same passphrase under a different salt is a different key`() {
        val passphrase = Secret.copyOfUtf8("open sesame")
        val first = VaultKeys.deriveKeyEncryptionKey(passphrase, VaultKeys.newSalt(), cheap)
        val second = VaultKeys.deriveKeyEncryptionKey(passphrase, VaultKeys.newSalt(), cheap)
        assertNotEquals(first.copyBytes().toHex(), second.copyBytes().toHex())
    }

    /**
     * Rewrapping is what a passphrase change does, and it happens with the vault untouched.
     * Two wrappings of one key must differ — a repeated nonce under a repeated key is the
     * one failure GCM does not survive — while both still yield the same key.
     */
    @Test
    fun `rewrapping the same key never repeats a nonce`() {
        val vaultKey = VaultKeys.newVaultKey()
        val salt = VaultKeys.newSalt()
        val old = VaultKeys.deriveKeyEncryptionKey(Secret.copyOfUtf8("first"), salt, cheap)
        val new = VaultKeys.deriveKeyEncryptionKey(Secret.copyOfUtf8("second"), salt, cheap)

        val underOld = VaultKeys.wrap(old, vaultKey)
        val underNew = VaultKeys.wrap(new, vaultKey)
        val againUnderOld = VaultKeys.wrap(old, vaultKey)

        assertNotEquals(underOld.toHex(), againUnderOld.toHex(), "same key wrapped twice")
        assertEquals(vaultKey.copyBytes().toHex(), VaultKeys.unwrap(old, underOld)?.copyBytes()?.toHex())
        assertEquals(vaultKey.copyBytes().toHex(), VaultKeys.unwrap(new, underNew)?.copyBytes()?.toHex())
        assertEquals(vaultKey.copyBytes().toHex(), VaultKeys.unwrap(old, againUnderOld)?.copyBytes()?.toHex())
        assertNull(VaultKeys.unwrap(new, underOld), "the old wrapping under the new key")
    }

    /**
     * Two unlock paths, one vault key: this is the shape biometric unlock takes, and the
     * reason the passphrase does not encrypt the contents directly.
     */
    @Test
    fun `one vault key can be wrapped under two independent keys`() {
        val vaultKey = VaultKeys.newVaultKey()
        val fromPassphrase =
            VaultKeys.deriveKeyEncryptionKey(Secret.copyOfUtf8("passphrase"), VaultKeys.newSalt(), cheap)
        val fromKeystore = VaultKeys.newVaultKey()

        val a = VaultKeys.wrap(fromPassphrase, vaultKey)
        val b = VaultKeys.wrap(fromKeystore, vaultKey)
        assertEquals(vaultKey.copyBytes().toHex(), VaultKeys.unwrap(fromPassphrase, a)?.copyBytes()?.toHex())
        assertEquals(vaultKey.copyBytes().toHex(), VaultKeys.unwrap(fromKeystore, b)?.copyBytes()?.toHex())
        assertNull(VaultKeys.unwrap(fromPassphrase, b), "one path unwrapping the other's blob")
    }

    /**
     * The wrapping is bound to its purpose, so a blob sealed under the same key for anything
     * else cannot be read back as a vault key.
     */
    @Test
    fun `a blob sealed without the wrap context does not unwrap`() {
        val kek = VaultKeys.newVaultKey()
        val nonce = ByteArray(AesGcm.NonceSize) { 7 }
        val forged = nonce + AesGcm.seal(kek, nonce, VaultKeys.newVaultKey())
        assertEquals(VaultKeys.WrappedSize, forged.size)
        assertNull(VaultKeys.unwrap(kek, forged))
    }

    @Test
    fun `a blob of the wrong size is rejected rather than decrypted`() {
        val kek = VaultKeys.newVaultKey()
        val wrapped = VaultKeys.wrap(kek, VaultKeys.newVaultKey())
        assertNull(VaultKeys.unwrap(kek, wrapped.copyOf(wrapped.size - 1)))
        assertNull(VaultKeys.unwrap(kek, wrapped + 0.toByte()))
        assertNull(VaultKeys.unwrap(kek, ByteArray(0)))
    }
}
