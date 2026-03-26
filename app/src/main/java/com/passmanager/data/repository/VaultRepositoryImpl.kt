package com.passmanager.data.repository

import com.passmanager.crypto.model.EncryptedData
import com.passmanager.data.db.dao.VaultItemDao
import com.passmanager.data.db.entity.VaultItemEntity
import com.passmanager.domain.model.VaultItem
import com.passmanager.domain.model.VaultItemHeader
import com.passmanager.domain.repository.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class VaultRepositoryImpl @Inject constructor(
    private val dao: VaultItemDao
) : VaultRepository {

    override fun observeAll(): Flow<List<VaultItem>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeHeaders(): Flow<List<VaultItemHeader>> =
        dao.observeHeaders().map { projections -> projections.map { it.toHeader() } }

    override fun observeById(id: String): Flow<VaultItem?> =
        dao.observeById(id).map { it?.toDomain() }

    override suspend fun getById(id: String): VaultItem? =
        dao.getById(id)?.toDomain()

    override suspend fun insert(
        id: String,
        encryptedData: EncryptedData,
        keyVersion: Int,
        createdAt: Long,
        category: String,
        encryptedTitle: ByteArray?,
        titleIv: ByteArray?,
        encryptedAddress: ByteArray?,
        addressIv: ByteArray?
    ) {
        dao.insert(
            VaultItemEntity(
                id = id,
                encryptedData = encryptedData.ciphertext,
                dataIv = encryptedData.iv,
                keyVersion = keyVersion,
                createdAt = createdAt,
                updatedAt = createdAt,
                category = category,
                encryptedTitle = encryptedTitle,
                titleIv = titleIv,
                encryptedAddress = encryptedAddress,
                addressIv = addressIv
            )
        )
    }

    override suspend fun update(
        id: String,
        encryptedData: EncryptedData,
        keyVersion: Int,
        updatedAt: Long,
        category: String?,
        encryptedTitle: ByteArray?,
        titleIv: ByteArray?,
        encryptedAddress: ByteArray?,
        addressIv: ByteArray?
    ) {
        val existing = dao.getById(id) ?: error("Item $id not found")
        dao.update(
            existing.copy(
                encryptedData = encryptedData.ciphertext,
                dataIv = encryptedData.iv,
                keyVersion = keyVersion,
                updatedAt = updatedAt,
                category = category ?: existing.category,
                encryptedTitle = encryptedTitle,
                titleIv = titleIv,
                encryptedAddress = encryptedAddress,
                addressIv = addressIv
            )
        )
    }

    override suspend fun updateHeaderColumns(
        id: String,
        encryptedTitle: ByteArray,
        titleIv: ByteArray,
        encryptedAddress: ByteArray?,
        addressIv: ByteArray?
    ) {
        dao.updateHeaderColumns(
            id = id,
            et = encryptedTitle,
            tiv = titleIv,
            ea = encryptedAddress,
            aiv = addressIv
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
        category = category
    )

    private fun com.passmanager.data.db.dao.VaultItemHeaderProjection.toHeader() = VaultItemHeader(
        id = id,
        encryptedTitle = encryptedTitle,
        titleIv = titleIv,
        encryptedAddress = encryptedAddress,
        addressIv = addressIv,
        category = category,
        updatedAt = updatedAt
    )
}
