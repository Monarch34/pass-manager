package com.passmanager.ui

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * Shows the system's biometric sheet and hands back the cipher it authenticated.
 *
 * The cipher goes in and the cipher comes back out, because that is the whole point: the
 * object returned by the system is the only one the Keystore will let do any work. Passing a
 * boolean "it worked" back instead would be an authentication this code could be tricked
 * into skipping.
 */
fun FragmentActivity.promptForCipher(
    cipher: Cipher,
    title: String,
    subtitle: String,
    onSuccess: (Cipher) -> Unit,
    onFailure: (String?) -> Unit,
) {
    val prompt = BiometricPrompt(
        this,
        ContextCompat.getMainExecutor(this),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val authenticated = result.cryptoObject?.cipher
                if (authenticated == null) onFailure(null) else onSuccess(authenticated)
            }

            override fun onAuthenticationError(code: Int, message: CharSequence) {
                // Cancelling is not a failure worth reporting back; the user closed a sheet.
                val cancelled = code == BiometricPrompt.ERROR_USER_CANCELED ||
                    code == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    code == BiometricPrompt.ERROR_CANCELED
                onFailure(if (cancelled) null else message.toString())
            }

            // onAuthenticationFailed fires for a finger the sensor did not recognise. The
            // sheet stays up and says so itself, so there is nothing useful to add here.
        },
    )

    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        // Strong biometrics only. Allowing the device credential would accept the PIN, which
        // is not this vault's passphrase.
        .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .setNegativeButtonText("Use passphrase")
        .setConfirmationRequired(false)
        .build()

    prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
}
