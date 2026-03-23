package com.passmanager.data.repository

import com.passmanager.crypto.model.EncryptedData
import com.passmanager.crypto.model.KdfParams
import com.passmanager.data.db.dao.VaultMetadataDao
import com.passmanager.data.db.entity.VaultMetadataEntity
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.repository.MetadataRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class MetadataRepositoryImpl @Inject constructor(
    private val dao: VaultMetadataDao
) : MetadataRepository {

    override suspend fun get(): VaultMetadata? = dao.get()?.toDomain()

    override suspend fun isVaultSetup(): Boolean = dao.exists() > 0

    override suspend fun save(metadata: VaultMetadata) {
        dao.insert(metadata.toEntity())
    }

    override suspend fun update(metadata: VaultMetadata) {
        dao.update(metadata.toEntity())
    }

    override suspend fun enableBiometric(
        biometricWrappedKey: ByteArray,
        biometricWrapperIv: ByteArray
    ) {
        val existing = dao.get() ?: error("Vault metadata not found")
        dao.update(
            existing.copy(
                biometricEnabled = 1,
                biometricWrappedKey = biometricWrappedKey,
                biometricWrapperIv = biometricWrapperIv
            )
        )
    }

    override suspend fun disableBiometric() {
        val existing = dao.get() ?: return
        dao.update(
            existing.copy(
                biometricEnabled = 0,
                biometricWrappedKey = null,
                biometricWrapperIv = null
            )
        )
    }

    private fun VaultMetadataEntity.toDomain(): VaultMetadata {
        val kdfParams = Json.decodeFromString<KdfParams>(kdfParamsJson)
        return VaultMetadata(
            currentKeyVersion = currentKeyVersion,
            wrappedVaultKey = EncryptedData(
                ciphertext = wrappedVaultKey,
                iv = wrapperIv
            ),
            kdfSalt = kdfSalt,
            kdfParams = kdfParams,
            biometricEnabled = biometricEnabled == 1,
            biometricWrappedKey = if (biometricWrappedKey != null && biometricWrapperIv != null)
                EncryptedData(ciphertext = biometricWrappedKey, iv = biometricWrapperIv)
            else null
        )
    }

    private fun VaultMetadata.toEntity() = VaultMetadataEntity(
        id = 1,
        currentKeyVersion = currentKeyVersion,
        wrappedVaultKey = wrappedVaultKey.ciphertext,
        wrapperIv = wrappedVaultKey.iv,
        kdfSalt = kdfSalt,
        kdfParamsJson = Json.encodeToString(kdfParams),
        biometricEnabled = if (biometricEnabled) 1 else 0,
        biometricWrappedKey = biometricWrappedKey?.ciphertext,
        biometricWrapperIv = biometricWrappedKey?.iv
    )
}
