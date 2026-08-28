package com.passmanager.domain.item

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ItemCategoryTest {

    /**
     * These five strings are on disk and in every exported file. Asserting them as
     * literals — rather than deriving the expectation the same way the code does — is what
     * makes this a contract test instead of a tautology. If someone renames an entry, this
     * fails; if they change [ItemCategory.key], this fails; and both should.
     */
    @Test
    fun storage_keys_are_exactly_these() {
        assertEquals("login", ItemCategory.LOGIN.key)
        assertEquals("card", ItemCategory.CARD.key)
        assertEquals("note", ItemCategory.NOTE.key)
        assertEquals("identity", ItemCategory.IDENTITY.key)
        assertEquals("bank", ItemCategory.BANK.key)
        assertEquals(5, ItemCategory.entries.size, "a new category is a format change")
    }

    @Test
    fun every_key_round_trips() {
        ItemCategory.entries.forEach { category ->
            assertEquals(category, ItemCategory.ofKey(category.key))
        }
    }

    @Test
    fun keys_are_distinct() {
        assertEquals(
            ItemCategory.entries.size,
            ItemCategory.entries.map { it.key }.toSet().size
        )
    }

    /**
     * An unknown value must not resolve to a real category. Silently coercing it is how a
     * damaged bank row becomes a login and then gets saved back as one.
     */
    @Test
    fun unknown_values_do_not_resolve() {
        assertNull(ItemCategory.ofKey("passport"))
        assertNull(ItemCategory.ofKey(""))
        assertNull(ItemCategory.ofKey(" login"))
        assertNull(ItemCategory.ofKey("login "))
    }

    /** [ItemCategory.key] is a wire value, so exactly one spelling may be accepted. */
    @Test
    fun matching_is_case_sensitive() {
        assertNotNull(ItemCategory.ofKey("login"))
        assertNull(ItemCategory.ofKey("Login"))
        assertNull(ItemCategory.ofKey("LOGIN"))
    }
}
