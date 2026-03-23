package com.passmanager.ui.lock

import com.passmanager.R
import com.passmanager.crypto.keystore.AndroidKeystoreManager
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.domain.usecase.UnlockWithBiometricUseCase
import com.passmanager.domain.usecase.UnlockWithPassphraseUseCase
import com.passmanager.domain.usecase.WrongPassphraseException
import com.passmanager.security.LockState
import com.passmanager.security.VaultLockManager
import com.passmanager.security.biometric.BiometricHelper
import com.passmanager.test.MainDispatcherRule
import com.passmanager.ui.common.UserMessage
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val biometricUseCase = mockk<UnlockWithBiometricUseCase>(relaxed = true)
        val metadataRepo = mockk<MetadataRepository>()
        val vaultLockManager = mockk<VaultLockManager>()

        coEvery { unlockUseCase(any()) } throws WrongPassphraseException()
        coEvery { metadataRepo.get() } returns null
        every { vaultLockManager.lockState } returns MutableStateFlow(LockState.ColdLocked)
        val keystore = mockk<AndroidKeystoreManager>()
        every { keystore.hasBiometricKey() } returns false
        val biometricHelper = mockk<BiometricHelper>()
        every { biometricHelper.canUseBiometric() } returns false

        val viewModel = LockViewModel(
            unlockUseCase,
            biometricUseCase,
            metadataRepo,
            vaultLockManager,
            keystore,
            biometricHelper
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
        val biometricUseCase = mockk<UnlockWithBiometricUseCase>(relaxed = true)
        val metadataRepo = mockk<MetadataRepository>()
        val vaultLockManager = mockk<VaultLockManager>()

        coEvery { unlockUseCase(any()) } returns Unit
        coEvery { metadataRepo.get() } returns null
        every { vaultLockManager.lockState } returns MutableStateFlow(LockState.ColdLocked)
        val keystore = mockk<AndroidKeystoreManager>()
        every { keystore.hasBiometricKey() } returns false
        val biometricHelper = mockk<BiometricHelper>()
        every { biometricHelper.canUseBiometric() } returns false

        val viewModel = LockViewModel(
            unlockUseCase,
            biometricUseCase,
            metadataRepo,
            vaultLockManager,
            keystore,
            biometricHelper
        )

        viewModel.unlockWithPassphrase("correct".toCharArray())
        advanceUntilIdle()
        val success = viewModel.uiState.value
        assertTrue(success.isUnlocked)
        assertFalse(success.isLoading)
    }
}
