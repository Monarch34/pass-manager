package com.passmanager.vault

import com.passmanager.crypto.Secret
import com.passmanager.crypto.kdf.Argon2Parameters
import com.passmanager.domain.item.ItemId
import com.passmanager.domain.item.ItemPayload
import com.passmanager.domain.item.SecretText
import com.passmanager.domain.item.VaultItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VaultSessionTest {

    private val cheap = Argon2Parameters(memoryKib = 8192, iterations = 1, parallelism = 1)

    private fun login(title: String, username: String = "", password: String = "") = VaultItem(
        id = ItemId.random(),
        createdAt = 1_700_000_000_000,
        updatedAt = 1_700_000_000_000,
        payload = ItemPayload.Login(
            title = title,
            username = username,
            password = SecretText.of(password),
        ),
    )

    private fun open(): Pair<InMemoryVaultStore, VaultSession> {
        val store = InMemoryVaultStore()
        val session = Secret.ofUtf8("open sesame").use { Vault.create(store, InMemoryBlobs(), it, cheap) }
        return store to session
    }

    @Test
    fun `a created vault opens again with the same passphrase`() {
        val (store, session) = open()
        session.save(login("GitHub", username = "octocat", password = "hunter2"))
        session.lock()

        val reopened = Secret.ofUtf8("open sesame").use { Vault.unlock(store, InMemoryBlobs(), it) }
        val items = assertIs<UnlockResult.Unlocked>(reopened).session.items
        assertEquals(1, items.size)
        assertEquals("GitHub", items.single().payload.title)
    }

    @Test
    fun `the wrong passphrase is reported as such and not as damage`() {
        val (store, _) = open()
        val result = Secret.ofUtf8("wrong").use { Vault.unlock(store, InMemoryBlobs(), it) }
        assertIs<UnlockResult.WrongPassphrase>(result)
    }

    @Test
    fun `a truncated vault is damaged rather than a wrong passphrase`() {
        val (store, _) = open()
        store.bytes = store.bytes!!.copyOf(store.bytes!!.size / 2)
        val result = Secret.ofUtf8("open sesame").use { Vault.unlock(store, InMemoryBlobs(), it) }
        assertIs<UnlockResult.Damaged>(result)
    }

    @Test
    fun `an absent vault is reported before a passphrase is asked for`() {
        val result = Secret.ofUtf8("anything").use { Vault.unlock(InMemoryVaultStore(), InMemoryBlobs(), it) }
        assertIs<UnlockResult.NoVault>(result)
    }

    @Test
    fun `saving replaces by identity rather than appending`() {
        val (_, session) = open()
        val original = login("GitHub", password = "hunter2")
        session.save(original)

        val edited = VaultItem(
            id = original.id,
            createdAt = original.createdAt,
            updatedAt = original.updatedAt + 1000,
            payload = ItemPayload.Login(title = "GitHub", password = SecretText.of("hunter3")),
        )
        session.save(edited)

        assertEquals(1, session.items.size)
        assertEquals("hunter3", (session.items.single().payload as ItemPayload.Login).password.reveal { it })
    }

    @Test
    fun `deleting removes only the named item`() {
        val (_, session) = open()
        val keep = login("Keep")
        val remove = login("Remove")
        session.save(keep)
        session.save(remove)

        session.delete(remove.id, now = 1_700_000_010_000)

        assertEquals(listOf("Keep"), session.items.map { it.payload.title })
    }

    /**
     * Every change is written through, so the file on disk is never behind what the screen
     * shows. A session that mutated only its own list would look correct until the app was
     * killed.
     */
    @Test
    fun `every edit reaches the store`() {
        val (store, session) = open()
        val before = store.writes
        val item = login("One")
        session.save(item)
        session.save(login("Two"))
        session.delete(item.id, now = 1_700_000_010_000)
        assertEquals(before + 3, store.writes)
    }

    /**
     * The whole vault is decrypted while open, so a search can match a password or a line of
     * notes. Version 1 matched only the title, the address and the category label, which
     * meant an entry could not be found by the username it was saved under.
     */
    @Test
    fun `search reaches fields version 1 could not`() {
        val (_, session) = open()
        session.save(login("Mail", username = "ayse@example.com", password = "s3cret-token"))

        assertEquals(1, session.search("ayse").size, "by username")
        assertEquals(1, session.search("s3cret-token").size, "by password")
        assertEquals(1, session.search("mail").size, "by title, case-insensitively")
        assertEquals(0, session.search("nothing here").size)
        assertEquals(1, session.search("   ").size, "a blank query matches everything")
    }

    /**
     * `String.lowercase()` turns İ into `i` plus a combining dot, so a title containing it
     * stops matching a query typed without one. That silently breaks search for the
     * alphabet this project's first users type in.
     */
    @Test
    fun `search folds Turkish dotted and dotless letters`() {
        val (_, session) = open()
        session.save(login("İstanbul Bankası"))
        session.save(login("Kadıköy"))

        assertTrue(session.search("istanbul").isNotEmpty(), "İstanbul was not found by 'istanbul'")
        assertTrue(session.search("İSTANBUL").isNotEmpty(), "İstanbul was not found by 'İSTANBUL'")
        assertTrue(session.search("kadıköy").isNotEmpty(), "Kadıköy was not found")
        assertTrue(session.search("BANKASI").isNotEmpty(), "case folding failed on 'BANKASI'")
    }

    @Test
    fun `locking destroys the key and the session refuses to work afterwards`() {
        val (_, session) = open()
        session.lock()
        assertTrue(session.isLocked)
        assertFailsWith<IllegalStateException> { session.save(login("After")) }
        assertFailsWith<IllegalStateException> { session.search("anything") }
        // Locking twice is not an error; the second call has nothing left to destroy.
        session.lock()
    }

    /** A save keeps the salt and cost the vault was created with; it never re-derives. */
    @Test
    fun `saving does not change the vault's derivation parameters`() {
        val (store, session) = open()
        val descriptorBefore = store.bytes!!.copyOfRange(0, 31)
        session.save(login("Something"))
        assertEquals(
            descriptorBefore.toList(),
            store.bytes!!.copyOfRange(0, 31).toList(),
            "a save rewrote the descriptor",
        )
    }

    @Test
    fun `an old password is findable`() {
        // The only reason the history is kept is to answer "have I used this one before",
        // and a list nothing can search cannot answer it.
        val (_, session) = openWithBank()
        assertEquals(1, session.search("hunter2").size)
    }

    @Test
    fun `a card security code does not match everything`() {
        // Three digits are a substring of most card and account numbers in any vault, which
        // is exactly why the security code is the one secret left out of the index. The bank
        // here matches on its account number and should; the card, whose only 447 is its
        // security code, must not.
        val (_, session) = openWithBank()
        assertEquals(listOf("Bank"), session.search("447").map { it.payload.title })
    }

    private fun openWithBank(): Pair<InMemoryVaultStore, VaultSession> {
        val store = InMemoryVaultStore()
        val session = Secret.ofUtf8("open sesame")
            .use { Vault.create(store, InMemoryBlobs(), it, Argon2Parameters(8192, 1, 1)) }
        session.save(
            VaultItem(
                id = ItemId.random(),
                createdAt = 1_700_000_000_000,
                updatedAt = 1_700_000_000_000,
                payload = ItemPayload.Bank(
                    title = "Bank",
                    accountNumber = SecretText.of("9930447221"),
                    password = SecretText.of("current"),
                    previousPasswords = listOf(SecretText.of("hunter2")),
                ),
            ),
        )
        session.save(
            VaultItem(
                id = ItemId.random(),
                createdAt = 1_700_000_000_000,
                updatedAt = 1_700_000_000_000,
                payload = ItemPayload.Card(
                    title = "Card",
                    cardNumber = SecretText.of("5100000000000000"),
                    cardCvc = SecretText.of("447"),
                ),
            ),
        )
        return store to session
    }
}

class TombstoneTest {

    private val cheap = Argon2Parameters(memoryKib = 8192, iterations = 1, parallelism = 1)

    private fun open(): Pair<InMemoryVaultStore, VaultSession> {
        val store = InMemoryVaultStore()
        val session = Secret.ofUtf8("open sesame").use { Vault.create(store, InMemoryBlobs(), it, cheap) }
        return store to session
    }

    private fun VaultSession.note(title: String): VaultItem {
        val item = VaultItem(
            id = ItemId.random(),
            createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_000_000,
            payload = ItemPayload.Note(title = title, notes = SecretText.Empty),
        )
        save(item)
        return item
    }

    @Test
    fun `deleting an item records that it was deleted`() {
        // The knowledge that a deletion happened is destroyed at the instant of the deletion,
        // so this is the only moment it can be written down.
        val (_, session) = open()
        val item = session.note("Old account")
        session.delete(item.id, now = 1_700_000_050_000)

        assertEquals(1, session.deletions.size)
        assertEquals(item.id, session.deletions.single().id)
        assertEquals(1_700_000_050_000, session.deletions.single().deletedAt)
    }

    @Test
    fun `a tombstone names nothing but an identifier and a time`() {
        // A record saying what was deleted outlives the item it names, which is the opposite
        // of deleting it. This is enforced by the type, and the test is here so that widening
        // it later is a deliberate act with a failing test attached.
        val (store, session) = open()
        val item = session.note("Extremely Secret Bank")
        session.delete(item.id, now = 1_700_000_050_000)

        val reopened = Secret.ofUtf8("open sesame").use { Vault.unlock(store, InMemoryBlobs(), it) }
        assertIs<UnlockResult.Unlocked>(reopened)
        assertEquals(1, reopened.session.deletions.size)
        // Weak, and worth having anyway: the title of a deleted entry must not survive
        // anywhere in the file, tombstone included.
        assertFalse(store.bytes!!.decodeToString().contains("Extremely Secret Bank"))
    }

    @Test
    fun `a tombstone survives being written and read back`() {
        val (store, session) = open()
        val item = session.note("Old account")
        session.delete(item.id, now = 1_700_000_050_000)

        val reopened = Secret.ofUtf8("open sesame").use { Vault.unlock(store, InMemoryBlobs(), it) }
        assertIs<UnlockResult.Unlocked>(reopened)
        assertEquals(listOf(item.id), reopened.session.deletions.map { it.id })
    }

    @Test
    fun `deleting the same identifier twice leaves one tombstone`() {
        val (_, session) = open()
        val item = session.note("Old account")
        session.delete(item.id, now = 1_700_000_050_000)
        session.save(item)
        session.delete(item.id, now = 1_700_000_090_000)

        assertEquals(1, session.deletions.size)
        assertEquals(1_700_000_090_000, session.deletions.single().deletedAt)
    }

    @Test
    fun `an item that comes back takes its tombstone with it`() {
        // Otherwise the next merge would remove what was just restored.
        val (_, session) = open()
        val item = session.note("Old account")
        session.delete(item.id, now = 1_700_000_050_000)
        session.save(item)

        assertEquals(0, session.deletions.size)
    }

    @Test
    fun `an ordinary edit does not disturb the tombstones`() {
        val (_, session) = open()
        val kept = session.note("Kept")
        val doomed = session.note("Doomed")
        session.delete(doomed.id, now = 1_700_000_050_000)
        session.save(kept.edited(ItemPayload.Note(title = "Renamed", notes = SecretText.Empty), now = 1_700_000_060_000))

        assertEquals(listOf(doomed.id), session.deletions.map { it.id })
    }
}
