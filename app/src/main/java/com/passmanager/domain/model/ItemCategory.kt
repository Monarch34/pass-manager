package com.passmanager.domain.model

import com.passmanager.domain.util.foldForSearch

/**
 * Vault item category — pure domain enum with no UI dependencies.
 *
 * UI presentation (icons, tints) lives in [com.passmanager.ui.model.ItemCategoryUi].
 */
enum class ItemCategory(val label: String) {
    LOGIN("Login"),
    CARD("Card"),
    NOTE("Note"),
    IDENTITY("Identity"),
    BANK("Bank");

    /**
     * Lowercase name used for DB storage (e.g. `"login"`, `"card"`).
     *
     * Stored rather than computed: it is read on every vault-list row and on every keystroke of
     * the search filter, and the old `get() = name.lowercase()` allocated a fresh String each time.
     */
    val dbKey: String = name.lowercase()

    /**
     * Case-folded [label], precomputed so the search filter's category branch can use a plain,
     * case-sensitive `contains` instead of a per-keystroke `ignoreCase = true` comparison.
     */
    val labelLower: String = label.foldForSearch()

    /**
     * Case-folded [dbKey]. Already lowercase ASCII, but folded through the same function as the
     * query so both category comparisons in the filter stay symmetric.
     */
    val dbKeyLower: String = dbKey.foldForSearch()

    companion object {
        /**
         * Parses a category from navigation args, deep links, or the DB `category` column.
         * Blank or unknown values map to [LOGIN] so corrupted or legacy rows do not crash the app.
         */
        fun fromString(value: String): ItemCategory {
            val v = value.trim()
            if (v.isEmpty()) return LOGIN
            return entries.firstOrNull { entry ->
                entry.name.equals(v, ignoreCase = true) || entry.dbKey.equals(v, ignoreCase = true)
            } ?: LOGIN
        }
    }
}
