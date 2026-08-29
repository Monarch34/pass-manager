package com.passmanager.format

import com.passmanager.domain.item.ItemId
import com.passmanager.domain.item.VaultItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Everything the vault holds, as it appears inside the seal.
 *
 * JSON rather than a hand-rolled binary encoding, because these bytes sit *inside* an
 * authentication tag: a parser here only ever sees input the vault key already vouched for,
 * so the usual argument against parsing untrusted structure does not apply. What does apply
 * is that the encoding must be identical on three platforms forever, and
 * kotlinx-serialization emits members in declaration order deterministically — the exact
 * property Foundation's `.sortedKeys` failed to provide.
 */
@Serializable
data class VaultBody(
    val items: List<VaultItem> = emptyList(),
    /**
     * That an item was deleted, without saying what it was.
     *
     * No title and no category: a record naming what was deleted outlives the item it names,
     * which is the opposite of deleting it. An identifier and a time are enough to stop a
     * merge resurrecting it.
     *
     * Nothing consumes this in version 2.0 — there is no second device to merge with yet.
     * It is written now because the knowledge that a deletion happened is destroyed at the
     * instant of the deletion, so recording it from the start is what retroactively covers
     * the whole 2.0 era once the desktop client lands. Declining it costs one resurrection
     * window, not a format break.
     */
    val deletions: List<Deletion> = emptyList(),
) {
    @Serializable
    data class Deletion(val id: ItemId, val deletedAt: Long)
}

/**
 * Encodes and decodes the body, preserving members it does not recognise.
 *
 * ### Why unknown members are kept
 *
 * There is one canonical copy of the vault and no second store to fall back on, so a reader
 * that drops what it does not understand destroys it permanently on the next save. That is
 * not hypothetical: version 1's importer re-encoded through its payload encoder and lost
 * unknown fields at rest immediately, and the next export propagated the loss.
 *
 * It matters here because the desktop client ships *after* both apps and nothing forces a
 * phone to update. A vault written by a newer desktop and re-saved by an older phone must
 * come back intact.
 *
 * ### What is preserved, and what is not
 *
 * Unknown members at the top level are round-tripped. Unknown members *inside an item's
 * payload* are not: preserving those means every payload carrying a bag of raw JSON, which
 * pushes a serialisation type into the domain model for a case that cannot arise until a
 * second client exists. The gap is covered rather than ignored — a field that must not be
 * dropped ships with a `minSchema` bump, and an older reader then refuses the file outright
 * instead of quietly discarding it.
 */
internal object VaultBodyCodec {

    private val json = Json {
        // A newer writer's additions must not make the file unreadable. The dangerous cases
        // are gated by minSchema instead, which refuses the file rather than silently
        // mangling it.
        ignoreUnknownKeys = true
        // Absent optional fields mean their defaults, and defaults are not written back.
        // This keeps the body small and, more importantly, keeps it stable: a value that is
        // sometimes written and sometimes omitted would change the bytes without changing
        // the meaning.
        encodeDefaults = false
        classDiscriminator = "type"
    }

    private val knownMembers = setOf("items", "deletions")

    fun encode(body: VaultBody, preserved: JsonObject): String {
        val encoded = json.encodeToJsonElement(body) as JsonObject
        if (preserved.isEmpty()) return json.encodeToString(JsonObject.serializer(), encoded)
        // Known members first and in declaration order, then whatever a newer writer added,
        // so the byte layout does not depend on the order a map happened to iterate in.
        val merged = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>(encoded)
        for ((key, value) in preserved) if (key !in knownMembers) merged[key] = value
        return json.encodeToString(JsonObject.serializer(), JsonObject(merged))
    }

    fun decode(text: String): Decoded {
        val root = json.parseToJsonElement(text) as? JsonObject
            ?: throw IllegalArgumentException("the vault body is not a JSON object")
        val body = json.decodeFromJsonElement(VaultBody.serializer(), root)
        val unknown = root.filterKeys { it !in knownMembers }
        return Decoded(body, JsonObject(unknown))
    }

    data class Decoded(val body: VaultBody, val preserved: JsonObject)
}
