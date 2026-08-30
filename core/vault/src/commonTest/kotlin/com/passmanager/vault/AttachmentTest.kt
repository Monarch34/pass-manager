package com.passmanager.vault

import com.passmanager.crypto.Secret
import com.passmanager.crypto.kdf.Argon2Parameters
import com.passmanager.domain.item.ItemId
import com.passmanager.domain.item.ItemPayload
import com.passmanager.domain.item.SecretText
import com.passmanager.domain.item.VaultItem
import com.passmanager.format.PmBlob
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttachmentTest {

    private val cheap = Argon2Parameters(memoryKib = 8192, iterations = 1, parallelism = 1)

    private fun open(): Triple<InMemoryVaultStore, InMemoryBlobs, VaultSession> {
        val vault = InMemoryVaultStore()
        val blobs = InMemoryBlobs()
        val session = Secret.ofUtf8("open sesame").use { Vault.create(vault, blobs, it, cheap) }
        return Triple(vault, blobs, session)
    }

    private fun VaultSession.newItem(title: String): VaultItem {
        val item = VaultItem(
            id = ItemId.random(),
            createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_000_000,
            payload = ItemPayload.Note(title = title, notes = SecretText.of("")),
        )
        save(item)
        return item
    }

    @Test
    fun `an attachment round trips`() {
        val (_, _, session) = open()
        val item = session.newItem("Passport")
        val content = ByteArray(2048) { (it * 7 and 0xff).toByte() }

        val stored = Secret.of(content).use {
            session.attach(item.id, "scan.pdf", "application/pdf", it, createdAt = 1_700_000_005_000)
        }

        assertEquals("scan.pdf", stored.filename)
        assertEquals(2048L, stored.size)
        assertEquals(
            content.toList(),
            assertNotNull(session.openAttachment(stored.id)).toByteArray().toList(),
        )
    }

    /**
     * The name, the type and the size are all inside the seal. The only thing readable
     * beside the file is its identifier, which is random — so a directory listing discloses
     * how many attachments exist and nothing about what they are.
     */
    @Test
    fun `nothing about an attachment is readable from the file`() {
        val (_, blobs, session) = open()
        val item = session.newItem("Passport")
        Secret.ofUtf8("PASSPORT NUMBER X1234567").use {
            session.attach(item.id, "passport-scan.jpg", "image/jpeg", it, createdAt = 1)
        }

        val file = blobs.files.values.single()
        val asText = file.decodeToString()
        assertTrue(!asText.contains("passport-scan.jpg"), "the filename was in the clear")
        assertTrue(!asText.contains("image/jpeg"), "the type was in the clear")
        assertTrue(!asText.contains("PASSPORT NUMBER"), "the contents were in the clear")
        assertTrue(!asText.contains(item.id.value), "the owning item was in the clear")
        assertEquals("PMB2", asText.substring(0, 4))
    }

    /**
     * Listing an item's attachments must not read them. A five-megabyte scan costs a few
     * kilobytes to describe, or opening an item with several would stall.
     */
    @Test
    fun `listing reads headers and not contents`() {
        val (_, blobs, session) = open()
        val item = session.newItem("Documents")
        Secret.of(ByteArray(400_000)).use {
            session.attach(item.id, "big.bin", "application/octet-stream", it, createdAt = 1)
        }

        blobs.largestRead = 0
        val listed = session.attachments(item.id)

        assertEquals(1, listed.size)
        assertEquals(400_000L, listed.single().size)
        assertTrue(
            blobs.largestRead <= PmBlob.HeaderPrefixSize,
            "listing read ${blobs.largestRead} bytes, more than a header prefix",
        )
    }

    /**
     * The pointer runs from attachment to item, so an edit to the item cannot orphan it.
     * This is the property the whole direction was chosen for.
     */
    @Test
    fun `editing an item keeps its attachments`() {
        val (_, _, session) = open()
        val item = session.newItem("Bank")
        Secret.ofUtf8("statement").use {
            session.attach(item.id, "statement.pdf", "application/pdf", it, createdAt = 1)
        }

        session.save(
            VaultItem(
                id = item.id,
                createdAt = item.createdAt,
                updatedAt = item.updatedAt + 5_000,
                payload = ItemPayload.Note(title = "Bank renamed", notes = SecretText.of("")),
            )
        )

        assertEquals(1, session.attachments(item.id).size)
    }

    @Test
    fun `deleting an item removes its attachments and leaves others alone`() {
        val (_, blobs, session) = open()
        val doomed = session.newItem("Doomed")
        val kept = session.newItem("Kept")
        Secret.ofUtf8("a").use { session.attach(doomed.id, "a.txt", "text/plain", it, 1) }
        Secret.ofUtf8("b").use { session.attach(kept.id, "b.txt", "text/plain", it, 2) }

        session.delete(doomed.id, now = 1_700_000_010_000)

        assertEquals(0, session.attachments(doomed.id).size)
        assertEquals(1, session.attachments(kept.id).size)
        assertEquals(1, blobs.files.size, "a file was left behind")
    }

    /**
     * A crash between rewriting the vault and unlinking the files leaves inert leftovers.
     * They are swept on the next unlock, never during one.
     */
    @Test
    fun `orphans left by an interrupted delete are swept`() {
        val (_, blobs, session) = open()
        val gone = session.newItem("Gone")
        val kept = session.newItem("Kept")
        val orphan = Secret.ofUtf8("orphan").use {
            session.attach(gone.id, "o.txt", "text/plain", it, 1)
        }
        Secret.ofUtf8("live").use { session.attach(kept.id, "k.txt", "text/plain", it, 2) }

        // A crash between the two halves of a delete: the vault was rewritten without the
        // item, and the unlink never happened. Restoring the file after the delete is what
        // that leaves behind.
        val leftBehind = blobs.files[orphan.id]!!.copyOf()
        session.delete(gone.id, now = 1_700_000_010_000)
        blobs.files[orphan.id] = leftBehind
        assertEquals(2, blobs.files.size)

        session.sweepOrphanedAttachments()

        assertEquals(1, blobs.files.size, "the sweep took the wrong number of files")
        assertEquals(1, session.attachments(kept.id).size, "a live attachment was swept")
    }

    @Test
    fun `an item may not exceed the attachment cap`() {
        val (_, _, session) = open()
        val item = session.newItem("Crowded")
        repeat(VaultSession.MaxAttachmentsPerItem) { index ->
            Secret.ofUtf8("file $index").use {
                session.attach(item.id, "f$index.txt", "text/plain", it, index.toLong())
            }
        }
        assertFailsWith<IllegalArgumentException> {
            Secret.ofUtf8("one too many").use {
                session.attach(item.id, "extra.txt", "text/plain", it, 99)
            }
        }
    }

    @Test
    fun `an attachment larger than the cap is refused`() {
        val (_, _, session) = open()
        val item = session.newItem("Big")
        assertFailsWith<IllegalArgumentException> {
            Secret.of(ByteArray(PmBlob.MaxContentSize + 1)).use {
                session.attach(item.id, "huge.bin", "application/octet-stream", it, 1)
            }
        }
    }

    @Test
    fun `attachments cannot be added to an item that does not exist`() {
        val (_, _, session) = open()
        assertFailsWith<IllegalArgumentException> {
            Secret.ofUtf8("x").use { session.attach(ItemId.random(), "x.txt", "text/plain", it, 1) }
        }
    }

    /** Editing a byte of an attachment must make it unreadable, not subtly different. */
    @Test
    fun `an altered attachment does not open`() {
        val (_, blobs, session) = open()
        val item = session.newItem("Tamper")
        val stored = Secret.ofUtf8("the real contents").use {
            session.attach(item.id, "t.txt", "text/plain", it, 1)
        }

        val file = blobs.files[stored.id]!!
        file[file.size - 5] = (file[file.size - 5].toInt() xor 1).toByte()

        assertNull(session.openAttachment(stored.id))
    }

    /** A different vault cannot read another's attachments, even given the file. */
    @Test
    fun `another vault cannot open the attachment`() {
        val (_, blobs, session) = open()
        val item = session.newItem("Mine")
        val stored = Secret.ofUtf8("private").use {
            session.attach(item.id, "p.txt", "text/plain", it, 1)
        }

        val otherVault = InMemoryVaultStore()
        val otherBlobs = InMemoryBlobs()
        val other = Secret.ofUtf8("a different vault").use {
            Vault.create(otherVault, otherBlobs, it, cheap)
        }
        otherBlobs.files[stored.id] = blobs.files[stored.id]!!

        assertNull(other.openAttachment(stored.id))
        assertEquals(0, other.attachments(item.id).size)
    }

    @Test
    fun `attachments survive locking and reopening the vault`() {
        val (vault, blobs, session) = open()
        val item = session.newItem("Persist")
        Secret.ofUtf8("kept across a lock").use {
            session.attach(item.id, "k.txt", "text/plain", it, 1)
        }
        session.lock()

        val reopened = Secret.ofUtf8("open sesame").use { Vault.unlock(vault, blobs, it) }
        val session2 = assertIs<UnlockResult.Unlocked>(reopened).session
        val listed = session2.attachments(item.id)
        assertEquals(1, listed.size)
        assertEquals(
            "kept across a lock",
            assertNotNull(session2.openAttachment(listed.single().id))
                .reveal { it.decodeToString() },
        )
    }
}

class DestroyTest {

    private val cheap = Argon2Parameters(memoryKib = 8192, iterations = 1, parallelism = 1)

    @Test
    fun `deleting a vault deletes its attachments too`() {
        // Android used to delete only the vault file, so every scan the owner had attached
        // stayed on the device — sealed, unopenable, and permanent, because nothing would
        // ever point at it again to sweep it up.
        val store = InMemoryVaultStore()
        val blobs = InMemoryBlobs()
        val session = Secret.ofUtf8("open sesame").use { Vault.create(store, blobs, it, cheap) }

        val item = VaultItem(
            id = ItemId.random(),
            createdAt = 1_700_000_000_000,
            updatedAt = 1_700_000_000_000,
            payload = ItemPayload.Note(title = "Passport", notes = SecretText.Empty),
        )
        session.save(item)
        Secret.of(ByteArray(64)).use {
            session.attach(item.id, "scan.png", "image/png", it, createdAt = 1_700_000_005_000)
        }
        assertEquals(1, blobs.list().size)

        session.lock()
        Vault.destroy(store, blobs)

        assertTrue(blobs.list().isEmpty(), "an attachment survived the vault being deleted")
        assertTrue(!store.exists())
    }
}
