package com.passmanager.domain.usecase

import com.passmanager.crypto.kdf.KdfProvider
import com.passmanager.crypto.util.toUtf8Bytes
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.domain.port.UnlockSessionRecorder
import com.passmanager.domain.port.VaultKeyProvider
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The passphrase unlock path.
 *
 * Peeling the layers is [VaultKeyWrapper]'s job, including which failure means what: a wrong
 * passphrase, a device key that is temporarily unusable, and a device key that is gone for good
 * are three different outcomes with three different screens, and collapsing them is how a user
 * ends up wiping an intact vault over a transient Keystore hiccup.
 */
class UnlockWithPassphraseUseCase @Inject constructor(
    private val metadataRepository: MetadataRepository,
    private val kdfProvider: KdfProvider,
    private val keyWrapper: VaultKeyWrapper,
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
            val vaultKey = keyWrapper.unwrap(metadata, derivedKey)
            sessionRecorder.recordSuccessfulUnlock()
            vaultKeyProvider.unlock(vaultKey)
        } finally {
            derivedKey?.fill(0)
            passphraseBytes.fill(0)
            passphrase.fill('\u0000')
        }
    }
}
