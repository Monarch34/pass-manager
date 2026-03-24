package com.passmanager.ui.vault

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.VaultItemHeader
import com.passmanager.domain.model.VaultSortOrder
import com.passmanager.data.preferences.AppPreferences
import com.passmanager.domain.repository.VaultRepository
import com.passmanager.domain.usecase.DecryptItemHeaderUseCase
import com.passmanager.domain.usecase.DecryptItemUseCase
import com.passmanager.security.LockState
import com.passmanager.security.VaultLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject

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
    val useGoogleFavicons: Boolean = AppPreferences.DEFAULT_USE_GOOGLE_FAVICONS
)

private data class VaultListPipelineResult(
    val filteredSorted: List<VaultItemHeader>,
    val sortOrder: VaultSortOrder,
    val categoryFilter: ItemCategory?,
    val headerCache: VaultListHeaderCache
)

private const val MAX_CONCURRENT_HEADER_DECRYPT = 4

@OptIn(FlowPreview::class)
@HiltViewModel
class VaultListViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val decryptItemUseCase: DecryptItemUseCase,
    private val decryptItemHeaderUseCase: DecryptItemHeaderUseCase,
    private val cipher: AesGcmCipher,
    private val vaultLockManager: VaultLockManager,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultListUiState())
    val uiState: StateFlow<VaultListUiState> = _uiState.asStateFlow()

    private val _headerCache = MutableStateFlow(VaultListHeaderCache())

    /** Tracks [VaultItemHeader.updatedAt] used when title/address were last decrypted per id. */
    private val _decryptCacheKey = MutableStateFlow<Map<String, Long>>(emptyMap())

    private val _searchQuery = MutableStateFlow("")
    private val _items = MutableStateFlow<List<VaultItemHeader>>(emptyList())
    private var decryptJob: Job? = null

    init {
        viewModelScope.launch {
            vaultRepository.observeHeaders().collect { headers ->
                _items.value = headers
                _uiState.update { it.copy(items = headers, isLoading = false) }
                decryptHeaders(headers)
            }
        }
        viewModelScope.launch {
            vaultLockManager.lockState.collect { state ->
                if (state !is LockState.Unlocked) {
                    _uiState.update { it.copy(isLocked = true) }
                    _headerCache.value = VaultListHeaderCache()
                    _decryptCacheKey.value = emptyMap()
                }
            }
        }
        _searchQuery.onEach { query ->
            _uiState.update { it.copy(searchQuery = query) }
        }.launchIn(viewModelScope)
        combine(
            _items,
            _searchQuery.debounce(300),
            _headerCache,
            appPreferences.vaultListSort,
            appPreferences.vaultGroupFilter
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
        appPreferences.useGoogleFavicons
            .onEach { useGoogle -> _uiState.update { it.copy(useGoogleFavicons = useGoogle) } }
            .launchIn(viewModelScope)
    }

    private fun decryptHeaders(headers: List<VaultItemHeader>) {
        decryptJob?.cancel()
        decryptJob = viewModelScope.launch(Dispatchers.Default) {
            val currentIds = headers.map { it.id }.toSet()
            val prev = _headerCache.value
            val titles = prev.titles.toMutableMap()
            val addresses = prev.addresses.toMutableMap()
            val keys = _decryptCacheKey.value.toMutableMap()

            // Prune entries for items that were deleted
            titles.keys.retainAll(currentIds)
            addresses.keys.retainAll(currentIds)
            keys.keys.retainAll(currentIds)

            // Only process headers whose cache is stale or missing
            val stale = headers.filter { h ->
                keys[h.id] != h.updatedAt || h.id !in titles
            }
            if (stale.isEmpty()) {
                _headerCache.value = VaultListHeaderCache(titles, addresses)
                _decryptCacheKey.value = keys
                return@launch
            }

            data class HeaderResult(
                val id: String,
                val title: String,
                val address: String,
                val updatedAt: Long
            )

            val decryptSemaphore = Semaphore(MAX_CONCURRENT_HEADER_DECRYPT)
            coroutineScope {
                stale.map { header ->
                    async {
                        decryptSemaphore.withPermit {
                            try {
                                if (header.encryptedTitle != null) {
                                    // Fast path: decrypt only the small per-field blobs
                                    val r = decryptItemHeaderUseCase(header)
                                    HeaderResult(
                                        id = header.id,
                                        title = r.title ?: "",
                                        address = r.address ?: "",
                                        updatedAt = header.updatedAt
                                    )
                                } else {
                                    // Legacy path: fall back to full blob decryption, then backfill
                                    val fullItem = vaultRepository.getById(header.id)
                                    if (fullItem == null) {
                                        null
                                    } else {
                                        val decrypted = decryptItemUseCase(fullItem)

                                        // Backfill header columns so future loads use the fast path
                                        val vaultKey = vaultLockManager.requireUnlockedKey()
                                        val titleBytes = decrypted.title.toByteArray(Charsets.UTF_8)
                                        val encTitle = cipher.encrypt(titleBytes, vaultKey)
                                        titleBytes.fill(0)

                                        val encAddress = if (decrypted.address.isNotEmpty()) {
                                            val addrBytes = decrypted.address.toByteArray(Charsets.UTF_8)
                                            cipher.encrypt(addrBytes, vaultKey).also { addrBytes.fill(0) }
                                        } else null

                                        vaultRepository.updateHeaderColumns(
                                            id = header.id,
                                            encryptedTitle = encTitle.ciphertext,
                                            titleIv = encTitle.iv,
                                            encryptedAddress = encAddress?.ciphertext,
                                            addressIv = encAddress?.iv
                                        )

                                        HeaderResult(
                                            id = header.id,
                                            title = decrypted.title,
                                            address = decrypted.address,
                                            updatedAt = header.updatedAt
                                        )
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("VaultListViewModel", "Failed to decrypt header ${header.id}", e)
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull().forEach { r ->
                    titles[r.id]    = r.title
                    addresses[r.id] = r.address
                    keys[r.id]      = r.updatedAt
                }
            }

            _headerCache.value = VaultListHeaderCache(HashMap(titles), HashMap(addresses))
            _decryptCacheKey.value = keys
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: VaultSortOrder) {
        viewModelScope.launch {
            appPreferences.setVaultListSort(order)
        }
    }

    fun setCategoryFilter(category: ItemCategory?) {
        viewModelScope.launch {
            appPreferences.setVaultGroupFilter(category)
        }
    }

    fun lock() {
        vaultLockManager.lock()
    }
}

private fun filterBySearchAndGroup(
    items: List<VaultItemHeader>,
    query: String,
    headerCache: VaultListHeaderCache,
    groupFilter: ItemCategory?
): List<VaultItemHeader> {
    return items.filter { item ->
        val groupOk = groupFilter == null || ItemCategory.fromString(item.category) == groupFilter
        if (!groupOk) return@filter false
        if (query.isBlank()) return@filter true
        val q = query.trim()
        val title = headerCache.titles[item.id].orEmpty()
        val address = headerCache.addresses[item.id].orEmpty()
        val cat = ItemCategory.fromString(item.category)
        title.contains(q, ignoreCase = true) ||
            address.contains(q, ignoreCase = true) ||
            cat.label.contains(q, ignoreCase = true) ||
            item.category.contains(q, ignoreCase = true)
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
