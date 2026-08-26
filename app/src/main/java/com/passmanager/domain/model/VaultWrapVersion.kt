package com.passmanager.domain.model

/**
 * How the vault key on disk is wrapped.
 *
 * This is not the same thing as `currentKeyVersion`, which tracks the vault key itself. This
 * tracks the *layers around it*.
 */
object VaultWrapVersion {

    /** `AesGcm(KEK, vaultKey)` — passphrase only. A stolen database file is offline-attackable. */
    const val PASSPHRASE_ONLY = 1

    /**
     * `KeystoreGCM_outer( AesGcm_inner(KEK, vaultKey) )` — the outer key lives in the Android
     * Keystore and cannot leave the device, so the file alone is useless.
     */
    const val DEVICE_BOUND = 2
}
