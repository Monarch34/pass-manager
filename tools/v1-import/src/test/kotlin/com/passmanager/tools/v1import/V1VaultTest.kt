package com.passmanager.tools.v1import

import com.passmanager.crypto.Secret
import com.passmanager.domain.item.ItemPayload
import com.passmanager.format.PmVault
import com.passmanager.format.VaultContents
import com.passmanager.format.VaultOpen
import com.passmanager.format.VaultParse
import com.passmanager.domain.item.VaultItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The importer, against real version 1 files.
 *
 * These two fixtures were written by version 1's actual Android and iOS exporters, not
 * generated here, which is what makes this a test of the importer rather than a test of a
 * synthetic file agreeing with itself. Between them they cover all five categories, Turkish
 * characters, an emoji, and a note containing tabs, newlines, quotation marks and a
 * backslash — the things that break a hand-rolled decoder.
 */
class V1VaultTest {

    private val passphrase = "CrossPlatform-Fixture-2026"

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/fixtures/$name")) { "missing fixture $name" }
            .use { it.readBytes() }

    private fun import(name: String): List<VaultItem> =
        Secret.ofUtf8(passphrase).use { V1Vault.read(fixture(name), it) }.getOrThrow()

    @Test
    fun `reads the Android fixture`() {
        val items = import("android-export-v1.pmvault")
        assertEquals(5, items.size)

        val login = assertIs<ItemPayload.Login>(items[0].payload)
        assertEquals("a1000000-0000-4000-8000-000000000001", items[0].id.value)
        assertEquals(1_735_689_600_000, items[0].createdAt)
        assertEquals(1_767_225_600_000, items[0].updatedAt)
        assertEquals("GitHub", login.title)
        assertEquals("octocat", login.username)
        assertEquals("https://github.com", login.address)
        assertEquals("hunter2", login.password.reveal { it })

        val card = assertIs<ItemPayload.Card>(items[1].payload)
        assertEquals("Kadıköy Bankası Kartı", card.title)
        assertEquals("Ayşe Yılmaz", card.cardholderName)
        assertEquals("4111111111111111", card.cardNumber.reveal { it })
        assertEquals("123", card.cardCvc.reveal { it })
        assertEquals("12/29", card.cardExpiry)
        assertEquals("Ay sonu ödemesi", card.notes.reveal { it })

        // Tabs, newlines, quotation marks, a backslash and an emoji, all through the seal.
        val note = assertIs<ItemPayload.Note>(items[2].payload)
        assertEquals("Kurtarma kodları", note.title)
        assertEquals("satır 1\nsatır 2\tsekmeli\n\"tırnaklı\" ve ters bölü \\ ile 🔐", note.notes.reveal { it })

        val identity = assertIs<ItemPayload.Identity>(items[3].payload)
        assertEquals("Ayşe", identity.firstName)
        assertEquals("Kadıköy, İstanbul", identity.address)
        assertEquals("ACME Yazılım A.Ş.", identity.company)

        val bank = assertIs<ItemPayload.Bank>(items[4].payload)
        assertEquals("TR33 0006 1005 1978 6457 8413 26", bank.accountNumber.reveal { it })
        assertEquals("Bank-2026!x", bank.password.reveal { it })
        assertEquals(
            listOf("Eski-2025!a", "Daha-Eski-2024!b"),
            bank.previousPasswords.map { p -> p.reveal { it } },
        )
        // Links are new in version 2, so an imported bank starts with none.
        assertTrue(bank.cardIds.isEmpty())
    }

    /**
     * The two files were written by different implementations on different platforms. That
     * they import to exactly the same items is the property version 1's format existed to
     * provide, and the one thing this tool must not quietly break.
     */
    @Test
    fun `the Android and iOS fixtures import identically`() {
        val android = import("android-export-v1.pmvault")
        val ios = import("ios-export-v1.pmvault")
        assertEquals(android.size, ios.size)
        for (i in android.indices) {
            assertEquals(android[i].id, ios[i].id, "item $i identifier")
            assertEquals(android[i].createdAt, ios[i].createdAt, "item $i createdAt")
            assertEquals(android[i].updatedAt, ios[i].updatedAt, "item $i updatedAt")
            assertEquals(android[i].payload, ios[i].payload, "item $i payload")
        }
    }

    /**
     * The decision that cannot be undone. Importing the same export on a phone and on a
     * tablet must produce two vaults that agree on identity — otherwise the first time they
     * are ever reconciled, every item is duplicated with no way back short of matching them
     * by hand.
     */
    @Test
    fun `identifiers survive the import unchanged`() {
        val expected = listOf(
            "a1000000-0000-4000-8000-000000000001",
            "a1000000-0000-4000-8000-000000000002",
            "a1000000-0000-4000-8000-000000000003",
            "a1000000-0000-4000-8000-000000000004",
            "a1000000-0000-4000-8000-000000000005",
        )
        assertEquals(expected, import("android-export-v1.pmvault").map { it.id.value })
        assertEquals(expected, import("ios-export-v1.pmvault").map { it.id.value })
    }

    /** The whole point of the exercise: old vault in, new vault out, nothing lost. */
    @Test
    fun `an imported vault writes and reopens as a v2 container`() {
        val imported = import("android-export-v1.pmvault")
        val written = Secret.ofUtf8("a new passphrase").use {
            PmVault.create(VaultContents(items = imported), it)
        }

        val sealed = assertIs<VaultParse.Sealed>(PmVault.parse(written))
        val opened = assertIs<VaultOpen.Opened>(
            sealed.openWithPassphrase(Secret.ofUtf8("a new passphrase"))
        )

        assertEquals(imported.size, opened.contents.items.size)
        for (i in imported.indices) {
            assertEquals(imported[i], opened.contents.items[i], "item $i did not survive the round trip")
        }

        // And the old passphrase must not open the new file.
        assertIs<VaultOpen.Unopenable>(sealed.openWithPassphrase(Secret.ofUtf8(passphrase)))
    }

    @Test
    fun `a wrong passphrase fails rather than returning nothing`() {
        val result = Secret.ofUtf8("not the passphrase").use {
            V1Vault.read(fixture("android-export-v1.pmvault"), it)
        }
        assertTrue(result.isFailure, "a wrong passphrase produced a result")
    }

    @Test
    fun `something that is not a v1 vault is refused`() {
        Secret.ofUtf8(passphrase).use { key ->
            assertTrue(V1Vault.read("not a vault".encodeToByteArray(), key).isFailure)
            assertTrue(V1Vault.read(ByteArray(0), key).isFailure)
            // A v2 file is not a v1 file, and must not be read as one.
            val v2 = PmVault.create(VaultContents(), key)
            assertTrue(V1Vault.read(v2, key).isFailure)
        }
    }
}
