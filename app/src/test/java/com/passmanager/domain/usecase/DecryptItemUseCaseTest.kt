package com.passmanager.domain.usecase

import com.passmanager.domain.model.VaultItem
import com.passmanager.security.VaultLockManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DecryptItemUseCaseTest {

    @Test
    fun `throws when vault is locked`() = runTest {
        val cipher = mockk<com.passmanager.crypto.cipher.AesGcmCipher>(relaxed = true)
        val vaultLockManager = mockk<VaultLockManager>()
        every { vaultLockManager.requireUnlockedKey() } throws IllegalStateException("Vault is locked")

        val useCase = DecryptItemUseCase(cipher, vaultLockManager)
        val item = VaultItem(
            id = "1",
            encryptedData = com.passmanager.crypto.model.EncryptedData(ByteArray(16), ByteArray(12)),
            keyVersion = 1,
            createdAt = 0L,
            updatedAt = 0L
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
        val vaultLockManager = mockk<VaultLockManager>()
        val vaultKey = ByteArray(32) { it.toByte() }
        every { vaultLockManager.requireUnlockedKey() } returns vaultKey

        val decryptedJson = """{"id":"1","title":"Test","username":"user","password":"pass","notes":""}"""
        every { cipher.decrypt(any(), any()) } returns decryptedJson.encodeToByteArray()

        val useCase = DecryptItemUseCase(cipher, vaultLockManager)
        val item = VaultItem(
            id = "1",
            encryptedData = com.passmanager.crypto.model.EncryptedData(ByteArray(16), ByteArray(12)),
            keyVersion = 1,
            createdAt = 0L,
            updatedAt = 0L
        )

        val result = useCase(item)
        assertEquals("Test", result.title)
        assertEquals("user", result.username)
        assertEquals("pass", result.password)
        assertEquals("", result.notes)
    }
}
