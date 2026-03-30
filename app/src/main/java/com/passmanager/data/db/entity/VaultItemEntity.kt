package com.passmanager.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.passmanager.crypto.util.contentEqualsNullable

@Entity(
    tableName = "vault_items",
    indices = [
        Index(value = ["updated_at"]),
        Index(value = ["category"]),
        Index(value = ["category", "updated_at"])
    ]
)
data class VaultItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "encrypted_data", typeAffinity = ColumnInfo.BLOB)
    val encryptedData: ByteArray,

    @ColumnInfo(name = "data_iv", typeAffinity = ColumnInfo.BLOB)
    val dataIv: ByteArray,

    @ColumnInfo(name = "key_version")
    val keyVersion: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "category", defaultValue = "login")
    val category: String = "login",

    @ColumnInfo(name = "encrypted_title", typeAffinity = ColumnInfo.BLOB)
    val encryptedTitle: ByteArray? = null,

    @ColumnInfo(name = "title_iv", typeAffinity = ColumnInfo.BLOB)
    val titleIv: ByteArray? = null,

    @ColumnInfo(name = "encrypted_address", typeAffinity = ColumnInfo.BLOB)
    val encryptedAddress: ByteArray? = null,

    @ColumnInfo(name = "address_iv", typeAffinity = ColumnInfo.BLOB)
    val addressIv: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultItemEntity) return false
        return id == other.id &&
            encryptedData.contentEquals(other.encryptedData) &&
            dataIv.contentEquals(other.dataIv) &&
            keyVersion == other.keyVersion &&
            createdAt == other.createdAt &&
            updatedAt == other.updatedAt &&
            category == other.category &&
            encryptedTitle.contentEqualsNullable(other.encryptedTitle) &&
            titleIv.contentEqualsNullable(other.titleIv) &&
            encryptedAddress.contentEqualsNullable(other.encryptedAddress) &&
            addressIv.contentEqualsNullable(other.addressIv)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + encryptedData.contentHashCode()
        result = 31 * result + dataIv.contentHashCode()
        result = 31 * result + keyVersion
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + updatedAt.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + (encryptedTitle?.contentHashCode() ?: 0)
        result = 31 * result + (titleIv?.contentHashCode() ?: 0)
        result = 31 * result + (encryptedAddress?.contentHashCode() ?: 0)
        result = 31 * result + (addressIv?.contentHashCode() ?: 0)
        return result
    }
}
