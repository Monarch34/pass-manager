package com.passmanager.domain.port

import com.passmanager.crypto.model.EncryptedData

/**
 * The device-bound outer layer ("pepper") around an already-wrapped vault key.
 *
 * On disk a v2 vault is `KeystoreGCM_outer( AesGcm_inner(KEK, vaultKey) )`. The inner layer is the
 * passphrase-derived wrapping that has always been there; this port owns the outer one, whose key
 * never leaves the Android Keystore and cannot be exported. That is what makes a stolen `vault.db`
 * useless without the phone it came from — and it is deliberately absent from `.pmvault`, which
 * stays portable.
 *
 * A port rather than a direct Keystore call so every use case that wraps or unwraps stays unit
 * testable; the real implementation is [com.passmanager.crypto.keystore.PepperKeyManager].
 */
interface PepperPort {

    /** True when the device key exists and this device can use the Keystore at all. */
    fun isKeyPresent(): Boolean

    /**
     * Creates the device key if it is missing. Safe to call repeatedly — an existing key is left
     * alone, because regenerating it would strand every vault sealed under the old one.
     *
     * @throws com.passmanager.domain.exception.DeviceKeyUnavailableException the Keystore refused.
     */
    fun ensureKey()

    /** Removes the device key. Only the vault reset flow may call this. */
    fun deleteKey()

    /**
     * Seals [plaintext] under the device key. The IV comes from the Keystore itself, never from
     * the caller.
     *
     * @throws com.passmanager.domain.exception.DeviceKeyLostException the key is gone for good.
     * @throws com.passmanager.domain.exception.DeviceKeyUnavailableException a transient failure.
     */
    fun seal(plaintext: ByteArray): EncryptedData

    /**
     * Opens what [seal] produced.
     *
     * @throws javax.crypto.AEADBadTagException the bytes are not what this key sealed.
     * @throws com.passmanager.domain.exception.DeviceKeyLostException the key is gone for good.
     * @throws com.passmanager.domain.exception.DeviceKeyUnavailableException a transient failure.
     */
    fun open(sealed: EncryptedData): ByteArray
}
