package com.passmanager.vault

import com.passmanager.crypto.Secret
import com.passmanager.crypto.kdf.Argon2Parameters
import com.passmanager.domain.item.ItemId
import com.passmanager.domain.item.VaultItem
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
class VaultSession internal constructor(
    private val store: VaultFileStore,
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
        persist(contents.items.filterNot { it.id == id })
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
}

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
        passphrase: Secret,
        parameters: Argon2Parameters = Argon2Parameters.Default,
    ): VaultSession {
        store.write(PmVault.create(VaultContents(), passphrase, parameters))
        // Opened by reading back what was just written rather than by assuming. This is the
        // only place the writer and the reader are checked against each other on a real
        // device, and a vault that cannot be reopened is worth finding out about now.
        return when (val result = unlock(store, passphrase)) {
            is UnlockResult.Unlocked -> result.session
            else -> error("a vault written here could not be reopened: $result")
        }
    }

    fun unlock(store: VaultFileStore, passphrase: Secret): UnlockResult {
        if (!store.exists()) return UnlockResult.NoVault
        return when (val parsed = PmVault.parse(store.read())) {
            is VaultParse.Sealed -> when (val opened = parsed.openWithPassphrase(passphrase)) {
                is VaultOpen.Opened ->
                    UnlockResult.Unlocked(VaultSession(store, opened.vaultKey, opened.contents))
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
