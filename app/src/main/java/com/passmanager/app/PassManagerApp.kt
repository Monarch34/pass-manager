package com.passmanager.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import com.passmanager.di.VaultDatabaseEntryPoint
import com.passmanager.security.VaultLockManager
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

@HiltAndroidApp
class PassManagerApp : Application() {

    @Inject
    lateinit var vaultLockManager: VaultLockManager

    override fun onCreate() {
        super.onCreate()

        // Ensure Room is opened on a background dispatcher before any Activity runs, so
        // Hilt's first injection path never does SQLite init on the UI thread (black screen / ANR).
        runBlocking {
            withContext(Dispatchers.IO) {
                EntryPointAccessors.fromApplication(this@PassManagerApp, VaultDatabaseEntryPoint::class.java)
                    .vaultDatabase()
                    .openHelper
                    .writableDatabase
            }
        }

        // Register VaultLockManager with the process lifecycle to enable auto-lock.
        ProcessLifecycleOwner.get().lifecycle.addObserver(vaultLockManager)
    }
}
