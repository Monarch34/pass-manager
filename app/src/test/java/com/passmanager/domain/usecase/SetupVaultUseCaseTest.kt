package com.passmanager.domain.usecase

import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.repository.MetadataRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupVaultUseCaseTest {

    @Test
    fun `saves metadata after key derivation and encryption`() = runTest {
        val kdfProvider = mockk<com.passmanager.crypto.kdf.KdfProvider>()
        val cipher = mockk<com.passmanager.crypto.cipher.AesGcmCipher>()
        val metadataRepository = mockk<com.passmanager.domain.repository.MetadataRepository>(relaxed = true)

        coEvery { kdfProvider.deriveKey(any(), any(), any()) } returns ByteArray(32)
        coEvery { cipher.encrypt(any(), any()) } returns com.passmanager.crypto.model.EncryptedData(
            ciphertext = ByteArray(32),
            iv = ByteArray(12)
        )

        val useCase = SetupVaultUseCase(kdfProvider, cipher, metadataRepository)
        useCase("password123".toCharArray())

        val metadataSlot = slot<VaultMetadata>()
        coVerify { metadataRepository.save(capture(metadataSlot)) }
        val saved = metadataSlot.captured
        assertEquals(1, saved.currentKeyVersion)
        assertTrue(saved.kdfSalt.isNotEmpty())
        assertTrue(saved.wrappedVaultKey.ciphertext.isNotEmpty())
        assertTrue(saved.wrappedVaultKey.iv.isNotEmpty())
    }
}
