package com.passmanager.crypto.cipher

import com.passmanager.crypto.model.EncryptedData
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

/**
 * AES-256-GCM authenticated encryption.
 * IV: 12 bytes (96-bit), GCM tag: 128 bits.
 */
class AesGcmCipher @Inject constructor() {

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val IV_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BITS = 128
    }

    private val secureRandom = SecureRandom()

    /**
     * Encrypt [plaintext] with [keyBytes].
     * Returns ciphertext + IV. Caller owns the returned arrays.
     */
    fun encrypt(plaintext: ByteArray, keyBytes: ByteArray): EncryptedData {
        val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = buildCipher(Cipher.ENCRYPT_MODE, keyBytes, iv)
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedData(ciphertext = ciphertext, iv = iv)
    }

    /**
     * Decrypt [encryptedData] with [keyBytes].
     * Throws [javax.crypto.AEADBadTagException] if authentication fails.
     */
    fun decrypt(encryptedData: EncryptedData, keyBytes: ByteArray): ByteArray {
        val cipher = buildCipher(Cipher.DECRYPT_MODE, keyBytes, encryptedData.iv)
        return cipher.doFinal(encryptedData.ciphertext)
    }

    /**
     * Decrypt using an already-initialised [Cipher] (biometric path).
     * The cipher must have been initialised for DECRYPT_MODE by the Keystore.
     */
    fun decryptWithCipher(encryptedData: EncryptedData, cipher: Cipher): ByteArray {
        return cipher.doFinal(encryptedData.ciphertext)
    }

    /**
     * Encrypt using an already-initialised [Cipher] (biometric path).
     * The cipher must have been initialised for ENCRYPT_MODE by the Keystore.
     * Note: the IV is taken from the cipher itself after initialisation.
     */
    fun encryptWithCipher(plaintext: ByteArray, cipher: Cipher): EncryptedData {
        val ciphertext = cipher.doFinal(plaintext)
        val iv = cipher.iv
        return EncryptedData(ciphertext = ciphertext, iv = iv)
    }

    private fun buildCipher(mode: Int, keyBytes: ByteArray, iv: ByteArray): Cipher {
        val secretKey = SecretKeySpec(keyBytes, KEY_ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        return Cipher.getInstance(ALGORITHM).also { it.init(mode, secretKey, spec) }
    }
}
