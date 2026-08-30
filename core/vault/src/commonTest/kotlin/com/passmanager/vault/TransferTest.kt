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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A clock that is always later than every timestamp used here. */
private const val Now = 1_800_000_000_000

private const val T0 = 1_700_000_000_000
private const val T1 = 1_700_000_100_000
private const val T2 = 1_700_000_200_000

class TransferTest {

    private val cheap = Argon2Parameters(memoryKib = 8192, iterations = 1, parallelism = 1)

    private class Device(val store: InMemoryVaultStore, val blobs: InMemoryBlobs, var session: VaultSession)

    private fun device(passphrase: String = "open sesame"): Device {
        val store = InMemoryVaultStore()
        val blobs = InMemoryBlobs()
        val session = Secret.ofUtf8(passphrase).use { Vault.create(store, blobs, it, cheap) }
        return Device(store, blobs, session)
    }

    private fun note(title: String, id: ItemId = ItemId.random(), at: Long = T0) = VaultItem(
        id = id,
        createdAt = T0,
        updatedAt = at,
        payload = ItemPayload.Note(title = title, notes = SecretText.of("secret of $title")),
    )

    private fun Device.exportWith(passphrase: String = "export me") =
        Secret.ofUtf8(passphrase).use { session.export(it, cheap) }

    private fun Device.read(file: ByteArray, passphrase: String = "export me") =
        Secret.ofUtf8(passphrase).use { session.read(file, it, Now) }

    private fun Device.titles() = session.items.map { it.payload.title }.sorted()

    // ── The file itself ─────────────────────────────────────────────────────

    @Test
    fun `an export is a vault file that the device key does not open`() {
        val phone = device()
        phone.session.save(note("Bank"))
        val file = phone.exportWith()

        // Readable by the reader that already exists, which is the whole point of not
        // inventing a second format.
        assertIs<com.passmanager.format.VaultParse.Sealed>(com.passmanager.format.PmVault.parse(file))

        // And sealed under a key drawn for this file alone: an export is a snapshot, not a
        // spare key, so the vault's own passphrase must not open it.
        val elsewhere = device()
        assertIs<ImportRead.WrongPassphrase>(elsewhere.read(file, "open sesame"))
    }

    @Test
    fun `a wrong passphrase and an edited file are one answer`() {
        val phone = device()
        phone.session.save(note("Bank"))
        val file = phone.exportWith()

        assertIs<ImportRead.WrongPassphrase>(phone.read(file, "not the passphrase"))

        val tampered = file.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1].toInt() xor 1).toByte()
        assertIs<ImportRead.WrongPassphrase>(phone.read(tampered))
    }

    @Test
    fun `a file that is not a vault says so before asking for a passphrase`() {
        val phone = device()
        assertIs<ImportRead.NotAVault>(phone.read("not a vault at all".encodeToByteArray()))
    }

    @Test
    fun `a truncated file is damaged rather than a wrong passphrase`() {
        val phone = device()
        phone.session.save(note("Bank"))
        val file = phone.exportWith()
        assertIs<ImportRead.Damaged>(phone.read(file.copyOf(file.size - 20)))
    }

    // ── The merge, case by case ─────────────────────────────────────────────

    @Test
    fun `re-importing your own export changes nothing`() {
        // The property that makes an export safe to keep: agreeing to it twice is the same
        // as agreeing to it once, and never costs an edit made since.
        val phone = device()
        phone.session.save(note("Bank"))
        val file = phone.exportWith()

        val read = assertIs<ImportRead.Ready>(phone.read(file))
        assertTrue(read.preview.isEmpty, "importing your own file proposed a change")
        read.apply()
        assertEquals(listOf("Bank"), phone.titles())
    }

    @Test
    fun `an item the vault has never seen is added`() {
        val phone = device()
        phone.session.save(note("Bank"))
        val file = phone.exportWith()

        val other = device()
        other.session.save(note("Email"))
        val read = assertIs<ImportRead.Ready>(other.read(file))
        assertEquals(listOf("Bank"), read.preview.added.map { it.payload.title })
        read.apply()
        assertEquals(listOf("Bank", "Email"), other.titles())
    }

    @Test
    fun `the newer edit wins whichever side it is on`() {
        val id = ItemId.random()
        val phone = device()
        phone.session.save(note("Old name", id, at = T0))
        val stale = phone.exportWith()

        // The file is older than the vault: the vault keeps what it has.
        phone.session.save(note("New name", id, at = T2))
        assertIs<ImportRead.Ready>(phone.read(stale)).also {
            assertTrue(it.preview.isEmpty)
            it.apply()
        }
        assertEquals(listOf("New name"), phone.titles())

        // And the other way round: a file newer than the vault replaces it.
        val fresh = phone.exportWith()
        val other = device()
        other.session.save(note("Old name", id, at = T0))
        assertIs<ImportRead.Ready>(other.read(fresh)).also {
            assertEquals(listOf("New name"), it.preview.replaced.map { item -> item.payload.title })
            it.apply()
        }
        assertEquals(listOf("New name"), other.titles())
    }

    @Test
    fun `a file that simply omits an item does not delete it`() {
        // Absence is not an event. Only a tombstone removes anything.
        val phone = device()
        phone.session.save(note("Bank"))
        val file = phone.exportWith()

        val other = device()
        other.session.save(note("Email"))
        assertIs<ImportRead.Ready>(other.read(file)).apply()
        assertEquals(listOf("Bank", "Email"), other.titles())
    }

    @Test
    fun `a deletion recorded after the item was written removes it and names it`() {
        val id = ItemId.random()
        val source = device()
        source.session.save(note("Closed account", id, at = T0))
        source.session.delete(id, now = T2)
        val file = source.exportWith()

        val other = device()
        other.session.save(note("Closed account", id, at = T0))
        val read = assertIs<ImportRead.Ready>(other.read(file))
        // Named, not counted: this is the only outcome that destroys something the owner can
        // currently see, and there is no undo.
        assertEquals(listOf("Closed account"), read.preview.removed.map { it.payload.title })
        read.apply()
        assertEquals(emptyList(), other.titles())
    }

    @Test
    fun `an edit after a deletion beats the deletion`() {
        val id = ItemId.random()
        val source = device()
        source.session.save(note("Account", id, at = T0))
        source.session.delete(id, now = T1)
        val fileWithTombstone = source.exportWith()

        val other = device()
        // Edited here at T2, after the other copy deleted it at T1. An edit is the more
        // deliberate act and the more recent one.
        other.session.save(note("Account, still wanted", id, at = T2))
        assertIs<ImportRead.Ready>(other.read(fileWithTombstone)).also {
            assertTrue(it.preview.removed.isEmpty(), "a stale deletion removed a newer edit")
            it.apply()
        }
        assertEquals(listOf("Account, still wanted"), other.titles())
    }

    @Test
    fun `a deletion is not undone by importing a file made before it`() {
        // The resurrection window tombstones exist to close.
        val id = ItemId.random()
        val source = device()
        source.session.save(note("Account", id, at = T0))
        val beforeDeleting = source.exportWith()
        source.session.delete(id, now = T2)

        val read = assertIs<ImportRead.Ready>(source.read(beforeDeleting))
        assertTrue(read.preview.added.isEmpty(), "a deleted item came back")
        read.apply()
        assertEquals(emptyList(), source.titles())
    }

    @Test
    fun `a forged future timestamp does not win every later comparison`() {
        val id = ItemId.random()
        val source = device()
        source.session.save(note("Forged", id, at = Now * 2))
        val file = source.exportWith()

        val other = device()
        other.session.save(note("Genuine", id, at = T0))
        assertIs<ImportRead.Ready>(other.read(file)).apply()
        // It wins once — its clamped time is `now`, which does beat a genuine older edit.
        assertEquals(listOf("Forged"), other.titles())

        // The point is that it is frozen at import rather than staying in the future: the
        // very next real edit beats it. Clamping only at comparison would have left it
        // winning against every edit forever.
        val survivor = other.session.items.single()
        assertTrue(survivor.updatedAt <= Now, "the forged time was stored unclamped")
    }

    @Test
    fun `a file that contradicts itself is folded rather than guessed at`() {
        // An identifier both present and deleted. The same rule that decides between the two
        // sides decides within one first, so no case downstream has to cope with it.
        val id = ItemId.random()
        val source = device()
        source.session.save(note("Present", id, at = T2))
        // Deleted earlier than it was written: the item is the later event and survives.
        source.session.delete(id, now = T0)
        source.session.save(note("Present", id, at = T2))
        val file = source.exportWith()

        val other = device()
        assertIs<ImportRead.Ready>(other.read(file)).apply()
        assertEquals(listOf("Present"), other.titles())
    }

    @Test
    fun `a merge never invents a payload`() {
        // Both sides are Banks with different histories. The winner must be exactly one of
        // them, not a combination neither device ever wrote.
        val id = ItemId.random()
        fun bank(password: String, history: List<String>, at: Long) = VaultItem(
            id = id, createdAt = T0, updatedAt = at,
            payload = ItemPayload.Bank(
                title = "Bank",
                password = SecretText.of(password),
                previousPasswords = history.map { SecretText.of(it) },
            ),
        )

        val source = device()
        source.session.save(bank("newer", listOf("b"), at = T2))
        val file = source.exportWith()

        val other = device()
        other.session.save(bank("older", listOf("a"), at = T0))
        assertIs<ImportRead.Ready>(other.read(file)).apply()

        val result = other.session.items.single().payload as ItemPayload.Bank
        assertEquals("newer", result.password.reveal { it })
        assertEquals(listOf("b"), result.previousPasswords.map { it.reveal { text -> text } })
    }

    // ── Attachments ─────────────────────────────────────────────────────────

    @Test
    fun `attachments travel with an export and arrive readable`() {
        val phone = device()
        val item = note("Passport")
        phone.session.save(item)
        val content = ByteArray(2048) { (it * 11 and 0xff).toByte() }
        Secret.of(content).use {
            phone.session.attach(item.id, "scan.png", "image/png", it, createdAt = T0)
        }
        val file = phone.exportWith()

        val other = device()
        val read = assertIs<ImportRead.Ready>(other.read(file))
        assertEquals(1, read.preview.attachmentsAdded)
        read.apply()

        val arrived = other.session.attachments(item.id).single()
        assertEquals("scan.png", arrived.filename)
        assertEquals(
            content.toList(),
            assertNotNull(other.session.openAttachment(arrived.id)).toByteArray().toList(),
        )
    }

    @Test
    fun `an attachment already held is not stored twice`() {
        val phone = device()
        val item = note("Passport")
        phone.session.save(item)
        Secret.of(ByteArray(512)).use {
            phone.session.attach(item.id, "scan.png", "image/png", it, createdAt = T0)
        }
        val file = phone.exportWith()

        val read = assertIs<ImportRead.Ready>(phone.read(file))
        assertEquals(0, read.preview.attachmentsAdded)
        read.apply()
        assertEquals(1, phone.blobs.list().size)
    }

    @Test
    fun `an attachment survives its item losing the merge`() {
        // The owner did not lose — only one of its payloads did. ItemId is unchanged by
        // supersession, so the attachment still has an owner.
        val id = ItemId.random()
        val source = device()
        source.session.save(note("Older", id, at = T0))
        Secret.of(ByteArray(256)).use {
            source.session.attach(id, "scan.png", "image/png", it, createdAt = T0)
        }
        val file = source.exportWith()

        val other = device()
        other.session.save(note("Newer", id, at = T2))
        assertIs<ImportRead.Ready>(other.read(file)).apply()

        assertEquals(listOf("Newer"), other.titles())
        assertEquals(1, other.session.attachments(id).size)
    }

    @Test
    fun `an attachment whose item is deleted by the merge is not imported`() {
        val id = ItemId.random()
        val source = device()
        source.session.save(note("Doomed", id, at = T0))
        Secret.of(ByteArray(256)).use {
            source.session.attach(id, "scan.png", "image/png", it, createdAt = T0)
        }
        val file = source.exportWith()

        val other = device()
        other.session.save(note("Doomed", id, at = T0))
        other.session.delete(id, now = T2)
        assertIs<ImportRead.Ready>(other.read(file)).apply()

        assertTrue(other.blobs.list().isEmpty(), "an attachment arrived for a deleted item")
    }

    @Test
    fun `a file missing an attachment it claims is refused whole`() {
        val phone = device()
        val item = note("Passport")
        phone.session.save(item)
        Secret.of(ByteArray(256)).use {
            phone.session.attach(item.id, "scan.png", "image/png", it, createdAt = T0)
        }
        val file = phone.exportWith()

        // Strip the last record. Everything left is authentic; the set is not what the body
        // says it is, and importing "most of" a backup silently is how someone finds out
        // years later that a scan is missing.
        val truncated = withoutLastRecord(file)
        val other = device()
        assertIs<ImportRead.Incomplete>(other.read(truncated))
    }

    /** Drops the final record, leaving every remaining record intact and authentic. */
    private fun withoutLastRecord(file: ByteArray): ByteArray {
        var offset = 31
        val slots = file[offset].toInt() and 0xff
        offset += 1
        repeat(slots) {
            val length = ((file[offset + 1].toInt() and 0xff) shl 8) or (file[offset + 2].toInt() and 0xff)
            offset += 3 + length
        }
        var lastStart = offset
        while (offset < file.size) {
            lastStart = offset
            var length = 0L
            for (i in 1..4) length = (length shl 8) or (file[offset + i].toLong() and 0xff)
            offset += 5 + length.toInt()
        }
        return file.copyOf(lastStart)
    }

    // ── Using it wrongly ────────────────────────────────────────────────────

    @Test
    fun `an import cannot be applied twice`() {
        val phone = device()
        phone.session.save(note("Bank"))
        val file = phone.exportWith()
        val read = assertIs<ImportRead.Ready>(phone.read(file))
        read.apply()
        assertFailsWith<IllegalStateException> { read.apply() }
    }

    @Test
    fun `discarding an import changes nothing`() {
        val phone = device()
        phone.session.save(note("Bank"))
        val file = phone.exportWith()

        val other = device()
        assertIs<ImportRead.Ready>(other.read(file)).discard()
        assertEquals(emptyList(), other.titles())
    }

    @Test
    fun `an export of a locked vault is refused`() {
        val phone = device()
        phone.session.lock()
        assertFailsWith<IllegalStateException> { Secret.ofUtf8("x").use { phone.session.export(it, cheap) } }
    }

    @Test
    fun `nothing readable survives in the file for the wrong key`() {
        val phone = device()
        phone.session.save(note("Extremely Secret Bank"))
        val file = phone.exportWith()
        assertFalse(file.decodeToString().contains("Extremely Secret Bank"))
    }
}
