package com.passmanager.ui.settings

import android.content.Context
import com.passmanager.R
import com.passmanager.domain.port.AppSettingsPort
import com.passmanager.domain.port.LockStateProvider
import com.passmanager.domain.usecase.ChangePassphraseUseCase
import com.passmanager.domain.usecase.SeedDemoVaultItemsUseCase
import com.passmanager.domain.exception.WrongPassphraseException
import com.passmanager.domain.port.BiometricLockPort
import com.passmanager.security.biometric.BiometricHelper
import com.passmanager.test.MainDispatcherRule
import com.passmanager.ui.common.UserMessage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun buildViewModel(
        changePassphrase: ChangePassphraseUseCase = mockk(relaxed = true),
        lockState: LockStateProvider = mockk(relaxed = true),
        seedDemoVaultItems: SeedDemoVaultItemsUseCase = mockk(relaxed = true)
    ): SettingsViewModel {
        val context = mockk<Context>(relaxed = true)
        val biometricLockPort = mockk<BiometricLockPort>()
        coEvery { biometricLockPort.isAvailable() } returns false
        val appSettings = mockk<AppSettingsPort>()
        every { appSettings.autoLockTimeoutSeconds } returns flowOf(60)
        every { appSettings.useGoogleFavicons } returns flowOf(false)
        val biometricHelper = mockk<BiometricHelper>()
        every { biometricHelper.canUseBiometric() } returns false

        return SettingsViewModel(
            context,
            biometricLockPort,
            appSettings,
            biometricHelper,
            changePassphrase,
            lockState,
            seedDemoVaultItems
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
}
