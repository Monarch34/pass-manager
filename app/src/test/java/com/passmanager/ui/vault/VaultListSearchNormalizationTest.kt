package com.passmanager.ui.vault

import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.LockState
import com.passmanager.domain.model.VaultItemHeader
import com.passmanager.domain.model.VaultSortOrder
import com.passmanager.domain.port.AppSettingsPort
import com.passmanager.domain.port.LockStateProvider
import com.passmanager.domain.usecase.DeleteVaultItemsByIdsUseCase
import com.passmanager.domain.usecase.ObserveVaultHeadersUseCase
import com.passmanager.domain.usecase.ProcessVaultListHeadersUseCase
import com.passmanager.test.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Search behavior after the A0 normalization pass (precomputed lowercase title/address maps and
 * category label/dbKey). Search must stay a case-insensitive substring match over title, address,
 * category label and category dbKey — these tests exist so the optimization cannot quietly narrow
 * what a user can find.
 */
class VaultListSearchNormalizationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private data class Row(
        val id: String,
        val title: String,
        val address: String = "",
        val category: ItemCategory = ItemCategory.LOGIN
    )

    private fun headerOf(row: Row) = VaultItemHeader(
        id = row.id,
        encryptedTitle = ByteArray(16),
        titleIv = ByteArray(12),
        encryptedAddress = if (row.address.isEmpty()) null else ByteArray(16),
        addressIv = if (row.address.isEmpty()) null else ByteArray(12),
        category = row.category,
        updatedAt = 1L
    )

    /** Builds a view model whose decrypt stage returns [rows] verbatim. */
    private fun viewModelFor(
        rows: List<Row>,
        sortOrder: VaultSortOrder = VaultSortOrder.NAME_ASC
    ): VaultListViewModel {
        val headers = rows.map(::headerOf)
        val observeHeaders = mockk<ObserveVaultHeadersUseCase>()
        val deleteByIds = mockk<DeleteVaultItemsByIdsUseCase>()
        val processHeaders = mockk<ProcessVaultListHeadersUseCase>()
        val lockStateProvider = mockk<LockStateProvider>()
        val appSettings = mockk<AppSettingsPort>()

        every { appSettings.useGoogleFavicons } returns flowOf(true)
        every { appSettings.vaultListSort } returns flowOf(sortOrder)
        every { appSettings.vaultGroupFilter } returns flowOf(null)
        every { observeHeaders() } returns flowOf(headers)
        every { lockStateProvider.lockState } returns MutableStateFlow(LockState.Unlocked)
        coEvery { processHeaders(any()) } answers {
            val stale = firstArg<List<VaultItemHeader>>()
            val byId = rows.associateBy { it.id }
            ProcessVaultListHeadersUseCase.VaultListHeaderProcessOutcome(
                stale.mapNotNull { h ->
                    byId[h.id]?.let {
                        ProcessVaultListHeadersUseCase.DecryptedHeaderRow(
                            it.id, it.title, it.address, h.updatedAt
                        )
                    }
                },
                hadDecryptFailure = false
            )
        }

        return VaultListViewModel(
            observeHeaders,
            deleteByIds,
            processHeaders,
            lockStateProvider,
            appSettings
        )
    }

    /** Applies [query] and waits until the pipeline has produced [expectedIds] in order. */
    private suspend fun VaultListViewModel.idsFor(query: String, expectedIds: List<String>): List<String> {
        // Wait for the decrypt batch to land first, otherwise the filter runs against an empty cache.
        uiState.first { it.hasLoaded && it.headerDisplayCache.titlesLower.isNotEmpty() }
        setSearchQuery(query)
        val state = uiState.first { s ->
            s.searchQuery == query && s.filteredItems.map { it.id } == expectedIds
        }
        return state.filteredItems.map { it.id }
    }

    @Test
    fun `uppercase query matches lowercase title`() = runTest {
        val vm = viewModelFor(
            listOf(
                Row("1", "github account"),
                Row("2", "shopping list", category = ItemCategory.NOTE)
            )
        )
        assertEquals(listOf("1"), vm.idsFor("GITHUB", listOf("1")))
    }

    @Test
    fun `lowercase query matches uppercase title`() = runTest {
        val vm = viewModelFor(
            listOf(
                Row("1", "GitHub Account"),
                Row("2", "shopping list", category = ItemCategory.NOTE)
            )
        )
        assertEquals(listOf("1"), vm.idsFor("github", listOf("1")))
    }

    @Test
    fun `query matches address case-insensitively`() = runTest {
        val vm = viewModelFor(
            listOf(
                Row("1", "Work mail", address = "Mail.Example.COM"),
                Row("2", "shopping list", category = ItemCategory.NOTE)
            )
        )
        assertEquals(listOf("1"), vm.idsFor("example.com", listOf("1")))
    }

    @Test
    fun `Turkish dotted capital I query behaves as before`() = runTest {
        // "İş Bankası" must still be reachable by "iş"/"İŞ". A lowercase()-based fold would have
        // expanded İ into i + U+0307 and broken this; foldForSearch keeps it a single char.
        val vm = viewModelFor(
            listOf(
                Row("1", "İş Bankası", category = ItemCategory.BANK),
                Row("2", "shopping list", category = ItemCategory.NOTE)
            )
        )
        assertEquals(listOf("1"), vm.idsFor("iş", listOf("1")))
    }

    @Test
    fun `Turkish dotless i query behaves as before`() = runTest {
        val vm = viewModelFor(
            listOf(
                Row("1", "Ilık Su", category = ItemCategory.NOTE),
                Row("2", "github account")
            )
        )
        assertEquals(listOf("1"), vm.idsFor("ılık", listOf("1")))
    }

    @Test
    fun `category label query still matches`() = runTest {
        val vm = viewModelFor(
            listOf(
                Row("1", "github account"),
                Row("2", "shopping list", category = ItemCategory.NOTE)
            )
        )
        // "Note" appears in neither title; only the category label/dbKey can match it.
        assertEquals(listOf("2"), vm.idsFor("Note", listOf("2")))
    }

    @Test
    fun `category query is case-insensitive`() = runTest {
        val vm = viewModelFor(
            listOf(
                Row("1", "github account"),
                Row("2", "visa", category = ItemCategory.CARD)
            )
        )
        assertEquals(listOf("2"), vm.idsFor("CARD", listOf("2")))
    }

    @Test
    fun `blank query returns everything`() = runTest {
        val vm = viewModelFor(
            listOf(
                Row("1", "github account"),
                Row("2", "shopping list", category = ItemCategory.NOTE)
            )
        )
        assertEquals(listOf("1", "2"), vm.idsFor("   ", listOf("1", "2")))
    }

    @Test
    fun `non-matching query returns nothing`() = runTest {
        val vm = viewModelFor(
            listOf(
                Row("1", "github account"),
                Row("2", "shopping list", category = ItemCategory.NOTE)
            )
        )
        assertEquals(emptyList<String>(), vm.idsFor("zzz-no-match", emptyList()))
    }

    @Test
    fun `NAME_ASC sort stays case-insensitive using the precomputed titles`() = runTest {
        // Chosen so case-sensitive and case-insensitive orderings differ: raw ASCII would put
        // "Banana" (B=66) ahead of "apple" (a=97), giving 2,1,3 instead of 1,2,3.
        val vm = viewModelFor(
            listOf(
                Row("1", "apple"),
                Row("2", "Banana"),
                Row("3", "cherry")
            )
        )
        val state = vm.uiState.first { s ->
            s.hasLoaded && s.filteredItems.size == 3 && s.headerDisplayCache.titlesLower.size == 3
        }
        assertEquals(listOf("1", "2", "3"), state.filteredItems.map { it.id })
    }

    @Test
    fun `NAME_ASC sort breaks ties on id`() = runTest {
        val vm = viewModelFor(
            listOf(
                Row("b", "Same Title"),
                Row("a", "same title")
            )
        )
        val state = vm.uiState.first { s ->
            s.hasLoaded && s.filteredItems.size == 2 && s.headerDisplayCache.titlesLower.size == 2
        }
        assertEquals(listOf("a", "b"), state.filteredItems.map { it.id })
    }

    @Test
    fun `decrypt batch populates both plain and folded caches`() = runTest {
        val vm = viewModelFor(listOf(Row("1", "GitHub Account", address = "GitHub.com")))
        val state = vm.uiState.first { it.headerDisplayCache.titlesLower.containsKey("1") }
        assertEquals("GitHub Account", state.headerDisplayCache.titles["1"])
        assertEquals("github account", state.headerDisplayCache.titlesLower["1"])
        assertEquals("GitHub.com", state.headerDisplayCache.addresses["1"])
        assertEquals("github.com", state.headerDisplayCache.addressesLower["1"])
    }
}
