package com.passmanager.domain.repository

import com.passmanager.crypto.model.EncryptedData
import com.passmanager.domain.model.VaultItem
import com.passmanager.domain.model.VaultItemHeader
import kotlinx.coroutines.flow.Flow

interface VaultRepository {
    fun observeAll(): Flow<List<VaultItem>>
    fun observeHeaders(): Flow<List<VaultItemHeader>>
    fun observeById(id: String): Flow<VaultItem?>
    suspend fun getById(id: String): VaultItem?
    suspend fun insert(
        id: String,
        encryptedData: EncryptedData,
        keyVersion: Int,
        createdAt: Long,
        category: String = "login",
        encryptedTitle: ByteArray? = null,
        titleIv: ByteArray? = null,
        encryptedAddress: ByteArray? = null,
        addressIv: ByteArray? = null
    )
    suspend fun update(
        id: String,
        encryptedData: EncryptedData,
        keyVersion: Int,
        updatedAt: Long,
        category: String? = null,
        encryptedTitle: ByteArray? = null,
        titleIv: ByteArray? = null,
        encryptedAddress: ByteArray? = null,
        addressIv: ByteArray? = null
    )
    suspend fun updateHeaderColumns(
        id: String,
        encryptedTitle: ByteArray,
        titleIv: ByteArray,
        encryptedAddress: ByteArray?,
        addressIv: ByteArray?
    )
    suspend fun deleteById(id: String)
    suspend fun isVaultEmpty(): Boolean
}
