package com.passmanager.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.passmanager.security.biometric.BiometricHelper
import kotlinx.coroutines.flow.SharedFlow
import javax.crypto.Cipher

/**
 * Collects a [cipherFlow] and shows the system biometric prompt whenever a cipher is emitted.
 * Extracts the duplicated LaunchedEffect from LockScreen and SettingsScreen.
 */
@Composable
fun BiometricPromptEffect(
    cipherFlow: SharedFlow<Cipher>,
    title: String,
    subtitle: String,
    negativeButtonText: String,
    onSuccess: (Cipher) -> Unit,
    onError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        cipherFlow.collect { cipher ->
            val activity = context as? FragmentActivity ?: return@collect
            val helper = BiometricHelper(context)
            helper.showPrompt(
                activity = activity,
                cipher = cipher,
                title = title,
                subtitle = subtitle,
                negativeButtonText = negativeButtonText,
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }
}
