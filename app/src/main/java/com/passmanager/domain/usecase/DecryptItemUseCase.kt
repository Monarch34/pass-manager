package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.domain.model.DecryptedVaultItem
import com.passmanager.domain.model.VaultItem
import com.passmanager.security.VaultLockManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

class DecryptItemUseCase @Inject constructor(
    private val cipher: AesGcmCipher,
    private val vaultLockManager: VaultLockManager
) {
    suspend operator fun invoke(item: VaultItem): DecryptedVaultItem =
        withContext(Dispatchers.Default) {
            val vaultKey = vaultLockManager.requireUnlockedKey()

            // TODO: When key rotation is implemented, validate item.keyVersion
            //  against the current key version and support multi-version decryption.

            val plaintext = cipher.decrypt(item.encryptedData, vaultKey)
            val json = plaintext.decodeToString()
            plaintext.fill(0)
            Json.decodeFromString<DecryptedVaultItem>(json)
        }
}
