package com.passmanager.domain.model

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Type-safe encrypted vault item payload.
 *
 * Each category is a distinct subclass with **only its relevant fields**.
 * The `@SerialName` tag becomes the JSON discriminator (`"type":"login"`)
 * so the category travels *inside* the encrypted blob — never out of sync
 * with the entity's `category` column.
 *
 * New blobs serialized with this class use kotlinx.serialization's
 * [class discriminator][kotlinx.serialization.json.JsonBuilder.classDiscriminator]
 * set to `"type"`.
 *
 * SECURITY NOTE: JVM String is immutable and cannot be zeroed.
 * Minimize retention in ViewModels; clear references when navigating away.
 */
@Keep
@Serializable
sealed class ItemPayload {
    abstract val id: String
    abstract val title: String
    abstract val notes: String

    /** Derive [ItemCategory] from the sealed subtype — compile-time exhaustive. */
    val category: ItemCategory
        get() = when (this) {
            is Login      -> ItemCategory.LOGIN
            is Card       -> ItemCategory.CARD
            is Bank       -> ItemCategory.BANK
            is SecureNote -> ItemCategory.NOTE
            is Identity   -> ItemCategory.IDENTITY
        }

    /** The address/URL shown in the vault list (subtitle under title). */
    val listSubtitle: String
        get() = when (this) {
            is Login      -> address
            is Card       -> cardholderName
            is Bank       -> bankName
            is SecureNote -> notes.take(60)
            is Identity   -> email.ifEmpty { "$firstName $lastName".trim() }
                .ifEmpty { company }
                .ifEmpty { phone }
        }

    // ──────────────────────────────────────────────────
    // Login / default credential
    // ──────────────────────────────────────────────────
    @Keep
    @Serializable
    @SerialName("login")
    data class Login(
        override val id: String,
        override val title: String,
        override val notes: String = "",
        val username: String = "",
        val address: String = "",
        val password: String = ""
    ) : ItemPayload()

    // ──────────────────────────────────────────────────
    // Payment card
    // ──────────────────────────────────────────────────
    @Keep
    @Serializable
    @SerialName("card")
    data class Card(
        override val id: String,
        override val title: String,
        override val notes: String = "",
        val cardholderName: String = "",
        val cardNumber: String = "",
        val cardCvc: String = "",
        val cardExpiry: String = ""
    ) : ItemPayload()

    // ──────────────────────────────────────────────────
    // Bank account (password with compliance rules)
    // ──────────────────────────────────────────────────
    @Keep
    @Serializable
    @SerialName("bank")
    data class Bank(
        override val id: String,
        override val title: String,
        override val notes: String = "",
        val accountNumber: String = "",
        val bankName: String = "",
        val password: String = "",
        val previousPasswords: List<String> = emptyList()
    ) : ItemPayload()

    // ──────────────────────────────────────────────────
    // Secure note (no credentials)
    // ──────────────────────────────────────────────────
    @Keep
    @Serializable
    @SerialName("note")
    data class SecureNote(
        override val id: String,
        override val title: String,
        override val notes: String = ""
    ) : ItemPayload()

    // ──────────────────────────────────────────────────
    // Identity (personal info)
    // ──────────────────────────────────────────────────
    @Keep
    @Serializable
    @SerialName("identity")
    data class Identity(
        override val id: String,
        override val title: String,
        override val notes: String = "",
        val firstName: String = "",
        val lastName: String = "",
        val email: String = "",
        val phone: String = "",
        val address: String = "",
        val company: String = ""
    ) : ItemPayload()
}
