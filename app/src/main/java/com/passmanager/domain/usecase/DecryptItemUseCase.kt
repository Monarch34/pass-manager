package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.domain.model.ItemPayload
import com.passmanager.domain.model.PayloadJson
import com.passmanager.domain.model.VaultItem
import com.passmanager.domain.port.VaultKeyProvider
import com.passmanager.domain.repository.MetadataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DecryptItemUseCase @Inject constructor(
    private val cipher: AesGcmCipher,
    private val vaultKeyProvider: VaultKeyProvider,
    private val metadataRepository: MetadataRepository
) {
    suspend operator fun invoke(item: VaultItem): ItemPayload =
        withContext(Dispatchers.Default) {
            val vaultKey = vaultKeyProvider.requireUnlockedKey()
            try {
                val metadata = metadataRepository.get() ?: error("Vault not set up")
                check(item.keyVersion == metadata.currentKeyVersion) {
                    "Key version mismatch: item=${item.keyVersion}, vault=${metadata.currentKeyVersion}"
                }

                val plaintext = cipher.decrypt(item.encryptedData, vaultKey)
                // JVM String is immutable and not zeroable — accepted residual.
                // Plaintext ByteArray is zeroed immediately to limit exposure window.
                val json = plaintext.decodeToString()
                plaintext.fill(0)

                PayloadJson.decode(json, categoryHint = item.category)
            } finally {
                vaultKey.fill(0)
            }
        }
}
