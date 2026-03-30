package com.passmanager.domain.repository

import com.passmanager.domain.model.VaultMetadata
import kotlinx.coroutines.flow.Flow

interface MetadataRepository {
    fun observe(): Flow<VaultMetadata?>
    suspend fun get(): VaultMetadata?
    suspend fun isVaultSetup(): Boolean
    suspend fun save(metadata: VaultMetadata)
    suspend fun update(metadata: VaultMetadata)
}
