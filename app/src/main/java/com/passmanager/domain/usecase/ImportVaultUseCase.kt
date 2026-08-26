package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.kdf.KdfProvider
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.crypto.util.toUtf8Bytes
import com.passmanager.domain.exception.PmVaultAuthenticationException
import com.passmanager.domain.model.HeaderEncryption
import com.passmanager.domain.model.ItemPayload
import com.passmanager.domain.model.PayloadJson
import com.passmanager.domain.model.PmVaultFile
import com.passmanager.domain.port.VaultKeyProvider
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.domain.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.AEADBadTagException
import javax.inject.Inject

/** What the merge decided to do with one entry of a `.pmvault` file. */
enum class ImportAction {
    /** No local row carries this id. */
    INSERT,

    /** A local row exists and the file's entry is newer. */
    OVERWRITE,

    /** A local row exists and is at least as new — it is left untouched. */
    SKIP
}

/**
 * One planned change. [payload] is decrypted vault content: an [ImportPlan] must not outlive the
 * dialog that shows it.
 */
data class ImportPlanEntry(
    val id: String,
    val title: String,
    val action: ImportAction,
    internal val payload: ItemPayload,
    internal val createdAt: Long,
    /** Already clamped to `min(fileValue, now)`. */
    internal val updatedAt: Long
)

/** The reviewed-before-applied summary `docs/FORMAT.md` requires. */
data class ImportPlan(
    val entries: List<ImportPlanEntry>,
    val exportedAt: Long
) {
    val insertCount: Int get() = entries.count { it.action == ImportAction.INSERT }
    val overwriteCount: Int get() = entries.count { it.action == ImportAction.OVERWRITE }
    val skippedCount: Int get() = entries.count { it.action == ImportAction.SKIP }

    /** Titles the user is about to lose, so the summary dialog can name them. */
    val overwrittenTitles: List<String>
        get() = entries.filter { it.action == ImportAction.OVERWRITE }.map { it.title }
}

data class ImportResult(val inserted: Int, val overwritten: Int, val skipped: Int)

/**
 * Reads a `.pmvault` v1 file and merges it into the local vault (`docs/FORMAT.md`).
 *
 * Split in two: [plan] does the expensive, failure-prone half (validate → derive → decrypt →
 * classify) so the UI can show the summary, and [apply] commits it. That split is why the plan
 * carries decrypted payloads — deriving again after the confirmation dialog would mean a second
 * Argon2 run on every import.
 *
 * Import never goes through [SaveVaultItemUseCase]: that stamps `now` on every row, which would
 * destroy exactly the timestamps the merge rules compare.
 */
class ImportVaultUseCase @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val metadataRepository: MetadataRepository,
    private val cipher: AesGcmCipher,
    private val vaultKeyProvider: VaultKeyProvider,
    private val kdfProvider: KdfProvider
) {

    /**
     * Validates, derives, decrypts and classifies — but writes nothing.
     *
     * @param passphrase zeroed before returning, success or not.
     * @param now the clamp ceiling; a file's `updatedAt` is read as `min(fileValue, now)` so a
     *   forged far-future timestamp cannot permanently shadow local edits.
     * @throws com.passmanager.domain.exception.PmVaultException on any container-level failure.
     */
    suspend fun plan(
        fileBytes: ByteArray,
        passphrase: CharArray,
        now: Long = System.currentTimeMillis()
    ): ImportPlan {
        // Parsing runs the whole pre-KDF validation gate, so a hostile header is rejected before
        // a single byte of Argon2 memory is touched.
        val parsed = PmVaultFile.parse(fileBytes)

        val body = withContext(Dispatchers.Default) {
            val passphraseBytes = passphrase.toUtf8Bytes()
            var importKey: ByteArray? = null
            var plaintext: ByteArray? = null
            try {
                importKey = kdfProvider.deriveKey(passphraseBytes, parsed.salt, parsed.kdfParams)
                plaintext = try {
                    cipher.decrypt(parsed.body, importKey, parsed.aad)
                } catch (e: AEADBadTagException) {
                    // GCM cannot tell a wrong passphrase from a tampered header or body.
                    throw PmVaultAuthenticationException()
                }
                PmVaultFile.decodeBody(plaintext)
            } finally {
                plaintext?.fill(0)
                importKey?.fill(0)
                passphraseBytes.fill(0)
                passphrase.fill('\u0000')
            }
        }

        // One header projection read rather than a getById per entry: the merge only needs each
        // local row's updatedAt, and headers carry that without loading a single payload blob.
        val localUpdatedAt = vaultRepository.getHeaders().associate { it.id to it.updatedAt }

        val entries = body.items.map { item ->
            // createdAt is preserved verbatim per docs/FORMAT.md; only updatedAt is clamped,
            // because only updatedAt decides who wins a merge.
            val updatedAt = minOf(item.updatedAt, now)
            val existing = localUpdatedAt[item.id]
            val action = when {
                existing == null -> ImportAction.INSERT
                updatedAt > existing -> ImportAction.OVERWRITE
                else -> ImportAction.SKIP
            }
            ImportPlanEntry(
                id = item.id,
                title = item.payload.title,
                action = action,
                payload = item.payload.withId(item.id),
                createdAt = item.createdAt,
                updatedAt = updatedAt
            )
        }
        return ImportPlan(entries = entries, exportedAt = body.exportedAt)
    }

    /**
     * Commits [plan]. Nothing is ever deleted.
     *
     * @param addOnly demotes every [ImportAction.OVERWRITE] to a skip, so an import can add what
     *   is missing without touching anything that already exists.
     */
    suspend fun apply(plan: ImportPlan, addOnly: Boolean = false): ImportResult {
        val metadata = metadataRepository.get() ?: error("Vault not set up")
        var inserted = 0
        var overwritten = 0
        var skipped = 0

        for (entry in plan.entries) {
            val effective = when {
                entry.action == ImportAction.SKIP -> ImportAction.SKIP
                entry.action == ImportAction.OVERWRITE && addOnly -> ImportAction.SKIP
                else -> entry.action
            }
            if (effective == ImportAction.SKIP) {
                skipped++
                continue
            }

            val enc = encryptEnvelopes(entry.payload)
            val header = HeaderEncryption(title = enc.title, address = enc.address)
            when (effective) {
                ImportAction.INSERT -> {
                    vaultRepository.insert(
                        id = entry.id,
                        encryptedData = enc.payload,
                        keyVersion = metadata.currentKeyVersion,
                        createdAt = entry.createdAt,
                        category = entry.payload.category,
                        headerEncryption = header,
                        updatedAt = entry.updatedAt
                    )
                    inserted++
                }
                ImportAction.OVERWRITE -> {
                    // createdAt is intentionally left alone: the row keeps the moment it first
                    // existed on this device, exactly as an edit would.
                    vaultRepository.update(
                        id = entry.id,
                        encryptedData = enc.payload,
                        keyVersion = metadata.currentKeyVersion,
                        updatedAt = entry.updatedAt,
                        category = entry.payload.category,
                        headerEncryption = header
                    )
                    overwritten++
                }
                ImportAction.SKIP -> Unit
            }
        }
        return ImportResult(inserted = inserted, overwritten = overwritten, skipped = skipped)
    }

    private class EncryptedFields(
        val payload: EncryptedData,
        val title: EncryptedData,
        val address: EncryptedData?
    )

    /** The same three-envelope shape [SaveVaultItemUseCase] writes: payload, title, subtitle. */
    private suspend fun encryptEnvelopes(payload: ItemPayload): EncryptedFields =
        withContext(Dispatchers.Default) {
            val vaultKey = vaultKeyProvider.requireUnlockedKey()
            try {
                // PayloadJson.encode returns a JVM String (not zeroable) — accepted residual.
                val jsonBytes = PayloadJson.encode(payload).toByteArray(Charsets.UTF_8)
                val encPayload = cipher.encrypt(jsonBytes, vaultKey)
                jsonBytes.fill(0)

                val titleBytes = payload.title.toByteArray(Charsets.UTF_8)
                val encTitle = cipher.encrypt(titleBytes, vaultKey)
                titleBytes.fill(0)

                val subtitle = payload.listSubtitle
                val encAddress = if (subtitle.isNotEmpty()) {
                    val bytes = subtitle.toByteArray(Charsets.UTF_8)
                    cipher.encrypt(bytes, vaultKey).also { bytes.fill(0) }
                } else null

                EncryptedFields(payload = encPayload, title = encTitle, address = encAddress)
            } finally {
                vaultKey.fill(0)
            }
        }
}
