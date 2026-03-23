package com.passmanager.ui.vault

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.domain.model.VaultItemHeader
import com.passmanager.domain.repository.VaultRepository
import com.passmanager.domain.usecase.DecryptItemHeaderUseCase
import com.passmanager.domain.usecase.DecryptItemUseCase
import com.passmanager.security.LockState
import com.passmanager.security.VaultLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VaultListUiState(
    val items: List<VaultItemHeader> = emptyList(),
    val searchQuery: String = "",
    val filteredItems: List<VaultItemHeader> = emptyList(),
    val isLocked: Boolean = false,
    val isLoading: Boolean = true
)

@OptIn(FlowPreview::class)
@HiltViewModel
class VaultListViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val decryptItemUseCase: DecryptItemUseCase,
    private val decryptItemHeaderUseCase: DecryptItemHeaderUseCase,
    private val cipher: AesGcmCipher,
    private val vaultLockManager: VaultLockManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultListUiState())
    val uiState: StateFlow<VaultListUiState> = _uiState.asStateFlow()

    private val _titleCache = MutableStateFlow<Map<String, String>>(emptyMap())
    val titleCache: StateFlow<Map<String, String>> = _titleCache.asStateFlow()

    private val _addressCache = MutableStateFlow<Map<String, String>>(emptyMap())
    val addressCache: StateFlow<Map<String, String>> = _addressCache.asStateFlow()

    /** Tracks [VaultItemHeader.updatedAt] used when title/address were last decrypted per id. */
    private val _decryptCacheKey = MutableStateFlow<Map<String, Long>>(emptyMap())

    private val _searchQuery = MutableStateFlow("")
    private val _items = MutableStateFlow<List<VaultItemHeader>>(emptyList())

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
                    _titleCache.value = emptyMap()
                    _addressCache.value = emptyMap()
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
            _titleCache
        ) { items, query, cache ->
            if (query.isBlank()) items
            else items.filter { cache[it.id]?.contains(query, ignoreCase = true) == true }
        }.onEach { filtered ->
            _uiState.update { it.copy(filteredItems = filtered) }
        }.launchIn(viewModelScope)
    }

    private fun decryptHeaders(headers: List<VaultItemHeader>) {
        viewModelScope.launch(Dispatchers.Default) {
            val currentIds = headers.map { it.id }.toSet()
            val titles    = _titleCache.value.toMutableMap()
            val addresses = _addressCache.value.toMutableMap()
            val keys      = _decryptCacheKey.value.toMutableMap()

            // Prune entries for items that were deleted
            titles.keys.retainAll(currentIds)
            addresses.keys.retainAll(currentIds)
            keys.keys.retainAll(currentIds)

            // Only process headers whose cache is stale or missing
            val stale = headers.filter { h ->
                keys[h.id] != h.updatedAt || h.id !in titles
            }
            if (stale.isEmpty()) {
                _titleCache.value      = titles
                _addressCache.value    = addresses
                _decryptCacheKey.value = keys
                return@launch
            }

            data class HeaderResult(
                val id: String,
                val title: String,
                val address: String,
                val updatedAt: Long
            )

            coroutineScope {
                stale.map { header ->
                    async {
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
                                    ?: return@async null
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
                        } catch (e: Exception) {
                            Log.e("VaultListViewModel", "Failed to decrypt header ${header.id}", e)
                            null
                        }
                    }
                }.awaitAll().filterNotNull().forEach { r ->
                    titles[r.id]    = r.title
                    addresses[r.id] = r.address
                    keys[r.id]      = r.updatedAt
                }
            }

            _titleCache.value      = titles
            _addressCache.value    = addresses
            _decryptCacheKey.value = keys
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun lock() {
        vaultLockManager.lock()
    }
}
