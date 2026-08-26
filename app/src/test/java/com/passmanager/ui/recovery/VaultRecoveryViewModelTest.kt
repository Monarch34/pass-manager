package com.passmanager.ui.recovery

import com.passmanager.R
import com.passmanager.domain.usecase.ResetVaultUseCase
import com.passmanager.test.MainDispatcherRule
import com.passmanager.ui.common.UserMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VaultRecoveryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val requiredWord = "RESET"

    @Test
    fun `the reset does not run until the confirmation word matches exactly`() = runTest {
        val resetVault = mockk<ResetVaultUseCase>(relaxed = true)
        val vm = VaultRecoveryViewModel(resetVault)

        vm.onConfirmationChanged("res")
        vm.resetVault(requiredWord)
        advanceUntilIdle()

        // A destructive, irreversible action does not fire on a partial match.
        coVerify(exactly = 0) { resetVault() }
        assertFalse(vm.uiState.value.isReset)
    }

    @Test
    fun `a lowercase confirmation is not accepted`() = runTest {
        val resetVault = mockk<ResetVaultUseCase>(relaxed = true)
        val vm = VaultRecoveryViewModel(resetVault)

        vm.onConfirmationChanged("reset")
        vm.resetVault(requiredWord)
        advanceUntilIdle()

        coVerify(exactly = 0) { resetVault() }
    }

    @Test
    fun `surrounding whitespace is tolerated`() = runTest {
        val resetVault = mockk<ResetVaultUseCase>(relaxed = true)
        val vm = VaultRecoveryViewModel(resetVault)

        vm.onConfirmationChanged("  RESET ")
        vm.resetVault(requiredWord)
        advanceUntilIdle()

        coVerify(exactly = 1) { resetVault() }
        assertTrue(vm.uiState.value.isReset)
    }

    @Test
    fun `an exact confirmation erases the vault and reports completion`() = runTest {
        val resetVault = mockk<ResetVaultUseCase>(relaxed = true)
        val vm = VaultRecoveryViewModel(resetVault)

        vm.onConfirmationChanged(requiredWord)
        vm.resetVault(requiredWord)
        advanceUntilIdle()

        coVerify(exactly = 1) { resetVault() }
        assertTrue(vm.uiState.value.isReset)
        assertFalse(vm.uiState.value.isResetting)
    }

    @Test
    fun `a failed reset surfaces an error and does not claim success`() = runTest {
        val resetVault = mockk<ResetVaultUseCase>()
        coEvery { resetVault() } throws IllegalStateException("database locked")
        val vm = VaultRecoveryViewModel(resetVault)

        vm.onConfirmationChanged(requiredWord)
        vm.resetVault(requiredWord)
        advanceUntilIdle()

        // Navigating to onboarding on a failed wipe would leave the old vault in place behind a
        // "start fresh" screen that cannot start anything.
        assertFalse(vm.uiState.value.isReset)
        assertEquals(
            UserMessage.Resource(R.string.recovery_failed),
            vm.uiState.value.error
        )
    }
}
