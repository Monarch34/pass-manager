package com.passmanager.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.VaultItemHeader
import com.passmanager.domain.model.VaultSortOrder
import com.passmanager.domain.port.AppSettingsDefaults
import com.passmanager.domain.port.AppSettingsPort
import com.passmanager.ui.common.AppLogger
import com.passmanager.ui.common.UserMessage
import com.passmanager.domain.usecase.DeleteVaultItemsByIdsUseCase
import com.passmanager.domain.usecase.ObserveVaultHeadersUseCase
import com.passmanager.domain.usecase.ProcessVaultListHeadersUseCase
import com.passmanager.domain.util.foldForSearch
import com.passmanager.R
import com.passmanager.domain.model.LockState
import com.passmanager.domain.port.LockStateProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable
import javax.inject.Inject

/** Idle time before a typed search query is applied to the list. */
private const val SEARCH_DEBOUNCE_MS = 300L

/** One-shot error presented as a snackbar from the vault list. */
sealed interface VaultListError {
    val message: UserMessage
    /** Partial header decryption failure during a batch decrypt. */
    data class DecryptWarning(override val message: UserMessage) : VaultListError
    /** Batch delete operation failure. */
    data class DeleteFailed(override val message: UserMessage) : VaultListError
}

@Immutable
data class VaultListUiState(
    val searchQuery: String = "",
    val filteredItems: List<VaultItemHeader> = emptyList(),
    /** Snapshot of decrypted titles/addresses for list rows (avoids a second StateFlow collect). */
    val headerDisplayCache: VaultListHeaderCache = VaultListHeaderCache(),
    val sortOrder: VaultSortOrder = VaultSortOrder.NAME_ASC,
    val categoryFilter: ItemCategory? = null,
    val isLocked: Boolean = false,
    /** True until the display pipeline has produced its first result; equivalent to `!hasLoaded`. */
    val isLoading: Boolean = true,
    /**
     * True once [filteredItems] reflects a real pipeline pass (headers loaded, cache and settings
     * applied). Before that the list is *unknown*, not *empty* — an empty-state component must not
     * claim "no items" while this is false.
     */
    val hasLoaded: Boolean = false,
    val useGoogleFavicons: Boolean = AppSettingsDefaults.USE_GOOGLE_FAVICONS,
    val selectedIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    /** One-shot snackbar for both decrypt failures and delete failures. */
    val error: VaultListError? = null
)

@Immutable
private data class VaultListPipelineResult(
    val filteredSorted: List<VaultItemHeader>,
    val sortOrder: VaultSortOrder,
    val categoryFilter: ItemCategory?,
    val headerCache: VaultListHeaderCache
)

@OptIn(FlowPreview::class)
@HiltViewModel
class VaultListViewModel @Inject constructor(
    private val observeVaultHeadersUseCase: ObserveVaultHeadersUseCase,
    private val deleteVaultItemsByIdsUseCase: DeleteVaultItemsByIdsUseCase,
    processVaultListHeadersUseCase: ProcessVaultListHeadersUseCase,
    private val lockStateProvider: LockStateProvider,
    private val appSettings: AppSettingsPort
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultListUiState())
    val uiState: StateFlow<VaultListUiState> = _uiState.asStateFlow()

    private val decryptionManager = VaultListDecryptionManager(processVaultListHeadersUseCase)

    private val _searchQuery = MutableStateFlow("")

    /**
     * `null` means "the headers query has not returned yet". The display pipeline filters that out,
     * so it cannot emit an empty result before the vault has actually been read — which is what made
     * the list flash its empty state on open.
     */
    private val _items = MutableStateFlow<List<VaultItemHeader>?>(null)

    init {
        // Forward decrypt warnings into _uiState
        decryptionManager.decryptWarning
            .onEach { msg ->
                if (msg != null) {
                    _uiState.update {
                        it.copy(error = VaultListError.DecryptWarning(msg))
                    }
                    decryptionManager.clearWarning()
                }
            }.launchIn(viewModelScope)

        viewModelScope.launch {
            observeVaultHeadersUseCase().collect { headers ->
                // Deliberately not mirrored into _uiState: the screen only ever renders
                // filteredItems, so publishing the raw list too recomposed the whole vault screen
                // a second time for every database emission without changing a single pixel.
                _items.value = headers
                if (lockStateProvider.lockState.value is LockState.Unlocked) {
                    decryptionManager.decryptHeaders(headers, viewModelScope)
                }
            }
        }
        viewModelScope.launch {
            lockStateProvider.lockState.collect { state ->
                val locked = state !is LockState.Unlocked
                _uiState.update { it.copy(isLocked = locked) }
                if (locked) {
                    decryptionManager.clearCache()
                    _uiState.update { it.copy(error = null) }
                } else {
                    decryptionManager.decryptHeaders(_items.value.orEmpty(), viewModelScope)
                }
            }
        }
        _searchQuery.onEach { query ->
            _uiState.update { it.copy(searchQuery = query) }
        }.launchIn(viewModelScope)
        combine(
            _items.filterNotNull(),
            // Debounce only what the user types. A fixed debounce also delayed the initial empty
            // query, so the list stayed empty for 300 ms after the vault opened and briefly showed
            // the "no items" copy over a populated vault.
            _searchQuery.debounce { query -> if (query.isEmpty()) 0L else SEARCH_DEBOUNCE_MS },
            decryptionManager.headerCache,
            appSettings.vaultListSort,
            appSettings.vaultGroupFilter
        ) { items, query, headerCache, sortOrder, groupFilter ->
            val filtered = filterBySearchAndGroup(items, query, headerCache, groupFilter)
            val sorted = sortVaultItems(filtered, sortOrder, headerCache.titlesLower)
            VaultListPipelineResult(sorted, sortOrder, groupFilter, headerCache)
        }.flowOn(Dispatchers.Default)
            .onEach { result ->
                _uiState.update {
                    it.copy(
                        filteredItems = result.filteredSorted,
                        headerDisplayCache = result.headerCache,
                        sortOrder = result.sortOrder,
                        categoryFilter = result.categoryFilter,
                        // First pipeline pass: filteredItems is now authoritative, so the list may
                        // stop showing the skeleton and may finally trust an empty result.
                        isLoading = false,
                        hasLoaded = true
                    )
                }
            }
            .launchIn(viewModelScope)
        appSettings.useGoogleFavicons
            .onEach { useGoogle -> _uiState.update { it.copy(useGoogleFavicons = useGoogle) } }
            .launchIn(viewModelScope)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: VaultSortOrder) {
        viewModelScope.launch {
            appSettings.setVaultListSort(order)
        }
    }

    fun setCategoryFilter(category: ItemCategory?) {
        viewModelScope.launch {
            appSettings.setVaultGroupFilter(category)
        }
    }

    fun enterSelectionMode(id: String) {
        _uiState.update { it.copy(selectedIds = setOf(id), isSelectionMode = true) }
    }

    fun toggleSelection(id: String) {
        _uiState.update { state ->
            val newIds = if (id in state.selectedIds) state.selectedIds - id else state.selectedIds + id
            state.copy(
                selectedIds = newIds,
                isSelectionMode = newIds.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet(), isSelectionMode = false) }
    }

    fun deleteSelected() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            try {
                deleteVaultItemsByIdsUseCase(ids)
                clearSelection()
            } catch (e: Exception) {
                AppLogger.e("VaultListViewModel", "Batch delete failed", e)
                _uiState.update {
                    it.copy(error = VaultListError.DeleteFailed(UserMessage.Resource(R.string.vault_delete_failed)))
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun lock() {
        lockStateProvider.lock()
    }
}

/**
 * Case-insensitive substring match on title, address, category label and category dbKey.
 *
 * Every operand is case-folded ahead of the inner loop — the query once per pipeline pass here,
 * titles/addresses once per decrypt batch in [VaultListDecryptionManager], the category strings
 * once at enum init — so the match itself is a plain, case-sensitive [String.contains]. The
 * previous version re-folded both operands on every comparison, i.e. up to 4x per item per
 * keystroke. Match results are unchanged; see `foldForSearch` for why it is not `lowercase()`.
 */
private fun filterBySearchAndGroup(
    items: List<VaultItemHeader>,
    query: String,
    headerCache: VaultListHeaderCache,
    groupFilter: ItemCategory?
): List<VaultItemHeader> {
    val q = query.trim().foldForSearch()
    return items.filter { item ->
        val groupOk = groupFilter == null || item.category == groupFilter
        if (!groupOk) return@filter false
        if (q.isEmpty()) return@filter true
        val title = headerCache.titlesLower[item.id].orEmpty()
        val address = headerCache.addressesLower[item.id].orEmpty()
        title.contains(q) ||
            address.contains(q) ||
            item.category.labelLower.contains(q) ||
            item.category.dbKeyLower.contains(q)
    }
}

private fun sortVaultItems(
    items: List<VaultItemHeader>,
    order: VaultSortOrder,
    titlesLower: Map<String, String>
): List<VaultItemHeader> {
    return when (order) {
        VaultSortOrder.NAME_ASC ->
            // The comparator runs O(n log n) times, so folding inside it allocated a fresh String
            // per comparison. The decrypt batch already produced the folded titles, so this now
            // reads them straight out of the cache and builds no per-emission map at all.
            items.sortedWith(
                compareBy<VaultItemHeader> { titlesLower[it.id].orEmpty() }
                    .thenBy { it.id }
            )
        VaultSortOrder.DATE_NEWEST -> items.sortedByDescending { it.updatedAt }
        VaultSortOrder.DATE_OLDEST -> items.sortedBy { it.updatedAt }
    }
}
