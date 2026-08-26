package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.kdf.KdfProvider
import com.passmanager.crypto.model.KdfParams
import com.passmanager.crypto.util.toUtf8Bytes
import com.passmanager.domain.model.PayloadJson
import com.passmanager.domain.model.PmVaultBodyJson
import com.passmanager.domain.model.PmVaultFile
import com.passmanager.domain.model.PmVaultItemJson
import com.passmanager.domain.port.VaultKeyProvider
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.domain.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.inject.Inject

/**
 * Builds an encrypted `.pmvault` v1 file from the whole vault (`docs/FORMAT.md`).
 *
 * The export passphrase is deliberately independent of the master passphrase: the file carries no
 * Keystore or device-bound material, which is what makes it the migration path between devices and
 * platforms. A fresh salt and fresh [KdfParams] are generated per export — an old vault's stored
 * (possibly weaker) parameters never leak into a new backup.
 */
class ExportVaultUseCase @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val metadataRepository: MetadataRepository,
    private val cipher: AesGcmCipher,
    private val vaultKeyProvider: VaultKeyProvider,
    private val kdfProvider: KdfProvider
) {
    private val secureRandom = SecureRandom()

    /**
     * @param passphrase zeroed before returning, success or not — the caller must not reuse it.
     * @return the complete file bytes, ready to be written to the destination the user picked.
     */
    suspend operator fun invoke(
        passphrase: CharArray,
        exportedAt: Long = System.currentTimeMillis()
    ): ByteArray = withContext(Dispatchers.Default) {
        val metadata = metadataRepository.get() ?: error("Vault not set up")
        val rows = vaultRepository.getAll()

        val vaultKey = vaultKeyProvider.requireUnlockedKey()
        val items = try {
            rows.map { row ->
                check(row.keyVersion == metadata.currentKeyVersion) {
                    "Key version mismatch: item=${row.keyVersion}, vault=${metadata.currentKeyVersion}"
                }
                val plaintext = cipher.decrypt(row.encryptedData, vaultKey)
                // JVM String is immutable and not zeroable — accepted residual, same as
                // DecryptItemUseCase. The byte array is zeroed as soon as it has been decoded.
                val payload = try {
                    PayloadJson.decode(plaintext.decodeToString(), categoryHint = row.category)
                } finally {
                    plaintext.fill(0)
                }
                PmVaultItemJson(
                    id = row.id,
                    category = payload.category.dbKey,
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt,
                    payload = payload.withId(row.id)
                )
            }
        } finally {
            vaultKey.fill(0)
        }

        val salt = ByteArray(PmVaultFile.SALT_LENGTH).also { secureRandom.nextBytes(it) }
        val params = KdfParams()
        val passphraseBytes = passphrase.toUtf8Bytes()
        var exportKey: ByteArray? = null
        var bodyBytes: ByteArray? = null
        try {
            exportKey = kdfProvider.deriveKey(passphraseBytes, salt, params)
            bodyBytes = PmVaultFile.encodeBody(
                PmVaultBodyJson(
                    version = PmVaultFile.VERSION,
                    exportedAt = exportedAt,
                    items = items
                )
            )
            val headerBytes = PmVaultFile.headerBytes(salt, params)
            PmVaultFile.assemble(headerBytes, cipher.encrypt(bodyBytes, exportKey, headerBytes))
        } finally {
            bodyBytes?.fill(0)
            exportKey?.fill(0)
            passphraseBytes.fill(0)
            passphrase.fill('\u0000')
        }
    }
}
