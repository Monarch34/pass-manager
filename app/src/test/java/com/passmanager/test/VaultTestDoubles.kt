package com.passmanager.test

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.model.HeaderEncryption
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.ItemPayload
import com.passmanager.domain.model.PayloadJson
import com.passmanager.domain.model.VaultItem
import com.passmanager.domain.model.VaultItemHeader
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.port.VaultKeyProvider
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.domain.repository.VaultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** An unlocked vault holding a fixed key. */
class FakeKeyProvider(private val key: ByteArray) : VaultKeyProvider {
    override fun requireUnlockedKey(): ByteArray = key.copyOf()
    override fun unlock(vaultKey: ByteArray) = Unit
}

class FakeMetadataRepository(private val value: VaultMetadata = defaultMetadata()) : MetadataRepository {
    override fun observe(): Flow<VaultMetadata?> = flowOf(value)
    override suspend fun get(): VaultMetadata = value
    override suspend fun isVaultSetup(): Boolean = true
    override suspend fun save(metadata: VaultMetadata) = Unit
    override suspend fun update(metadata: VaultMetadata) = Unit

    companion object {
        fun defaultMetadata() = VaultMetadata(
            currentKeyVersion = 1,
            wrappedVaultKey = EncryptedData(ByteArray(32), ByteArray(12)),
            kdfSalt = ByteArray(16),
            kdfParams = KdfParams(),
            biometricEnabled = false,
            biometricWrappedKey = null
        )
    }
}

/**
 * In-memory [VaultRepository]. Rows are exposed directly so a test can plant a row with chosen
 * timestamps, or read one back to check what a merge wrote.
 */
class FakeVaultRepository : VaultRepository {
    class Row(val item: VaultItem, val header: HeaderEncryption?)

    val rows = linkedMapOf<String, Row>()

    override fun observeHeaders(): Flow<List<VaultItemHeader>> = flowOf(headers())
    override suspend fun getHeaders(): List<VaultItemHeader> = headers()
    override fun observeById(id: String): Flow<VaultItem?> = flowOf(rows[id]?.item)
    override suspend fun getById(id: String): VaultItem? = rows[id]?.item
    override suspend fun getAll(): List<VaultItem> = rows.values.map { it.item }

    override suspend fun insert(
        id: String,
        encryptedData: EncryptedData,
        keyVersion: Int,
        createdAt: Long,
        category: ItemCategory,
        headerEncryption: HeaderEncryption?,
        updatedAt: Long
    ) {
        check(!rows.containsKey(id)) { "insert on an existing id: $id" }
        rows[id] = Row(
            VaultItem(id, encryptedData, keyVersion, createdAt, updatedAt, category),
            headerEncryption
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
        val existing = requireNotNull(rows[id]) { "update on a missing id: $id" }
        rows[id] = Row(
            existing.item.copy(
                encryptedData = encryptedData,
                keyVersion = keyVersion,
                updatedAt = updatedAt,
                category = category
            ),
            headerEncryption
        )
    }

    override suspend fun updateHeaderColumns(id: String, headerEncryption: HeaderEncryption) {
        val existing = requireNotNull(rows[id])
        rows[id] = Row(existing.item, headerEncryption)
    }

    override suspend fun deleteById(id: String) { rows.remove(id) }
    override suspend fun deleteByIds(ids: List<String>) { ids.forEach { rows.remove(it) } }
    override suspend fun isVaultEmpty(): Boolean = rows.isEmpty()

    private fun headers(): List<VaultItemHeader> = rows.values.map { row ->
        VaultItemHeader(
            id = row.item.id,
            encryptedTitle = row.header?.title?.ciphertext,
            titleIv = row.header?.title?.iv,
            encryptedAddress = row.header?.address?.ciphertext,
            addressIv = row.header?.address?.iv,
            category = row.item.category,
            updatedAt = row.item.updatedAt
        )
    }
}

/**
 * Plants an already-encrypted row with chosen timestamps — what a save use case would have written,
 * without its `now` stamp.
 */
fun FakeVaultRepository.seedItem(
    cipher: AesGcmCipher,
    vaultKey: ByteArray,
    payload: ItemPayload,
    createdAt: Long,
    updatedAt: Long,
    keyVersion: Int = 1
) {
    val json = PayloadJson.encode(payload).toByteArray(Charsets.UTF_8)
    val title = payload.title.toByteArray(Charsets.UTF_8)
    val subtitle = payload.listSubtitle
    rows[payload.id] = FakeVaultRepository.Row(
        VaultItem(
            id = payload.id,
            encryptedData = cipher.encrypt(json, vaultKey),
            keyVersion = keyVersion,
            createdAt = createdAt,
            updatedAt = updatedAt,
            category = payload.category
        ),
        HeaderEncryption(
            title = cipher.encrypt(title, vaultKey),
            address = if (subtitle.isEmpty()) {
                null
            } else {
                cipher.encrypt(subtitle.toByteArray(Charsets.UTF_8), vaultKey)
            }
        )
    )
}
