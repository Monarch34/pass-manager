package com.passmanager.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The one snackbar style every screen uses.
 *
 * Material's default snackbar paints itself in the *inverse* scheme — light on a dark theme — so
 * every toast landed as a white flash over an otherwise dark UI, and each of the eight screens
 * hosting one restated that default. This keeps the toast inside the theme instead: the highest
 * surface container for the plate, normal onSurface text, and the brand primary for any action, in
 * both light and dark. Contrast to the background is carried by the container step, the same way
 * the app's cards do it.
 */
@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
            actionColor = MaterialTheme.colorScheme.primary,
            dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
