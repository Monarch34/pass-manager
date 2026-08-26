package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.crypto.util.contentEqualsNullable
import com.passmanager.domain.exception.DeviceKeyLostException
import com.passmanager.domain.exception.DeviceKeyUnavailableException
import com.passmanager.domain.exception.WrongPassphraseException
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.model.VaultWrapVersion
import com.passmanager.domain.port.PepperPort
import javax.crypto.AEADBadTagException
import javax.inject.Inject

/** The three stored fields that together describe a wrapped vault key. */
data class WrappedVaultKey(
    /** Goes to `wrapped_vault_key` + `wrapper_iv`; the IV is always the inner one. */
    val onDisk: EncryptedData,
    /** Goes to `pepper_iv`; null for a passphrase-only vault. */
    val pepperIv: ByteArray?,
    val wrapVersion: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WrappedVaultKey) return false
        return onDisk == other.onDisk &&
            wrapVersion == other.wrapVersion &&
            pepperIv.contentEqualsNullable(other.pepperIv)
    }

    override fun hashCode(): Int {
        var result = onDisk.hashCode()
        result = 31 * result + (pepperIv?.contentHashCode() ?: 0)
        result = 31 * result + wrapVersion
        return result
    }
}

/**
 * Applies and removes the two layers around the vault key.
 *
 * ```
 * disk = KeystoreGCM_outer( AesGcm_inner(KEK, vaultKey) )
 * ```
 *
 * The order is the whole point. The inner layer is what the passphrase controls, so it stays
 * closest to the key; the outer layer is what the device controls, so it wraps everything. That
 * way an upgrade can seal an existing vault without ever seeing the passphrase, and a
 * `.pmvault` export — which is built from decrypted items, never from these bytes — stays free of
 * anything device-specific.
 */
class VaultKeyWrapper @Inject constructor(
    private val cipher: AesGcmCipher,
    private val pepper: PepperPort
) {

    /**
     * Wraps [vaultKey] under [kek], adding the device layer when [deviceBound] is set.
     *
     * @throws DeviceKeyUnavailableException the Keystore refused; the caller decides whether to
     *   fall back to a passphrase-only vault or surface a retry.
     */
    fun wrap(vaultKey: ByteArray, kek: ByteArray, deviceBound: Boolean): WrappedVaultKey {
        val inner = cipher.encrypt(vaultKey, kek)
        if (!deviceBound) {
            return WrappedVaultKey(
                onDisk = inner,
                pepperIv = null,
                wrapVersion = VaultWrapVersion.PASSPHRASE_ONLY
            )
        }
        pepper.ensureKey()
        val outer = pepper.seal(inner.ciphertext)
        return WrappedVaultKey(
            // Ciphertext is the sealed blob; the IV stays the inner one.
            onDisk = EncryptedData(ciphertext = outer.ciphertext, iv = inner.iv),
            pepperIv = outer.iv,
            wrapVersion = VaultWrapVersion.DEVICE_BOUND
        )
    }

    /**
     * Adds the device layer to an already-wrapped key **without unwrapping it**.
     *
     * This is what makes the v1 → v2 upgrade cheap and safe: the inner ciphertext is re-sealed
     * exactly as it sits on disk, so no passphrase is needed, no Argon2 runs, and the inner
     * wrapping is never re-derived at a moment where a crash could leave it half-written.
     */
    fun sealExisting(metadata: VaultMetadata): WrappedVaultKey {
        check(!metadata.isDeviceBound) { "Vault already carries a device-bound layer" }
        pepper.ensureKey()
        val outer = pepper.seal(metadata.wrappedVaultKey.ciphertext)
        return WrappedVaultKey(
            onDisk = EncryptedData(
                ciphertext = outer.ciphertext,
                iv = metadata.wrappedVaultKey.iv
            ),
            pepperIv = outer.iv,
            wrapVersion = VaultWrapVersion.DEVICE_BOUND
        )
    }

    /**
     * Recovers the vault key from [metadata] using [kek].
     *
     * Which path to take is decided by `pepper_iv` being present, not by `wrap_version`: the
     * column is editable by anyone with the file, the IV is a fact about the bytes. When the
     * declared shape turns out to be wrong the other layering is tried before giving up, so a
     * tampered or stale `wrap_version` costs a wasted decrypt rather than a locked-out vault.
     *
     * @throws WrongPassphraseException neither layering opened the key.
     * @throws DeviceKeyLostException the device key is permanently gone — recovery screen.
     * @throws DeviceKeyUnavailableException a transient Keystore failure — offer a retry.
     */
    fun unwrap(metadata: VaultMetadata, kek: ByteArray): ByteArray {
        if (!metadata.isDeviceBound) {
            // Includes the inconsistent case of a row claiming v2 with no pepper_iv: there is no
            // outer layer to peel, so the passphrase path is the only candidate there ever was.
            return openPassphraseOnly(metadata, kek)
        }
        return try {
            openDeviceBound(metadata, kek)
        } catch (e: AEADBadTagException) {
            // Either the passphrase is wrong or the stored shape disagrees with the bytes. Device
            // key failures are deliberately NOT caught here: masking one behind a fallback that
            // can only ever end in "wrong passphrase" would send the user hunting for a typo
            // while the real problem is unrecoverable.
            openPassphraseOnly(metadata, kek)
        }
    }

    private fun openDeviceBound(metadata: VaultMetadata, kek: ByteArray): ByteArray {
        val pepperIv = metadata.pepperIv ?: throw AEADBadTagException()
        val innerCiphertext = pepper.open(
            EncryptedData(ciphertext = metadata.wrappedVaultKey.ciphertext, iv = pepperIv)
        )
        return try {
            cipher.decrypt(
                EncryptedData(ciphertext = innerCiphertext, iv = metadata.wrappedVaultKey.iv),
                kek
            )
        } finally {
            innerCiphertext.fill(0)
        }
    }

    private fun openPassphraseOnly(metadata: VaultMetadata, kek: ByteArray): ByteArray = try {
        cipher.decrypt(metadata.wrappedVaultKey, kek)
    } catch (e: AEADBadTagException) {
        throw WrongPassphraseException()
    }
}
