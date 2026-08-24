package com.passmanager.app

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.passmanager.ui.components.FAVICON_HOST
import okhttp3.OkHttpClient
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
class PassManagerApp : Application(), ImageLoaderFactory {

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

    /**
     * The image loader every site icon goes through, and the place two promises in Settings are
     * actually kept.
     *
     * Redirects are refused so that "no other host is contacted" is enforced rather than asserted:
     * Coil's default client follows 3xx silently, and a favicon endpoint that moves would hand the
     * user's vault domains to whatever host the redirect named. [FAVICON_HOST] is picked precisely
     * because it answers 200 directly and needs no redirect (see FaviconImage).
     *
     * No disk cache, for the same reason the vault encrypts every title and address: a disk cache
     * would leave the set of sites the user holds credentials for in cacheDir in the clear, both as
     * cached icons and as request URLs in Coil's journal. Icons live in memory for the process and
     * are re-fetched after a cold start, and only while the setting is on.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
            }
            .diskCache(null)
            .build()
}
