package com.passmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.passmanager.data.db.entity.VaultItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultItemDao {

    @Query("SELECT * FROM vault_items ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun getById(id: String): VaultItemEntity?

    @Query("SELECT * FROM vault_items WHERE id = :id")
    fun observeById(id: String): Flow<VaultItemEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: VaultItemEntity)

    @Update
    suspend fun update(item: VaultItemEntity)

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM vault_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM vault_items")
    suspend fun count(): Int

    /**
     * Lightweight projection for list display — skips the full encrypted blob.
     * Rows created before the header-column migration have null encrypted_title.
     */
    @Query(
        "SELECT id, encrypted_title, title_iv, encrypted_address, address_iv, category, updated_at " +
        "FROM vault_items ORDER BY updated_at DESC"
    )
    fun observeHeaders(): Flow<List<VaultItemHeaderProjection>>

    /**
     * Backfill the separately-encrypted title/address columns for a legacy row.
     * Called once per item on first unlock after the migration.
     */
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
