package com.passmanager.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val AUTO_LOCK_TIMEOUT_SECONDS = intPreferencesKey("auto_lock_timeout_seconds")
        const val DEFAULT_AUTO_LOCK_SECONDS = 300 // 5 minutes
    }

    val autoLockTimeoutSeconds: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[AUTO_LOCK_TIMEOUT_SECONDS] ?: DEFAULT_AUTO_LOCK_SECONDS
    }

    suspend fun setAutoLockTimeout(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_LOCK_TIMEOUT_SECONDS] = seconds
        }
    }
}
