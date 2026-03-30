package com.passmanager.ui.vault

import com.passmanager.R
import com.passmanager.domain.model.VaultItemHeader
import com.passmanager.domain.usecase.ProcessVaultListHeadersUseCase
import com.passmanager.ui.common.AppLogger
import com.passmanager.ui.common.UserMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages incremental decryption of vault list header columns (title + address).
 *
 * Responsibilities:
 * - Keeps a stale-tracking cache keyed by `(id -> updatedAt)` to avoid re-decrypting unchanged items.
 * - Prunes cache entries for items that have been deleted from the list.
 * - Dispatches batch decrypt work on [Dispatchers.Default] via [ProcessVaultListHeadersUseCase].
 * - Exposes results via [headerCache] and one-shot decrypt failures via [decryptWarning].
 *
 * This class deliberately owns no [CoroutineScope]; callers pass their scope to [decryptHeaders].
 */
class VaultListDecryptionManager(
    private val processVaultListHeadersUseCase: ProcessVaultListHeadersUseCase
) {

    private val _headerCache = MutableStateFlow(VaultListHeaderCache())
    /** Snapshot of decrypted titles/addresses for vault list rows. */
    val headerCache: StateFlow<VaultListHeaderCache> = _headerCache.asStateFlow()

    private val _decryptWarning = MutableStateFlow<UserMessage?>(null)
    /** One-shot decrypt-failure warning; caller must clear after display. */
    val decryptWarning: StateFlow<UserMessage?> = _decryptWarning.asStateFlow()

    /** Tracks `updatedAt` timestamp per item id used when the entry was last decrypted. */
    private val decryptCacheKey = MutableStateFlow<Map<String, Long>>(emptyMap())

    private var decryptJob: Job? = null

    /**
     * Triggers an incremental decrypt pass for [headers] using [scope].
     * Cancels any in-flight pass before starting a new one.
     */
    fun decryptHeaders(headers: List<VaultItemHeader>, scope: CoroutineScope) {
        decryptJob?.cancel()
        decryptJob = scope.launch(Dispatchers.Default) {
            val currentIds = headers.map { it.id }.toSet()
            val prev = _headerCache.value
            val titles   = prev.titles.toMutableMap()
            val addresses = prev.addresses.toMutableMap()
            val keys     = decryptCacheKey.value.toMutableMap()

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
                decryptCacheKey.value = keys
                return@launch
            }

            val outcome = try {
                processVaultListHeadersUseCase(stale)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Batch decrypt error", e)
                _decryptWarning.value = UserMessage.Resource(R.string.vault_list_decrypt_partial)
                return@launch
            }

            outcome.rows.forEach { r ->
                titles[r.id]    = r.title
                addresses[r.id] = r.address
                keys[r.id]      = r.updatedAt
            }

            _headerCache.value = VaultListHeaderCache(HashMap(titles), HashMap(addresses))
            decryptCacheKey.value = keys

            if (outcome.hadDecryptFailure) {
                _decryptWarning.value = UserMessage.Resource(R.string.vault_list_decrypt_partial)
            }
        }
    }

    /** Clears cached decryption data — called when the vault is locked. */
    fun clearCache() {
        decryptJob?.cancel()
        _headerCache.value  = VaultListHeaderCache()
        decryptCacheKey.value = emptyMap()
    }

    /** Acknowledges and clears the one-shot decrypt warning. */
    fun clearWarning() {
        _decryptWarning.value = null
    }

    private companion object {
        private const val TAG = "VaultListDecryptMgr"
    }
}
