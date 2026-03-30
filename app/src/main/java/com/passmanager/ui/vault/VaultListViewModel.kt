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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.runtime.Immutable
import javax.inject.Inject

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
    val items: List<VaultItemHeader> = emptyList(),
    val searchQuery: String = "",
    val filteredItems: List<VaultItemHeader> = emptyList(),
    /** Snapshot of decrypted titles/addresses for list rows (avoids a second StateFlow collect). */
    val headerDisplayCache: VaultListHeaderCache = VaultListHeaderCache(),
    val sortOrder: VaultSortOrder = VaultSortOrder.NAME_ASC,
    val categoryFilter: ItemCategory? = null,
    val isLocked: Boolean = false,
    val isLoading: Boolean = true,
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
    private val _items = MutableStateFlow<List<VaultItemHeader>>(emptyList())

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
                _items.value = headers
                _uiState.update { it.copy(items = headers, isLoading = false) }
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
                    decryptionManager.decryptHeaders(_items.value, viewModelScope)
                }
            }
        }
        _searchQuery.onEach { query ->
            _uiState.update { it.copy(searchQuery = query) }
        }.launchIn(viewModelScope)
        combine(
            _items,
            _searchQuery.debounce(300),
            decryptionManager.headerCache,
            appSettings.vaultListSort,
            appSettings.vaultGroupFilter
        ) { items, query, headerCache, sortOrder, groupFilter ->
            val filtered = filterBySearchAndGroup(items, query, headerCache, groupFilter)
            val sorted = sortVaultItems(filtered, sortOrder, headerCache.titles)
            VaultListPipelineResult(sorted, sortOrder, groupFilter, headerCache)
        }.flowOn(Dispatchers.Default)
            .onEach { result ->
                _uiState.update {
                    it.copy(
                        filteredItems = result.filteredSorted,
                        headerDisplayCache = result.headerCache,
                        sortOrder = result.sortOrder,
                        categoryFilter = result.categoryFilter
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

private fun filterBySearchAndGroup(
    items: List<VaultItemHeader>,
    query: String,
    headerCache: VaultListHeaderCache,
    groupFilter: ItemCategory?
): List<VaultItemHeader> {
    return items.filter { item ->
        val groupOk = groupFilter == null || item.category == groupFilter
        if (!groupOk) return@filter false
        if (query.isBlank()) return@filter true
        val q = query.trim()
        val title = headerCache.titles[item.id].orEmpty()
        val address = headerCache.addresses[item.id].orEmpty()
        title.contains(q, ignoreCase = true) ||
            address.contains(q, ignoreCase = true) ||
            item.category.label.contains(q, ignoreCase = true) ||
            item.category.dbKey.contains(q, ignoreCase = true)
    }
}

private fun sortVaultItems(
    items: List<VaultItemHeader>,
    order: VaultSortOrder,
    titles: Map<String, String>
): List<VaultItemHeader> {
    return when (order) {
        VaultSortOrder.NAME_ASC -> items.sortedWith(
            compareBy<VaultItemHeader> { titles[it.id].orEmpty().lowercase() }
                .thenBy { it.id }
        )
        VaultSortOrder.DATE_NEWEST -> items.sortedByDescending { it.updatedAt }
        VaultSortOrder.DATE_OLDEST -> items.sortedBy { it.updatedAt }
    }
}
