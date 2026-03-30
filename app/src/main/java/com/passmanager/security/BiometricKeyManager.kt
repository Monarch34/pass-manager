package com.passmanager.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.keystore.AndroidKeystoreManager
import com.passmanager.domain.model.LockState
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.port.BiometricLockPort
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.security.biometric.BiometricHelper
import com.passmanager.domain.exception.BiometricKeyInvalidatedException
import javax.crypto.Cipher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Owns the full biometric key lifecycle: enrollment, unlock, and disabling.
 *
 * The vault key is never stored in plaintext. On enrollment it is wrapped
 * (encrypted) with a hardware-backed Keystore key that requires biometric
 * authentication to use. The wrapped key is persisted via [MetadataRepository].
 *
 * Biometric is a second unlock path — it decrypts the same vault key that
 * the passphrase path produces. Disabling it only removes the wrapped copy;
 * the vault itself is unaffected.
 */
@Singleton
class BiometricKeyManager @Inject constructor(
    private val metadataRepository: MetadataRepository,
    private val cipher: AesGcmCipher,
    private val keystoreManager: AndroidKeystoreManager,
    private val vaultLockManager: VaultLockManager,
    private val sessionManager: SessionManager,
    private val biometricHelper: BiometricHelper
) : BiometricLockPort {

    override suspend fun isHardwareAvailable(): Boolean = biometricHelper.canUseBiometric()

    override fun observeIsEnrolled(): Flow<Boolean> =
        metadataRepository.observe().map { it?.biometricEnabled ?: false }

    override suspend fun isAvailable(): Boolean {
        val metadata = metadataRepository.get() ?: return false
        return metadata.biometricEnabled &&
            metadata.biometricWrappedKey != null &&
            keystoreManager.hasBiometricKey() &&
            biometricHelper.canUseBiometric()
    }

    override fun prepareEnrollment(): Cipher {
        keystoreManager.generateBiometricKey()
        return keystoreManager.createEncryptCipher()
    }

    override suspend fun completeEnrollment(authenticatedCipher: Cipher) {
        check(vaultLockManager.lockState.value is LockState.Unlocked) {
            "Vault must be unlocked to enroll biometric"
        }
        val vaultKey = vaultLockManager.requireUnlockedKey()
        try {
            val wrappedKey = cipher.encryptWithCipher(vaultKey, authenticatedCipher)
            val current = metadataRepository.get() ?: error("Vault not set up")
            metadataRepository.update(
                current.copy(biometricEnabled = true, biometricWrappedKey = wrappedKey)
            )
        } finally {
            vaultKey.fill(0)
        }
    }

    override suspend fun disable() {
        val current = metadataRepository.get() ?: return
        metadataRepository.update(
            current.copy(biometricEnabled = false, biometricWrappedKey = null)
        )
        keystoreManager.deleteBiometricKey()
    }

    override suspend fun disableIfEnabled() {
        val metadata = metadataRepository.get() ?: return
        if (metadata.biometricEnabled) disable()
    }

    override suspend fun createAuthCipher(): Cipher {
        val metadata = metadataRepository.get() ?: error("Vault not set up")
        val iv = metadata.biometricWrappedKey?.iv ?: error("Biometric not enabled")
        return try {
            keystoreManager.createDecryptCipher(iv)
        } catch (e: KeyPermanentlyInvalidatedException) {
            handleInvalidatedKey(metadata)
            throw BiometricKeyInvalidatedException()
        }
    }

    override suspend fun unlock(authenticatedCipher: Cipher) {
        val metadata = metadataRepository.get() ?: error("Vault not set up")
        val wrappedKey = metadata.biometricWrappedKey
            ?: error("Biometric not enabled — no wrapped key in metadata")
        var vaultKey: ByteArray? = null
        try {
            vaultKey = cipher.decryptWithCipher(wrappedKey, authenticatedCipher)
            sessionManager.recordSuccessfulUnlock()
            vaultLockManager.unlock(vaultKey)
        } catch (e: KeyPermanentlyInvalidatedException) {
            vaultKey?.fill(0)
            handleInvalidatedKey(metadata)
            throw BiometricKeyInvalidatedException()
        }
    }

    private suspend fun handleInvalidatedKey(current: VaultMetadata) {
        metadataRepository.update(
            current.copy(biometricEnabled = false, biometricWrappedKey = null)
        )
        keystoreManager.deleteBiometricKey()
        vaultLockManager.forceColdLock()
    }
}

