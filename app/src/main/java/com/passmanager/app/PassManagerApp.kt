package com.passmanager.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.passmanager.di.VaultDatabaseEntryPoint
import com.passmanager.security.VaultLockManager
import com.passmanager.ui.common.AppLogger
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class PassManagerApp : Application() {

    @Inject
    lateinit var vaultLockManager: VaultLockManager

    /** Process-lifetime scope for startup work that must not hold up the first frame. */
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Warm Room up off the main thread so the first DB touch (CheckVaultSetupUseCase) does not
        // pay for opening the file and running migrations. This used to be a runBlocking call:
        // moving the work to an IO dispatcher and then *waiting* for it on the main thread still
        // blocked startup for the full open+migrate cost, it just did so on another thread. Now the
        // warm-up races the Activity instead of gating it.
        //
        // Correctness does not depend on this finishing first: every real read goes through a
        // suspending Room DAO, which Room dispatches on its own query executor, so nothing here
        // pushes SQLite work onto the UI thread either way.
        startupScope.launch {
            try {
                EntryPointAccessors.fromApplication(this@PassManagerApp, VaultDatabaseEntryPoint::class.java)
                    .vaultDatabase()
                    .openHelper
                    .writableDatabase
            } catch (e: Exception) {
                // A failure here is not fatal on its own — the first real query will hit the same
                // error on a path that reports it to the user. Crashing the process from a
                // background warm-up would only hide where the problem actually is.
                AppLogger.e("PassManagerApp", "Database warm-up failed", e)
            }
        }

        // Register VaultLockManager with the process lifecycle to enable auto-lock.
        ProcessLifecycleOwner.get().lifecycle.addObserver(vaultLockManager)
    }
}
