package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.exception.DeviceKeyLostException
import com.passmanager.domain.exception.DeviceKeyUnavailableException
import com.passmanager.domain.exception.WrongPassphraseException
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.model.VaultWrapVersion
import com.passmanager.test.FakePepperPort
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The layering itself, and the three-way split between "wrong passphrase", "try again later" and
 * "this vault is gone". Getting that split wrong is the difference between a user retyping their
 * passphrase and a user erasing an intact vault.
 */
class VaultKeyWrapperTest {

    private val cipher = AesGcmCipher()
    private val pepper = FakePepperPort()
    private val wrapper = VaultKeyWrapper(cipher, pepper)

    private val kek = ByteArray(32) { 0x42 }
    private val otherKek = ByteArray(32) { 0x43 }
    private val vaultKey = ByteArray(32) { 0x11 }

    private fun metadata(wrapped: WrappedVaultKey) = VaultMetadata(
        currentKeyVersion = 1,
        wrappedVaultKey = wrapped.onDisk,
        kdfSalt = ByteArray(16),
        kdfParams = KdfParams(),
        biometricEnabled = false,
        biometricWrappedKey = null,
        wrapVersion = wrapped.wrapVersion,
        pepperIv = wrapped.pepperIv
    )

    // ── Layering ─────────────────────────────────────

    @Test
    fun `passphrase-only wrapping round-trips and stores no pepper iv`() {
        val wrapped = wrapper.wrap(vaultKey.copyOf(), kek, deviceBound = false)

        assertEquals(VaultWrapVersion.PASSPHRASE_ONLY, wrapped.wrapVersion)
        assertNull(wrapped.pepperIv)
        assertArrayEquals(vaultKey, wrapper.unwrap(metadata(wrapped), kek))
    }

    @Test
    fun `device-bound wrapping round-trips and keeps the inner iv on disk`() {
        val inner = cipher.encrypt(vaultKey.copyOf(), kek)
        val wrapped = wrapper.wrap(vaultKey.copyOf(), kek, deviceBound = true)

        assertEquals(VaultWrapVersion.DEVICE_BOUND, wrapped.wrapVersion)
        assertNotNull(wrapped.pepperIv)
        // The stored IV is the inner one, not the Keystore's; the two must not be confused.
        assertEquals(inner.iv.size, wrapped.onDisk.iv.size)
        assertFalse(wrapped.onDisk.iv.contentEquals(wrapped.pepperIv!!))
        assertArrayEquals(vaultKey, wrapper.unwrap(metadata(wrapped), kek))
    }

    @Test
    fun `the outer layer really is on the outside`() {
        val wrapped = wrapper.wrap(vaultKey.copyOf(), kek, deviceBound = true)

        // Peeling the Keystore layer must yield the inner ciphertext, which the KEK then opens.
        // If the order were reversed this would hand back the vault key with no passphrase.
        val innerCiphertext = pepper.open(
            com.passmanager.crypto.model.EncryptedData(
                ciphertext = wrapped.onDisk.ciphertext,
                iv = wrapped.pepperIv!!
            )
        )
        assertFalse(
            "the inner layer must still be encrypted under the KEK",
            innerCiphertext.contentEquals(vaultKey)
        )
        assertArrayEquals(
            vaultKey,
            cipher.decrypt(
                com.passmanager.crypto.model.EncryptedData(innerCiphertext, wrapped.onDisk.iv),
                kek
            )
        )
    }

    @Test
    fun `sealing an existing vault leaves the inner layer untouched`() {
        val v1 = wrapper.wrap(vaultKey.copyOf(), kek, deviceBound = false)
        val before = metadata(v1)

        val upgraded = wrapper.sealExisting(before)

        // No re-derivation, no new inner IV: the upgrade only adds a layer around what was there.
        assertArrayEquals(before.wrappedVaultKey.iv, upgraded.onDisk.iv)
        assertEquals(VaultWrapVersion.DEVICE_BOUND, upgraded.wrapVersion)
        assertArrayEquals(vaultKey, wrapper.unwrap(metadata(upgraded), kek))
    }

    @Test
    fun `sealing an already device-bound vault is refused`() {
        val v2 = metadata(wrapper.wrap(vaultKey.copyOf(), kek, deviceBound = true))

        assertThrows(IllegalStateException::class.java) { wrapper.sealExisting(v2) }
    }

    // ── Failure classification ───────────────────────

    @Test
    fun `a wrong kek is a wrong passphrase on both wrap versions`() {
        for (deviceBound in listOf(false, true)) {
            val stored = metadata(wrapper.wrap(vaultKey.copyOf(), kek, deviceBound))

            assertThrows(
                "deviceBound=$deviceBound",
                WrongPassphraseException::class.java
            ) { wrapper.unwrap(stored, otherKek) }
        }
    }

    @Test
    fun `a missing device key is permanent loss and is never softened into a wrong passphrase`() {
        val stored = metadata(wrapper.wrap(vaultKey.copyOf(), kek, deviceBound = true))
        pepper.keyPresent = false

        assertThrows(DeviceKeyLostException::class.java) { wrapper.unwrap(stored, kek) }
    }

    @Test
    fun `a transient keystore failure stays transient even with a wrong passphrase`() {
        val stored = metadata(wrapper.wrap(vaultKey.copyOf(), kek, deviceBound = true))
        pepper.failWith = DeviceKeyUnavailableException()

        // The passphrase is wrong here too, but the device fault is what the user must act on:
        // reporting a typo would send them in circles while the real cause is a busy keymaster.
        assertThrows(DeviceKeyUnavailableException::class.java) { wrapper.unwrap(stored, otherKek) }
    }

    // ── Tampered wrap_version ────────────────────────

    @Test
    fun `a device-bound row whose column claims v1 still opens`() {
        val stored = metadata(wrapper.wrap(vaultKey.copyOf(), kek, deviceBound = true))
            .copy(wrapVersion = VaultWrapVersion.PASSPHRASE_ONLY)

        assertArrayEquals(vaultKey, wrapper.unwrap(stored, kek))
    }

    @Test
    fun `a passphrase-only row whose column claims v2 still opens`() {
        val stored = metadata(wrapper.wrap(vaultKey.copyOf(), kek, deviceBound = false))
            .copy(wrapVersion = VaultWrapVersion.DEVICE_BOUND)

        assertArrayEquals(vaultKey, wrapper.unwrap(stored, kek))
    }

    @Test
    fun `a fabricated pepper iv on a v1 row falls back rather than failing outright`() {
        // Bytes that were never sealed: the outer open fails its tag check, and the fallback finds
        // the real single-layer wrapping underneath.
        val real = wrapper.wrap(vaultKey.copyOf(), kek, deviceBound = false)
        val forged = metadata(real).copy(
            wrapVersion = VaultWrapVersion.DEVICE_BOUND,
            pepperIv = ByteArray(12) { 0x09 }
        )

        assertArrayEquals(vaultKey, wrapper.unwrap(forged, kek))
    }

    @Test
    fun `isDeviceBound follows the bytes, not the column`() {
        val v1 = metadata(wrapper.wrap(vaultKey.copyOf(), kek, deviceBound = false))

        assertFalse(v1.copy(wrapVersion = VaultWrapVersion.DEVICE_BOUND).isDeviceBound)
        assertTrue(v1.copy(pepperIv = ByteArray(12)).isDeviceBound)
    }
}
