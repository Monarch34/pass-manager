package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.kdf.KdfProvider
import com.passmanager.crypto.util.toUtf8Bytes
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.domain.port.UnlockSessionRecorder
import com.passmanager.domain.port.VaultKeyProvider
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.passmanager.domain.exception.WrongPassphraseException

class UnlockWithPassphraseUseCase @Inject constructor(
    private val metadataRepository: MetadataRepository,
    private val kdfProvider: KdfProvider,
    private val cipher: AesGcmCipher,
    private val vaultKeyProvider: VaultKeyProvider,
    private val sessionRecorder: UnlockSessionRecorder
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
            } catch (e: AEADBadTagException) {
                throw WrongPassphraseException()
            }
            sessionRecorder.recordSuccessfulUnlock()
            vaultKeyProvider.unlock(vaultKey)
        } finally {
            derivedKey?.fill(0)
            passphraseBytes.fill(0)
            passphrase.fill('\u0000')
        }
    }
}
