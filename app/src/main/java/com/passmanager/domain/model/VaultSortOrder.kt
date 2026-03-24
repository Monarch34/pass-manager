package com.passmanager.domain.model

/**
 * How vault list rows are ordered after search and group filters.
 */
enum class VaultSortOrder {
    /** Title A → Z (case-insensitive). */
    NAME_ASC,

    /** Most recently updated first. */
    DATE_NEWEST,

    /** Oldest updated first. */
    DATE_OLDEST,
}
