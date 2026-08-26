package com.passmanager.security

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.keystore.AndroidKeystoreManager
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.model.LockState
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.model.VaultWrapVersion
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.security.biometric.BiometricHelper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.crypto.Cipher

/**
 * Regression cover for A2: adding the device-bound outer layer must not change the biometric path.
 *
 * Biometric unlock has always wrapped the **raw vault key** with its own auth-bound Keystore key,
 * which makes it device-bound already, by a different mechanism. If the pepper layer ever leaked
 * into this path the two would be entangled: a Keystore hiccup in one would break the other, and
 * disabling biometrics could take the passphrase path down with it.
 */
class BiometricKeyManagerTest {

    private val cipher = AesGcmCipher()
    private val metadataRepository = mockk<MetadataRepository>(relaxed = true)
    private val keystoreManager = mockk<AndroidKeystoreManager>(relaxed = true)
    private val vaultLockManager = mockk<VaultLockManager>(relaxed = true)
    private val sessionManager = mockk<SessionManager>(relaxed = true)
    private val biometricHelper = mockk<BiometricHelper>(relaxed = true)

    private val manager = BiometricKeyManager(
        metadataRepository, cipher, keystoreManager, vaultLockManager, sessionManager, biometricHelper
    )

    private val vaultKey = ByteArray(32) { 0x11 }

    /** A device-bound vault: the wrapped key on disk is nothing like the raw vault key. */
    private fun deviceBoundMetadata(biometricWrapped: EncryptedData? = null) = VaultMetadata(
        currentKeyVersion = 1,
        wrappedVaultKey = EncryptedData(ByteArray(48) { 0x7F }, ByteArray(12) { 0x01 }),
        kdfSalt = ByteArray(16),
        kdfParams = KdfParams(),
        biometricEnabled = biometricWrapped != null,
        biometricWrappedKey = biometricWrapped,
        wrapVersion = VaultWrapVersion.DEVICE_BOUND,
        pepperIv = ByteArray(12) { 0x02 }
    )

    @Test
    fun `enrollment wraps the raw vault key, not the device-bound blob`() = runTest {
        val before = deviceBoundMetadata()
        coEvery { metadataRepository.get() } returns before
        every { vaultLockManager.lockState } returns MutableStateFlow(LockState.Unlocked)
        every { vaultLockManager.requireUnlockedKey() } returns vaultKey.copyOf()

        // Stands in for the Keystore's authenticated cipher; a plain AES-GCM cipher is enough to
        // observe *what* got wrapped, which is the point of this test.
        val biometricKey = ByteArray(32) { 0x33 }
        val authenticatedCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.ENCRYPT_MODE,
                javax.crypto.spec.SecretKeySpec(biometricKey, "AES")
            )
        }

        val saved = slot<VaultMetadata>()
        coEvery { metadataRepository.update(capture(saved)) } returns Unit

        manager.completeEnrollment(authenticatedCipher)

        val wrapped = requireNotNull(saved.captured.biometricWrappedKey)
        assertArrayEquals(vaultKey, cipher.decrypt(wrapped, biometricKey))

        // The passphrase-side wrapping is untouched by enrolling a fingerprint.
        assertEquals(before.wrappedVaultKey, saved.captured.wrappedVaultKey)
        assertEquals(VaultWrapVersion.DEVICE_BOUND, saved.captured.wrapVersion)
        assertArrayEquals(before.pepperIv, saved.captured.pepperIv)
    }

    @Test
    fun `biometric unlock hands over the raw vault key`() = runTest {
        val biometricKey = ByteArray(32) { 0x33 }
        val wrapped = cipher.encrypt(vaultKey.copyOf(), biometricKey)
        coEvery { metadataRepository.get() } returns deviceBoundMetadata(wrapped)

        val authenticatedCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.DECRYPT_MODE,
                javax.crypto.spec.SecretKeySpec(biometricKey, "AES"),
                javax.crypto.spec.GCMParameterSpec(128, wrapped.iv)
            )
        }

        val unlocked = slot<ByteArray>()
        every { vaultLockManager.unlock(capture(unlocked)) } returns Unit

        manager.unlock(authenticatedCipher)

        // No pepper layer anywhere on this path: what comes out is the vault key itself.
        assertArrayEquals(vaultKey, unlocked.captured)
        verify { sessionManager.recordSuccessfulUnlock() }
    }

    @Test
    fun `disabling biometrics leaves the passphrase wrapping alone`() = runTest {
        val before = deviceBoundMetadata(EncryptedData(ByteArray(48), ByteArray(12)))
        coEvery { metadataRepository.get() } returns before
        val saved = slot<VaultMetadata>()
        coEvery { metadataRepository.update(capture(saved)) } returns Unit

        manager.disable()

        assertEquals(false, saved.captured.biometricEnabled)
        assertEquals(null, saved.captured.biometricWrappedKey)
        // Only the biometric Keystore alias is removed; the pepper key is a different alias and
        // must survive, or turning biometrics off would lock the vault permanently.
        verify(exactly = 1) { keystoreManager.deleteBiometricKey() }
        assertEquals(VaultWrapVersion.DEVICE_BOUND, saved.captured.wrapVersion)
        assertArrayEquals(before.pepperIv, saved.captured.pepperIv)
        assertEquals(before.wrappedVaultKey, saved.captured.wrappedVaultKey)
    }
}
