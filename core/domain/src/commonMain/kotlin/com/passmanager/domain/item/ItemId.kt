package com.passmanager.domain.item

import com.passmanager.crypto.random.secureRandomBytes
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * What makes two items the same item.
 *
 * There is no sync in version 2.0, but there will be a desktop client, and the moment two
 * devices hold the same vault, merging is unavoidable. Merging is defined by identity, so
 * the identifier has to be right now: it is the one thing that cannot be added later
 * without rewriting every vault that exists. An item that gets a fresh identifier each time
 * it is written cannot be merged at all, only duplicated.
 *
 * **Canonically lower-case.** Version 1 stored these as a case-sensitive text primary key
 * and normalised them nowhere, so the same identifier written by two code paths could
 * differ only in case and silently become two items. Every identifier is folded on the way
 * in, which costs one line and closes that permanently.
 */
@Serializable(with = ItemIdSerializer::class)
class ItemId internal constructor(val value: String) {

    // An ordinary class, not a `value class`. The zero-cost wrapper would be better on
    // every target but one: Kotlin/Native cannot export an inline class to Objective-C, so
    // Swift would receive `Any` where an identifier was meant and could not name the type
    // at all. An allocation per item is not worth an iOS application that cannot compile.

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean = other is ItemId && other.value == value

    override fun hashCode(): Int = value.hashCode()

    companion object {
        /**
         * A random version-4 UUID.
         *
         * Random rather than derived from content or from a counter: a content-derived
         * identifier would make two items holding the same password equal, and a counter
         * would collide the instant a second device started numbering from one.
         */
        fun random(): ItemId {
            val bytes = secureRandomBytes(16)
            // Version 4, variant 1 — the two fields that say "these bytes are random".
            bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
            bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
            val hex = StringBuilder(36)
            for (i in bytes.indices) {
                if (i == 4 || i == 6 || i == 8 || i == 10) hex.append('-')
                val value = bytes[i].toInt() and 0xff
                hex.append(HexDigits[value shr 4])
                hex.append(HexDigits[value and 0xf])
            }
            return ItemId(hex.toString())
        }

        /**
         * Accepts an identifier from a file, folding its case. Returns null for anything
         * that cannot be one — a blank string, or something long enough to be a payload
         * rather than an identifier.
         *
         * Deliberately not restricted to UUID syntax. The importer's whole job is to carry
         * version 1's identifiers across unchanged, and rejecting one because it does not
         * parse as a UUID would turn a cosmetic difference into lost data.
         */
        fun parse(raw: String): ItemId? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed.length > MaxLength) return null
            return ItemId(trimmed.lowercase())
        }

        /** Long enough for any identifier, short enough that a parser cannot be fed a novel. */
        const val MaxLength = 128

        private const val HexDigits = "0123456789abcdef"
    }
}

internal object ItemIdSerializer : KSerializer<ItemId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.passmanager.domain.item.ItemId", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ItemId) = encoder.encodeString(value.value)

    /**
     * Folds case on the way in, so a file written by something less careful cannot
     * introduce two identifiers that differ only in case.
     */
    override fun deserialize(decoder: Decoder): ItemId {
        val raw = decoder.decodeString()
        return ItemId.parse(raw)
            ?: throw IllegalArgumentException("item identifier is empty or too long")
    }
}
