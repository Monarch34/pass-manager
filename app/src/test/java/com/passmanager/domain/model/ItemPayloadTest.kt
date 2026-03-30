package com.passmanager.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ItemPayloadTest {

    @Test
    fun `Login category is LOGIN`() {
        val payload = ItemPayload.Login(id = "1", title = "Test", password = "pw")
        assertEquals(ItemCategory.LOGIN, payload.category)
    }

    @Test
    fun `Card category is CARD`() {
        val payload = ItemPayload.Card(id = "1", title = "Visa")
        assertEquals(ItemCategory.CARD, payload.category)
    }

    @Test
    fun `Bank category is BANK`() {
        val payload = ItemPayload.Bank(id = "1", title = "MyBank")
        assertEquals(ItemCategory.BANK, payload.category)
    }

    @Test
    fun `SecureNote category is NOTE`() {
        val payload = ItemPayload.SecureNote(id = "1", title = "Note")
        assertEquals(ItemCategory.NOTE, payload.category)
    }

    @Test
    fun `Identity category is IDENTITY`() {
        val payload = ItemPayload.Identity(id = "1", title = "Me")
        assertEquals(ItemCategory.IDENTITY, payload.category)
    }

    @Test
    fun `Login listSubtitle is address`() {
        val payload = ItemPayload.Login(id = "1", title = "GitHub", address = "github.com")
        assertEquals("github.com", payload.listSubtitle)
    }

    @Test
    fun `Card listSubtitle is cardholder name`() {
        val payload = ItemPayload.Card(id = "1", title = "Visa", cardholderName = "John Doe")
        assertEquals("John Doe", payload.listSubtitle)
    }

    @Test
    fun `Bank listSubtitle is bank name`() {
        val payload = ItemPayload.Bank(id = "1", title = "Savings", bankName = "Chase")
        assertEquals("Chase", payload.listSubtitle)
    }

    @Test
    fun `SecureNote listSubtitle truncates notes at 60 chars`() {
        val longNotes = "A".repeat(100)
        val payload = ItemPayload.SecureNote(id = "1", title = "Note", notes = longNotes)
        assertEquals(60, payload.listSubtitle.length)
        assertEquals("A".repeat(60), payload.listSubtitle)
    }

    @Test
    fun `Identity listSubtitle prefers email`() {
        val payload = ItemPayload.Identity(
            id = "1", title = "Me",
            email = "me@example.com", firstName = "John", lastName = "Doe"
        )
        assertEquals("me@example.com", payload.listSubtitle)
    }

    @Test
    fun `Identity listSubtitle falls back to name when email empty`() {
        val payload = ItemPayload.Identity(
            id = "1", title = "Me",
            email = "", firstName = "John", lastName = "Doe"
        )
        assertEquals("John Doe", payload.listSubtitle)
    }

    @Test
    fun `Identity listSubtitle trims when only first name`() {
        val payload = ItemPayload.Identity(
            id = "1", title = "Me",
            email = "", firstName = "John", lastName = ""
        )
        assertEquals("John", payload.listSubtitle)
    }
}
