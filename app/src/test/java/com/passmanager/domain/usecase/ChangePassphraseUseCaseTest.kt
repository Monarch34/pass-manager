package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.kdf.KdfProvider
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.exception.WrongPassphraseException
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.model.VaultWrapVersion
import com.passmanager.domain.port.BiometricLockPort
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.test.FakePepperPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangePassphraseUseCaseTest {

    private val cipher = AesGcmCipher()
    private val pepper = FakePepperPort()
    private val keyWrapper = VaultKeyWrapper(cipher, pepper)
    private val kdfProvider = mockk<KdfProvider>()
    private val metadataRepository = mockk<MetadataRepository>(relaxed = true)
    private val biometricLockPort = mockk<BiometricLockPort>(relaxed = true)

    private val useCase = ChangePassphraseUseCase(
        kdfProvider, keyWrapper, metadataRepository, biometricLockPort
    )

    private val currentKek = ByteArray(32) { 0x42 }
    private val newKek = ByteArray(32) { 0x77 }
    private val vaultKey = ByteArray(32) { 0x55 }

    private fun metadata(deviceBound: Boolean): VaultMetadata {
        val wrapped = keyWrapper.wrap(vaultKey.copyOf(), currentKek, deviceBound)
        return VaultMetadata(
            currentKeyVersion = 1,
            wrappedVaultKey = wrapped.onDisk,
            kdfSalt = ByteArray(16) { 0x01 },
            kdfParams = KdfParams(),
            biometricEnabled = false,
            biometricWrappedKey = null,
            wrapVersion = wrapped.wrapVersion,
            pepperIv = wrapped.pepperIv
        )
    }

    /** First derivation is the current KEK, second the new one — the order the use case uses. */
    private fun stubDerivations() {
        every { kdfProvider.deriveKey(any(), any(), any()) } returnsMany
            listOf(currentKek.copyOf(), newKek.copyOf())
    }

    private suspend fun failureOf(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Throwable) {
        e
    }

    @Test
    fun `successful change re-salts and re-wraps against the new passphrase`() = runTest {
        val before = metadata(deviceBound = false)
        coEvery { metadataRepository.get() } returns before
        stubDerivations()
        val saved = slot<VaultMetadata>()
        coEvery { metadataRepository.update(capture(saved)) } returns Unit

        useCase("current".toCharArray(), "newPass".toCharArray())

        assertFalse(saved.captured.kdfSalt.contentEquals(before.kdfSalt))
        assertArrayEquals(vaultKey, keyWrapper.unwrap(saved.captured, newKek))
    }

    @Test
    fun `a device-bound vault stays device-bound`() = runTest {
        coEvery { metadataRepository.get() } returns metadata(deviceBound = true)
        stubDerivations()
        val saved = slot<VaultMetadata>()
        coEvery { metadataRepository.update(capture(saved)) } returns Unit

        useCase("current".toCharArray(), "newPass".toCharArray())

        // Silently dropping the outer layer would downgrade the vault's protection as a side
        // effect of changing a passphrase.
        assertEquals(VaultWrapVersion.DEVICE_BOUND, saved.captured.wrapVersion)
        assertNotNull(saved.captured.pepperIv)
        assertArrayEquals(vaultKey, keyWrapper.unwrap(saved.captured, newKek))
    }

    @Test
    fun `the kdf parameters are refreshed to current defaults`() = runTest {
        val stale = metadata(deviceBound = false).copy(
            kdfParams = KdfParams(memory = 65536, iterations = 10, parallelism = 4, hashLength = 32)
        )
        coEvery { metadataRepository.get() } returns stale
        stubDerivations()
        val saved = slot<VaultMetadata>()
        coEvery { metadataRepository.update(capture(saved)) } returns Unit

        useCase("current".toCharArray(), "newPass".toCharArray())

        assertEquals(KdfParams(), saved.captured.kdfParams)
    }

    @Test
    fun `wrong current passphrase throws WrongPassphraseException`() = runTest {
        coEvery { metadataRepository.get() } returns metadata(deviceBound = false)
        every { kdfProvider.deriveKey(any(), any(), any()) } returns ByteArray(32) { 0x09 }

        val failure = failureOf { useCase("wrong".toCharArray(), "new".toCharArray()) }

        assertEquals(WrongPassphraseException::class.java, failure?.javaClass)
    }

    @Test
    fun `biometric is disabled after a successful change`() = runTest {
        coEvery { metadataRepository.get() } returns metadata(deviceBound = false)
        stubDerivations()

        useCase("current".toCharArray(), "newPass".toCharArray())

        coVerify(exactly = 1) { biometricLockPort.disableIfEnabled() }
    }

    @Test
    fun `throws when vault not initialized`() = runTest {
        coEvery { metadataRepository.get() } returns null

        val failure = failureOf { useCase("any".toCharArray(), "new".toCharArray()) }

        assertEquals(IllegalStateException::class.java, failure?.javaClass)
    }

    @Test
    fun `passphrases are zeroed after a successful call`() = runTest {
        coEvery { metadataRepository.get() } returns metadata(deviceBound = false)
        stubDerivations()

        val current = "current".toCharArray()
        val newPass = "newPass".toCharArray()
        useCase(current, newPass)

        assertTrue(current.all { it == '\u0000' })
        assertTrue(newPass.all { it == '\u0000' })
    }
}
