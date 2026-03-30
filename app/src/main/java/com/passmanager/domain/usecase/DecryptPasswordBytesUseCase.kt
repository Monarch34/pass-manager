package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.util.SecureJsonFieldExtractor
import com.passmanager.domain.model.VaultItem
import com.passmanager.domain.port.VaultKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Extracts the password field from a vault item as raw bytes without
 * decoding the plaintext to a JVM String. All sensitive buffers are
 * zeroed after use. The returned [Result.passwordBytes] MUST be zeroed
 * by the caller.
 */
class DecryptPasswordBytesUseCase @Inject constructor(
    private val cipher: AesGcmCipher,
    private val vaultKeyProvider: VaultKeyProvider
) {
    data class Result(
        val passwordBytes: ByteArray,
        val title: String
    ) {
        override fun equals(other: Any?) = this === other
        override fun hashCode() = System.identityHashCode(this)
    }

    suspend operator fun invoke(item: VaultItem): Result = withContext(Dispatchers.Default) {
        val vaultKey = vaultKeyProvider.requireUnlockedKey()
        val plaintext = cipher.decrypt(item.encryptedData, vaultKey)
        vaultKey.fill(0)
        try {
            val titleBytes = SecureJsonFieldExtractor.extractFieldBytes(plaintext, "title", MAX_TITLE_BYTES)
            val title = if (titleBytes != null) {
                String(titleBytes, Charsets.UTF_8).also { titleBytes.fill(0) }
            } else "Unknown"

            val passwordBytes = SecureJsonFieldExtractor.extractFieldBytes(plaintext, "password", MAX_PASSWORD_BYTES)
                ?: throw IllegalStateException("No password field in item ${item.id}")

            Result(passwordBytes = passwordBytes, title = title)
        } finally {
            plaintext.fill(0)
        }
    }

    private companion object {
        const val MAX_PASSWORD_BYTES = 1024
        const val MAX_TITLE_BYTES    = 512
    }
}
