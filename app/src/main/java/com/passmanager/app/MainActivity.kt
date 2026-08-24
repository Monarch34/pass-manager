package com.passmanager.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import com.passmanager.BuildConfig
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.passmanager.navigation.AppNavigation
import com.passmanager.ui.theme.PassManagerTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * App-wide access to the runtime POST_NOTIFICATIONS prompt (Android 13+).
 *
 * The prompt only makes sense where the user has context for it — while a desktop pairing is being
 * set up, not on launch — but that flow lives outside this activity, so [MainActivity] owns the
 * launcher and publishes it here. Every entry point is a no-op while no activity is attached, so a
 * call from a torn-down screen can never crash.
 */
object NotificationPermissionRequester {

    private var launcher: ((String) -> Unit)? = null

    internal fun attach(launch: (String) -> Unit) {
        launcher = launch
    }

    /** Only the owner that attached [launch] may clear it, so overlapping activities cannot. */
    internal fun detach(launch: (String) -> Unit) {
        if (launcher === launch) launcher = null
    }

    /** True when notifications can be posted: already granted, or not gated on this API level. */
    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * Shows the system permission dialog when it is still needed. No-op below API 33, when the
     * permission is already granted, or while no activity is attached. Android stops showing the
     * dialog after a couple of dismissals, so call this from a moment the user can make sense of.
     */
    fun requestIfNeeded(context: Context) {
        if (isGranted(context)) return
        launcher?.invoke(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    // Must be registered before the activity is STARTED, hence the field initializer.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Nothing to undo on denial: notifications are a convenience, the pairing flow and its
            // in-app status UI keep working either way.
        }

    private val requestNotificationPermission: (String) -> Unit = { permission ->
        notificationPermissionLauncher.launch(permission)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent screenshots and screen-recorders from capturing sensitive data. Debug builds
        // are exempt on purpose: FLAG_SECURE also blanks `adb screencap`, which makes visual bugs
        // impossible to capture or bisect — the doubled list icon was only diagnosable from a
        // screenshot. The flag this protects users with is a release property; a debug build's
        // vault holds developer test data.
        if (!BuildConfig.DEBUG) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        NotificationPermissionRequester.attach(requestNotificationPermission)

        enableEdgeToEdge()

        setContent {
            // Static brand palette only - see PassManagerTheme for why Material You is not offered.
            PassManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onDestroy() {
        NotificationPermissionRequester.detach(requestNotificationPermission)
        super.onDestroy()
    }
}
