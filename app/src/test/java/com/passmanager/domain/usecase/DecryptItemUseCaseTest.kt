package com.passmanager.domain.usecase

import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.ItemPayload
import com.passmanager.domain.model.VaultItem
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.domain.port.VaultKeyProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DecryptItemUseCaseTest {

    private val metadataRepository = mockk<MetadataRepository>()

    init {
        coEvery { metadataRepository.get() } returns mockk<VaultMetadata> {
            every { currentKeyVersion } returns 1
        }
    }

    @Test
    fun `throws when vault is locked`() = runTest {
        val cipher = mockk<com.passmanager.crypto.cipher.AesGcmCipher>(relaxed = true)
        val vaultLockManager = mockk<VaultKeyProvider>()
        every { vaultLockManager.requireUnlockedKey() } throws IllegalStateException("Vault is locked")

        val useCase = DecryptItemUseCase(cipher, vaultLockManager, metadataRepository)
        val item = VaultItem(
            id = "1",
            encryptedData = com.passmanager.crypto.model.EncryptedData(ByteArray(16), ByteArray(12)),
            keyVersion = 1,
            createdAt = 0L,
            updatedAt = 0L,
            category = ItemCategory.LOGIN
        )

        try {
            useCase(item)
            throw AssertionError("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `decrypts successfully when vault is unlocked`() = runTest {
        val cipher = mockk<com.passmanager.crypto.cipher.AesGcmCipher>()
        val vaultLockManager = mockk<VaultKeyProvider>()
        val vaultKey = ByteArray(32) { it.toByte() }
        every { vaultLockManager.requireUnlockedKey() } returns vaultKey

        val decryptedJson = """{"id":"1","title":"Test","username":"user","password":"pass","notes":""}"""
        every { cipher.decrypt(any(), any()) } returns decryptedJson.encodeToByteArray()

        val useCase = DecryptItemUseCase(cipher, vaultLockManager, metadataRepository)
        val item = VaultItem(
            id = "1",
            encryptedData = com.passmanager.crypto.model.EncryptedData(ByteArray(16), ByteArray(12)),
            keyVersion = 1,
            createdAt = 0L,
            updatedAt = 0L,
            category = ItemCategory.LOGIN
        )

        val result = useCase(item)
        val login = result as ItemPayload.Login
        assertEquals("Test", login.title)
        assertEquals("user", login.username)
        assertEquals("pass", login.password)
        assertEquals("", login.notes)
    }
}
