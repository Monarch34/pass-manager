package com.passmanager.domain.usecase

import com.passmanager.crypto.kdf.KdfProvider
import com.passmanager.crypto.util.toUtf8Bytes
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.exception.DeviceKeyUnavailableException
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.repository.MetadataRepository
import java.security.SecureRandom
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SetupVaultUseCase @Inject constructor(
    private val kdfProvider: KdfProvider,
    private val keyWrapper: VaultKeyWrapper,
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
            // New vaults are device-bound from the start: there is nothing to migrate and no
            // backup at risk, so the strongest wrapping is free here. If the Keystore refuses,
            // fall back rather than blocking vault creation — a working passphrase-only vault
            // that Settings can upgrade later beats onboarding that dead-ends on this device.
            val wrapped = try {
                keyWrapper.wrap(vaultKeyBytes, derivedKey, deviceBound = true)
            } catch (e: DeviceKeyUnavailableException) {
                keyWrapper.wrap(vaultKeyBytes, derivedKey, deviceBound = false)
            }

            metadataRepository.save(
                VaultMetadata(
                    currentKeyVersion = 1,
                    wrappedVaultKey = wrapped.onDisk,
                    kdfSalt = salt,
                    kdfParams = kdfParams,
                    biometricEnabled = false,
                    biometricWrappedKey = null,
                    wrapVersion = wrapped.wrapVersion,
                    pepperIv = wrapped.pepperIv
                )
            )
        } finally {
            derivedKey?.fill(0)
            passphraseBytes.fill(0)
            vaultKeyBytes.fill(0)
            passphrase.fill('\u0000')
        }
    }
}
