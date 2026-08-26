package com.passmanager.ui.vault

import androidx.compose.runtime.Immutable

/**
 * Title and URL/address strings for vault list rows, updated together so the UI
 * recomposes once per decrypt batch instead of once per map.
 *
 * [titlesLower]/[addressesLower] are case-folded copies of [titles]/[addresses], computed once
 * per decrypt batch (via `String.foldForSearch`) so the search filter and the NAME_ASC sort never
 * re-fold on every keystroke or every comparison. They deliberately use `foldForSearch` rather
 * than [String.lowercase] so Turkish İ/ı keep the exact case-insensitive substring semantics of
 * the previous `contains(ignoreCase = true)` path.
 */
@Immutable
data class VaultListHeaderCache(
    val titles: Map<String, String> = emptyMap(),
    val addresses: Map<String, String> = emptyMap(),
    val titlesLower: Map<String, String> = emptyMap(),
    val addressesLower: Map<String, String> = emptyMap(),
)
