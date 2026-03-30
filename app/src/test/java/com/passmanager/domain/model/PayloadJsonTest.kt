package com.passmanager.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadJsonTest {

    @Test
    fun `encode and decode Login roundtrip`() {
        val original = ItemPayload.Login(
            id = "1", title = "GitHub", notes = "dev",
            username = "user", address = "github.com", password = "secret"
        )
        val json = PayloadJson.encode(original)
        val decoded = PayloadJson.decode(json)
        assertTrue(decoded is ItemPayload.Login)
        assertEquals(original, decoded)
    }

    @Test
    fun `encode and decode Card roundtrip`() {
        val original = ItemPayload.Card(
            id = "2", title = "Visa", notes = "",
            cardholderName = "John", cardNumber = "4111111111111111",
            cardCvc = "123", cardExpiry = "12/25"
        )
        val json = PayloadJson.encode(original)
        val decoded = PayloadJson.decode(json)
        assertTrue(decoded is ItemPayload.Card)
        assertEquals(original, decoded)
    }

    @Test
    fun `encode and decode Bank roundtrip`() {
        val original = ItemPayload.Bank(
            id = "3", title = "Chase", notes = "",
            accountNumber = "12345", bankName = "Chase Bank",
            password = "bankpw", previousPasswords = listOf("old1", "old2")
        )
        val json = PayloadJson.encode(original)
        val decoded = PayloadJson.decode(json)
        assertTrue(decoded is ItemPayload.Bank)
        assertEquals(original, decoded)
    }

    @Test
    fun `encode and decode SecureNote roundtrip`() {
        val original = ItemPayload.SecureNote(
            id = "4", title = "MyNote", notes = "Sensitive text here"
        )
        val json = PayloadJson.encode(original)
        val decoded = PayloadJson.decode(json)
        assertTrue(decoded is ItemPayload.SecureNote)
        assertEquals(original, decoded)
    }

    @Test
    fun `encode and decode Identity roundtrip`() {
        val original = ItemPayload.Identity(
            id = "5", title = "Me", notes = "",
            firstName = "John", lastName = "Doe",
            email = "j@example.com", phone = "+1234",
            address = "123 Main St", company = "ACME"
        )
        val json = PayloadJson.encode(original)
        val decoded = PayloadJson.decode(json)
        assertTrue(decoded is ItemPayload.Identity)
        assertEquals(original, decoded)
    }

    @Test
    fun `decode legacy v1 JSON without type field`() {
        val legacyJson = """{"id":"1","title":"Site","username":"user","address":"site.com","password":"pw","notes":""}"""
        val decoded = PayloadJson.decode(legacyJson, ItemCategory.LOGIN)
        assertTrue(decoded is ItemPayload.Login)
        val login = decoded as ItemPayload.Login
        assertEquals("1", login.id)
        assertEquals("Site", login.title)
        assertEquals("user", login.username)
        assertEquals("site.com", login.address)
        assertEquals("pw", login.password)
    }

    @Test
    fun `decode legacy v1 Bank uses field mapping`() {
        val legacyJson = """{"id":"1","title":"Bank","username":"12345","address":"Chase","password":"pw","notes":""}"""
        val decoded = PayloadJson.decode(legacyJson, ItemCategory.BANK)
        assertTrue(decoded is ItemPayload.Bank)
        val bank = decoded as ItemPayload.Bank
        assertEquals("12345", bank.accountNumber)
        assertEquals("Chase", bank.bankName)
        assertEquals("pw", bank.password)
    }

    @Test
    fun `unknown fields ignored in deserialization`() {
        val json = """{"type":"login","id":"1","title":"T","futureField":"val","password":"pw"}"""
        val decoded = PayloadJson.decode(json)
        assertTrue(decoded is ItemPayload.Login)
        assertEquals("T", (decoded as ItemPayload.Login).title)
    }
}
