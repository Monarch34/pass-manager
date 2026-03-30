package com.passmanager.data.repository

import com.passmanager.crypto.model.EncryptedData
import com.passmanager.data.db.dao.VaultItemDao
import com.passmanager.data.db.dao.VaultItemHeaderProjection
import com.passmanager.data.db.entity.VaultItemEntity
import com.passmanager.domain.model.HeaderEncryption
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.VaultItem
import com.passmanager.domain.model.VaultItemHeader
import com.passmanager.domain.repository.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class VaultRepositoryImpl @Inject constructor(
    private val dao: VaultItemDao
) : VaultRepository {

    override fun observeHeaders(): Flow<List<VaultItemHeader>> =
        dao.observeHeaders().map { projections -> projections.map { it.toHeader() } }

    override suspend fun getHeaders(): List<VaultItemHeader> =
        dao.getHeaders().map { it.toHeader() }

    override fun observeById(id: String): Flow<VaultItem?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getById(id: String): VaultItem? =
        dao.getById(id)?.toDomain()

    override suspend fun insert(
        id: String,
        encryptedData: EncryptedData,
        keyVersion: Int,
        createdAt: Long,
        category: ItemCategory,
        headerEncryption: HeaderEncryption?
    ) {
        dao.insert(
            VaultItemEntity(
                id = id,
                encryptedData = encryptedData.ciphertext,
                dataIv = encryptedData.iv,
                keyVersion = keyVersion,
                createdAt = createdAt,
                updatedAt = createdAt,
                category = category.dbKey,
                encryptedTitle = headerEncryption?.title?.ciphertext,
                titleIv = headerEncryption?.title?.iv,
                encryptedAddress = headerEncryption?.address?.ciphertext,
                addressIv = headerEncryption?.address?.iv
            )
        )
    }

    override suspend fun update(
        id: String,
        encryptedData: EncryptedData,
        keyVersion: Int,
        updatedAt: Long,
        category: ItemCategory,
        headerEncryption: HeaderEncryption
    ) {
        dao.updateDirectly(
            id = id,
            ed = encryptedData.ciphertext,
            div = encryptedData.iv,
            kv = keyVersion,
            ua = updatedAt,
            cat = category.dbKey,
            et = headerEncryption.title.ciphertext,
            tiv = headerEncryption.title.iv,
            ea = headerEncryption.address?.ciphertext,
            aiv = headerEncryption.address?.iv
        )
    }

    override suspend fun updateHeaderColumns(id: String, headerEncryption: HeaderEncryption) {
        dao.updateHeaderColumns(
            id = id,
            et = headerEncryption.title.ciphertext,
            tiv = headerEncryption.title.iv,
            ea = headerEncryption.address?.ciphertext,
            aiv = headerEncryption.address?.iv
        )
    }

    override suspend fun deleteById(id: String) = dao.deleteById(id)

    override suspend fun deleteByIds(ids: List<String>) = dao.deleteByIds(ids)

    override suspend fun isVaultEmpty(): Boolean = dao.count() == 0

    private fun VaultItemEntity.toDomain() = VaultItem(
        id = id,
        encryptedData = EncryptedData(ciphertext = encryptedData, iv = dataIv),
        keyVersion = keyVersion,
        createdAt = createdAt,
        updatedAt = updatedAt,
        category = ItemCategory.fromString(category)
    )

    private fun VaultItemHeaderProjection.toHeader() = VaultItemHeader(
        id = id,
        encryptedTitle = encryptedTitle,
        titleIv = titleIv,
        encryptedAddress = encryptedAddress,
        addressIv = addressIv,
        category = ItemCategory.fromString(category),
        updatedAt = updatedAt
    )
}
