package com.passmanager.ui.common

import androidx.compose.ui.focus.FocusManager

/**
 * Clears focus from the currently focused child. Use after closing dropdowns, dialogs, or sheets
 * so read-only fields and the IME do not stay active.
 */
fun FocusManager.clearAllFocus() {
    clearFocus(force = true)
}
