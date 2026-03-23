package com.passmanager.desktop.clipboard

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Copies passwords to system clipboard and auto-clears after [CLEAR_AFTER_MS].
 * W2 mitigation: password goes ByteArray → String (clipboard API boundary only) → clipboard.
 */
class ClipboardManager(private val scope: CoroutineScope) {

    private var clearJob: Job? = null

    fun copyPassword(passwordBytes: ByteArray) {
        val text = passwordBytes.decodeToString()
        passwordBytes.fill(0)

        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)

        clearJob?.cancel()
        clearJob = scope.launch {
            delay(CLEAR_AFTER_MS)
            clearNow()
        }
    }

    fun clearNow() {
        clearJob?.cancel()
        clearJob = null
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(""), null)
        } catch (_: Exception) { }
    }

    companion object {
        const val CLEAR_AFTER_MS = 30_000L
    }
}
