package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.kdf.KdfProvider
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.port.UnlockSessionRecorder
import com.passmanager.domain.port.VaultKeyProvider
import com.passmanager.domain.repository.MetadataRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import com.passmanager.domain.exception.WrongPassphraseException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import javax.crypto.AEADBadTagException

class UnlockWithPassphraseUseCaseTest {

    private val metadataRepository = mockk<MetadataRepository>()
    private val kdfProvider = mockk<KdfProvider>()
    private val cipher = mockk<AesGcmCipher>()
    private val vaultKeyProvider = mockk<VaultKeyProvider>(relaxed = true)
    private val sessionRecorder = mockk<UnlockSessionRecorder>(relaxed = true)

    private val useCase = UnlockWithPassphraseUseCase(
        metadataRepository, kdfProvider, cipher, vaultKeyProvider, sessionRecorder
    )

    private val fakeMetadata = VaultMetadata(
        currentKeyVersion = 1,
        wrappedVaultKey = EncryptedData(ByteArray(32), ByteArray(12)),
        kdfSalt = ByteArray(16),
        kdfParams = KdfParams(),
        biometricEnabled = false,
        biometricWrappedKey = null
    )

    @Test
    fun `successful unlock derives key records session and unlocks key provider`() = runTest {
        val derivedKey = ByteArray(32) { 0x42 }
        val vaultKey = ByteArray(32) { 0x11 }

        coEvery { metadataRepository.get() } returns fakeMetadata
        every { kdfProvider.deriveKey(any(), any(), any()) } returns derivedKey
        every { cipher.decrypt(any(), any()) } returns vaultKey

        useCase("correct".toCharArray())

        verify { sessionRecorder.recordSuccessfulUnlock() }
        verify { vaultKeyProvider.unlock(vaultKey) }
    }

    @Test
    fun `wrong passphrase throws WrongPassphraseException`() = runTest {
        coEvery { metadataRepository.get() } returns fakeMetadata
        every { kdfProvider.deriveKey(any(), any(), any()) } returns ByteArray(32)
        every { cipher.decrypt(any(), any()) } throws AEADBadTagException()

        try {
            useCase("wrong".toCharArray())
            throw AssertionError("Expected WrongPassphraseException")
        } catch (e: WrongPassphraseException) {
            // expected
        }
    }

    @Test
    fun `throws when metadata not found`() = runTest {
        coEvery { metadataRepository.get() } returns null

        try {
            useCase("any".toCharArray())
            throw AssertionError("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `passphrase array is zeroed after successful call`() = runTest {
        coEvery { metadataRepository.get() } returns fakeMetadata
        every { kdfProvider.deriveKey(any(), any(), any()) } returns ByteArray(32)
        every { cipher.decrypt(any(), any()) } returns ByteArray(32)

        val passphrase = "myPassword".toCharArray()
        useCase(passphrase)

        passphrase.forEach { char ->
            assertEquals('\u0000', char)
        }
    }

    @Test
    fun `passphrase array is zeroed even after wrong passphrase`() = runTest {
        coEvery { metadataRepository.get() } returns fakeMetadata
        every { kdfProvider.deriveKey(any(), any(), any()) } returns ByteArray(32)
        every { cipher.decrypt(any(), any()) } throws AEADBadTagException()

        val passphrase = "wrong".toCharArray()
        try {
            useCase(passphrase)
        } catch (_: WrongPassphraseException) {}

        passphrase.forEach { char ->
            assertEquals('\u0000', char)
        }
    }
}
