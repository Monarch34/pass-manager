package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.exception.DeviceKeyUnavailableException
import com.passmanager.domain.model.ItemPayload
import com.passmanager.domain.model.LockState
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.model.VaultWrapVersion
import com.passmanager.domain.port.BiometricLockPort
import com.passmanager.domain.port.LockStateProvider
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.test.FakePepperPort
import com.passmanager.test.FakeVaultRepository
import com.passmanager.test.seedItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The v1 → v2 upgrade, and the reset that is the only way out of a lost device key. */
class DeviceBindingLifecycleTest {

    private val cipher = AesGcmCipher()
    private val pepper = FakePepperPort()
    private val keyWrapper = VaultKeyWrapper(cipher, pepper)
    private val metadataRepository = mockk<MetadataRepository>(relaxed = true)

    private val kek = ByteArray(32) { 0x42 }
    private val vaultKey = ByteArray(32) { 0x11 }

    private fun lockProvider(state: LockState): LockStateProvider =
        mockk<LockStateProvider>(relaxed = true).also {
            every { it.lockState } returns MutableStateFlow(state)
        }

    private fun metadata(deviceBound: Boolean): VaultMetadata {
        val wrapped = keyWrapper.wrap(vaultKey.copyOf(), kek, deviceBound)
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

    private fun upgradeUseCase(state: LockState = LockState.Unlocked) =
        UpgradeVaultToDeviceBoundUseCase(metadataRepository, keyWrapper, lockProvider(state))

    private suspend fun failureOf(block: suspend () -> Unit): Throwable? = try {
        block()
        null
    } catch (e: Throwable) {
        e
    }

    // ── Upgrade ──────────────────────────────────────

    @Test
    fun `upgrading seals the existing key and the passphrase still opens it`() = runTest {
        val before = metadata(deviceBound = false)
        coEvery { metadataRepository.get() } returns before
        val saved = slot<VaultMetadata>()
        coEvery { metadataRepository.update(capture(saved)) } returns Unit

        assertTrue(upgradeUseCase().invoke())

        assertEquals(VaultWrapVersion.DEVICE_BOUND, saved.captured.wrapVersion)
        assertNotNull(saved.captured.pepperIv)
        // The point of sealing in place: no re-derivation, so the same passphrase-derived key
        // still opens the vault afterwards.
        assertArrayEquals(vaultKey, keyWrapper.unwrap(saved.captured, kek))
    }

    @Test
    fun `upgrading preserves the salt, cost parameters and key version`() = runTest {
        val before = metadata(deviceBound = false)
        coEvery { metadataRepository.get() } returns before
        val saved = slot<VaultMetadata>()
        coEvery { metadataRepository.update(capture(saved)) } returns Unit

        upgradeUseCase().invoke()

        assertArrayEquals(before.kdfSalt, saved.captured.kdfSalt)
        assertEquals(before.kdfParams, saved.captured.kdfParams)
        assertEquals(before.currentKeyVersion, saved.captured.currentKeyVersion)
        assertArrayEquals(before.wrappedVaultKey.iv, saved.captured.wrappedVaultKey.iv)
    }

    @Test
    fun `upgrading an already device-bound vault is a no-op`() = runTest {
        coEvery { metadataRepository.get() } returns metadata(deviceBound = true)

        assertFalse(upgradeUseCase().invoke())

        coVerify(exactly = 0) { metadataRepository.update(any()) }
    }

    @Test
    fun `upgrading requires an unlocked vault`() = runTest {
        coEvery { metadataRepository.get() } returns metadata(deviceBound = false)

        val failure = failureOf { upgradeUseCase(LockState.WarmLocked).invoke() }

        assertEquals(IllegalStateException::class.java, failure?.javaClass)
        coVerify(exactly = 0) { metadataRepository.update(any()) }
    }

    @Test
    fun `a keystore failure leaves the vault exactly as it was`() = runTest {
        coEvery { metadataRepository.get() } returns metadata(deviceBound = false)
        pepper.failWith = DeviceKeyUnavailableException()

        val failure = failureOf { upgradeUseCase().invoke() }

        // One metadata write, at the very end — so a failure cannot leave a half-upgraded vault.
        assertEquals(DeviceKeyUnavailableException::class.java, failure?.javaClass)
        coVerify(exactly = 0) { metadataRepository.update(any()) }
    }

    // ── Reset ────────────────────────────────────────

    @Test
    fun `reset erases items, metadata and both device keys`() = runTest {
        val lockProvider = lockProvider(LockState.Unlocked)
        val vaultRepository = FakeVaultRepository()
        vaultRepository.seedItem(
            cipher,
            vaultKey,
            ItemPayload.Login(id = "a", title = "GitHub", password = "x"),
            createdAt = 1L,
            updatedAt = 2L
        )
        val biometricLockPort = mockk<BiometricLockPort>(relaxed = true)

        ResetVaultUseCase(
            vaultRepository, metadataRepository, biometricLockPort, pepper, lockProvider
        ).invoke()

        assertTrue(vaultRepository.rows.isEmpty())
        // Belt and braces: the vault key must not outlive the rows it decrypted, whatever
        // route the caller took to get here.
        verify(exactly = 1) { lockProvider.lock() }
        coVerify(exactly = 1) { metadataRepository.delete() }
        coVerify(exactly = 1) { biometricLockPort.disable() }
        assertFalse(pepper.isKeyPresent())
    }

    @Test
    fun `reset still completes when disabling biometric fails`() = runTest {
        val lockProvider = lockProvider(LockState.Unlocked)
        val vaultRepository = FakeVaultRepository()
        val biometricLockPort = mockk<BiometricLockPort>()
        coEvery { biometricLockPort.disable() } throws IllegalStateException("no metadata row")

        // The metadata row is already gone by this point, so a complaining biometric teardown must
        // not strand the user on the recovery screen with nothing left to recover.
        ResetVaultUseCase(
            vaultRepository, metadataRepository, biometricLockPort, pepper, lockProvider
        ).invoke()

        assertFalse(pepper.isKeyPresent())
    }
}
