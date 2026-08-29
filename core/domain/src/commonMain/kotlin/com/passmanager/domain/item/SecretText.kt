package com.passmanager.domain.item

import com.passmanager.crypto.Secret
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A field of an item that is the thing an attacker came for: a password, a card number, a
 * security code.
 *
 * The distinction between this and an ordinary `String` field is the whole point. A vault
 * item's title, its website and its notes are not what a breach is about; its password is.
 * Marking that difference in the type means the compiler knows which fields must not be
 * printed, and that a decrypted vault sitting in memory for the duration of a session holds
 * its passwords in something that can be erased.
 *
 * Version 1 stored every field as a `String` and said so honestly in a comment — "JVM
 * String is immutable and cannot be zeroed. Minimize retention in ViewModels" — which is a
 * rule a person has to remember at every call site. This is the same rule expressed as a
 * type.
 *
 * ### The residual, stated plainly
 *
 * Serialising the vault produces a JSON string, and every secret in it appears inside that
 * string. Neither the JVM nor Swift can erase a string, so that copy survives until the
 * garbage collector reclaims it. What this type changes is the *duration*: a decrypted
 * vault is open for as long as the user is using the app, and a save takes milliseconds. It
 * removes the exposure that lasts a session and leaves the one that lasts an instant.
 */
@Serializable(with = SecretTextSerializer::class)
class SecretText private constructor(private val secret: Secret) {

    /** True for an absent field, so callers can skip an empty password without revealing it. */
    val isEmpty: Boolean get() = secret.size == 0

    /**
     * Runs [block] with the field's text.
     *
     * A `String` is unavoidable here — it is what a text field, a clipboard and an autofill
     * response all take — but it is created at the moment of use and not held by this
     * object, which is the difference that matters.
     */
    fun <R> reveal(block: (String) -> R): R = secret.reveal { block(it.decodeToString()) }

    fun destroy() = secret.destroy()

    /** Never the contents. This is the property that survives a careless log statement. */
    override fun toString(): String = if (isEmpty) "SecretText(empty)" else "SecretText(hidden)"

    override fun equals(other: Any?): Boolean = other is SecretText && secret == other.secret

    override fun hashCode(): Int = secret.hashCode()

    companion object {
        fun of(text: String): SecretText = SecretText(Secret.copyOfUtf8(text))

        /** Takes ownership of [secret]; the caller must not destroy it afterwards. */
        fun adopt(secret: Secret): SecretText = SecretText(secret)

        val Empty: SecretText get() = SecretText(Secret.copyOfUtf8(""))
    }
}

internal object SecretTextSerializer : KSerializer<SecretText> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.passmanager.domain.item.SecretText", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: SecretText) =
        value.reveal(encoder::encodeString)

    override fun deserialize(decoder: Decoder): SecretText =
        SecretText.of(decoder.decodeString())
}
