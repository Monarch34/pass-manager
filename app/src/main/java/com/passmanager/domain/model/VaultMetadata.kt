package com.passmanager.domain.model

import com.passmanager.crypto.model.EncryptedData
import com.passmanager.crypto.model.KdfParams

data class VaultMetadata(
    val currentKeyVersion: Int,
    val wrappedVaultKey: EncryptedData,
    val kdfSalt: ByteArray,
    val kdfParams: KdfParams,
    val biometricEnabled: Boolean,
    val biometricWrappedKey: EncryptedData?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultMetadata) return false
        return currentKeyVersion == other.currentKeyVersion &&
            wrappedVaultKey == other.wrappedVaultKey &&
            kdfSalt.contentEquals(other.kdfSalt) &&
            kdfParams == other.kdfParams &&
            biometricEnabled == other.biometricEnabled &&
            biometricWrappedKey == other.biometricWrappedKey
    }

    override fun hashCode(): Int {
        var result = currentKeyVersion
        result = 31 * result + wrappedVaultKey.hashCode()
        result = 31 * result + kdfSalt.contentHashCode()
        result = 31 * result + kdfParams.hashCode()
        result = 31 * result + biometricEnabled.hashCode()
        result = 31 * result + (biometricWrappedKey?.hashCode() ?: 0)
        return result
    }
}
