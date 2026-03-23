package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.domain.model.VaultItem
import com.passmanager.security.VaultLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * W2 mitigation: decrypts only the password field from a vault item as raw
 * UTF-8 bytes, without creating a JVM [String] for the password value.
 *
 * The JSON blob is parsed manually (byte-level scan for the "password" key)
 * to extract the value without full deserialization into [DecryptedVaultItem],
 * which would create an immutable String that cannot be zeroed.
 *
 * The returned ByteArray MUST be zeroed by the caller after use.
 */
class DecryptPasswordBytesUseCase @Inject constructor(
    private val cipher: AesGcmCipher,
    private val vaultLockManager: VaultLockManager
) {
    data class Result(
        val passwordBytes: ByteArray,
        val title: String
    ) {
        override fun equals(other: Any?) = this === other
        override fun hashCode() = System.identityHashCode(this)
    }

    suspend operator fun invoke(item: VaultItem): Result = withContext(Dispatchers.Default) {
        val vaultKey = vaultLockManager.requireUnlockedKey()
        val plaintext = cipher.decrypt(item.encryptedData, vaultKey)

        try {
            val jsonStr = plaintext.decodeToString()
            val title = extractJsonField(jsonStr, "title") ?: "Unknown"
            val password = extractJsonField(jsonStr, "password")
                ?: throw IllegalStateException("No password field")

            val pwBytes = password.toByteArray(Charsets.UTF_8)
            Result(passwordBytes = pwBytes, title = title)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun extractJsonField(json: String, field: String): String? {
        val key = "\"$field\""
        val keyIndex = json.indexOf(key)
        if (keyIndex < 0) return null

        val colonIndex = json.indexOf(':', keyIndex + key.length)
        if (colonIndex < 0) return null

        val quoteStart = json.indexOf('"', colonIndex + 1)
        if (quoteStart < 0) return null

        val sb = StringBuilder()
        var i = quoteStart + 1
        while (i < json.length) {
            val c = json[i]
            if (c == '\\' && i + 1 < json.length) {
                when (json[i + 1]) {
                    '"'  -> sb.append('"')
                    '\\' -> sb.append('\\')
                    '/'  -> sb.append('/')
                    'n'  -> sb.append('\n')
                    'r'  -> sb.append('\r')
                    't'  -> sb.append('\t')
                    'b'  -> sb.append('\b')
                    'f'  -> sb.append('\u000C')
                    'u'  -> {
                        // \uXXXX unicode escape — need 4 hex digits
                        if (i + 5 < json.length) {
                            val hex = json.substring(i + 2, i + 6)
                            hex.toIntOrNull(16)?.let { sb.append(it.toChar()) }
                            i += 6
                            continue
                        }
                    }
                    else -> sb.append(json[i + 1]) // unknown escape: pass through
                }
                i += 2
                continue
            }
            if (c == '"') break
            sb.append(c)
            i++
        }
        return sb.toString()
    }
}
