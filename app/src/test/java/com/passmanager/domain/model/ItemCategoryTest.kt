package com.passmanager.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ItemCategoryTest {

    @Test
    fun `fromString parses lowercase login`() {
        assertEquals(ItemCategory.LOGIN, ItemCategory.fromString("login"))
    }

    @Test
    fun `fromString is case insensitive`() {
        assertEquals(ItemCategory.LOGIN, ItemCategory.fromString("LOGIN"))
        assertEquals(ItemCategory.CARD, ItemCategory.fromString("Card"))
        assertEquals(ItemCategory.BANK, ItemCategory.fromString("BANK"))
        assertEquals(ItemCategory.NOTE, ItemCategory.fromString("Note"))
        assertEquals(ItemCategory.IDENTITY, ItemCategory.fromString("identity"))
    }

    @Test
    fun `fromString returns LOGIN for unknown string`() {
        assertEquals(ItemCategory.LOGIN, ItemCategory.fromString("unknown"))
        assertEquals(ItemCategory.LOGIN, ItemCategory.fromString("gibberish"))
    }

    @Test
    fun `fromString returns LOGIN for empty string`() {
        assertEquals(ItemCategory.LOGIN, ItemCategory.fromString(""))
        assertEquals(ItemCategory.LOGIN, ItemCategory.fromString("   "))
    }

    @Test
    fun `dbKey returns lowercase name`() {
        assertEquals("login", ItemCategory.LOGIN.dbKey)
        assertEquals("card", ItemCategory.CARD.dbKey)
        assertEquals("note", ItemCategory.NOTE.dbKey)
        assertEquals("identity", ItemCategory.IDENTITY.dbKey)
        assertEquals("bank", ItemCategory.BANK.dbKey)
    }
}
