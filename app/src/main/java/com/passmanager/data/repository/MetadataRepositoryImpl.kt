package com.passmanager.data.repository

import com.passmanager.crypto.model.EncryptedData
import com.passmanager.crypto.model.KdfParams
import com.passmanager.data.db.dao.VaultMetadataDao
import com.passmanager.data.db.entity.VaultMetadataEntity
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.repository.MetadataRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class MetadataRepositoryImpl @Inject constructor(
    private val dao: VaultMetadataDao
) : MetadataRepository {

    override fun observe(): Flow<VaultMetadata?> =
        dao.observe().map { it?.toDomain() }

    override suspend fun get(): VaultMetadata? = dao.get()?.toDomain()

    override suspend fun isVaultSetup(): Boolean = dao.exists()

    override suspend fun save(metadata: VaultMetadata) {
        dao.insert(metadata.toEntity())
    }

    override suspend fun update(metadata: VaultMetadata) {
        dao.update(metadata.toEntity())
    }

    private fun VaultMetadataEntity.toDomain(): VaultMetadata {
        val kdfParams = Json.decodeFromString<KdfParams>(this.kdfParamsJson)
        return VaultMetadata(
            currentKeyVersion = this.currentKeyVersion,
            wrappedVaultKey = EncryptedData(
                ciphertext = this.wrappedVaultKey,
                iv = this.wrapperIv
            ),
            kdfSalt = this.kdfSalt,
            kdfParams = kdfParams,
            biometricEnabled = this.biometricEnabled == 1,
            biometricWrappedKey = if (this.biometricWrappedKey != null && this.biometricWrapperIv != null)
                EncryptedData(ciphertext = this.biometricWrappedKey, iv = this.biometricWrapperIv)
            else null
        )
    }

    private fun VaultMetadata.toEntity() = VaultMetadataEntity(
        id = 1,
        currentKeyVersion = this.currentKeyVersion,
        wrappedVaultKey = this.wrappedVaultKey.ciphertext,
        wrapperIv = this.wrappedVaultKey.iv,
        kdfSalt = this.kdfSalt,
        kdfParamsJson = Json.encodeToString(this.kdfParams),
        biometricEnabled = if (this.biometricEnabled) 1 else 0,
        biometricWrappedKey = this.biometricWrappedKey?.ciphertext,
        biometricWrapperIv = this.biometricWrappedKey?.iv
    )
}
