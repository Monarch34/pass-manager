package com.passmanager.domain.model

import com.passmanager.crypto.model.EncryptedData
import com.passmanager.crypto.model.KdfParams
import com.passmanager.crypto.util.contentEqualsNullable

data class VaultMetadata(
    val currentKeyVersion: Int,
    /**
     * What is actually stored on disk, paired with the **inner** AES-GCM IV.
     *
     * For [VaultWrapVersion.PASSPHRASE_ONLY] the ciphertext is `AesGcm(KEK, vaultKey)` and the IV
     * is its own. For [VaultWrapVersion.DEVICE_BOUND] the ciphertext is that same inner blob after
     * the Keystore sealed it, while this IV still belongs to the inner layer — the outer one lives
     * in [pepperIv]. Keeping the inner IV here is what lets an upgrade re-seal an existing vault
     * without touching the inner wrapping, and therefore without the passphrase.
     */
    val wrappedVaultKey: EncryptedData,
    val kdfSalt: ByteArray,
    val kdfParams: KdfParams,
    val biometricEnabled: Boolean,
    val biometricWrappedKey: EncryptedData?,
    val wrapVersion: Int = VaultWrapVersion.PASSPHRASE_ONLY,
    /**
     * IV of the device-bound outer layer. Non-null exactly when an outer layer exists — and it,
     * not [wrapVersion], is what the unwrapper trusts: a column can be edited, but the bytes
     * cannot lie about their own shape.
     */
    val pepperIv: ByteArray? = null
) {
    /** True when the bytes on disk actually carry a device-bound outer layer. */
    val isDeviceBound: Boolean get() = pepperIv != null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VaultMetadata) return false
        return currentKeyVersion == other.currentKeyVersion &&
            wrappedVaultKey == other.wrappedVaultKey &&
            kdfSalt.contentEquals(other.kdfSalt) &&
            kdfParams == other.kdfParams &&
            biometricEnabled == other.biometricEnabled &&
            biometricWrappedKey == other.biometricWrappedKey &&
            wrapVersion == other.wrapVersion &&
            pepperIv.contentEqualsNullable(other.pepperIv)
    }

    override fun hashCode(): Int {
        var result = currentKeyVersion
        result = 31 * result + wrappedVaultKey.hashCode()
        result = 31 * result + kdfSalt.contentHashCode()
        result = 31 * result + kdfParams.hashCode()
        result = 31 * result + biometricEnabled.hashCode()
        result = 31 * result + (biometricWrappedKey?.hashCode() ?: 0)
        result = 31 * result + wrapVersion
        result = 31 * result + (pepperIv?.contentHashCode() ?: 0)
        return result
    }
}
