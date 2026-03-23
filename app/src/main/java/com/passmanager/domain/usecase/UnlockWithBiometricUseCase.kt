package com.passmanager.domain.usecase

import android.security.keystore.KeyPermanentlyInvalidatedException
import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.keystore.AndroidKeystoreManager
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.security.SessionManager
import com.passmanager.security.VaultLockManager
import javax.crypto.Cipher
import javax.inject.Inject

/**
 * Biometric unlock: decrypt the biometric-wrapped vault key using an authenticated
 * Keystore cipher from [BiometricPrompt]. Works on cold start whenever biometric
 * is enrolled and metadata contains a wrapped key.
 *
 * Handles [KeyPermanentlyInvalidatedException] by disabling biometric
 * and forcing a cold lock.
 */
class UnlockWithBiometricUseCase @Inject constructor(
    private val metadataRepository: MetadataRepository,
    private val cipher: AesGcmCipher,
    private val keystoreManager: AndroidKeystoreManager,
    private val vaultLockManager: VaultLockManager,
    private val sessionManager: SessionManager
) {
    /**
     * @param authenticatedCipher The cipher returned by BiometricPrompt after successful auth.
     */
    suspend operator fun invoke(authenticatedCipher: Cipher) {
        val metadata = metadataRepository.get() ?: error("Vault not set up")
        val biometricWrappedKey = metadata.biometricWrappedKey
            ?: error("Biometric not enabled — no wrapped key found")

        try {
            val vaultKey = cipher.decryptWithCipher(biometricWrappedKey, authenticatedCipher)
            sessionManager.recordPassphraseUnlock()
            vaultLockManager.unlock(vaultKey)
        } catch (e: KeyPermanentlyInvalidatedException) {
            handleInvalidatedKey()
            throw BiometricKeyInvalidatedException()
        }
    }

    /**
     * Returns a [Cipher] ready for the BiometricPrompt.
     * May throw [KeyPermanentlyInvalidatedException] if the key was invalidated.
     */
    suspend fun createAuthCipher(): Cipher {
        val metadata = metadataRepository.get() ?: error("Vault not set up")
        val iv = metadata.biometricWrappedKey?.iv ?: error("Biometric not enabled")
        return try {
            keystoreManager.createDecryptCipher(iv)
        } catch (e: KeyPermanentlyInvalidatedException) {
            handleInvalidatedKey()
            throw BiometricKeyInvalidatedException()
        }
    }

    private suspend fun handleInvalidatedKey() {
        metadataRepository.disableBiometric()
        keystoreManager.deleteBiometricKey()
        vaultLockManager.forceColdLock()
    }
}

class BiometricKeyInvalidatedException : Exception("Biometric key was invalidated; please unlock with passphrase")
