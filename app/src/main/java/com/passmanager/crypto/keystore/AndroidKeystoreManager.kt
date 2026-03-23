package com.passmanager.crypto.keystore

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/**
 * Manages a single AES-256-GCM key in the Android Keystore,
 * used exclusively to wrap/unwrap the vault key for biometric warm unlock.
 *
 * Key characteristics:
 * - User authentication required (biometric strong)
 * - Invalidated on new biometric enrollment
 * - Unlocked device required (API 28+)
 */
class AndroidKeystoreManager @Inject constructor() {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "passmanager_biometric_key"
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
    }

    private val keyStore: KeyStore by lazy { KeyStore.getInstance(KEYSTORE_PROVIDER).also { it.load(null) } }

    /** True if the biometric key currently exists in the Keystore. */
    fun hasBiometricKey(): Boolean = keyStore.containsAlias(KEY_ALIAS)

    /**
     * Generate (or re-generate) the biometric key.
     * Calling this invalidates any previously wrapped vault key.
     */
    fun generateBiometricKey() {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val specBuilder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            specBuilder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            specBuilder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            specBuilder.setUnlockedDeviceRequired(true)
        }

        keyGenerator.init(specBuilder.build())
        keyGenerator.generateKey()
    }

    /**
     * Delete the biometric key from the Keystore.
     * Used when biometric is disabled or the key is invalidated.
     */
    fun deleteBiometricKey() {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    /**
     * Create a [Cipher] initialised for ENCRYPT_MODE with the biometric key.
     * Present this cipher to [BiometricPrompt] to authenticate the operation.
     * After successful authentication, use [AesGcmCipher.encryptWithCipher].
     */
    fun createEncryptCipher(): Cipher {
        val key = getKey()
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher
    }

    /**
     * Create a [Cipher] initialised for DECRYPT_MODE with the biometric key and [iv].
     * Present this cipher to [BiometricPrompt] to authenticate the operation.
     * After successful authentication, use [AesGcmCipher.decryptWithCipher].
     */
    fun createDecryptCipher(iv: ByteArray): Cipher {
        val key = getKey()
        val cipher = Cipher.getInstance(ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher
    }

    private fun getKey(): SecretKey {
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            ?: error("Biometric key not found in Keystore. Call generateBiometricKey() first.")
        return entry.secretKey
    }
}
