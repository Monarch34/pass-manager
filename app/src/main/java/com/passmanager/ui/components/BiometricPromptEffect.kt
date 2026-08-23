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
 *
 * [onError] (permanent failure, including the user dismissing the prompt) and [onFail] (soft
 * failure, e.g. an unrecognised finger) have no defaults on purpose: the system prompt closes on
 * both, and a screen that stays silent afterwards leaves the user with no idea why nothing opened.
 * Pass an explicit empty lambda when a caller has genuinely nothing to say.
 */
@Composable
fun BiometricPromptEffect(
    cipherFlow: SharedFlow<Cipher>,
    title: String,
    subtitle: String,
    negativeButtonText: String,
    onSuccess: (Cipher) -> Unit,
    onError: (String) -> Unit,
    onFail: () -> Unit
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
                onError = onError,
                onFail = onFail
            )
        }
    }
}
