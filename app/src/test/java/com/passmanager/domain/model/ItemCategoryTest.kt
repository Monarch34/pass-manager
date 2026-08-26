package com.passmanager.domain.model

import com.passmanager.domain.util.foldForSearch
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

    @Test
    fun `labelLower and dbKeyLower are precomputed case folds`() {
        for (category in ItemCategory.entries) {
            assertEquals(category.label.lowercase(), category.labelLower)
            assertEquals(category.dbKey.lowercase(), category.dbKeyLower)
        }
        assertEquals("login", ItemCategory.LOGIN.labelLower)
        assertEquals("identity", ItemCategory.IDENTITY.labelLower)
    }

    @Test
    fun `search fold of label and dbKey matches the legacy ignoreCase contains`() {
        // The vault filter matches a folded query against these two vals with a plain `contains`;
        // it must agree with the `contains(ignoreCase = true)` it replaced.
        val queries = listOf("log", "LOGIN", "Card", "note", "IDENT", "bank", "zzz")
        for (category in ItemCategory.entries) {
            for (q in queries) {
                assertEquals(
                    "label mismatch for $category / '$q'",
                    category.label.contains(q, ignoreCase = true),
                    category.labelLower.contains(q.foldForSearch())
                )
                assertEquals(
                    "dbKey mismatch for $category / '$q'",
                    category.dbKey.contains(q, ignoreCase = true),
                    category.dbKeyLower.contains(q.foldForSearch())
                )
            }
        }
    }
}
