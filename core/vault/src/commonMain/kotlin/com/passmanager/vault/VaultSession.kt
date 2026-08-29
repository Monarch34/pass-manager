package com.passmanager.vault

import com.passmanager.crypto.Secret
import com.passmanager.crypto.kdf.Argon2Parameters
import com.passmanager.domain.item.ItemId
import com.passmanager.domain.item.VaultItem
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import com.passmanager.format.BlobHeader
import com.passmanager.format.BlobId
import com.passmanager.format.PmBlob
import com.passmanager.format.PmVault
import com.passmanager.format.VaultContents
import com.passmanager.format.VaultOpen
import com.passmanager.format.VaultParse

/**
 * An open vault: the key, the items, and what it means to change one.
 *
 * The vault key is held here for exactly as long as the vault is unlocked, and destroying it
 * is what [lock] means. Holding it is what makes an edit cheap — a save re-seals under the
 * key already in hand rather than deriving it again, so editing an entry costs no Argon2 and
 * the application can never be manoeuvred into asking for the passphrase a second time.
 *
 * Everything here is shared. Android and iOS differ only in where the bytes are kept, which
 * is [VaultFileStore]'s business.
 */
@OptIn(ExperimentalEncodingApi::class)
class VaultSession internal constructor(
    private val store: VaultFileStore,
    private val blobs: BlobFileStore,
    private val vaultKey: Secret,
    private var contents: VaultContents,
) {

    var isLocked: Boolean = false
        private set

    val items: List<VaultItem> get() = contents.items

    /**
     * Inserts or replaces by identity, then writes the whole vault.
     *
     * There is no partial write. The container is one sealed document, so changing one entry
     * and changing all of them cost the same, and a save that only rewrote part of it would
     * be a second code path producing files the reader has never seen.
     */
    fun save(item: VaultItem) {
        requireOpen()
        val existing = contents.items.indexOfFirst { it.id == item.id }
        val updated = contents.items.toMutableList()
        if (existing >= 0) updated[existing] = item else updated += item
        persist(updated)
    }

    fun delete(id: ItemId) {
        requireOpen()
        val doomed = attachments(id)
        // The vault first, the attachments second. A crash between the two leaves files
        // nothing points at, which the next unlock sweeps up; the other order would delete
        // an attachment and then fail to remove the item that still claims it.
        persist(contents.items.filterNot { it.id == id })
        for (attachment in doomed) blobs.delete(attachment.id)
    }

    /**
     * Full-text search across everything an item holds, secrets included.
     *
     * This is not a feature bolted on top of the one-document design; it is what falls out
     * of it. The whole vault is already decrypted, so searching a password or a line of
     * notes costs nothing extra — and it fixes a real version 1 defect, where an entry could
     * not be found by its username because only the title and address were matched.
     */
    fun search(query: String): List<VaultItem> {
        requireOpen()
        val needle = query.trim().foldForSearch()
        if (needle.isEmpty()) return contents.items
        return contents.items.filter { it.searchText().contains(needle) }
    }

    // -- Attachments ---------------------------------------------------------

    /**
     * The attachments belonging to an item.
     *
     * Every attachment names its item rather than the item listing its attachments, so this
     * reads each one's header and keeps the ones that match. That is a real cost — a few
     * kilobytes per attachment on the device, not per item — and it is the price of the
     * direction the pointer runs in: an attachment cannot be orphaned by an edit to the item
     * it belongs to, because the item does not know about it.
     */
    fun attachments(itemId: ItemId): List<Attachment> {
        requireOpen()
        return blobs.list().mapNotNull { id ->
            val header = headerOf(id) ?: return@mapNotNull null
            if (header.itemId != itemId) null else header.toAttachment(id)
        }.sortedBy { it.createdAt }
    }

    /**
     * Seals [content] as a new attachment on [itemId].
     *
     * The vault itself is not touched. Attaching a file to an entry rewrites no passwords
     * and cannot corrupt the vault, which is the whole reason attachments are their own
     * files.
     */
    fun attach(
        itemId: ItemId,
        filename: String,
        mimeType: String,
        content: Secret,
        createdAt: Long,
        thumbnail: String? = null,
    ): Attachment {
        requireOpen()
        require(contents.items.any { it.id == itemId }) { "no such item" }
        require(content.size <= PmBlob.MaxContentSize) {
            "an attachment is " + content.size + " bytes; the limit is " + PmBlob.MaxContentSize
        }
        require(attachments(itemId).size < MaxAttachmentsPerItem) {
            "an item may have at most " + MaxAttachmentsPerItem + " attachments"
        }

        val id = BlobId.random()
        val header = BlobHeader(
            itemId = itemId,
            filename = filename,
            mimeType = mimeType,
            size = content.size.toLong(),
            createdAt = createdAt,
            thumbnail = thumbnail,
        )
        blobs.write(id.hex, PmBlob.create(vaultKey, id, header, content))
        return header.toAttachment(id.hex)
    }

    /** Decrypts an attachment. The caller owns the result and must destroy it. */
    fun openAttachment(id: String): Secret? {
        requireOpen()
        val bytes = runCatching { blobs.read(id) }.getOrNull() ?: return null
        return PmBlob.readContent(vaultKey, bytes)
    }

    fun deleteAttachment(id: String) {
        requireOpen()
        blobs.delete(id)
    }

    /**
     * Removes attachments whose item no longer exists.
     *
     * Deleting an item rewrites the vault first and unlinks its attachments second, so a
     * crash in between leaves files nobody points at. That order is deliberate — the reverse
     * would lose an attachment the user still owns — and this is the other half of it: inert
     * leftovers are swept up on the next unlock, never during one.
     */
    fun sweepOrphanedAttachments() {
        requireOpen()
        val live = contents.items.map { it.id }.toHashSet()
        for (id in blobs.list()) {
            val header = headerOf(id) ?: continue
            if (header.itemId !in live) blobs.delete(id)
        }
    }

    /** Reads only as much of an attachment as its details need. */
    private fun headerOf(id: String): BlobHeader? {
        val prefix = runCatching { blobs.readPrefix(id, PmBlob.HeaderPrefixSize) }.getOrNull()
            ?: return null
        return PmBlob.readHeader(vaultKey, prefix)
    }

    /**
     * Hands the vault key to a second unlock path so it can be stored behind a keystore or
     * a biometric gate.
     *
     * This is the only way out of the session for the key, and it is scoped rather than
     * returned: whatever registers a new way in gets the key for the length of a call and
     * cannot keep it. Adding an unlock path is exactly what the two-key model is for, so
     * refusing to expose the key at all would not make the design safer, only make the
     * second path impossible.
     */
    fun <R> useVaultKey(block: (Secret) -> R): R {
        requireOpen()
        return block(vaultKey)
    }

    /** Destroys the key. The session is spent afterwards and every method throws. */
    fun lock() {
        if (!isLocked) {
            vaultKey.destroy()
            isLocked = true
        }
    }

    private fun persist(updated: List<VaultItem>) {
        // withItems, not a fresh VaultContents: the members a newer writer added and this
        // version does not understand have to survive an edit made here, or saving on an
        // older client silently destroys them.
        val next = contents.withItems(updated)
        val current = store.read()
        val sealed = PmVault.parse(current) as? VaultParse.Sealed
            ?: error("the vault on disk is no longer readable")
        // Reusing the descriptor and the slots keeps the salt, the cost and every unlock
        // path exactly as they were: a save is not the moment to re-derive anything.
        store.write(PmVault.write(sealed.descriptor, sealed.slots, next, vaultKey))
        contents = next
    }

    private fun requireOpen() {
        check(!isLocked) { "this vault session was locked" }
    }

    companion object {
        /**
         * Eight. A cap, not a judgement about how many scans an account needs: it bounds
         * what opening one item can cost, and an item with hundreds of attachments is a
         * folder wearing a password entry's clothes.
         */
        const val MaxAttachmentsPerItem = 8
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun BlobHeader.toAttachment(id: String) = Attachment(
    id = id,
    filename = filename,
    mimeType = mimeType,
    size = size,
    createdAt = createdAt,
    // The header is JSON, so a thumbnail travels as text and is turned back into an image's
    // bytes here rather than in each platform's UI.
    thumbnail = thumbnail?.let { runCatching { Base64.decode(it) }.getOrNull() },
)

/** How opening a vault turned out. Mirrors the container's own outcomes rather than flattening them. */
sealed interface UnlockResult {
    class Unlocked(val session: VaultSession) : UnlockResult

    /**
     * The passphrase was wrong, or the file was edited. Indistinguishable by design, and
     * reporting them as one is the point.
     */
    data object WrongPassphrase : UnlockResult

    /** Provably broken, proved without a key. */
    data class Damaged(val what: String, val offset: Int) : UnlockResult

    /** Written by a version this build cannot read. */
    data class Unsupported(val container: Int, val schema: Int, val minSchema: Int) : UnlockResult

    data object NotAVault : UnlockResult

    data object NoVault : UnlockResult
}

object Vault {

    /** Creates a vault and leaves it open. */
    fun create(
        store: VaultFileStore,
        blobs: BlobFileStore,
        passphrase: Secret,
        parameters: Argon2Parameters = Argon2Parameters.Default,
    ): VaultSession {
        store.write(PmVault.create(VaultContents(), passphrase, parameters))
        // Opened by reading back what was just written rather than by assuming. This is the
        // only place the writer and the reader are checked against each other on a real
        // device, and a vault that cannot be reopened is worth finding out about now.
        return when (val result = unlock(store, blobs, passphrase)) {
            is UnlockResult.Unlocked -> result.session
            else -> error("a vault written here could not be reopened: $result")
        }
    }

    /**
     * Opens a vault with the key itself, for the unlock paths where no passphrase was ever
     * typed — a keystore or a biometric gate holding a copy of it.
     *
     * The file is still parsed and its body still authenticated. This replaces the
     * derivation, not the verification, so a damaged or edited vault fails here exactly as
     * it does on the passphrase path. On success the session takes ownership of [vaultKey].
     */
    fun unlockWithVaultKey(
        store: VaultFileStore,
        blobs: BlobFileStore,
        vaultKey: Secret,
    ): UnlockResult {
        if (!store.exists()) return UnlockResult.NoVault
        return when (val parsed = PmVault.parse(store.read())) {
            is VaultParse.Sealed -> {
                val contents = parsed.openWithVaultKey(vaultKey)
                if (contents == null) {
                    UnlockResult.WrongPassphrase
                } else {
                    UnlockResult.Unlocked(VaultSession(store, blobs, vaultKey, contents))
                }
            }
            is VaultParse.Damaged -> UnlockResult.Damaged(parsed.what, parsed.offset)
            is VaultParse.Unsupported ->
                UnlockResult.Unsupported(parsed.container, parsed.schema, parsed.minSchema)
            VaultParse.NotAVault -> UnlockResult.NotAVault
        }
    }

    fun unlock(
        store: VaultFileStore,
        blobs: BlobFileStore,
        passphrase: Secret,
    ): UnlockResult {
        if (!store.exists()) return UnlockResult.NoVault
        return when (val parsed = PmVault.parse(store.read())) {
            is VaultParse.Sealed -> when (val opened = parsed.openWithPassphrase(passphrase)) {
                is VaultOpen.Opened -> UnlockResult.Unlocked(
                    VaultSession(store, blobs, opened.vaultKey, opened.contents)
                )
                VaultOpen.Unopenable -> UnlockResult.WrongPassphrase
            }
            is VaultParse.Damaged -> UnlockResult.Damaged(parsed.what, parsed.offset)
            is VaultParse.Unsupported ->
                UnlockResult.Unsupported(parsed.container, parsed.schema, parsed.minSchema)
            VaultParse.NotAVault -> UnlockResult.NotAVault
        }
    }
}

/**
 * Case folding that survives Turkish.
 *
 * `String.lowercase()` turns İ into `i` followed by a combining dot, so a title containing it
 * stops matching a query that does not — which silently breaks search for exactly the
 * alphabet this project's first users type in. Folding through uppercase first collapses the
 * dotted and dotless forms onto one character each.
 */
internal fun String.foldForSearch(): String =
    buildString(length) { for (c in this@foldForSearch) append(c.uppercaseChar().lowercaseChar()) }

private fun VaultItem.searchText(): String = buildString {
    append(payload.title).append(' ')
    payload.notes.reveal { append(it).append(' ') }
    appendPayloadFields(this@searchText)
}.foldForSearch()
