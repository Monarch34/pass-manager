package com.passmanager.ui.vault

import androidx.compose.runtime.Immutable

/**
 * Title and URL/address strings for vault list rows, updated together so the UI
 * recomposes once per decrypt batch instead of once per map.
 */
@Immutable
data class VaultListHeaderCache(
    val titles: Map<String, String> = emptyMap(),
    val addresses: Map<String, String> = emptyMap(),
)
