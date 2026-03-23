package com.passmanager.domain.usecase

import com.passmanager.crypto.util.toUtf8Bytes
import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.kdf.KdfProvider
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.repository.MetadataRepository
import java.security.SecureRandom
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sets up the vault for first use:
 * 1. Generate a random 256-bit vault key
 * 2. Derive a wrapping key from the passphrase using Argon2id
 * 3. Wrap (encrypt) the vault key with the derived key
 * 4. Store metadata (salt, KDF params, wrapped key) in the database
 *
 * Security: The derived key is zeroed immediately after use.
 */
class SetupVaultUseCase @Inject constructor(
    private val kdfProvider: KdfProvider,
    private val cipher: AesGcmCipher,
    private val metadataRepository: MetadataRepository
) {
    private val secureRandom = SecureRandom()

    suspend operator fun invoke(passphrase: CharArray) {
        val passphraseBytes = passphrase.toUtf8Bytes()
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
        val kdfParams = KdfParams()
        val vaultKeyBytes = ByteArray(32).also { secureRandom.nextBytes(it) }

        var derivedKey: ByteArray? = null
        try {
            derivedKey = withContext(Dispatchers.Default) {
                kdfProvider.deriveKey(passphraseBytes, salt, kdfParams)
            }
            val wrappedVaultKey = cipher.encrypt(vaultKeyBytes, derivedKey)

            val metadata = VaultMetadata(
                currentKeyVersion = 1,
                wrappedVaultKey = wrappedVaultKey,
                kdfSalt = salt,
                kdfParams = kdfParams,
                biometricEnabled = false,
                biometricWrappedKey = null
            )
            metadataRepository.save(metadata)
        } finally {
            derivedKey?.fill(0)
            passphraseBytes.fill(0)
            vaultKeyBytes.fill(0)
        }
    }
}
