package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.kdf.KdfProvider
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.exception.DeviceKeyLostException
import com.passmanager.domain.exception.DeviceKeyUnavailableException
import com.passmanager.domain.exception.UnlockThrottledException
import com.passmanager.domain.exception.WrongPassphraseException
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.model.VaultWrapVersion
import com.passmanager.domain.port.UnlockSessionRecorder
import com.passmanager.domain.port.VaultKeyProvider
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.test.FakePepperPort
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.coVerify
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real AES-GCM and a real [VaultKeyWrapper] over a fake Keystore: what is under test is the
 * layering and how its failures are classified, and mocking the cipher would assert neither.
 */
class UnlockWithPassphraseUseCaseTest {

    private val cipher = AesGcmCipher()
    private val pepper = FakePepperPort()
    private val keyWrapper = VaultKeyWrapper(cipher, pepper)

    private val metadataRepository = mockk<MetadataRepository>()
    private val kdfProvider = mockk<KdfProvider>()
    private val vaultKeyProvider = mockk<VaultKeyProvider>(relaxed = true)
    private val sessionRecorder = mockk<UnlockSessionRecorder>(relaxed = true)

    private val throttle = mockk<UnlockThrottle>(relaxed = true).also {
        coEvery { it.remainingLockoutMs() } returns 0L
    }

    private val useCase = UnlockWithPassphraseUseCase(
        metadataRepository, kdfProvider, keyWrapper, vaultKeyProvider, sessionRecorder, throttle
    )

    private val correctKek = ByteArray(32) { 0x42 }
    private val wrongKek = ByteArray(32) { 0x43 }
    private val vaultKey = ByteArray(32) { 0x11 }

    private fun metadata(deviceBound: Boolean): VaultMetadata {
        val wrapped = keyWrapper.wrap(vaultKey.copyOf(), correctKek, deviceBound)
        return VaultMetadata(
            currentKeyVersion = 1,
            wrappedVaultKey = wrapped.onDisk,
            kdfSalt = ByteArray(16),
            kdfParams = KdfParams(),
            biometricEnabled = false,
            biometricWrappedKey = null,
            wrapVersion = wrapped.wrapVersion,
            pepperIv = wrapped.pepperIv
        )
    }

    private suspend fun failureOf(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Throwable) {
        e
    }

    @Test
    fun `passphrase-only vault unlocks and hands over the vault key`() = runTest {
        coEvery { metadataRepository.get() } returns metadata(deviceBound = false)
        every { kdfProvider.deriveKey(any(), any(), any()) } returns correctKek.copyOf()
        val captured = slot<ByteArray>()
        every { vaultKeyProvider.unlock(capture(captured)) } returns Unit

        useCase("correct".toCharArray())

        verify { sessionRecorder.recordSuccessfulUnlock() }
        assertArrayEquals(vaultKey, captured.captured)
    }

    @Test
    fun `device-bound vault peels both layers`() = runTest {
        val stored = metadata(deviceBound = true)
        assertEquals(VaultWrapVersion.DEVICE_BOUND, stored.wrapVersion)
        coEvery { metadataRepository.get() } returns stored
        every { kdfProvider.deriveKey(any(), any(), any()) } returns correctKek.copyOf()
        val captured = slot<ByteArray>()
        every { vaultKeyProvider.unlock(capture(captured)) } returns Unit

        useCase("correct".toCharArray())

        assertArrayEquals(vaultKey, captured.captured)
    }

    @Test
    fun `wrong passphrase is reported as such on both wrap versions`() = runTest {
        every { kdfProvider.deriveKey(any(), any(), any()) } returns wrongKek.copyOf()

        for (deviceBound in listOf(false, true)) {
            coEvery { metadataRepository.get() } returns metadata(deviceBound)

            val failure = failureOf { useCase("wrong".toCharArray()) }

            assertEquals(
                "deviceBound=$deviceBound",
                WrongPassphraseException::class.java,
                failure?.javaClass
            )
        }
    }

    @Test
    fun `a missing device key is permanent loss, not a wrong passphrase`() = runTest {
        coEvery { metadataRepository.get() } returns metadata(deviceBound = true)
        every { kdfProvider.deriveKey(any(), any(), any()) } returns correctKek.copyOf()
        pepper.keyPresent = false

        val failure = failureOf { useCase("correct".toCharArray()) }

        // Reporting a wrong passphrase here would send the user hunting for a typo that does not
        // exist. This has to reach the recovery screen instead.
        assertEquals(DeviceKeyLostException::class.java, failure?.javaClass)
    }

    @Test
    fun `a transient keystore fault asks for a retry, never for a reset`() = runTest {
        coEvery { metadataRepository.get() } returns metadata(deviceBound = true)
        every { kdfProvider.deriveKey(any(), any(), any()) } returns correctKek.copyOf()
        pepper.failWith = DeviceKeyUnavailableException()

        val failure = failureOf { useCase("correct".toCharArray()) }

        assertEquals(DeviceKeyUnavailableException::class.java, failure?.javaClass)
    }

    @Test
    fun `a wrap_version that lies about the bytes still unlocks`() = runTest {
        // Column downgraded on a genuinely device-bound row: pepper_iv is the honest signal.
        coEvery { metadataRepository.get() } returns
            metadata(deviceBound = true).copy(wrapVersion = VaultWrapVersion.PASSPHRASE_ONLY)
        every { kdfProvider.deriveKey(any(), any(), any()) } returns correctKek.copyOf()
        val captured = slot<ByteArray>()
        every { vaultKeyProvider.unlock(capture(captured)) } returns Unit

        useCase("correct".toCharArray())

        assertArrayEquals(vaultKey, captured.captured)
    }

    @Test
    fun `a v2 claim with no pepper iv falls back to the passphrase-only path`() = runTest {
        coEvery { metadataRepository.get() } returns
            metadata(deviceBound = false).copy(wrapVersion = VaultWrapVersion.DEVICE_BOUND)
        every { kdfProvider.deriveKey(any(), any(), any()) } returns correctKek.copyOf()
        val captured = slot<ByteArray>()
        every { vaultKeyProvider.unlock(capture(captured)) } returns Unit

        useCase("correct".toCharArray())

        assertArrayEquals(vaultKey, captured.captured)
    }

    @Test
    fun `throws when metadata not found`() = runTest {
        coEvery { metadataRepository.get() } returns null

        val failure = failureOf { useCase("any".toCharArray()) }

        assertEquals(IllegalStateException::class.java, failure?.javaClass)
    }

    @Test
    fun `passphrase array is zeroed after a successful call`() = runTest {
        coEvery { metadataRepository.get() } returns metadata(deviceBound = true)
        every { kdfProvider.deriveKey(any(), any(), any()) } returns correctKek.copyOf()

        val passphrase = "myPassword".toCharArray()
        useCase(passphrase)

        passphrase.forEach { assertEquals('\u0000', it) }
    }

    @Test
    fun `passphrase array is zeroed even after a wrong passphrase`() = runTest {
        coEvery { metadataRepository.get() } returns metadata(deviceBound = false)
        every { kdfProvider.deriveKey(any(), any(), any()) } returns wrongKek.copyOf()

        val passphrase = "wrong".toCharArray()
        failureOf { useCase(passphrase) }

        passphrase.forEach { assertEquals('\u0000', it) }
    }

    // -- Back-pressure ordering -----------------------

    @Test
    fun `the attempt is booked before the derivation starts`() = runTest {
        coEvery { metadataRepository.get() } returns metadata(deviceBound = false)
        var booked = false
        var bookedWhenDeriving = false
        coEvery { throttle.registerAttempt() } coAnswers { booked = true; 1 }
        every { kdfProvider.deriveKey(any(), any(), any()) } answers {
            bookedWhenDeriving = booked
            correctKek.copyOf()
        }

        useCase("correct".toCharArray())

        // Argon2 takes about a second. A counter bumped after it would hand every attempt fired
        // into that window the same pre-increment count, so a burst of guesses would cost one
        // tick between them instead of one each.
        assertTrue("the counter must be written before any derivation", bookedWhenDeriving)
    }

    @Test
    fun `a throttled attempt never reaches the kdf and books nothing`() = runTest {
        coEvery { throttle.remainingLockoutMs() } returns 5_000L
        coEvery { metadataRepository.get() } returns metadata(deviceBound = false)

        val failure = failureOf { useCase("correct".toCharArray()) }

        assertEquals(UnlockThrottledException::class.java, failure?.javaClass)
        assertEquals(5_000L, (failure as UnlockThrottledException).remainingMs)
        verify(exactly = 0) { kdfProvider.deriveKey(any(), any(), any()) }
        // A refused attempt is not a failed one; counting it would let the penalty feed itself.
        coVerify(exactly = 0) { throttle.registerAttempt() }
    }

    @Test
    fun `a refused attempt still zeroes the passphrase`() = runTest {
        coEvery { throttle.remainingLockoutMs() } returns 5_000L
        coEvery { metadataRepository.get() } returns metadata(deviceBound = false)

        val passphrase = "correct".toCharArray()
        failureOf { useCase(passphrase) }

        passphrase.forEach { assertEquals('\u0000', it) }
    }

    @Test
    fun `a successful unlock clears the penalty`() = runTest {
        coEvery { metadataRepository.get() } returns metadata(deviceBound = false)
        every { kdfProvider.deriveKey(any(), any(), any()) } returns correctKek.copyOf()

        useCase("correct".toCharArray())

        coVerify(exactly = 1) { throttle.clear() }
    }

    @Test
    fun `a wrong passphrase leaves the booked attempt standing`() = runTest {
        coEvery { metadataRepository.get() } returns metadata(deviceBound = false)
        every { kdfProvider.deriveKey(any(), any(), any()) } returns wrongKek.copyOf()

        failureOf { useCase("wrong".toCharArray()) }

        coVerify(exactly = 1) { throttle.registerAttempt() }
        coVerify(exactly = 0) { throttle.clear() }
    }
}
