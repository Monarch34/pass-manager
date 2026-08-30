package com.passmanager.domain.item

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class VaultItemTest {

    private val id = ItemId.random()
    private val created = 1_700_000_000_000

    private fun bank(
        password: String,
        history: List<String> = emptyList(),
        title: String = "Bank",
    ) = ItemPayload.Bank(
        title = title,
        bankName = "Example",
        password = SecretText.of(password),
        previousPasswords = history.map { SecretText.of(it) },
    )

    private fun item(payload: ItemPayload, updated: Long = created) =
        VaultItem(id = id, createdAt = created, updatedAt = updated, payload = payload)

    // -- edited -------------------------------------------------------------

    @Test
    fun `saving without changing anything does not move the timestamp`() {
        // The case this exists for: opening an entry and pressing Save. Stamping that as an
        // edit would make this copy beat a device where the item genuinely changed.
        val original = item(ItemPayload.Note(title = "Codes", notes = SecretText.of("4821")))
        val resaved = original.edited(
            ItemPayload.Note(title = "Codes", notes = SecretText.of("4821")),
            now = created + 60_000,
        )
        assertEquals(created, resaved.updatedAt)
    }

    @Test
    fun `a changed secret moves the timestamp even though nothing visible changed`() {
        val original = item(ItemPayload.Note(title = "Codes", notes = SecretText.of("4821")))
        val edited = original.edited(
            ItemPayload.Note(title = "Codes", notes = SecretText.of("9930")),
            now = created + 60_000,
        )
        assertEquals(created + 60_000, edited.updatedAt)
    }

    @Test
    fun `an edit keeps the identity and the creation time`() {
        val original = item(ItemPayload.Note(title = "Codes", notes = SecretText.Empty))
        val edited = original.edited(
            ItemPayload.Note(title = "Recovery codes", notes = SecretText.Empty),
            now = created + 1,
        )
        assertEquals(id, edited.id)
        assertEquals(created, edited.createdAt)
    }

    // -- withHistoryFrom ----------------------------------------------------

    @Test
    fun `a changed password is remembered`() {
        val next = bank("new").withHistoryFrom(bank("old"))
        assertEquals(1, next.previousPasswords.size)
        assertTrue(next.previousPasswords.first().reveal { it } == "old")
    }

    @Test
    fun `an unchanged password is not remembered twice`() {
        // Editing the title of a bank must not fill the history with copies of one password.
        val next = bank("same", title = "Renamed").withHistoryFrom(bank("same"))
        assertEquals(0, next.previousPasswords.size)
    }

    @Test
    fun `history is carried forward by an edit that does not touch the password`() {
        // The bug this replaces: the iOS editor built a Bank with an empty history on every
        // save, so editing a bank on the phone destroyed every old password it had kept.
        val next = bank("same", title = "Renamed").withHistoryFrom(bank("same", listOf("a", "b")))
        assertEquals(listOf("a", "b"), next.previousPasswords.map { it.reveal { text -> text } })
    }

    @Test
    fun `the newest replaced password comes first`() {
        val next = bank("third").withHistoryFrom(bank("second", listOf("first")))
        assertEquals(listOf("second", "first"), next.previousPasswords.map { it.reveal { t -> t } })
    }

    @Test
    fun `a password that was never set is not remembered`() {
        val next = bank("first").withHistoryFrom(bank(""))
        assertEquals(0, next.previousPasswords.size)
    }

    @Test
    fun `the history stops growing`() {
        var current = bank("p0")
        repeat(20) { round ->
            current = bank("p${round + 1}").withHistoryFrom(current)
        }
        assertEquals(ItemPayload.Bank.MaxRememberedPasswords, current.previousPasswords.size)
        // And it is the most recent that survive, not the oldest. p19 rather than p20:
        // p20 is the password the account has now, and this list is the ones it had before.
        assertEquals("p19", current.previousPasswords.first().reveal { it })
    }

    @Test
    fun `a brand new bank has no history to carry`() {
        val next = bank("first").withHistoryFrom(null)
        assertEquals(0, next.previousPasswords.size)
        assertSame(next, next.withHistoryFrom(null))
    }

    // -- links --------------------------------------------------------------

    @Test
    fun `an identifier that names nothing is still part of the payload`() {
        // Readers skip what they cannot resolve; the payload itself must keep it, or an
        // editor round trip deletes a link to a card that exists on another device.
        val dangling = ItemId.random()
        val next = ItemPayload.Bank(title = "Bank", cardIds = listOf(dangling))
            .withHistoryFrom(ItemPayload.Bank(title = "Bank"))
        assertEquals(listOf(dangling), next.cardIds)
    }
}
