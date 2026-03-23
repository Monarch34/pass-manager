package com.passmanager.crypto.kdf

import com.passmanager.crypto.model.KdfParams

/**
 * Key derivation function abstraction.
 * Implementations must be deterministic: same inputs always produce the same output.
 */
interface KdfProvider {
    /**
     * Derive a key from [passphrase] using [salt] and [params].
     * The returned ByteArray is owned by the caller; zero it after use.
     *
     * @param passphrase raw UTF-8 bytes of the master passphrase (will NOT be zeroed by this function)
     * @param salt       random 16-byte salt (stored in vault metadata)
     * @param params     KDF tuning parameters
     */
    fun deriveKey(passphrase: ByteArray, salt: ByteArray, params: KdfParams): ByteArray
}
