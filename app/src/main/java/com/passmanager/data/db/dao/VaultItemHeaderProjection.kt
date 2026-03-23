package com.passmanager.data.db.dao

import androidx.room.ColumnInfo

/**
 * Lightweight Room projection for the vault list screen.
 * Only the columns needed for display are loaded — the full encrypted blob is skipped.
 */
data class VaultItemHeaderProjection(
    @ColumnInfo(name = "id")               val id: String,
    @ColumnInfo(name = "encrypted_title")  val encryptedTitle: ByteArray?,
    @ColumnInfo(name = "title_iv")         val titleIv: ByteArray?,
    @ColumnInfo(name = "encrypted_address") val encryptedAddress: ByteArray?,
    @ColumnInfo(name = "address_iv")       val addressIv: ByteArray?,
    @ColumnInfo(name = "category")         val category: String,
    @ColumnInfo(name = "updated_at")       val updatedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultItemHeaderProjection) return false
        return id == other.id &&
            nullableContentEquals(encryptedTitle, other.encryptedTitle) &&
            nullableContentEquals(titleIv, other.titleIv) &&
            nullableContentEquals(encryptedAddress, other.encryptedAddress) &&
            nullableContentEquals(addressIv, other.addressIv) &&
            category == other.category &&
            updatedAt == other.updatedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (encryptedTitle?.contentHashCode() ?: 0)
        result = 31 * result + (titleIv?.contentHashCode() ?: 0)
        result = 31 * result + (encryptedAddress?.contentHashCode() ?: 0)
        result = 31 * result + (addressIv?.contentHashCode() ?: 0)
        result = 31 * result + category.hashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }

    private fun nullableContentEquals(a: ByteArray?, b: ByteArray?): Boolean =
        when {
            a == null && b == null -> true
            a == null || b == null -> false
            else -> a.contentEquals(b)
        }
}
