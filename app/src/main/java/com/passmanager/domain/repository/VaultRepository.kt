package com.passmanager.domain.repository

import com.passmanager.crypto.model.EncryptedData
import com.passmanager.domain.model.HeaderEncryption
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.VaultItem
import com.passmanager.domain.model.VaultItemHeader
import kotlinx.coroutines.flow.Flow

interface VaultRepository {
    fun observeHeaders(): Flow<List<VaultItemHeader>>
    suspend fun getHeaders(): List<VaultItemHeader>
    fun observeById(id: String): Flow<VaultItem?>
    suspend fun getById(id: String): VaultItem?
    suspend fun insert(
        id: String,
        encryptedData: EncryptedData,
        keyVersion: Int,
        createdAt: Long,
        category: ItemCategory,
        headerEncryption: HeaderEncryption? = null
    )
    suspend fun update(
        id: String,
        encryptedData: EncryptedData,
        keyVersion: Int,
        updatedAt: Long,
        category: ItemCategory,
        headerEncryption: HeaderEncryption
    )
    suspend fun updateHeaderColumns(id: String, headerEncryption: HeaderEncryption)
    suspend fun deleteById(id: String)
    suspend fun deleteByIds(ids: List<String>)
    suspend fun isVaultEmpty(): Boolean
}
