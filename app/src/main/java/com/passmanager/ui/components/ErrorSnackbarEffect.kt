package com.passmanager.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.passmanager.ui.common.UserMessage
import com.passmanager.ui.common.resolve

@Composable
fun ErrorSnackbarEffect(
    error: UserMessage?,
    onErrorShown: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val messageText = error?.resolve()
    LaunchedEffect(error) {
        messageText?.let {
            snackbarHostState.showSnackbar(it)
            onErrorShown()
        }
    }
}
