package com.passmanager.crypto.aead

/**
 * AES-256 in Galois/Counter Mode: the only cipher this project uses.
 *
 * One AEAD, one key size, one nonce size, one tag size. Every parameter GCM exposes is
 * fixed here rather than carried in the file, because a cipher suite written into a
 * container is a cipher suite an attacker can edit — and the weakest option a reader will
 * accept becomes the security of every file it reads.
 *
 * AES-GCM specifically, rather than a modern alternative, for one decisive reason: version 1
 * of this application wrote AES-GCM, and the importer that reads those vaults has to run on
 * every platform version 2 supports. Choosing anything else would not replace AES-GCM here,
 * it would only add a second cipher beside it.
 *
 * ### Nonces
 *
 * GCM's 96-bit nonce is small enough that repeating one under the same key is a real risk
 * to design against, and the consequence of repeating it is not a lost message but a lost
 * key: two ciphertexts under one nonce reveal the authentication subkey, and from then on
 * an attacker can forge tags at will. This module never generates a nonce implicitly.
 * Callers pass one, and the wrapping construction in `VaultKeys` draws a fresh random nonce
 * for a key that is itself used a handful of times.
 */
object AesGcm {

    /** AES-256. AES-128 is not offered; a caller cannot select down. */
    const val KeySize = 32

    /**
     * 96 bits, the only nonce length GCM treats as-is. Any other length is folded through
     * GHASH first, which is legal but is a second code path for no benefit.
     */
    const val NonceSize = 12

    /** 128 bits. Truncated tags are legal in the specification and not accepted here. */
    const val TagSize = 16

    /**
     * Encrypts and authenticates, returning ciphertext followed by the 16-byte tag.
     *
     * `associatedData` is authenticated but not encrypted: it is for the parts of a
     * container that have to stay readable before anything is decrypted — a version number,
     * a key derivation's parameters — while still being impossible to edit.
     */
    fun seal(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray = EmptyAssociatedData,
    ): ByteArray {
        requireKeyAndNonce(key, nonce)
        return platformAesGcmSeal(key, nonce, plaintext, associatedData)
    }

    /**
     * Verifies and decrypts, or returns `null`.
     *
     * Null rather than an exception, because failure here is overwhelmingly the ordinary
     * case of a wrong passphrase, and an exception invites a caller to log it with the
     * inputs attached. A caller that cannot tell "wrong passphrase" from "damaged file"
     * from "someone edited this" is holding the correct amount of information: GCM cannot
     * distinguish them either, and pretending otherwise is how a decryption oracle starts.
     */
    fun open(
        key: ByteArray,
        nonce: ByteArray,
        sealed: ByteArray,
        associatedData: ByteArray = EmptyAssociatedData,
    ): ByteArray? {
        requireKeyAndNonce(key, nonce)
        // Shorter than a tag is not a failed authentication but a malformed input, and it
        // is rejected before any platform call so that every target behaves alike.
        if (sealed.size < TagSize) return null
        return platformAesGcmOpen(key, nonce, sealed, associatedData)
    }

    private fun requireKeyAndNonce(key: ByteArray, nonce: ByteArray) {
        require(key.size == KeySize) { "key is ${key.size} bytes; AES-256-GCM needs $KeySize" }
        require(nonce.size == NonceSize) {
            "nonce is ${nonce.size} bytes; this uses $NonceSize-byte nonces only"
        }
    }

    private val EmptyAssociatedData = ByteArray(0)
}

/**
 * The platform's AES-GCM. Never called directly: [AesGcm] is the only entry point, so the
 * size checks hold on every target rather than on whichever one the caller happened to test.
 */
internal expect fun platformAesGcmSeal(
    key: ByteArray,
    nonce: ByteArray,
    plaintext: ByteArray,
    associatedData: ByteArray,
): ByteArray

internal expect fun platformAesGcmOpen(
    key: ByteArray,
    nonce: ByteArray,
    sealed: ByteArray,
    associatedData: ByteArray,
): ByteArray?
