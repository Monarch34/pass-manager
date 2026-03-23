package com.passmanager.domain.repository

import com.passmanager.domain.model.VaultMetadata

interface MetadataRepository {
    suspend fun get(): VaultMetadata?
    suspend fun isVaultSetup(): Boolean
    suspend fun save(metadata: VaultMetadata)
    suspend fun update(metadata: VaultMetadata)
    suspend fun enableBiometric(biometricWrappedKey: ByteArray, biometricWrapperIv: ByteArray)
    suspend fun disableBiometric()
}
