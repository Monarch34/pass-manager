package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.domain.model.VaultItemHeader
import com.passmanager.security.VaultLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Fast-path decryption for the vault list screen.
 * Decrypts only the separately-stored title and address fields from a [VaultItemHeader],
 * avoiding the overhead of loading and parsing the full item blob.
 *
 * Returns null for either field when the header columns are not yet populated
 * (items saved before the header-column migration). The caller must handle
 * this case by falling back to [DecryptItemUseCase].
 */
class DecryptItemHeaderUseCase @Inject constructor(
    private val cipher: AesGcmCipher,
    private val vaultLockManager: VaultLockManager
) {
    data class Result(val title: String?, val address: String?)

    suspend operator fun invoke(header: VaultItemHeader): Result =
        withContext(Dispatchers.Default) {
            val key = vaultLockManager.requireUnlockedKey()

            val title = header.encryptedTitle?.let { blob ->
                val iv = header.titleIv ?: return@let null
                val plain = cipher.decrypt(EncryptedData(blob, iv), key)
                val text = plain.decodeToString()
                plain.fill(0)
                text
            }

            val address = header.encryptedAddress?.let { blob ->
                val iv = header.addressIv ?: return@let null
                val plain = cipher.decrypt(EncryptedData(blob, iv), key)
                val text = plain.decodeToString()
                plain.fill(0)
                text
            }

            Result(title = title, address = address)
        }
}
