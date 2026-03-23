package com.passmanager.domain.usecase

import com.passmanager.crypto.util.toUtf8Bytes
import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.kdf.KdfProvider
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.security.SessionManager
import com.passmanager.security.VaultLockManager
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cold unlock: derive key from passphrase, decrypt vault key, store in memory.
 * Throws [WrongPassphraseException] on authentication failure.
 */
class UnlockWithPassphraseUseCase @Inject constructor(
    private val metadataRepository: MetadataRepository,
    private val kdfProvider: KdfProvider,
    private val cipher: AesGcmCipher,
    private val vaultLockManager: VaultLockManager,
    private val sessionManager: SessionManager
) {
    suspend operator fun invoke(passphrase: CharArray) {
        val metadata = metadataRepository.get() ?: error("Vault not set up")
        val passphraseBytes = passphrase.toUtf8Bytes()
        var derivedKey: ByteArray? = null

        try {
            derivedKey = withContext(Dispatchers.Default) {
                kdfProvider.deriveKey(passphraseBytes, metadata.kdfSalt, metadata.kdfParams)
            }
            val vaultKey = try {
                cipher.decrypt(metadata.wrappedVaultKey, derivedKey)
            } catch (e: javax.crypto.AEADBadTagException) {
                throw WrongPassphraseException()
            }
            sessionManager.recordPassphraseUnlock()
            vaultLockManager.unlock(vaultKey)
        } finally {
            derivedKey?.fill(0)
            passphraseBytes.fill(0)
        }
    }
}

class WrongPassphraseException : Exception("Incorrect passphrase")
