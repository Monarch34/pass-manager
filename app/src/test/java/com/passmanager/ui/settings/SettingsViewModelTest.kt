package com.passmanager.ui.settings

import android.content.Context
import com.passmanager.R
import com.passmanager.domain.port.AppSettingsPort
import com.passmanager.domain.port.LockStateProvider
import com.passmanager.domain.usecase.ChangePassphraseUseCase
import com.passmanager.domain.usecase.ExportVaultUseCase
import com.passmanager.domain.usecase.ImportVaultUseCase
import com.passmanager.domain.usecase.SeedDemoVaultItemsUseCase
import com.passmanager.domain.usecase.UpgradeVaultToDeviceBoundUseCase
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.exception.DeviceKeyUnavailableException
import com.passmanager.domain.exception.PmVaultAuthenticationException
import com.passmanager.domain.exception.WrongPassphraseException
import com.passmanager.domain.model.LockState
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.port.BiometricLockPort
import com.passmanager.domain.port.VaultFilePort
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.domain.usecase.ImportPlan
import com.passmanager.security.biometric.BiometricHelper
import com.passmanager.test.MainDispatcherRule
import com.passmanager.ui.common.UserMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun appSettingsMock(): AppSettingsPort = mockk<AppSettingsPort>(relaxed = true).also {
        every { it.autoLockTimeoutSeconds } returns flowOf(60)
        every { it.useGoogleFavicons } returns flowOf(false)
        every { it.lastExportAtMs } returns flowOf(null)
    }

    private fun buildViewModel(
        changePassphrase: ChangePassphraseUseCase = mockk(relaxed = true),
        lockState: LockStateProvider = mockk(relaxed = true),
        seedDemoVaultItems: SeedDemoVaultItemsUseCase = mockk(relaxed = true),
        exportVault: ExportVaultUseCase = mockk(relaxed = true),
        importVault: ImportVaultUseCase = mockk(relaxed = true),
        vaultFilePort: VaultFilePort = mockk(relaxed = true),
        transferRequests: VaultTransferRequests = VaultTransferRequests(),
        appSettings: AppSettingsPort = appSettingsMock(),
        metadataRepository: MetadataRepository = mockk<MetadataRepository>(relaxed = true).also {
            every { it.observe() } returns flowOf(null)
        },
        upgradeVault: UpgradeVaultToDeviceBoundUseCase = mockk(relaxed = true)
    ): SettingsViewModel {
        val context = mockk<Context>(relaxed = true)
        val biometricLockPort = mockk<BiometricLockPort>()
        coEvery { biometricLockPort.isAvailable() } returns false
        val biometricHelper = mockk<BiometricHelper>()
        every { biometricHelper.canUseBiometric() } returns false

        return SettingsViewModel(
            context,
            biometricLockPort,
            appSettings,
            biometricHelper,
            changePassphrase,
            lockState,
            seedDemoVaultItems,
            exportVault,
            importVault,
            vaultFilePort,
            transferRequests,
            metadataRepository,
            upgradeVault
        )
    }

    @Test
    fun `changePassphrase shows mismatch when confirm differs`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.changePassphrase("old".toCharArray(), "new".toCharArray(), "other".toCharArray())
        advanceUntilIdle()

        assertEquals(
            SettingsError.PassphraseChange(UserMessage.Resource(R.string.onboarding_passphrase_mismatch)),
            vm.uiState.value.error
        )
    }

    @Test
    fun `changePassphrase maps wrong current passphrase`() = runTest {
        val changePassphrase = mockk<ChangePassphraseUseCase>()
        coEvery { changePassphrase(any(), any()) } throws WrongPassphraseException()
        val lockState = mockk<LockStateProvider>(relaxed = true)

        val vm = buildViewModel(changePassphrase = changePassphrase, lockState = lockState)
        advanceUntilIdle()

        vm.changePassphrase("wrong".toCharArray(), "new1".toCharArray(), "new1".toCharArray())
        advanceUntilIdle()

        assertEquals(
            SettingsError.PassphraseChange(UserMessage.Resource(R.string.settings_wrong_current_passphrase)),
            vm.uiState.value.error
        )
    }

    @Test
    fun `changePassphrase on success locks vault`() = runTest {
        val changePassphrase = mockk<ChangePassphraseUseCase>()
        coEvery { changePassphrase(any(), any()) } returns Unit
        val lockState = mockk<LockStateProvider>(relaxed = true)

        val vm = buildViewModel(changePassphrase = changePassphrase, lockState = lockState)
        advanceUntilIdle()

        vm.changePassphrase("old".toCharArray(), "new1".toCharArray(), "new1".toCharArray())
        advanceUntilIdle()

        verify { lockState.lock() }
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `clearError removes error from ui state`() = runTest {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.clearError()
        assertNull(vm.uiState.value.error)
    }

    // ── SAF + lock lifecycle ─────────────────────────

    private fun lockProvider(state: LockState): LockStateProvider {
        val provider = mockk<LockStateProvider>(relaxed = true)
        every { provider.lockState } returns MutableStateFlow(state)
        return provider
    }

    @Test
    fun `picking an export destination while unlocked asks for the passphrase immediately`() = runTest {
        val vm = buildViewModel(lockState = lockProvider(LockState.Unlocked))
        advanceUntilIdle()

        vm.onExportFileChosen("content://doc/1")
        advanceUntilIdle()

        val dialog = vm.uiState.value.transferDialog
        assertTrue(dialog is VaultTransferDialog.ExportPassphrase)
        assertEquals("content://doc/1", (dialog as VaultTransferDialog.ExportPassphrase).uri)
    }

    @Test
    fun `a picker result arriving at a locked vault is stashed instead of prompting`() = runTest {
        val requests = VaultTransferRequests()
        val vm = buildViewModel(
            lockState = lockProvider(LockState.WarmLocked),
            transferRequests = requests
        )
        advanceUntilIdle()

        vm.onImportFileChosen("content://doc/2")
        advanceUntilIdle()

        // No passphrase prompt behind the lock screen; the document waits instead.
        assertNull(vm.uiState.value.transferDialog)
        assertEquals(
            PendingVaultTransfer(VaultTransferKind.IMPORT, "content://doc/2"),
            requests.pending.value
        )
    }

    @Test
    fun `a stashed transfer is picked up and cleared once the vault is unlocked again`() = runTest {
        val requests = VaultTransferRequests()
        requests.stash(VaultTransferKind.IMPORT, "content://doc/3")

        // Locking destroys the previous ViewModel; this is the one built after the unlock.
        val vm = buildViewModel(
            lockState = lockProvider(LockState.Unlocked),
            transferRequests = requests
        )
        advanceUntilIdle()

        val dialog = vm.uiState.value.transferDialog
        assertTrue(dialog is VaultTransferDialog.ImportPassphrase)
        assertEquals("content://doc/3", (dialog as VaultTransferDialog.ImportPassphrase).uri)
        assertNull(requests.pending.value)
    }

    @Test
    fun `export rejects a passphrase that does not match its confirmation`() = runTest {
        val export = mockk<ExportVaultUseCase>(relaxed = true)
        val vm = buildViewModel(lockState = lockProvider(LockState.Unlocked), exportVault = export)
        advanceUntilIdle()
        vm.onExportFileChosen("content://doc/4")

        vm.exportVault("Str0ng-Passphrase!".toCharArray(), "different".toCharArray())
        advanceUntilIdle()

        val dialog = vm.uiState.value.transferDialog as VaultTransferDialog.ExportPassphrase
        assertEquals(UserMessage.Resource(R.string.onboarding_passphrase_mismatch), dialog.error)
        coVerify(exactly = 0) { export(any(), any()) }
    }

    @Test
    fun `export refuses a passphrase below the strength floor`() = runTest {
        val export = mockk<ExportVaultUseCase>(relaxed = true)
        val vm = buildViewModel(lockState = lockProvider(LockState.Unlocked), exportVault = export)
        advanceUntilIdle()
        vm.onExportFileChosen("content://doc/5")

        vm.exportVault("password".toCharArray(), "password".toCharArray())
        advanceUntilIdle()

        val dialog = vm.uiState.value.transferDialog as VaultTransferDialog.ExportPassphrase
        assertEquals(UserMessage.Resource(R.string.settings_export_passphrase_weak), dialog.error)
        coVerify(exactly = 0) { export(any(), any()) }
    }

    @Test
    fun `a successful export writes the file and records the export time`() = runTest {
        val export = mockk<ExportVaultUseCase>()
        coEvery { export(any(), any()) } returns ByteArray(64)
        val filePort = mockk<VaultFilePort>(relaxed = true)
        val appSettings = appSettingsMock()
        val vm = buildViewModel(
            lockState = lockProvider(LockState.Unlocked),
            exportVault = export,
            vaultFilePort = filePort,
            appSettings = appSettings
        )
        advanceUntilIdle()
        vm.onExportFileChosen("content://doc/6")

        vm.exportVault("Str0ng-Passphrase!".toCharArray(), "Str0ng-Passphrase!".toCharArray())
        advanceUntilIdle()

        coVerify(exactly = 1) { filePort.write("content://doc/6", any()) }
        coVerify(exactly = 1) { appSettings.setLastExportAt(any()) }
        assertNull(vm.uiState.value.transferDialog)
        assertEquals(
            UserMessage.Resource(R.string.settings_export_done),
            vm.uiState.value.transferMessage
        )
    }

    @Test
    fun `a wrong import passphrase reopens the prompt with the shared failure message`() = runTest {
        val import = mockk<ImportVaultUseCase>()
        coEvery { import.plan(any(), any(), any()) } throws PmVaultAuthenticationException()
        val filePort = mockk<VaultFilePort>()
        coEvery { filePort.read(any()) } returns ByteArray(0)
        val vm = buildViewModel(
            lockState = lockProvider(LockState.Unlocked),
            importVault = import,
            vaultFilePort = filePort
        )
        advanceUntilIdle()
        vm.onImportFileChosen("content://doc/7")

        vm.prepareImport("nope".toCharArray())
        advanceUntilIdle()

        val dialog = vm.uiState.value.transferDialog as VaultTransferDialog.ImportPassphrase
        assertEquals(
            UserMessage.Resource(R.string.settings_import_wrong_passphrase),
            dialog.error
        )
    }

    @Test
    fun `dismissing the summary drops the decrypted plan without writing anything`() = runTest {
        val import = mockk<ImportVaultUseCase>(relaxed = true)
        coEvery { import.plan(any(), any(), any()) } returns ImportPlan(emptyList(), exportedAt = 1L)
        val filePort = mockk<VaultFilePort>()
        coEvery { filePort.read(any()) } returns ByteArray(0)
        val vm = buildViewModel(
            lockState = lockProvider(LockState.Unlocked),
            importVault = import,
            vaultFilePort = filePort
        )
        advanceUntilIdle()
        vm.onImportFileChosen("content://doc/8")
        vm.prepareImport("pass".toCharArray())
        advanceUntilIdle()
        assertTrue(vm.uiState.value.transferDialog is VaultTransferDialog.ImportSummary)

        vm.dismissTransferDialog()
        vm.applyImport(addOnly = false)
        advanceUntilIdle()

        assertNull(vm.uiState.value.transferDialog)
        coVerify(exactly = 0) { import.apply(any(), any()) }
    }

    // -- Device binding (v1 to v2) --------------------

    private fun metadataRepositoryFor(deviceBound: Boolean): MetadataRepository =
        mockk<MetadataRepository>(relaxed = true).also {
            every { it.observe() } returns flowOf(
                VaultMetadata(
                    currentKeyVersion = 1,
                    wrappedVaultKey = EncryptedData(ByteArray(32), ByteArray(12)),
                    kdfSalt = ByteArray(16),
                    kdfParams = KdfParams(),
                    biometricEnabled = false,
                    biometricWrappedKey = null,
                    wrapVersion = if (deviceBound) 2 else 1,
                    pepperIv = if (deviceBound) ByteArray(12) else null
                )
            )
        }

    @Test
    fun `the settings row reflects whether the vault is device-bound`() = runTest {
        val vm = buildViewModel(metadataRepository = metadataRepositoryFor(deviceBound = true))
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isDeviceBound)
    }

    @Test
    fun `the upgrade is gated on an export completed in this flow`() = runTest {
        val upgrade = mockk<UpgradeVaultToDeviceBoundUseCase>(relaxed = true)
        val vm = buildViewModel(
            lockState = lockProvider(LockState.Unlocked),
            metadataRepository = metadataRepositoryFor(deviceBound = false),
            upgradeVault = upgrade
        )
        advanceUntilIdle()

        vm.openDeviceBindingDialog()

        val dialog = vm.uiState.value.deviceBindingDialog
        assertTrue(dialog is DeviceBindingDialog.Explain)
        // The confirm button is bound to this flag; a stale backup must not open the gate.
        assertFalse((dialog as DeviceBindingDialog.Explain).backupDone)
        coVerify(exactly = 0) { upgrade() }
    }

    @Test
    fun `an export inside the upgrade flow opens the gate`() = runTest {
        val export = mockk<ExportVaultUseCase>()
        coEvery { export(any(), any()) } returns ByteArray(64)
        val vm = buildViewModel(
            lockState = lockProvider(LockState.Unlocked),
            exportVault = export,
            metadataRepository = metadataRepositoryFor(deviceBound = false)
        )
        advanceUntilIdle()

        vm.openDeviceBindingDialog()
        vm.onUpgradeExportRequested()
        vm.onExportFileChosen("content://doc/upgrade")
        vm.exportVault("Str0ng-Passphrase!".toCharArray(), "Str0ng-Passphrase!".toCharArray())
        advanceUntilIdle()

        val dialog = vm.uiState.value.deviceBindingDialog
        assertTrue(dialog is DeviceBindingDialog.Explain)
        assertTrue((dialog as DeviceBindingDialog.Explain).backupDone)
    }

    @Test
    fun `an ordinary export does not open the upgrade gate`() = runTest {
        val export = mockk<ExportVaultUseCase>()
        coEvery { export(any(), any()) } returns ByteArray(64)
        val vm = buildViewModel(
            lockState = lockProvider(LockState.Unlocked),
            exportVault = export,
            metadataRepository = metadataRepositoryFor(deviceBound = false)
        )
        advanceUntilIdle()

        vm.onExportFileChosen("content://doc/plain")
        vm.exportVault("Str0ng-Passphrase!".toCharArray(), "Str0ng-Passphrase!".toCharArray())
        advanceUntilIdle()

        assertNull(vm.uiState.value.deviceBindingDialog)
    }

    @Test
    fun `skipping the backup goes through a second confirmation`() = runTest {
        val upgrade = mockk<UpgradeVaultToDeviceBoundUseCase>(relaxed = true)
        val vm = buildViewModel(
            lockState = lockProvider(LockState.Unlocked),
            metadataRepository = metadataRepositoryFor(deviceBound = false),
            upgradeVault = upgrade
        )
        advanceUntilIdle()

        vm.openDeviceBindingDialog()
        vm.requestUpgradeWithoutBackup()
        advanceUntilIdle()

        assertEquals(DeviceBindingDialog.ConfirmWithoutBackup, vm.uiState.value.deviceBindingDialog)
        coVerify(exactly = 0) { upgrade() }

        vm.confirmDeviceBinding()
        advanceUntilIdle()

        coVerify(exactly = 1) { upgrade() }
    }

    @Test
    fun `backing out of the no-backup confirmation returns to the explanation`() = runTest {
        val vm = buildViewModel(
            lockState = lockProvider(LockState.Unlocked),
            metadataRepository = metadataRepositoryFor(deviceBound = false)
        )
        advanceUntilIdle()

        vm.openDeviceBindingDialog()
        vm.requestUpgradeWithoutBackup()
        vm.cancelUpgradeWithoutBackup()

        assertEquals(
            DeviceBindingDialog.Explain(backupDone = false),
            vm.uiState.value.deviceBindingDialog
        )
    }

    @Test
    fun `a failed upgrade closes the dialog with a retryable message`() = runTest {
        val upgrade = mockk<UpgradeVaultToDeviceBoundUseCase>()
        coEvery { upgrade() } throws DeviceKeyUnavailableException()
        val vm = buildViewModel(
            lockState = lockProvider(LockState.Unlocked),
            metadataRepository = metadataRepositoryFor(deviceBound = false),
            upgradeVault = upgrade
        )
        advanceUntilIdle()

        vm.openDeviceBindingDialog()
        vm.confirmDeviceBinding()
        advanceUntilIdle()

        assertNull(vm.uiState.value.deviceBindingDialog)
        assertEquals(
            UserMessage.Resource(R.string.device_binding_failed),
            vm.uiState.value.transferMessage
        )
    }
}
