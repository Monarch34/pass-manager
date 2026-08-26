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

    /** When the vault was last exported to a `.pmvault` file, or `null` if it never was. */
    val lastExportAtMs: Flow<Long?>

    /**
     * True once the user has turned down the one-time device-binding prompt. It never asks
     * again after that — the offer stays available in Settings, but nagging someone who has
     * already said no is how a security prompt gets dismissed on reflex.
     */
    val deviceBindingPromptDeclined: Flow<Boolean>

    /** When a backup reminder was last dismissed, so it stays quiet for a while afterwards. */
    val backupReminderSnoozedAtMs: Flow<Long?>

    suspend fun setAutoLockTimeout(seconds: Int)
    suspend fun setUseGoogleFavicons(value: Boolean)
    suspend fun setVaultListSort(order: VaultSortOrder)
    suspend fun setVaultGroupFilter(category: ItemCategory?)
    suspend fun setLastExportAt(epochMillis: Long)
    suspend fun setDeviceBindingPromptDeclined(declined: Boolean)
    suspend fun setBackupReminderSnoozedAt(epochMillis: Long)
}
