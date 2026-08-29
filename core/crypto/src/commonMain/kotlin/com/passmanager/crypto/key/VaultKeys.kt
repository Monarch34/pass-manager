package com.passmanager.crypto.key

import com.passmanager.crypto.Secret
import com.passmanager.crypto.aead.AesGcm
import com.passmanager.crypto.kdf.Argon2Parameters
import com.passmanager.crypto.kdf.argon2id
import com.passmanager.crypto.random.secureRandomBytes

/**
 * The two-key model: a random vault key that encrypts the contents, and a key-encryption
 * key derived from the passphrase that encrypts only the vault key.
 *
 * The passphrase never encrypts anything directly, and that indirection buys three things
 * that a single derived key cannot:
 *
 * - **Changing the passphrase rewraps sixty bytes.** Under a single derived key it would
 *   mean decrypting and re-encrypting the whole vault: slow, and a window in which the
 *   entire plaintext exists at once and a crash leaves a half-written file.
 * - **A second way in costs one more wrapped copy.** Unlocking with biometrics is the same
 *   vault key wrapped again under a key the platform keystore holds and the passphrase
 *   never touches. Both unlock paths reach the same vault key; neither can derive the
 *   other's. The same shape gives a written-down recovery code its own slot.
 * - **A stolen key expires.** The vault key can be replaced and the contents re-encrypted
 *   without the user choosing a new passphrase, and the passphrase can be changed without
 *   touching the contents. Under one key those two are the same operation and neither can
 *   happen alone.
 *
 * Every key here is a [Secret]; only the salt and the wrapped blob are plain arrays, because
 * only those two are safe to write to a file. How they are stored is not decided here — this
 * module produces and consumes opaque bytes, and the container is the format module's
 * business.
 */
object VaultKeys {

    /** Matches [AesGcm.KeySize]: the vault key is used directly as an AES-256 key. */
    const val VaultKeySize = AesGcm.KeySize

    /**
     * 16 bytes. RFC 9106 requires 8 and recommends 16, and a salt is only doing its job if
     * two vaults never share one — at 16 bytes drawn at random, they never will.
     */
    const val SaltSize = 16

    /** A wrapped vault key: nonce, then the sealed key and its tag. */
    const val WrappedSize = AesGcm.NonceSize + VaultKeySize + AesGcm.TagSize

    /**
     * A fresh vault key. Drawn, never derived — its strength is 256 bits of entropy
     * regardless of what the user chose as a passphrase.
     */
    fun newVaultKey(): Secret = Secret.random(VaultKeySize)

    /** Not a secret: the salt is stored in the clear beside the thing it salts. */
    fun newSalt(): ByteArray = secureRandomBytes(SaltSize)

    /**
     * Turns a passphrase into the key that wraps the vault key.
     *
     * @param pepper an optional value held outside the vault file — a keystore-backed key,
     *   for instance. Including one means a copy of the file is not enough to attack the
     *   passphrase offline, because the attacker is missing an input to the hash rather than
     *   merely facing a slow one. It also means the vault cannot be opened on another
     *   device, which is why anything that turns it on must offer a way back in.
     */
    fun deriveKeyEncryptionKey(
        passphrase: Secret,
        salt: ByteArray,
        parameters: Argon2Parameters,
        pepper: Secret? = null,
    ): Secret = argon2id(
        password = passphrase,
        salt = salt,
        parameters = parameters,
        tagLength = AesGcm.KeySize,
        pepper = pepper,
    )

    /**
     * Seals the vault key under the key-encryption key.
     *
     * The nonce is fresh for every call and stored with the ciphertext, so rewrapping the
     * same vault key after a passphrase change never reuses one.
     */
    fun wrap(keyEncryptionKey: Secret, vaultKey: Secret): ByteArray {
        require(vaultKey.size == VaultKeySize) {
            "vault key is ${vaultKey.size} bytes; expected $VaultKeySize"
        }
        val nonce = secureRandomBytes(AesGcm.NonceSize)
        return nonce + AesGcm.seal(keyEncryptionKey, nonce, vaultKey, WrapContext)
    }

    /**
     * Recovers the vault key, or returns `null` if the passphrase was wrong, the file was
     * damaged, or someone edited it. Those are indistinguishable, and treating them as one
     * outcome is the point.
     */
    fun unwrap(keyEncryptionKey: Secret, wrapped: ByteArray): Secret? {
        if (wrapped.size != WrappedSize) return null
        val nonce = wrapped.copyOfRange(0, AesGcm.NonceSize)
        val sealed = wrapped.copyOfRange(AesGcm.NonceSize, wrapped.size)
        return AesGcm.open(keyEncryptionKey, nonce, sealed, WrapContext)
    }

    /**
     * Authenticated alongside the wrapped key, so that a blob sealed for some other purpose
     * can never be unwrapped as a vault key even if it were ever produced under the same
     * key. It carries the version because the day this construction changes, the old
     * ciphertexts must stop verifying under the new rules rather than being reinterpreted.
     */
    private val WrapContext = "passmanager.vault-key.v2".encodeToByteArray()
}
