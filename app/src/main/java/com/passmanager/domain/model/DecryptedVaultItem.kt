package com.passmanager.domain.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * The plaintext representation of a vault item.
 * Serialized to JSON and encrypted as a single blob — hiding field structure.
 *
 * SECURITY NOTE: JVM String is immutable and cannot be zeroed from memory.
 * Minimize retention: clear from ViewModels when navigating away.
 * Full mitigation would require CharArray-based models (incompatible with kotlinx.serialization).
 */
@Keep
@Serializable
data class DecryptedVaultItem(
    val id: String,
    val title: String,
    val username: String,
    /** Website URL or other address for this login (optional). */
    val address: String = "",
    val password: String,
    val notes: String,
    val cardholderName: String = "",
    val cardNumber: String = "",
    val cardCvc: String = "",
    val cardExpiry: String = "",
    val previousPasswords: List<String> = emptyList()
)
