package com.passmanager.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.passmanager.data.db.entity.VaultMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultMetadataDao {
    @Query("SELECT * FROM vault_metadata WHERE id = 1")
    fun observe(): Flow<VaultMetadataEntity?>

    @Query("SELECT * FROM vault_metadata WHERE id = 1")
    suspend fun get(): VaultMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(metadata: VaultMetadataEntity)

    @Update
    suspend fun update(metadata: VaultMetadataEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM vault_metadata WHERE id = 1)")
    suspend fun exists(): Boolean
}
