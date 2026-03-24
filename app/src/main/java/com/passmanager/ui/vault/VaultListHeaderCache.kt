package com.passmanager.ui.vault

/**
 * Title and URL/address strings for vault list rows, updated together so the UI
 * recomposes once per decrypt batch instead of once per map.
 */
data class VaultListHeaderCache(
    val titles: Map<String, String> = emptyMap(),
    val addresses: Map<String, String> = emptyMap(),
)
