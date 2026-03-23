package com.passmanager.domain.usecase

import com.passmanager.crypto.util.toUtf8Bytes
import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.kdf.KdfProvider
import com.passmanager.domain.repository.MetadataRepository
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChangePassphraseUseCase @Inject constructor(
    private val kdfProvider: KdfProvider,
    private val cipher: AesGcmCipher,
    private val metadataRepository: MetadataRepository
) {
    private val secureRandom = SecureRandom()

    suspend operator fun invoke(currentPassphrase: CharArray, newPassphrase: CharArray) {
        val metadata = metadataRepository.get() ?: error("Vault not initialized")

        val currentBytes = currentPassphrase.toUtf8Bytes()
        var currentDerivedKey: ByteArray? = null
        var newDerivedKey: ByteArray? = null
        var vaultKey: ByteArray? = null
        var newBytes: ByteArray? = null

        try {
            currentDerivedKey = withContext(Dispatchers.Default) {
                kdfProvider.deriveKey(currentBytes, metadata.kdfSalt, metadata.kdfParams)
            }

            vaultKey = try {
                cipher.decrypt(metadata.wrappedVaultKey, currentDerivedKey)
            } catch (e: AEADBadTagException) {
                throw WrongPassphraseException()
            }

            val newSalt = ByteArray(16).also { secureRandom.nextBytes(it) }
            val newBytesArr = newPassphrase.toUtf8Bytes()
            newBytes = newBytesArr
            newDerivedKey = withContext(Dispatchers.Default) {
                kdfProvider.deriveKey(newBytesArr, newSalt, metadata.kdfParams)
            }

            val newWrapped = cipher.encrypt(vaultKey, newDerivedKey)
            metadataRepository.update(metadata.copy(kdfSalt = newSalt, wrappedVaultKey = newWrapped))
        } finally {
            currentDerivedKey?.fill(0)
            newDerivedKey?.fill(0)
            currentBytes.fill(0)
            newBytes?.fill(0)
            vaultKey?.fill(0)
        }
    }
}
