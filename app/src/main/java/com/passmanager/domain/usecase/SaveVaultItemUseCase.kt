package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.domain.model.HeaderEncryption
import com.passmanager.domain.model.ItemPayload
import com.passmanager.domain.model.PayloadJson
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.domain.repository.VaultRepository
import com.passmanager.domain.port.VaultKeyProvider
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SaveVaultItemUseCase @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val metadataRepository: MetadataRepository,
    private val cipher: AesGcmCipher,
    private val vaultKeyProvider: VaultKeyProvider
) {
    private class EncryptedFields(
        val payload: EncryptedData,
        val title: EncryptedData,
        val address: EncryptedData?
    )

    suspend operator fun invoke(payload: ItemPayload, existingId: String? = null): String {
        val metadata = metadataRepository.get() ?: error("Vault not set up")
        val now = System.currentTimeMillis()
        val itemId = existingId ?: UUID.randomUUID().toString()
        val finalPayload = payload.withId(itemId)

        val enc = withContext(Dispatchers.Default) {
            val vaultKey = vaultKeyProvider.requireUnlockedKey()
            try {
                // PayloadJson.encode returns a JVM String (not zeroable) — accepted residual.
                val jsonBytes = PayloadJson.encode(finalPayload).toByteArray(Charsets.UTF_8)
                val encPayload = cipher.encrypt(jsonBytes, vaultKey)
                jsonBytes.fill(0)

                val titleBytes = finalPayload.title.toByteArray(Charsets.UTF_8)
                val encTitle = cipher.encrypt(titleBytes, vaultKey)
                titleBytes.fill(0)

                val subtitle = finalPayload.listSubtitle
                val encAddress = if (subtitle.isNotEmpty()) {
                    val bytes = subtitle.toByteArray(Charsets.UTF_8)
                    cipher.encrypt(bytes, vaultKey).also { bytes.fill(0) }
                } else null

                EncryptedFields(payload = encPayload, title = encTitle, address = encAddress)
            } finally {
                vaultKey.fill(0)
            }
        }

        val header = HeaderEncryption(title = enc.title, address = enc.address)
        if (existingId == null) {
            vaultRepository.insert(
                id = itemId,
                encryptedData = enc.payload,
                keyVersion = metadata.currentKeyVersion,
                createdAt = now,
                category = finalPayload.category,
                headerEncryption = header
            )
        } else {
            vaultRepository.update(
                id = itemId,
                encryptedData = enc.payload,
                keyVersion = metadata.currentKeyVersion,
                updatedAt = now,
                category = finalPayload.category,
                headerEncryption = header
            )
        }
        return itemId
    }
}

private fun ItemPayload.withId(id: String): ItemPayload = when (this) {
    is ItemPayload.Login      -> copy(id = id)
    is ItemPayload.Card       -> copy(id = id)
    is ItemPayload.Bank       -> copy(id = id)
    is ItemPayload.SecureNote -> copy(id = id)
    is ItemPayload.Identity   -> copy(id = id)
}
