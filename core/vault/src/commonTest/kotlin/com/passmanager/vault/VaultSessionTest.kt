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

        session.delete(remove.id)

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
        session.delete(item.id)
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
}
