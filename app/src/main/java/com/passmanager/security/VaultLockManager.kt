package com.passmanager.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.passmanager.domain.port.AppSettingsPort
import com.passmanager.domain.model.LockState
import com.passmanager.domain.port.LockStateProvider
import com.passmanager.domain.port.VaultKeyProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultLockManager @Inject constructor(
    private val sessionManager: SessionManager,
    private val appSettings: AppSettingsPort
) : DefaultLifecycleObserver, VaultKeyProvider, LockStateProvider {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var autoLockJob: Job? = null

    private val keyLock = Any()
    @Volatile private var vaultKey: ByteArray? = null

    private val _lockState = MutableStateFlow<LockState>(LockState.ColdLocked)
    override val lockState: StateFlow<LockState> = _lockState.asStateFlow()

    override fun requireUnlockedKey(): ByteArray {
        synchronized(keyLock) {
            val key = vaultKey ?: throw IllegalStateException("Vault is locked")
            return key.copyOf()
        }
    }

    override fun unlock(vaultKey: ByteArray) {
        synchronized(keyLock) {
            this.vaultKey?.fill(0)
            this.vaultKey = vaultKey
        }
        _lockState.value = LockState.Unlocked
    }

    override fun lock() {
        synchronized(keyLock) {
            vaultKey?.fill(0)
            vaultKey = null
        }
        _lockState.value = if (sessionManager.hasUnlockedThisSession) {
            LockState.WarmLocked
        } else {
            LockState.ColdLocked
        }
    }

    fun forceColdLock() {
        synchronized(keyLock) {
            vaultKey?.fill(0)
            vaultKey = null
        }
        _lockState.value = LockState.ColdLocked
        sessionManager.invalidate()
    }

    override fun onStart(owner: LifecycleOwner) {
        autoLockJob?.cancel()
        autoLockJob = null
    }

    /**
     * Starts the auto-lock timer. If the app process is killed while
     * backgrounded, the in-memory vault key is destroyed with it — so
     * process death is the primary lock mechanism. This timer is a
     * supplementary guard for when the process stays alive.
     */
    override fun onStop(owner: LifecycleOwner) {
        autoLockJob = scope.launch {
            val timeoutSeconds = appSettings.autoLockTimeoutSeconds.first()
            delay(timeoutSeconds * 1000L)
            lock()
        }
    }
}
