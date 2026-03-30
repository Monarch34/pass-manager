package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.kdf.KdfProvider
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.port.BiometricLockPort
import com.passmanager.domain.repository.MetadataRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import com.passmanager.domain.exception.WrongPassphraseException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

class ChangePassphraseUseCaseTest {

    private val kdfProvider = mockk<KdfProvider>()
    private val cipher = mockk<AesGcmCipher>()
    private val metadataRepository = mockk<MetadataRepository>(relaxed = true)
    private val biometricLockPort = mockk<BiometricLockPort>(relaxed = true)

    private val useCase = ChangePassphraseUseCase(
        kdfProvider, cipher, metadataRepository, biometricLockPort
    )

    private val fakeMetadata = VaultMetadata(
        currentKeyVersion = 1,
        wrappedVaultKey = EncryptedData(ByteArray(32), ByteArray(12)),
        kdfSalt = ByteArray(16) { 0x01 },
        kdfParams = KdfParams(),
        biometricEnabled = false,
        biometricWrappedKey = null
    )

    @Test
    fun `successful change updates metadata with new salt and wrapped key`() = runTest {
        val vaultKey = ByteArray(32) { 0x55 }
        val newWrappedKey = EncryptedData(ByteArray(32) { 0x66 }, ByteArray(12) { 0x77 })
        coEvery { metadataRepository.get() } returns fakeMetadata
        every { kdfProvider.deriveKey(any(), any(), any()) } returns ByteArray(32)
        every { cipher.decrypt(any(), any()) } returns vaultKey
        every { cipher.encrypt(any(), any()) } returns newWrappedKey

        val metadataSlot = slot<VaultMetadata>()
        coEvery { metadataRepository.update(capture(metadataSlot)) } returns Unit

        useCase("current".toCharArray(), "newPass".toCharArray())

        val saved = metadataSlot.captured
        // Salt should be replaced (new 16-byte random salt, different from original)
        assertFalse(saved.kdfSalt.contentEquals(fakeMetadata.kdfSalt))
        assertEquals(newWrappedKey, saved.wrappedVaultKey)
    }

    @Test
    fun `wrong current passphrase throws WrongPassphraseException`() = runTest {
        coEvery { metadataRepository.get() } returns fakeMetadata
        every { kdfProvider.deriveKey(any(), any(), any()) } returns ByteArray(32)
        every { cipher.decrypt(any(), any()) } throws AEADBadTagException()

        try {
            useCase("wrong".toCharArray(), "new".toCharArray())
            throw AssertionError("Expected WrongPassphraseException")
        } catch (e: WrongPassphraseException) {
            // expected
        }
    }

    @Test
    fun `biometric is disabled after successful change`() = runTest {
        coEvery { metadataRepository.get() } returns fakeMetadata
        every { kdfProvider.deriveKey(any(), any(), any()) } returns ByteArray(32)
        every { cipher.decrypt(any(), any()) } returns ByteArray(32)
        every { cipher.encrypt(any(), any()) } returns EncryptedData(ByteArray(32), ByteArray(12))

        useCase("current".toCharArray(), "newPass".toCharArray())

        coVerify(exactly = 1) { biometricLockPort.disableIfEnabled() }
    }

    @Test
    fun `throws when vault not initialized`() = runTest {
        coEvery { metadataRepository.get() } returns null

        try {
            useCase("any".toCharArray(), "new".toCharArray())
            throw AssertionError("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `passphrases are zeroed after successful call`() = runTest {
        coEvery { metadataRepository.get() } returns fakeMetadata
        every { kdfProvider.deriveKey(any(), any(), any()) } returns ByteArray(32)
        every { cipher.decrypt(any(), any()) } returns ByteArray(32)
        every { cipher.encrypt(any(), any()) } returns EncryptedData(ByteArray(32), ByteArray(12))

        val current = "current".toCharArray()
        val newPass = "newPass".toCharArray()
        useCase(current, newPass)

        assertTrue(current.all { it == '\u0000' })
        assertTrue(newPass.all { it == '\u0000' })
    }
}
