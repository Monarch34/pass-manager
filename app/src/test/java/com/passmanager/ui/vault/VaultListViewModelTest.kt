package com.passmanager.ui.vault

import app.cash.turbine.test
import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.domain.model.VaultItemHeader
import com.passmanager.domain.repository.VaultRepository
import com.passmanager.domain.usecase.DecryptItemHeaderUseCase
import com.passmanager.domain.usecase.DecryptItemUseCase
import com.passmanager.security.LockState
import com.passmanager.security.VaultLockManager
import com.passmanager.test.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VaultListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun header(id: String = "1") = VaultItemHeader(
        id = id,
        encryptedTitle = ByteArray(16),
        titleIv = ByteArray(12),
        encryptedAddress = null,
        addressIv = null,
        category = "login",
        updatedAt = 1L
    )

    @Test
    fun `loads items and sets isLoading false`() = runTest {
        val h = header()
        val vaultRepo = mockk<VaultRepository>()
        val decryptUseCase = mockk<DecryptItemUseCase>(relaxed = true)
        val decryptHeaderUseCase = mockk<DecryptItemHeaderUseCase>()
        val cipher = mockk<AesGcmCipher>(relaxed = true)
        val vaultLockManager = mockk<VaultLockManager>()

        every { vaultRepo.observeHeaders() } returns flowOf(listOf(h))
        coEvery { decryptHeaderUseCase(any()) } returns DecryptItemHeaderUseCase.Result("Test", "")
        every { vaultLockManager.lockState } returns MutableStateFlow(LockState.Unlocked(ByteArray(32)))

        val viewModel = VaultListViewModel(
            vaultRepo,
            decryptUseCase,
            decryptHeaderUseCase,
            cipher,
            vaultLockManager
        )

        viewModel.uiState.test {
            skipItems(1)
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertEquals(listOf(h), state.filteredItems)
        }
    }

    @Test
    fun `setSearchQuery updates searchQuery in state`() = runTest {
        val vaultRepo = mockk<VaultRepository>()
        val decryptUseCase = mockk<DecryptItemUseCase>(relaxed = true)
        val decryptHeaderUseCase = mockk<DecryptItemHeaderUseCase>(relaxed = true)
        val cipher = mockk<AesGcmCipher>(relaxed = true)
        val vaultLockManager = mockk<VaultLockManager>()

        every { vaultRepo.observeHeaders() } returns flowOf(emptyList())
        every { vaultLockManager.lockState } returns MutableStateFlow(LockState.Unlocked(ByteArray(32)))

        val viewModel = VaultListViewModel(
            vaultRepo,
            decryptUseCase,
            decryptHeaderUseCase,
            cipher,
            vaultLockManager
        )

        viewModel.uiState.test {
            skipItems(1)
            viewModel.setSearchQuery("test")
            val state = awaitItem()
            assertEquals("test", state.searchQuery)
        }
    }

    @Test
    fun `lock sets isLocked when lockState changes`() = runTest {
        val lockStateFlow = MutableStateFlow<LockState>(LockState.Unlocked(ByteArray(32)))
        val vaultRepo = mockk<VaultRepository>()
        val decryptUseCase = mockk<DecryptItemUseCase>(relaxed = true)
        val decryptHeaderUseCase = mockk<DecryptItemHeaderUseCase>(relaxed = true)
        val cipher = mockk<AesGcmCipher>(relaxed = true)
        val vaultLockManager = mockk<VaultLockManager>()

        every { vaultRepo.observeHeaders() } returns flowOf(emptyList())
        every { vaultLockManager.lockState } returns lockStateFlow
        every { vaultLockManager.lock() } returns Unit

        val viewModel = VaultListViewModel(
            vaultRepo,
            decryptUseCase,
            decryptHeaderUseCase,
            cipher,
            vaultLockManager
        )

        viewModel.uiState.test {
            skipItems(1)
            lockStateFlow.value = LockState.ColdLocked
            val state = awaitItem()
            assertTrue(state.isLocked)
        }
    }
}
