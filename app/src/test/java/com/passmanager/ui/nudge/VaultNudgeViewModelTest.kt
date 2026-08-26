package com.passmanager.ui.nudge

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.model.ItemPayload
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.model.VaultWrapVersion
import com.passmanager.domain.port.AppSettingsPort
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.test.FakeVaultRepository
import com.passmanager.test.MainDispatcherRule
import com.passmanager.test.seedItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VaultNudgeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val now = 1_800_000_000_000L
    private val day = VaultNudgePolicy.DAY_MS
    private val cipher = AesGcmCipher()
    private val vaultKey = ByteArray(32) { 0x11 }

    private fun metadata(deviceBound: Boolean) = VaultMetadata(
        currentKeyVersion = 1,
        wrappedVaultKey = EncryptedData(ByteArray(32), ByteArray(12)),
        kdfSalt = ByteArray(16),
        kdfParams = KdfParams(),
        biometricEnabled = false,
        biometricWrappedKey = null,
        wrapVersion = if (deviceBound) VaultWrapVersion.DEVICE_BOUND else VaultWrapVersion.PASSPHRASE_ONLY,
        pepperIv = if (deviceBound) ByteArray(12) else null
    )

    private fun settings(
        declined: Boolean = false,
        lastExportAt: Long? = null,
        snoozedAt: Long? = null
    ): AppSettingsPort = mockk<AppSettingsPort>(relaxed = true).also {
        every { it.deviceBindingPromptDeclined } returns flowOf(declined)
        every { it.lastExportAtMs } returns flowOf(lastExportAt)
        every { it.backupReminderSnoozedAtMs } returns flowOf(snoozedAt)
    }

    private fun buildViewModel(
        settings: AppSettingsPort,
        deviceBound: Boolean,
        itemCount: Int = 0
    ): Pair<VaultNudgeViewModel, FakeVaultRepository> {
        val metadataRepository = mockk<MetadataRepository>(relaxed = true)
        coEvery { metadataRepository.get() } returns metadata(deviceBound)
        val vaultRepository = FakeVaultRepository()
        repeat(itemCount) { index ->
            vaultRepository.seedItem(
                cipher,
                vaultKey,
                ItemPayload.SecureNote(id = "note-$index", title = "Note $index"),
                createdAt = 1L,
                updatedAt = 2L
            )
        }
        return VaultNudgeViewModel(settings, metadataRepository, vaultRepository) to vaultRepository
    }

    private suspend fun evaluate(
        settings: AppSettingsPort,
        deviceBound: Boolean,
        itemCount: Int = 0
    ): VaultNudgeViewModel {
        val (vm, _) = buildViewModel(settings, deviceBound, itemCount)
        vm.evaluate(now)
        return vm
    }

    @Test
    fun `a pre-v2 vault is offered device binding`() = runTest {
        val vm = evaluate(settings(), deviceBound = false)

        assertEquals(VaultNudge.DeviceBinding, vm.nudge.value)
    }

    @Test
    fun `once declined, the device-binding prompt never returns`() = runTest {
        val vm = evaluate(settings(declined = true), deviceBound = false)

        // The offer stays available in Settings; the prompt itself is spent.
        assertNull(vm.nudge.value)
    }

    @Test
    fun `dismissing the device-binding prompt records the decline`() = runTest {
        val settings = settings()
        val vm = evaluate(settings, deviceBound = false)

        vm.dismissDeviceBindingPrompt()
        advanceUntilIdle()

        assertNull(vm.nudge.value)
        coVerify(exactly = 1) { settings.setDeviceBindingPromptDeclined(true) }
    }

    @Test
    fun `a device-bound vault with enough items and no backup is asked to back up`() = runTest {
        val vm = evaluate(
            settings(),
            deviceBound = true,
            itemCount = VaultNudgePolicy.FIRST_BACKUP_ITEM_THRESHOLD
        )

        assertEquals(VaultNudge.FirstBackup, vm.nudge.value)
    }

    @Test
    fun `a nearly empty vault is left alone`() = runTest {
        val vm = evaluate(
            settings(),
            deviceBound = true,
            itemCount = VaultNudgePolicy.FIRST_BACKUP_ITEM_THRESHOLD - 1
        )

        assertNull(vm.nudge.value)
    }

    @Test
    fun `a stale backup is reported with its age in days`() = runTest {
        val age = VaultNudgePolicy.BACKUP_OVERDUE_AFTER_DAYS + 5
        val vm = evaluate(
            settings(lastExportAt = now - age * day),
            deviceBound = true,
            itemCount = 10
        )

        assertEquals(VaultNudge.BackupOverdue(age), vm.nudge.value)
    }

    @Test
    fun `a recent backup says nothing`() = runTest {
        val vm = evaluate(
            settings(lastExportAt = now - 3 * day),
            deviceBound = true,
            itemCount = 10
        )

        assertNull(vm.nudge.value)
    }

    @Test
    fun `a snoozed reminder stays quiet for the snooze window`() = runTest {
        val vm = evaluate(
            settings(
                lastExportAt = now - 90 * day,
                snoozedAt = now - (VaultNudgePolicy.BACKUP_SNOOZE_DAYS - 1) * day
            ),
            deviceBound = true,
            itemCount = 10
        )

        assertNull(vm.nudge.value)
    }

    @Test
    fun `a snooze that has run out lets the reminder through again`() = runTest {
        val vm = evaluate(
            settings(
                lastExportAt = now - 90 * day,
                snoozedAt = now - VaultNudgePolicy.BACKUP_SNOOZE_DAYS * day
            ),
            deviceBound = true,
            itemCount = 10
        )

        assertEquals(VaultNudge.BackupOverdue(90), vm.nudge.value)
    }

    @Test
    fun `device binding takes priority over a backup reminder`() = runTest {
        // Both apply; only one prompt is ever shown, and this is the one that expires.
        val vm = evaluate(
            settings(lastExportAt = now - 90 * day),
            deviceBound = false,
            itemCount = 10
        )

        assertEquals(VaultNudge.DeviceBinding, vm.nudge.value)
    }

    @Test
    fun `a vault that does not exist yet is never nudged`() = runTest {
        val metadataRepository = mockk<MetadataRepository>(relaxed = true)
        coEvery { metadataRepository.get() } returns null
        val vm = VaultNudgeViewModel(settings(), metadataRepository, FakeVaultRepository())

        vm.evaluate(now)

        assertNull(vm.nudge.value)
    }
}
