package com.passmanager.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.passmanager.crypto.util.contentEqualsNullable

@Entity(tableName = "vault_metadata")
data class VaultMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1,

    @ColumnInfo(name = "current_key_version")
    val currentKeyVersion: Int,

    @ColumnInfo(name = "wrapped_vault_key", typeAffinity = ColumnInfo.BLOB)
    val wrappedVaultKey: ByteArray,

    @ColumnInfo(name = "wrapper_iv", typeAffinity = ColumnInfo.BLOB)
    val wrapperIv: ByteArray,

    @ColumnInfo(name = "kdf_salt", typeAffinity = ColumnInfo.BLOB)
    val kdfSalt: ByteArray,

    @ColumnInfo(name = "kdf_params_json")
    val kdfParamsJson: String,

    @ColumnInfo(name = "biometric_enabled")
    val biometricEnabled: Int,

    @ColumnInfo(name = "biometric_wrapped_key")
    val biometricWrappedKey: ByteArray?,

    @ColumnInfo(name = "biometric_wrapper_iv")
    val biometricWrapperIv: ByteArray?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultMetadataEntity) return false
        return id == other.id &&
            currentKeyVersion == other.currentKeyVersion &&
            wrappedVaultKey.contentEquals(other.wrappedVaultKey) &&
            wrapperIv.contentEquals(other.wrapperIv) &&
            kdfSalt.contentEquals(other.kdfSalt) &&
            kdfParamsJson == other.kdfParamsJson &&
            biometricEnabled == other.biometricEnabled &&
            biometricWrappedKey.contentEqualsNullable(other.biometricWrappedKey) &&
            biometricWrapperIv.contentEqualsNullable(other.biometricWrapperIv)
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + currentKeyVersion
        result = 31 * result + wrappedVaultKey.contentHashCode()
        result = 31 * result + wrapperIv.contentHashCode()
        result = 31 * result + kdfSalt.contentHashCode()
        result = 31 * result + kdfParamsJson.hashCode()
        result = 31 * result + biometricEnabled
        result = 31 * result + (biometricWrappedKey?.contentHashCode() ?: 0)
        result = 31 * result + (biometricWrapperIv?.contentHashCode() ?: 0)
        return result
    }
}
