package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.keystore.AndroidKeystoreManager
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.security.VaultLockManager
import javax.crypto.Cipher
import javax.inject.Inject

/**
 * Enables biometric unlock:
 * 1. Generate a new Keystore key
 * 2. Wrap the vault key with it using the authenticated cipher
 * 3. Store the wrapped key in metadata
 */
class EnableBiometricUseCase @Inject constructor(
    private val metadataRepository: MetadataRepository,
    private val cipher: AesGcmCipher,
    private val keystoreManager: AndroidKeystoreManager,
    private val vaultLockManager: VaultLockManager
) {
    /**
     * Step 1: Generate key and return a Cipher to present to BiometricPrompt.
     * Must call [completeEnrollment] with the authenticated cipher after success.
     */
    fun prepareEnrollment(): Cipher {
        keystoreManager.generateBiometricKey()
        return keystoreManager.createEncryptCipher()
    }

    /**
     * Step 2: Use the authenticated cipher to wrap the vault key and save.
     */
    suspend fun completeEnrollment(authenticatedCipher: Cipher) {
        val vaultKey = vaultLockManager.requireUnlockedKey()
        val encryptedVaultKey = cipher.encryptWithCipher(vaultKey, authenticatedCipher)
        metadataRepository.enableBiometric(
            biometricWrappedKey = encryptedVaultKey.ciphertext,
            biometricWrapperIv = encryptedVaultKey.iv
        )
    }
}
