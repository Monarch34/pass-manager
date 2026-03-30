package com.passmanager.security.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun canUseBiometric(): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Show a [BiometricPrompt] with the given [cipher].
     * [onSuccess] receives the authenticated cipher.
     * [onError] receives the error message.
     * [onFail] is called on soft failure (e.g. too many attempts — not permanent).
     */
    fun showPrompt(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        subtitle: String,
        negativeButtonText: String,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit,
        onFail: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (activity.isDestroyed) return
                val authenticatedCipher = result.cryptoObject?.cipher
                if (authenticatedCipher != null) {
                    onSuccess(authenticatedCipher)
                } else {
                    onError("Authentication succeeded but no cipher returned")
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (activity.isDestroyed) return
                onError(errString.toString())
            }

            override fun onAuthenticationFailed() {
                if (activity.isDestroyed) return
                onFail()
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeButtonText)
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    }
}
