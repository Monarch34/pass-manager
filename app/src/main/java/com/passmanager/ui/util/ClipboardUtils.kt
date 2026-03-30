package com.passmanager.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copies [value] to the system clipboard with an optional auto-clear after [clearAfterMs].
 * Auto-clear uses [ClipboardManager.clearPrimaryClip] on API 28+ or overwrites with empty text.
 *
 * Vault item details use a 15s clear for all copy actions (credentials, PII, card data) so the
 * clipboard is not left populated indefinitely.
 *
 * When [clearAfterMs] is greater than 0, [scope] must be provided (e.g. [rememberCoroutineScope] in
 * Compose) so the delayed clear is cancelled if the caller is disposed.
 */
fun copyToClipboard(
    context: Context,
    label: String,
    value: String,
    clearAfterMs: Long = 0L,
    scope: CoroutineScope? = null
) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))

    if (clearAfterMs > 0L) {
        val jobScope = requireNotNull(scope) {
            "CoroutineScope is required when clearAfterMs > 0 (pass rememberCoroutineScope() from Composable)"
        }
        jobScope.launch {
            delay(clearAfterMs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
}
