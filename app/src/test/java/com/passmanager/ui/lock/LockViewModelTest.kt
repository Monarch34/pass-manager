package com.passmanager.ui.lock

import com.passmanager.R
import com.passmanager.domain.model.LockState
import com.passmanager.domain.port.LockStateProvider
import com.passmanager.domain.usecase.UnlockThrottle
import com.passmanager.domain.usecase.UnlockWithPassphraseUseCase
import com.passmanager.domain.exception.UnlockThrottledException
import com.passmanager.domain.exception.WrongPassphraseException
import com.passmanager.security.BiometricKeyManager
import com.passmanager.security.biometric.BiometricHelper
import com.passmanager.test.MainDispatcherRule
import com.passmanager.ui.common.UserMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LockViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `unlockWithPassphrase sets error on wrong passphrase`() = runTest {
        val unlockUseCase = mockk<UnlockWithPassphraseUseCase>()
        val biometricKeyManager = mockk<BiometricKeyManager>()
        val lockStateProvider = mockk<LockStateProvider>()

        coEvery { unlockUseCase(any()) } throws WrongPassphraseException()
        coEvery { biometricKeyManager.isAvailable() } returns false
        every { lockStateProvider.lockState } returns MutableStateFlow(LockState.ColdLocked)
        val biometricHelper = mockk<BiometricHelper>()
        every { biometricHelper.canUseBiometric() } returns false

        val viewModel = LockViewModel(
            unlockUseCase,
            biometricKeyManager,
            lockStateProvider,
            biometricHelper,
            noLockout()
        )

        viewModel.unlockWithPassphrase("wrong".toCharArray())
        advanceUntilIdle()
        val wrong = viewModel.uiState.value
        assertEquals(UserMessage.Resource(R.string.lock_wrong_passphrase), wrong.error)
        assertFalse(wrong.isUnlocked)
        assertFalse(wrong.isLoading)
    }

    @Test
    fun `unlockWithPassphrase sets isUnlocked on success`() = runTest {
        val unlockUseCase = mockk<UnlockWithPassphraseUseCase>()
        val biometricKeyManager = mockk<BiometricKeyManager>()
        val lockStateProvider = mockk<LockStateProvider>()

        coEvery { unlockUseCase(any()) } returns Unit
        coEvery { biometricKeyManager.isAvailable() } returns false
        every { lockStateProvider.lockState } returns MutableStateFlow(LockState.ColdLocked)
        val biometricHelper = mockk<BiometricHelper>()
        every { biometricHelper.canUseBiometric() } returns false

        val viewModel = LockViewModel(
            unlockUseCase,
            biometricKeyManager,
            lockStateProvider,
            biometricHelper,
            noLockout()
        )

        viewModel.unlockWithPassphrase("correct".toCharArray())
        advanceUntilIdle()
        val success = viewModel.uiState.value
        assertTrue(success.isUnlocked)
        assertFalse(success.isLoading)
    }

    // -- Unlock back-pressure -------------------------

    /** A throttle that reports no penalty, for the tests that are not about back-pressure. */
    private fun noLockout(): UnlockThrottle = mockk<UnlockThrottle>(relaxed = true).also {
        coEvery { it.remainingLockoutMs() } returns 0L
    }

    private fun buildViewModel(
        unlockUseCase: UnlockWithPassphraseUseCase = mockk(relaxed = true),
        throttle: UnlockThrottle = noLockout()
    ): LockViewModel {
        val biometricKeyManager = mockk<BiometricKeyManager>(relaxed = true)
        coEvery { biometricKeyManager.isAvailable() } returns false
        val lockStateProvider = mockk<LockStateProvider>(relaxed = true)
        every { lockStateProvider.lockState } returns MutableStateFlow(LockState.ColdLocked)
        val biometricHelper = mockk<BiometricHelper>(relaxed = true)
        every { biometricHelper.canUseBiometric() } returns false
        return LockViewModel(
            unlockUseCase, biometricKeyManager, lockStateProvider, biometricHelper, throttle
        )
    }

    @Test
    fun `a lockout still running when the screen opens is shown immediately`() = runTest {
        val throttle = mockk<UnlockThrottle>(relaxed = true)
        coEvery { throttle.remainingLockoutMs() } returns 8_000L

        // The penalty outlives the screen, so it has to be read back rather than only remembered
        // from the failure that caused it.
        val vm = buildViewModel(throttle = throttle)

        assertEquals(8, vm.uiState.value.lockoutRemainingSeconds)
    }

    @Test
    fun `a throttled attempt reports the wait without an error message`() = runTest {
        val unlockUseCase = mockk<UnlockWithPassphraseUseCase>()
        coEvery { unlockUseCase(any()) } throws UnlockThrottledException(remainingMs = 4_000L)
        val vm = buildViewModel(unlockUseCase = unlockUseCase)

        vm.unlockWithPassphrase("whatever".toCharArray())

        // Not an error banner: the countdown is the whole message, and a snackbar on top of it
        // would just repeat itself once a second.
        assertEquals(4, vm.uiState.value.lockoutRemainingSeconds)
        assertNull(vm.uiState.value.error)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `a partial second is rounded up so the countdown never lies`() = runTest {
        val unlockUseCase = mockk<UnlockWithPassphraseUseCase>()
        coEvery { unlockUseCase(any()) } throws UnlockThrottledException(remainingMs = 1_200L)
        val vm = buildViewModel(unlockUseCase = unlockUseCase)

        vm.unlockWithPassphrase("whatever".toCharArray())

        // Showing 1 while 1.2 s remain would invite a tap that gets refused again.
        assertEquals(2, vm.uiState.value.lockoutRemainingSeconds)
    }

    @Test
    fun `a wrong passphrase picks up whatever penalty it just earned`() = runTest {
        val unlockUseCase = mockk<UnlockWithPassphraseUseCase>()
        coEvery { unlockUseCase(any()) } throws WrongPassphraseException()
        val throttle = mockk<UnlockThrottle>(relaxed = true)
        coEvery { throttle.remainingLockoutMs() } returnsMany listOf(0L, 2_000L)

        val vm = buildViewModel(unlockUseCase = unlockUseCase, throttle = throttle)
        vm.unlockWithPassphrase("wrong".toCharArray())

        assertEquals(UserMessage.Resource(R.string.lock_wrong_passphrase), vm.uiState.value.error)
        assertEquals(2, vm.uiState.value.lockoutRemainingSeconds)
    }

    @Test
    fun `biometric unlock is not throttled and clears the penalty`() = runTest {
        val throttle = mockk<UnlockThrottle>(relaxed = true)
        coEvery { throttle.remainingLockoutMs() } returns 30_000L
        val biometricKeyManager = mockk<BiometricKeyManager>(relaxed = true)
        coEvery { biometricKeyManager.isAvailable() } returns true
        val lockStateProvider = mockk<LockStateProvider>(relaxed = true)
        every { lockStateProvider.lockState } returns MutableStateFlow(LockState.WarmLocked)
        val biometricHelper = mockk<BiometricHelper>(relaxed = true)
        every { biometricHelper.canUseBiometric() } returns true

        val vm = LockViewModel(
            mockk(relaxed = true), biometricKeyManager, lockStateProvider, biometricHelper, throttle
        )
        assertEquals(30, vm.uiState.value.lockoutRemainingSeconds)

        vm.onBiometricSuccess(mockk(relaxed = true))
        advanceUntilIdle()

        // Passing biometric proves the device owner is present, which is exactly what the
        // throttle exists to establish - so it goes, countdown and all.
        assertTrue(vm.uiState.value.isUnlocked)
        assertEquals(0, vm.uiState.value.lockoutRemainingSeconds)
        coVerify(exactly = 1) { throttle.clear() }
    }
}
