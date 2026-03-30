package com.passmanager.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * Central JSON codec for [ItemPayload].
 *
 * New items are serialized with a `"type"` discriminator.
 * Legacy v1 blobs (flat JSON without a `"type"` field) lack the discriminator and are
 * migrated transparently by [decodeLegacy] using the row's [ItemCategory].
 *
 * Thread-safe — the [Json] instance is immutable after creation.
 */
object PayloadJson {

    private val module = SerializersModule {
        polymorphic(ItemPayload::class) {
            subclass(ItemPayload.Login::class)
            subclass(ItemPayload.Card::class)
            subclass(ItemPayload.Bank::class)
            subclass(ItemPayload.SecureNote::class)
            subclass(ItemPayload.Identity::class)
        }
    }

    val instance: Json = Json {
        serializersModule = module
        classDiscriminator = "type"
        ignoreUnknownKeys = true      // forward-compat: new fields won't crash old code
        encodeDefaults = false         // omit empty strings → smaller blobs
        isLenient = false
    }

    /** Encode a payload to a JSON string. */
    fun encode(payload: ItemPayload): String =
        instance.encodeToString(ItemPayload.serializer(), payload)

    /**
     * Decode a JSON string to an [ItemPayload].
     *
     * If the JSON contains a `"type"` discriminator, polymorphic dispatch handles it.
     * Otherwise the blob is a legacy v1 format — [categoryHint] (from the entity's
     * plaintext `category` column) disambiguates.
     */
    fun decode(json: String, categoryHint: ItemCategory = ItemCategory.LOGIN): ItemPayload {
        val tree = instance.parseToJsonElement(json).jsonObject
        return if (tree.containsKey("type")) {
            instance.decodeFromJsonElement(ItemPayload.serializer(), tree)
        } else {
            decodeLegacy(tree, categoryHint)
        }
    }

    // ── Legacy v1 migration ──────────────────────────

    private fun decodeLegacy(obj: JsonObject, categoryHint: ItemCategory): ItemPayload {
        val id    = obj.str("id")
        val title = obj.str("title")
        val notes = obj.str("notes")

        return when (categoryHint) {
            ItemCategory.CARD -> ItemPayload.Card(
                id = id,
                title = title,
                notes = notes,
                cardholderName = obj.str("cardholderName"),
                cardNumber     = obj.str("cardNumber"),
                cardCvc        = obj.str("cardCvc"),
                cardExpiry     = obj.str("cardExpiry")
            )
            ItemCategory.BANK -> ItemPayload.Bank(
                id = id,
                title = title,
                notes = notes,
                accountNumber     = obj.str("username"),   // v1 reused "username"
                bankName          = obj.str("address"),    // v1 reused "address"
                password          = obj.str("password"),
                previousPasswords = obj.strList("previousPasswords")
            )
            ItemCategory.NOTE -> ItemPayload.SecureNote(
                id = id,
                title = title,
                notes = notes
            )
            ItemCategory.IDENTITY -> ItemPayload.Identity(
                id = id,
                title = title,
                notes = notes,
                // v1 had no identity fields — best effort
                firstName = obj.str("username"),
                address   = obj.str("address")
            )
            ItemCategory.LOGIN -> ItemPayload.Login(
                id = id,
                title = title,
                notes = notes,
                username = obj.str("username"),
                address  = obj.str("address"),
                password = obj.str("password")
            )
        }
    }

    private fun JsonObject.str(key: String): String =
        get(key)?.jsonPrimitive?.content ?: ""

    private fun JsonObject.strList(key: String): List<String> =
        try {
            get(key)?.let { element ->
                instance.decodeFromJsonElement(
                    kotlinx.serialization.builtins.ListSerializer(
                        kotlinx.serialization.serializer<String>()
                    ),
                    element
                )
            } ?: emptyList()
        } catch (_: Exception) { emptyList() }
}
