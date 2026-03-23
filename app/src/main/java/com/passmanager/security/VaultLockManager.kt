package com.passmanager.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.passmanager.data.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central vault lock manager.
 *
 * - Holds the in-memory vault key as part of [LockState.Unlocked]
 * - Zeroes the vault key on lock
 * - Implements auto-lock timer via [DefaultLifecycleObserver] (ProcessLifecycleOwner)
 * - Determines ColdLocked vs WarmLocked based on [SessionManager]
 */
@Singleton
class VaultLockManager @Inject constructor(
    private val sessionManager: SessionManager,
    private val appPreferences: AppPreferences
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var autoLockJob: Job? = null

    private val _lockState = MutableStateFlow<LockState>(LockState.ColdLocked)
    val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    /** Returns the vault key if unlocked, or throws [IllegalStateException]. */
    fun requireUnlockedKey(): ByteArray {
        val state = _lockState.value
        check(state is LockState.Unlocked) { "Vault is locked" }
        return state.vaultKey
    }

    fun unlock(vaultKey: ByteArray) {
        _lockState.value = LockState.Unlocked(vaultKey)
    }

    fun lock() {
        val previous = _lockState.value
        _lockState.value = if (sessionManager.hasPassphraseUnlockedThisSession) LockState.WarmLocked
            else LockState.ColdLocked
        if (previous is LockState.Unlocked) previous.vaultKey.fill(0)
    }

    /** Force cold lock regardless of session state (e.g. biometric key invalidated). */
    fun forceColdLock() {
        val previous = _lockState.value
        _lockState.value = LockState.ColdLocked
        if (previous is LockState.Unlocked) previous.vaultKey.fill(0)
    }

    // ProcessLifecycleOwner callbacks — called when the app enters/leaves foreground

    override fun onStart(owner: LifecycleOwner) {
        // App came to foreground: cancel any pending auto-lock timer
        autoLockJob?.cancel()
        autoLockJob = null
    }

    override fun onStop(owner: LifecycleOwner) {
        // App went to background: start auto-lock countdown
        autoLockJob = scope.launch {
            val timeoutSeconds = appPreferences.autoLockTimeoutSeconds.first()
            delay(timeoutSeconds * 1000L)
            lock()
        }
    }
}
