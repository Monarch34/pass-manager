package com.passmanager.data.repository

import com.passmanager.crypto.model.EncryptedData
import com.passmanager.crypto.model.KdfParams
import com.passmanager.data.db.dao.VaultMetadataDao
import com.passmanager.data.db.entity.VaultMetadataEntity
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.repository.MetadataRepository
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Codec for the `kdf_params_json` column.
 *
 * `encodeDefaults = true` is load-bearing: every [KdfParams] field has a default, so the
 * stock [Json] encoded a fully-default instance as `"{}"`. A row like that carries no
 * parameters at all — unlocking it re-derived the key with whatever the defaults happened
 * to be at that moment, so changing a default would have locked such vaults out for good.
 * Writing the values explicitly pins each vault to the cost it was created with.
 *
 * Thread-safe — the [Json] instance is immutable after creation.
 */
private val kdfJson: Json = Json { encodeDefaults = true }

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

    override suspend fun delete() {
        dao.deleteAll()
    }

    private fun VaultMetadataEntity.toDomain(): VaultMetadata {
        // Missing keys still fall back to the KdfParams defaults, which keeps pre-v8 rows
        // readable; MIGRATION_7_8 back-fills them so the fallback stops mattering.
        val kdfParams = kdfJson.decodeFromString(KdfParams.serializer(), this.kdfParamsJson)
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
            else null,
            wrapVersion = this.wrapVersion,
            pepperIv = this.pepperIv
        )
    }

    private fun VaultMetadata.toEntity() = VaultMetadataEntity(
        id = 1,
        currentKeyVersion = this.currentKeyVersion,
        wrappedVaultKey = this.wrappedVaultKey.ciphertext,
        wrapperIv = this.wrappedVaultKey.iv,
        kdfSalt = this.kdfSalt,
        kdfParamsJson = kdfJson.encodeToString(KdfParams.serializer(), this.kdfParams),
        biometricEnabled = if (this.biometricEnabled) 1 else 0,
        biometricWrappedKey = this.biometricWrappedKey?.ciphertext,
        biometricWrapperIv = this.biometricWrappedKey?.iv,
        wrapVersion = this.wrapVersion,
        pepperIv = this.pepperIv
    )
}
