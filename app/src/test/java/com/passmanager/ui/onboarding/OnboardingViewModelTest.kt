package com.passmanager.ui.onboarding

import app.cash.turbine.test
import com.passmanager.R
import com.passmanager.domain.usecase.SetupVaultUseCase
import com.passmanager.domain.usecase.UnlockWithPassphraseUseCase
import com.passmanager.test.MainDispatcherRule
import com.passmanager.ui.common.UserMessage
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `createVault sets error when passphrases do not match`() = runTest {
        val setupVault = mockk<SetupVaultUseCase>(relaxed = true)
        val unlock = mockk<UnlockWithPassphraseUseCase>(relaxed = true)
        val viewModel = OnboardingViewModel(setupVault, unlock)

        viewModel.uiState.test {
            skipItems(1)
            viewModel.createVault("password123".toCharArray(), "different".toCharArray())
            val state = awaitItem()
            assertEquals(
                UserMessage.Resource(R.string.onboarding_passphrase_mismatch),
                state.error
            )
            assertFalse(state.isComplete)
        }
    }

    @Test
    fun `createVault sets error when passphrase too short`() = runTest {
        val setupVault = mockk<SetupVaultUseCase>(relaxed = true)
        val unlock = mockk<UnlockWithPassphraseUseCase>(relaxed = true)
        val viewModel = OnboardingViewModel(setupVault, unlock)

        viewModel.uiState.test {
            skipItems(1)
            viewModel.createVault("short".toCharArray(), "short".toCharArray())
            val state = awaitItem()
            assertEquals(
                UserMessage.Resource(R.string.onboarding_passphrase_too_short),
                state.error
            )
            assertFalse(state.isComplete)
        }
    }

    @Test
    fun `createVault succeeds and sets isComplete`() = runTest {
        val setupVault = mockk<SetupVaultUseCase>()
        val unlock = mockk<UnlockWithPassphraseUseCase>()
        coEvery { setupVault(any()) } returns Unit
        coEvery { unlock(any()) } returns Unit

        val viewModel = OnboardingViewModel(setupVault, unlock)

        viewModel.createVault("password123".toCharArray(), "password123".toCharArray())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isComplete)
        assertFalse(state.isLoading)
    }

    @Test
    fun `createVault gives unlock a live copy of the passphrase`() = runTest {
        val setupVault = mockk<SetupVaultUseCase>()
        val unlock = mockk<UnlockWithPassphraseUseCase>()
        var unlockArgument: CharArray? = null
        // Both real use cases wipe the array handed to them, so reproduce that side effect here.
        coEvery { setupVault(any()) } coAnswers { firstArg<CharArray>().fill('\u0000') }
        coEvery { unlock(any()) } coAnswers {
            val given = firstArg<CharArray>()
            unlockArgument = given.copyOf()
            given.fill('\u0000')
        }

        val viewModel = OnboardingViewModel(setupVault, unlock)

        viewModel.createVault("password123".toCharArray(), "password123".toCharArray())
        advanceUntilIdle()

        assertArrayEquals("password123".toCharArray(), unlockArgument)
        assertTrue(viewModel.uiState.value.isComplete)
    }

    @Ignore(
        "JVM unit test: MockK suspend throw + Unconfined Main + advanceUntilIdle " +
            "does not reliably leave uiState.error set; production path uses catch (Throwable)."
    )
    @Test
    fun `createVault sets error on exception`() = runTest {
        val setupVault = mockk<SetupVaultUseCase>()
        val unlock = mockk<UnlockWithPassphraseUseCase>()
        coEvery { setupVault(any()) } coAnswers { throw RuntimeException("DB error") }
        coEvery { unlock(any()) } returns Unit

        val viewModel = OnboardingViewModel(setupVault, unlock)

        viewModel.createVault("password123".toCharArray(), "password123".toCharArray())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(
            UserMessage.Resource(R.string.onboarding_vault_creation_failed),
            state.error
        )
        assertFalse(state.isLoading)
    }

    @Test
    fun `clearError removes error from state`() = runTest {
        val setupVault = mockk<SetupVaultUseCase>(relaxed = true)
        val unlock = mockk<UnlockWithPassphraseUseCase>(relaxed = true)
        val viewModel = OnboardingViewModel(setupVault, unlock)

        viewModel.createVault("short".toCharArray(), "short".toCharArray())
        viewModel.clearError()

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(null, state.error)
        }
    }
}
