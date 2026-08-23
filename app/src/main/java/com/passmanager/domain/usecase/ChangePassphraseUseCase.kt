package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.kdf.KdfProvider
import com.passmanager.crypto.model.KdfParams
import com.passmanager.crypto.util.toUtf8Bytes
import com.passmanager.domain.port.BiometricLockPort
import com.passmanager.domain.repository.MetadataRepository
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.passmanager.domain.exception.WrongPassphraseException

class ChangePassphraseUseCase @Inject constructor(
    private val kdfProvider: KdfProvider,
    private val cipher: AesGcmCipher,
    private val metadataRepository: MetadataRepository,
    private val biometricLockPort: BiometricLockPort
) {
    private val secureRandom = SecureRandom()

    suspend operator fun invoke(currentPassphrase: CharArray, newPassphrase: CharArray) {
        val metadata = metadataRepository.get() ?: error("Vault not initialized")

        val currentBytes = currentPassphrase.toUtf8Bytes()
        var currentDerivedKey: ByteArray? = null
        var vaultKey: ByteArray? = null
        var newBytes: ByteArray? = null
        var newDerivedKey: ByteArray? = null

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
            // The vault key is being re-wrapped against a fresh salt anyway, so this is the one
            // safe moment to move an old vault onto the current cost parameters. Without it a
            // vault created before the defaults changed would keep paying the old cost forever,
            // since every other path unlocks with whatever is stored in its own row.
            val newParams = KdfParams()
            newBytes = newPassphrase.toUtf8Bytes()
            newDerivedKey = withContext(Dispatchers.Default) {
                kdfProvider.deriveKey(newBytes, newSalt, newParams)
            }

            val newWrapped = cipher.encrypt(vaultKey, newDerivedKey)
            metadataRepository.update(
                metadata.copy(kdfSalt = newSalt, wrappedVaultKey = newWrapped, kdfParams = newParams)
            )

            biometricLockPort.disableIfEnabled()
        } finally {
            currentDerivedKey?.fill(0)
            newDerivedKey?.fill(0)
            currentBytes.fill(0)
            newBytes?.fill(0)
            vaultKey?.fill(0)
            currentPassphrase.fill('\u0000')
            newPassphrase.fill('\u0000')
        }
    }
}
