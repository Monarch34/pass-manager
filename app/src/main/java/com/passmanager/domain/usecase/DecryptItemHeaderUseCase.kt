package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.domain.model.VaultItemHeader
import com.passmanager.domain.port.VaultKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DecryptItemHeaderUseCase @Inject constructor(
    private val cipher: AesGcmCipher,
    private val vaultKeyProvider: VaultKeyProvider
) {
    data class Result(val title: String?, val address: String?)

    suspend operator fun invoke(header: VaultItemHeader): Result =
        withContext(Dispatchers.Default) {
            val key = vaultKeyProvider.requireUnlockedKey()
            try {
                // JVM Strings are immutable and not zeroable — accepted residual.
                // ByteArray plaintext is zeroed immediately to limit exposure.
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
            } finally {
                key.fill(0)
            }
        }
}
