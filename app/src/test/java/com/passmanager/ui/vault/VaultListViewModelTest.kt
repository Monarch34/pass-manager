package com.passmanager.ui.vault

import kotlinx.coroutines.flow.first
import com.passmanager.domain.port.AppSettingsPort
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.LockState
import com.passmanager.domain.model.VaultItemHeader
import com.passmanager.domain.model.VaultSortOrder
import com.passmanager.domain.port.LockStateProvider
import com.passmanager.domain.usecase.DeleteVaultItemsByIdsUseCase
import com.passmanager.domain.usecase.ObserveVaultHeadersUseCase
import com.passmanager.domain.usecase.ProcessVaultListHeadersUseCase
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
        category = ItemCategory.LOGIN,
        updatedAt = 1L
    )

    @Test
    fun `loads items and sets isLoading false`() = runTest {
        val h = header()
        val observeHeaders = mockk<ObserveVaultHeadersUseCase>()
        val deleteByIds = mockk<DeleteVaultItemsByIdsUseCase>()
        val processHeaders = mockk<ProcessVaultListHeadersUseCase>()
        val lockStateProvider = mockk<LockStateProvider>()
        val appSettings = mockk<AppSettingsPort>()
        every { appSettings.useGoogleFavicons } returns flowOf(true)
        every { appSettings.vaultListSort } returns flowOf(VaultSortOrder.NAME_ASC)
        every { appSettings.vaultGroupFilter } returns flowOf(null)

        every { observeHeaders() } returns flowOf(listOf(h))
        coEvery { processHeaders(any()) } returns ProcessVaultListHeadersUseCase.VaultListHeaderProcessOutcome(
            listOf(
                ProcessVaultListHeadersUseCase.DecryptedHeaderRow(h.id, "Test", "", h.updatedAt)
            ),
            hadDecryptFailure = false
        )
        every { lockStateProvider.lockState } returns MutableStateFlow(LockState.Unlocked)

        val viewModel = VaultListViewModel(
            observeHeaders,
            deleteByIds,
            processHeaders,
            lockStateProvider,
            appSettings
        )

        viewModel.uiState.first { !it.isLoading && it.filteredItems == listOf(h) }
        val state = viewModel.uiState.first { it.headerDisplayCache.titles[h.id] == "Test" }
        assertFalse(state.isLoading)
        assertEquals(listOf(h), state.filteredItems)
        assertEquals("Test", state.headerDisplayCache.titles[h.id])
    }

    @Test
    fun `setSearchQuery updates searchQuery in state`() = runTest {
        val observeHeaders = mockk<ObserveVaultHeadersUseCase>()
        val deleteByIds = mockk<DeleteVaultItemsByIdsUseCase>()
        val processHeaders = mockk<ProcessVaultListHeadersUseCase>()
        val lockStateProvider = mockk<LockStateProvider>()
        val appSettings = mockk<AppSettingsPort>()
        every { appSettings.useGoogleFavicons } returns flowOf(true)
        every { appSettings.vaultListSort } returns flowOf(VaultSortOrder.NAME_ASC)
        every { appSettings.vaultGroupFilter } returns flowOf(null)

        every { observeHeaders() } returns flowOf(emptyList())
        coEvery { processHeaders(any()) } returns ProcessVaultListHeadersUseCase.VaultListHeaderProcessOutcome(
            emptyList(),
            hadDecryptFailure = false
        )
        every { lockStateProvider.lockState } returns MutableStateFlow(LockState.Unlocked)

        val viewModel = VaultListViewModel(
            observeHeaders,
            deleteByIds,
            processHeaders,
            lockStateProvider,
            appSettings
        )

        viewModel.uiState.first { !it.isLoading }
        viewModel.setSearchQuery("test")
        assertEquals("test", viewModel.uiState.value.searchQuery)
    }

    @Test
    fun `lock sets isLocked when lockState changes`() = runTest {
        val lockStateFlow = MutableStateFlow<LockState>(LockState.Unlocked)
        val observeHeaders = mockk<ObserveVaultHeadersUseCase>()
        val deleteByIds = mockk<DeleteVaultItemsByIdsUseCase>()
        val processHeaders = mockk<ProcessVaultListHeadersUseCase>()
        val lockStateProvider = mockk<LockStateProvider>()
        val appSettings = mockk<AppSettingsPort>()
        every { appSettings.useGoogleFavicons } returns flowOf(true)
        every { appSettings.vaultListSort } returns flowOf(VaultSortOrder.NAME_ASC)
        every { appSettings.vaultGroupFilter } returns flowOf(null)

        every { observeHeaders() } returns flowOf(emptyList())
        coEvery { processHeaders(any()) } returns ProcessVaultListHeadersUseCase.VaultListHeaderProcessOutcome(
            emptyList(),
            hadDecryptFailure = false
        )
        every { lockStateProvider.lockState } returns lockStateFlow
        every { lockStateProvider.lock() } returns Unit

        val viewModel = VaultListViewModel(
            observeHeaders,
            deleteByIds,
            processHeaders,
            lockStateProvider,
            appSettings
        )

        viewModel.uiState.first { !it.isLoading }
        lockStateFlow.value = LockState.ColdLocked
        assertTrue(viewModel.uiState.value.isLocked)
    }
}
