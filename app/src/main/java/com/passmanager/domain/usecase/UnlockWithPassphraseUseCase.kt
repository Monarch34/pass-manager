package com.passmanager.domain.usecase

import com.passmanager.crypto.kdf.KdfProvider
import com.passmanager.crypto.util.toUtf8Bytes
import com.passmanager.domain.exception.UnlockThrottledException
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
    private val sessionRecorder: UnlockSessionRecorder,
    private val throttle: UnlockThrottle
) {
    /**
     * @throws UnlockThrottledException too many recent failures; nothing was derived.
     * @throws com.passmanager.domain.exception.WrongPassphraseException
     * @throws com.passmanager.domain.exception.DeviceKeyLostException
     * @throws com.passmanager.domain.exception.DeviceKeyUnavailableException
     */
    suspend operator fun invoke(passphrase: CharArray) {
        var passphraseBytes: ByteArray? = null
        var derivedKey: ByteArray? = null

        try {
            val remaining = throttle.remainingLockoutMs()
            if (remaining > 0) throw UnlockThrottledException(remaining)

            val metadata = metadataRepository.get() ?: error("Vault not set up")

            // Booked as a failure *before* the derivation, and the write is awaited. Argon2 takes
            // about a second; a counter bumped after it would hand every attempt fired into that
            // window the same pre-increment count, so a burst would cost one tick between them.
            // Success below is what cancels it.
            throttle.registerAttempt()

            passphraseBytes = passphrase.toUtf8Bytes()
            derivedKey = withContext(Dispatchers.Default) {
                kdfProvider.deriveKey(passphraseBytes, metadata.kdfSalt, metadata.kdfParams)
            }
            val vaultKey = keyWrapper.unwrap(metadata, derivedKey)
            throttle.clear()
            sessionRecorder.recordSuccessfulUnlock()
            vaultKeyProvider.unlock(vaultKey)
        } finally {
            derivedKey?.fill(0)
            passphraseBytes?.fill(0)
            passphrase.fill('\u0000')
        }
    }
}
