package com.passmanager.domain.model

import com.passmanager.crypto.util.contentEqualsNullable

/**
 * Lightweight vault item representation used for list display.
 * Contains only the fields needed to show an item in a list:
 * separately-encrypted title/address (fast path) and category.
 * Items created before the header-column migration have null encrypted fields
 * and fall back to full-blob decryption.
 */
data class VaultItemHeader(
    val id: String,
    val encryptedTitle:   ByteArray?,
    val titleIv:          ByteArray?,
    val encryptedAddress: ByteArray?,
    val addressIv:        ByteArray?,
    val category:         ItemCategory,
    val updatedAt:        Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultItemHeader) return false
        return id == other.id &&
            encryptedTitle.contentEqualsNullable(other.encryptedTitle) &&
            titleIv.contentEqualsNullable(other.titleIv) &&
            encryptedAddress.contentEqualsNullable(other.encryptedAddress) &&
            addressIv.contentEqualsNullable(other.addressIv) &&
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
}
