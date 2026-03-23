package com.passmanager.desktop.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM authenticated encryption.
 * IV: 12 bytes (96-bit), GCM tag: 128 bits.
 * Copied from Android — pure JVM, no Android dependencies.
 */
class AesGcmCipher {

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val IV_LENGTH_BYTES = 12
        private const val TAG_LENGTH_BITS = 128
    }

    private val secureRandom = SecureRandom()

    fun encrypt(plaintext: ByteArray, keyBytes: ByteArray): EncryptedData {
        val iv = ByteArray(IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = buildCipher(Cipher.ENCRYPT_MODE, keyBytes, iv)
        val ciphertext = cipher.doFinal(plaintext)
        return EncryptedData(ciphertext = ciphertext, iv = iv)
    }

    fun decrypt(encryptedData: EncryptedData, keyBytes: ByteArray): ByteArray {
        val cipher = buildCipher(Cipher.DECRYPT_MODE, keyBytes, encryptedData.iv)
        return cipher.doFinal(encryptedData.ciphertext)
    }

    private fun buildCipher(mode: Int, keyBytes: ByteArray, iv: ByteArray): Cipher {
        val secretKey = SecretKeySpec(keyBytes, KEY_ALGORITHM)
        val spec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
        return Cipher.getInstance(ALGORITHM).also { it.init(mode, secretKey, spec) }
    }
}

data class EncryptedData(
    val ciphertext: ByteArray,
    val iv: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedData) return false
        return ciphertext.contentEquals(other.ciphertext) && iv.contentEquals(other.iv)
    }

    override fun hashCode(): Int {
        var result = ciphertext.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}
