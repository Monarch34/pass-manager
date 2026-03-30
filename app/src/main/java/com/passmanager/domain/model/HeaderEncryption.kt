package com.passmanager.domain.model

import com.passmanager.crypto.model.EncryptedData

/**
 * Encrypted header columns stored alongside the main vault blob to enable
 * O(1) list-row decryption without loading the full payload.
 * [address] is null for item types that have no list subtitle (e.g. SecureNote).
 */
data class HeaderEncryption(
    val title: EncryptedData,
    val address: EncryptedData?
)
