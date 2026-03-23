package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.domain.model.DecryptedVaultItem
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.domain.repository.VaultRepository
import com.passmanager.security.VaultLockManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

class SaveVaultItemUseCase @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val metadataRepository: MetadataRepository,
    private val cipher: AesGcmCipher,
    private val vaultLockManager: VaultLockManager
) {
    suspend operator fun invoke(
        id: String? = null,
        title: String,
        username: String,
        address: String = "",
        password: String,
        notes: String,
        category: String = "login"
    ): String {
        val vaultKey = vaultLockManager.requireUnlockedKey()
        val metadata = metadataRepository.get() ?: error("Vault not set up")
        val now = System.currentTimeMillis()
        val itemId = id ?: UUID.randomUUID().toString()
        val item = DecryptedVaultItem(
            id = itemId,
            title = title,
            username = username,
            address = address,
            password = password,
            notes = notes
        )
        val json = Json.encodeToString(item)
        val plaintext = json.toByteArray(Charsets.UTF_8)
        val encryptedData = cipher.encrypt(plaintext, vaultKey)
        plaintext.fill(0)

        // Encrypt title and address separately so the list screen can decrypt them
        // without loading the full item blob.
        val titleBytes = title.toByteArray(Charsets.UTF_8)
        val encryptedTitle = cipher.encrypt(titleBytes, vaultKey)
        titleBytes.fill(0)

        val encryptedAddress = if (address.isNotEmpty()) {
            val addrBytes = address.toByteArray(Charsets.UTF_8)
            cipher.encrypt(addrBytes, vaultKey).also { addrBytes.fill(0) }
        } else null

        if (id == null) {
            vaultRepository.insert(
                id = itemId,
                encryptedData = encryptedData,
                keyVersion = metadata.currentKeyVersion,
                createdAt = now,
                category = category,
                encryptedTitle = encryptedTitle.ciphertext,
                titleIv = encryptedTitle.iv,
                encryptedAddress = encryptedAddress?.ciphertext,
                addressIv = encryptedAddress?.iv
            )
        } else {
            vaultRepository.update(
                id = itemId,
                encryptedData = encryptedData,
                keyVersion = metadata.currentKeyVersion,
                updatedAt = now,
                category = category,
                encryptedTitle = encryptedTitle.ciphertext,
                titleIv = encryptedTitle.iv,
                encryptedAddress = encryptedAddress?.ciphertext,
                addressIv = encryptedAddress?.iv
            )
        }
        return itemId
    }
}
