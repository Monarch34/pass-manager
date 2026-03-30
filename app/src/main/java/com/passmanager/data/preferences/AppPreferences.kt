package com.passmanager.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.VaultSortOrder
import com.passmanager.domain.port.AppSettingsDefaults
import com.passmanager.domain.port.AppSettingsPort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) : AppSettingsPort {
    companion object {
        private val AUTO_LOCK_TIMEOUT_SECONDS = intPreferencesKey("auto_lock_timeout_seconds")
        private val USE_GOOGLE_FAVICONS = booleanPreferencesKey("use_google_favicons")
        private val VAULT_LIST_SORT = stringPreferencesKey("vault_list_sort")
        private val VAULT_GROUP_FILTER = stringPreferencesKey("vault_group_filter")
        private const val ALL_CATEGORIES = "__all__"
    }

    override val autoLockTimeoutSeconds: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[AUTO_LOCK_TIMEOUT_SECONDS] ?: AppSettingsDefaults.AUTO_LOCK_SECONDS
    }

    override val useGoogleFavicons: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[USE_GOOGLE_FAVICONS] ?: AppSettingsDefaults.USE_GOOGLE_FAVICONS
    }

    override val vaultListSort: Flow<VaultSortOrder> = context.dataStore.data.map { preferences ->
        when (preferences[VAULT_LIST_SORT]) {
            VaultSortOrder.DATE_NEWEST.name -> VaultSortOrder.DATE_NEWEST
            VaultSortOrder.DATE_OLDEST.name -> VaultSortOrder.DATE_OLDEST
            else -> VaultSortOrder.NAME_ASC
        }
    }

    /** `null` means all groups (categories). */
    override val vaultGroupFilter: Flow<ItemCategory?> = context.dataStore.data.map { preferences ->
        val raw = preferences[VAULT_GROUP_FILTER] ?: ALL_CATEGORIES
        if (raw == ALL_CATEGORIES) {
            null
        } else {
            ItemCategory.entries.firstOrNull { it.name == raw }
        }
    }

    override suspend fun setAutoLockTimeout(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_LOCK_TIMEOUT_SECONDS] = seconds
        }
    }

    override suspend fun setUseGoogleFavicons(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_GOOGLE_FAVICONS] = value
        }
    }

    override suspend fun setVaultListSort(order: VaultSortOrder) {
        context.dataStore.edit { preferences ->
            preferences[VAULT_LIST_SORT] = order.name
        }
    }

    override suspend fun setVaultGroupFilter(category: ItemCategory?) {
        context.dataStore.edit { preferences ->
            preferences[VAULT_GROUP_FILTER] = category?.name ?: ALL_CATEGORIES
        }
    }
}
