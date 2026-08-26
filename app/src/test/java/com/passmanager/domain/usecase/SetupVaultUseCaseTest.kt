package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.kdf.KdfProvider
import com.passmanager.domain.exception.DeviceKeyUnavailableException
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.model.VaultWrapVersion
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.test.FakePepperPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupVaultUseCaseTest {

    private val cipher = AesGcmCipher()
    private val pepper = FakePepperPort()
    private val keyWrapper = VaultKeyWrapper(cipher, pepper)
    private val kdfProvider = mockk<KdfProvider>()
    private val metadataRepository = mockk<MetadataRepository>(relaxed = true)

    private val useCase = SetupVaultUseCase(kdfProvider, keyWrapper, metadataRepository)

    private val kek = ByteArray(32) { 0x42 }

    private suspend fun setupAndCapture(passphrase: String): VaultMetadata {
        every { kdfProvider.deriveKey(any(), any(), any()) } returns kek.copyOf()
        val saved = slot<VaultMetadata>()
        coEvery { metadataRepository.save(capture(saved)) } returns Unit
        useCase(passphrase.toCharArray())
        return saved.captured
    }

    @Test
    fun `a new vault is device-bound from the start`() = runTest {
        val saved = setupAndCapture("password123")

        assertEquals(1, saved.currentKeyVersion)
        assertEquals(VaultWrapVersion.DEVICE_BOUND, saved.wrapVersion)
        assertNotNull(saved.pepperIv)
        assertTrue(saved.kdfSalt.isNotEmpty())
        assertTrue(saved.wrappedVaultKey.ciphertext.isNotEmpty())
        assertTrue(saved.wrappedVaultKey.iv.isNotEmpty())
    }

    @Test
    fun `the saved key really does unwrap with the derived key`() = runTest {
        val saved = setupAndCapture("password123")

        // Both layers, in order. If the wrapping order were ever reversed, this is what fails.
        val vaultKey = keyWrapper.unwrap(saved, kek)

        assertEquals(32, vaultKey.size)
        assertTrue("a random vault key must not be all zeroes", vaultKey.any { it != 0.toByte() })
    }

    @Test
    fun `a keystore that refuses still produces a usable vault`() = runTest {
        // Dead-ending onboarding on this device would be worse than a passphrase-only vault that
        // Settings can upgrade later, so setup falls back instead of failing.
        pepper.failWith = DeviceKeyUnavailableException()

        val saved = setupAndCapture("password123")

        assertEquals(VaultWrapVersion.PASSPHRASE_ONLY, saved.wrapVersion)
        assertNull(saved.pepperIv)
        assertEquals(32, keyWrapper.unwrap(saved, kek).size)
    }

    @Test
    fun `every vault gets its own salt`() = runTest {
        val first = setupAndCapture("password123")
        val second = setupAndCapture("password123")

        assertFalse(first.kdfSalt.contentEquals(second.kdfSalt))
        coVerify(exactly = 2) { metadataRepository.save(any()) }
    }
}
