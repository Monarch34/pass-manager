package com.passmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.passmanager.data.db.entity.VaultItemEntity
import kotlinx.coroutines.flow.Flow

private const val HEADERS_QUERY =
    "SELECT id, encrypted_title, title_iv, encrypted_address, address_iv, category, updated_at " +
    "FROM vault_items ORDER BY updated_at DESC"

@Dao
interface VaultItemDao {

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun getById(id: String): VaultItemEntity?

    @Query("SELECT * FROM vault_items WHERE id = :id")
    fun observeById(id: String): Flow<VaultItemEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: VaultItemEntity)

    @Update
    suspend fun update(item: VaultItemEntity)

    @Query(
        "UPDATE vault_items SET " +
        "encrypted_data = :ed, data_iv = :div, key_version = :kv, updated_at = :ua, " +
        "category = :cat, encrypted_title = :et, title_iv = :tiv, " +
        "encrypted_address = :ea, address_iv = :aiv " +
        "WHERE id = :id"
    )
    suspend fun updateDirectly(
        id: String,
        ed: ByteArray,
        div: ByteArray,
        kv: Int,
        ua: Long,
        cat: String,
        et: ByteArray,
        tiv: ByteArray,
        ea: ByteArray?,
        aiv: ByteArray?
    )

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM vault_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM vault_items")
    suspend fun count(): Int

    @Query(HEADERS_QUERY)
    fun observeHeaders(): Flow<List<VaultItemHeaderProjection>>

    @Query(HEADERS_QUERY)
    suspend fun getHeaders(): List<VaultItemHeaderProjection>

    @Query(
        "UPDATE vault_items " +
        "SET encrypted_title = :et, title_iv = :tiv, encrypted_address = :ea, address_iv = :aiv " +
        "WHERE id = :id"
    )
    suspend fun updateHeaderColumns(
        id: String,
        et: ByteArray,
        tiv: ByteArray,
        ea: ByteArray?,
        aiv: ByteArray?
    )
}
