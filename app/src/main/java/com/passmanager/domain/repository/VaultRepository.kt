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

    /** Every row with its full encrypted payload — the vault exporter's single read. */
    suspend fun getAll(): List<VaultItem>

    /**
     * @param updatedAt defaults to [createdAt] for freshly authored items. A vault import passes
     *   the timestamp carried by the `.pmvault` file instead, which is the whole point of it being
     *   a parameter: merge decisions on a later import compare against it.
     */
    suspend fun insert(
        id: String,
        encryptedData: EncryptedData,
        keyVersion: Int,
        createdAt: Long,
        category: ItemCategory,
        headerEncryption: HeaderEncryption? = null,
        updatedAt: Long = createdAt
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

    /** Wipes every row. Only the vault reset flow may call this. */
    suspend fun deleteAll()
    suspend fun isVaultEmpty(): Boolean

    /** How many items the vault holds. */
    suspend fun count(): Int
}
