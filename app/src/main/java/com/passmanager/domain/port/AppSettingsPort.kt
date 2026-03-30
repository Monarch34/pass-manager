package com.passmanager.domain.port

import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.VaultSortOrder
import kotlinx.coroutines.flow.Flow

/** Default values for UI state before first DataStore emission — keep in domain, not data layer. */
object AppSettingsDefaults {
    const val AUTO_LOCK_SECONDS = 300
    const val USE_GOOGLE_FAVICONS = false
}

/**
 * User-tunable app settings backed by DataStore.
 * Implemented by [com.passmanager.data.preferences.AppPreferences].
 */
interface AppSettingsPort {
    val autoLockTimeoutSeconds: Flow<Int>
    val useGoogleFavicons: Flow<Boolean>
    val vaultListSort: Flow<VaultSortOrder>
    val vaultGroupFilter: Flow<ItemCategory?>

    suspend fun setAutoLockTimeout(seconds: Int)
    suspend fun setUseGoogleFavicons(value: Boolean)
    suspend fun setVaultListSort(order: VaultSortOrder)
    suspend fun setVaultGroupFilter(category: ItemCategory?)
}
