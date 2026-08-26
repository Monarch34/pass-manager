package com.passmanager.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The vault search filter replaced per-comparison `contains(ignoreCase = true)` with
 * `foldForSearch()` on both operands plus a plain `contains`. These tests pin the equivalence,
 * because a silent divergence would change which items a user can find.
 */
class SearchTextFoldTest {

    /** (haystack, needle) pairs spanning ASCII case, Turkish dotted/dotless i, and non-matches. */
    private val cases = listOf(
        "github account" to "GITHUB",
        "GitHub Account" to "github",
        "GITHUB ACCOUNT" to "github",
        "github account" to "github",
        "İş Bankası" to "iş",
        "İş Bankası" to "İŞ",
        "is bankasi" to "İS",
        "Ilık Su" to "ılık",
        "ılık su" to "ILIK",
        "Straße" to "STRASSE",
        "Straße" to "straße",
        "shopping list" to "github",
        "" to "x",
        "anything" to ""
    )

    @Test
    fun `folded contains matches ignoreCase contains for every case`() {
        for ((haystack, needle) in cases) {
            val legacy = haystack.contains(needle, ignoreCase = true)
            val folded = haystack.foldForSearch().contains(needle.foldForSearch())
            assertEquals("mismatch for haystack='$haystack' needle='$needle'", legacy, folded)
        }
    }

    @Test
    fun `fold preserves length so substring offsets stay valid`() {
        // This is the reason foldForSearch exists instead of lowercase(): "İ".lowercase() expands
        // U+0130 into 'i' + U+0307, which would break substring matching on Turkish titles.
        assertEquals(1, "İ".foldForSearch().length)
        assertEquals("İş Bankası".length, "İş Bankası".foldForSearch().length)
        assertEquals(2, "İ".lowercase().length) // guards the assumption above
    }

    @Test
    fun `Turkish dotted capital I matches plain i where lowercase would fail`() {
        val title = "İş Bankası"
        assertTrue(title.contains("iş", ignoreCase = true))
        assertTrue(title.foldForSearch().contains("iş".foldForSearch()))
        // The naive optimization this replaced would have regressed exactly here.
        assertFalse(title.lowercase().contains("iş".lowercase()))
    }

    @Test
    fun `fold is idempotent`() {
        for ((haystack, _) in cases) {
            assertEquals(haystack.foldForSearch(), haystack.foldForSearch().foldForSearch())
        }
    }

    @Test
    fun `empty string folds to empty`() {
        assertEquals("", "".foldForSearch())
    }
}
