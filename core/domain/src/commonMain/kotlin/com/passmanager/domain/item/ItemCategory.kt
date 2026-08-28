package com.passmanager.domain.item

/**
 * What kind of thing a vault entry is.
 *
 * [key] is a storage and interchange value: it appears in the database's plaintext
 * `category` column and in every exported container, and both are read by more than one
 * implementation. It is therefore written out explicitly rather than derived from [name] —
 * a rename or a reorder of this enum must not be able to change what is already on disk.
 *
 * There is deliberately no display label here. Labels are localized and belong to whatever
 * is drawing them; a domain enum that carries English strings cannot be translated without
 * editing the domain, and quietly makes every consumer depend on one language.
 */
enum class ItemCategory(val key: String) {
    LOGIN("login"),
    CARD("card"),
    NOTE("note"),
    IDENTITY("identity"),
    BANK("bank");

    companion object {

        private val byKey: Map<String, ItemCategory> = entries.associateBy { it.key }

        /**
         * Resolves a stored or imported category, or `null` if it is not one we know.
         *
         * Returning null is the point. The obvious alternative — fall back to [LOGIN] so a
         * corrupt row cannot crash the list — silently relabels data: a bank record whose
         * category byte was damaged comes back as a login, is shown as a login, and is
         * saved back as one, at which point the original kind is gone for good. A caller
         * that genuinely wants to tolerate an unreadable row can choose to; a caller
         * importing a file should refuse it, and only an explicit null lets them differ.
         *
         * The match is exact and case-sensitive because [key] is a wire value, not user
         * input. Accepting `"Login"` here would mean two spellings are both valid on disk,
         * and then something has to decide which one to write.
         */
        fun ofKey(key: String): ItemCategory? = byKey[key]
    }
}
