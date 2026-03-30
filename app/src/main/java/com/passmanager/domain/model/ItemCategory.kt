package com.passmanager.domain.model

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

    /** Lowercase name used for DB storage (e.g. `"login"`, `"card"`). */
    val dbKey: String get() = name.lowercase()

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
