package com.passmanager.ui.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.passmanager.di.SecureClipboardEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copies sensitive vault values (credentials, PII, card data) to the system clipboard and clears
 * them again after [DEFAULT_CLEAR_AFTER_MS].
 *
 * The clear timer runs on an application scoped [CoroutineScope] on purpose: a composition scope
 * (e.g. [androidx.compose.runtime.rememberCoroutineScope]) is cancelled as soon as the sheet is
 * dismissed or the user navigates back, which is exactly the moment a copy happens, so the delayed
 * clear would never run and the secret would stay on the clipboard indefinitely.
 *
 * Injected as a `@Singleton` through [com.passmanager.di.ClipboardModule]; Composables obtain it via
 * [rememberSecureClipboard].
 */
class SecureClipboard(private val context: Context) {

    private val clipboard: ClipboardManager
        get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Pending auto-clear. A new copy replaces it, otherwise the older timer would clear the newer value. */
    private var clearJob: Job? = null

    /**
     * Puts [value] on the clipboard, flagged as sensitive, and schedules the clear after
     * [clearAfterMs]. A value of 0 or less copies without any auto-clear.
     */
    fun copy(value: String, clearAfterMs: Long = DEFAULT_CLEAR_AFTER_MS) {
        val clip = ClipData.newPlainText(CLIP_LABEL, value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ shows a copy preview at the bottom of the screen; without this flag the
            // password or card CVC is rendered there in plain text.
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)

        clearJob?.cancel()
        clearJob = if (clearAfterMs > 0L) {
            scope.launch {
                delay(clearAfterMs)
                clearIfOurs()
            }
        } else {
            null
        }
    }

    /**
     * Clears the clipboard unless we can positively see that someone else replaced our clip: the
     * user may have copied something in the meantime and wiping that would be hostile.
     *
     * The check deliberately defaults to clearing when the label cannot be read. From API 29 the
     * clipboard is only readable while the app has focus, and the clear fires exactly when the user
     * has switched away to paste — so `primaryClipDescription` is normally null right here. Treating
     * "unreadable" as "not ours" would skip every clear that matters and leave the secret on the
     * clipboard, which is the bug this timer exists to prevent. Writing is not focus restricted, so
     * the clear itself still goes through.
     *
     * Uses [ClipboardManager.clearPrimaryClip] on API 28+ and overwrites with empty text below that.
     */
    private fun clearIfOurs() {
        val label = runCatching { clipboard.primaryClipDescription?.label?.toString() }.getOrNull()
        if (label != null && label != CLIP_LABEL) return

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }

    companion object {
        /**
         * Clip label used for every copy. It is visible to other apps and to system clipboard UIs,
         * so it stays neutral instead of naming the field or the site the value belongs to.
         */
        private const val CLIP_LABEL = "Password Manager"

        /** Vault item details use the same 15s clear for all copy actions. */
        const val DEFAULT_CLEAR_AFTER_MS = 15_000L
    }
}

/**
 * Resolves the application scoped [SecureClipboard] from the Hilt singleton graph. It is not held by
 * a ViewModel so the clear timer is independent of both composition and ViewModel lifetime.
 */
@Composable
fun rememberSecureClipboard(): SecureClipboard {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SecureClipboardEntryPoint::class.java
        ).secureClipboard()
    }
}
