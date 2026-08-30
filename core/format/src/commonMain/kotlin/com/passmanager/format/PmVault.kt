package com.passmanager.format

import com.passmanager.crypto.Secret
import com.passmanager.crypto.aead.AesGcm
import com.passmanager.crypto.kdf.Argon2Parameters
import com.passmanager.crypto.kdf.hkdfSha256
import com.passmanager.crypto.key.VaultKeys
import com.passmanager.crypto.random.secureRandomBytes
import com.passmanager.crypto.wipe
import com.passmanager.domain.item.VaultItem
import kotlinx.serialization.json.JsonObject

/**
 * The `.pmvault` container: one grammar, used both for the vault on the device and for a
 * file handed to another device.
 *
 * ```
 * descriptor   31 bytes, plaintext, and verbatim the body's associated data
 * wrap block   slot count, then one length-prefixed slot per way in — not in any tag
 * records      to end of file: type u8 || length u32be || nonce[12] || sealed
 * ```
 *
 * ### Why records rather than one seal over the whole file
 *
 * `AesGcm` takes and returns whole arrays on every target, and no streaming authenticated
 * cipher is reachable from Kotlin on Apple. Sealing an entire export in one call would mean
 * holding it and its ciphertext in memory at once, which fails on a phone as soon as
 * attachments are involved.
 *
 * Chunking *inside* a record is deliberately refused: it needs an index and an
 * end-of-stream marker inside every chunk's associated data, or an attacker can splice and
 * truncate without failing a tag. Framing at the record level and capping each record
 * achieves the same thing with nothing to get wrong.
 *
 * ### A short file is not a wrong passphrase
 *
 * GCM detects truncation and is structurally incapable of explaining it, which is why
 * version 1 had to tell users "the passphrase is wrong or the file is corrupted" and leave
 * them to guess. Record lengths fix that without a header field and without a key: a record
 * declaring more bytes than remain proves the file is short. So [parse] can return
 * [VaultParse.Damaged] with an offset, and only a genuine authentication failure reaches the
 * deliberately ambiguous [VaultOpen.Unopenable].
 */
object PmVault {

    /** The one record holding the item body. Exactly one appears in a vault. */
    internal const val RecordItemBody = 1

    /** Each record carries its own nonce, so no two are ever sealed under the same one. */
    private const val RecordHeaderSize = 1 + 4

    /**
     * Domain separation for the body key. The vault key itself is never used as an AES key;
     * every purpose derives its own, so a weakness in one cannot be carried to another.
     */
    private val BodyKeyContext = "passmanager.vault-body.v2".encodeToByteArray()

    /** A single record is capped so a corrupt length cannot become a vast allocation. */
    private const val MaxRecordSize = 64 * 1024 * 1024

    // ── Reading ─────────────────────────────────────────────────────────────

    /**
     * Reads the structure of a file without any key. Every outcome except
     * [VaultParse.Sealed] is knowable before a passphrase is asked for, which is what lets
     * the application say something true instead of blaming the passphrase.
     */
    fun parse(bytes: ByteArray): VaultParse {
        val descriptor = when (val result = VaultDescriptor.parse(bytes)) {
            is DescriptorParse.Parsed -> result.descriptor
            is DescriptorParse.Unsupported ->
                return VaultParse.Unsupported(result.container, result.schema, result.minSchema)
            is DescriptorParse.Damaged -> return VaultParse.Damaged(result.what, result.offset)
            DescriptorParse.NotAVault -> return VaultParse.NotAVault
        }

        var offset = VaultDescriptor.Size
        if (!bytes.has(offset, 1)) return VaultParse.Damaged("no wrap block", offset)
        val slotCount = bytes.u8(offset)
        offset += 1
        if (slotCount !in 1..WrapSlot.MaxSlots) {
            return VaultParse.Damaged("slot count $slotCount is outside 1..${WrapSlot.MaxSlots}", offset - 1)
        }

        val slots = ArrayList<WrapSlot>(slotCount)
        repeat(slotCount) {
            if (!bytes.has(offset, 3)) return VaultParse.Damaged("truncated slot header", offset)
            val kind = bytes.u8(offset)
            val bodyLength = bytes.u16(offset + 1)
            offset += 3
            if (!bytes.has(offset, bodyLength)) {
                return VaultParse.Damaged("slot declares $bodyLength bytes past the end", offset)
            }
            slots += WrapSlot(kind, bytes.copyOfRange(offset, offset + bodyLength))
            offset += bodyLength
        }

        var body: ByteArray? = null
        while (offset < bytes.size) {
            if (!bytes.has(offset, RecordHeaderSize)) {
                return VaultParse.Damaged("truncated record header", offset)
            }
            val type = bytes.u8(offset)
            val length = bytes.u32(offset + 1)
            val payloadStart = offset + RecordHeaderSize
            if (length > MaxRecordSize) {
                return VaultParse.Damaged("record declares $length bytes", offset + 1)
            }
            if (!bytes.has(payloadStart, length.toInt())) {
                // The truncation check, and the reason no header field is needed for it.
                return VaultParse.Damaged(
                    "record declares $length bytes but ${bytes.size - payloadStart} remain",
                    offset + 1,
                )
            }
            val end = payloadStart + length.toInt()
            if (type == RecordItemBody) {
                if (body != null) return VaultParse.Damaged("more than one item body", offset)
                if (length < AesGcm.NonceSize + AesGcm.TagSize) {
                    return VaultParse.Damaged("item body is too short to be sealed", offset + 1)
                }
                body = bytes.copyOfRange(payloadStart, end)
            }
            // Records of unknown type are stepped over, not rejected: that is what the
            // length prefix is for, and it is how a later version adds one.
            offset = end
        }

        if (body == null) return VaultParse.Damaged("no item body", bytes.size)
        return VaultParse.Sealed(descriptor, slots, body, bytes.copyOfRange(0, VaultDescriptor.Size))
    }

    // ── Writing ─────────────────────────────────────────────────────────────

    /**
     * Frames an already-sealed set of keys and an item body into a file.
     *
     * Deriving keys and wrapping them is `VaultKeys`' job and stays there; this only decides
     * layout. The separation is what lets an export and an on-device vault share one writer
     * while differing in the only way that matters — which key their body is sealed under.
     */
    fun write(
        descriptor: VaultDescriptor,
        slots: List<WrapSlot>,
        contents: VaultContents,
        vaultKey: Secret,
    ): ByteArray {
        require(slots.isNotEmpty() && slots.size <= WrapSlot.MaxSlots) {
            "a vault carries 1..${WrapSlot.MaxSlots} slots, not ${slots.size}"
        }

        val descriptorBytes = descriptor.encode()
        val nonce = secureRandomBytes(AesGcm.NonceSize)
        val sealed = bodyKey(vaultKey).use { key ->
            plaintext(contents).use { body ->
                AesGcm.seal(key, nonce, body, descriptorBytes)
            }
        }

        val recordLength = AesGcm.NonceSize + sealed.size
        val total = descriptorBytes.size + 1 + slots.sumOf { it.encodedSize } +
            RecordHeaderSize + recordLength
        val out = ByteArray(total)

        var offset = 0
        descriptorBytes.copyInto(out, offset); offset += descriptorBytes.size
        out.putU8(offset, slots.size); offset += 1
        for (slot in slots) {
            out.putU8(offset, slot.kind)
            out.putU16(offset + 1, slot.body.size)
            slot.body.copyInto(out, offset + 3)
            offset += slot.encodedSize
        }
        out.putU8(offset, RecordItemBody)
        out.putU32(offset + 1, recordLength.toLong())
        offset += RecordHeaderSize
        nonce.copyInto(out, offset); offset += AesGcm.NonceSize
        sealed.copyInto(out, offset)
        return out
    }

    /**
     * The whole of creating a vault from a passphrase: draw a key, derive a wrapping key,
     * wrap, frame.
     *
     * @param pepper an optional value held outside the file — a keystore-backed secret — so
     *   that a stolen copy cannot be attacked offline at all. A file written with one can
     *   only be opened on the device that holds it, which is why anything enabling it must
     *   also offer a way back in.
     */
    fun create(
        contents: VaultContents,
        passphrase: Secret,
        parameters: Argon2Parameters = Argon2Parameters.Default,
        pepper: Secret? = null,
    ): ByteArray {
        val salt = VaultKeys.generateSalt()
        val descriptor = VaultDescriptor(
            container = VaultDescriptor.Container,
            schema = VaultDescriptor.Schema,
            minSchema = VaultDescriptor.Schema,
            kdf = parameters,
            salt = salt,
        )
        return VaultKeys.generateVaultKey().use { vaultKey ->
            val wrapped = VaultKeys.deriveKeyEncryptionKey(passphrase, salt, parameters, pepper)
                .use { kek -> VaultKeys.wrap(kek, vaultKey) }
            write(descriptor, listOf(WrapSlot.passphrase(wrapped)), contents, vaultKey)
        }
    }

    // ── Internals shared by both directions ─────────────────────────────────

    internal fun bodyKey(vaultKey: Secret): Secret =
        hkdfSha256(vaultKey, ByteArray(0), BodyKeyContext, AesGcm.KeySize)

    /**
     * The body's plaintext: a length, then the JSON.
     *
     * The length prefix exists so that a later writer can pad the body to hide its size
     * without the reader having to change. Nothing pads today, and the reader takes exactly
     * as many bytes as the prefix names and ignores anything after it.
     */
    private fun plaintext(contents: VaultContents): Secret {
        val json = VaultBodyCodec.encode(
            VaultBody(items = contents.items, deletions = contents.deletions),
            contents.preserved,
        ).encodeToByteArray()
        val out = ByteArray(4 + json.size)
        out.putU32(0, json.size.toLong())
        json.copyInto(out, 4)
        json.wipe()
        return Secret.adopt(out)
    }

    internal fun parsePlaintext(plain: ByteArray): VaultContents? {
        if (!plain.has(0, 4)) return null
        val length = plain.u32(0)
        if (length > (plain.size - 4).toLong()) return null
        val decoded = VaultBodyCodec.decode(plain.decodeToString(4, 4 + length.toInt()))
        return VaultContents.of(decoded.body.items, decoded.body.deletions, decoded.preserved)
    }
}

/**
 * What a vault holds once it is open.
 *
 * The preserved members are not a constructor parameter. They are a `JsonObject`, which
 * belongs to the serialisation library rather than to this module's surface, and a public
 * constructor mentioning it would drag that library into the Swift framework's public API —
 * where it would appear as a type Swift can neither build nor read. A caller constructs
 * contents from items; carrying unknown members forward is this module's business.
 */
class VaultContents private constructor(
    val items: List<VaultItem>,
    val deletions: List<VaultBody.Deletion>,
    internal val preserved: JsonObject,
) {
    constructor(
        items: List<VaultItem> = emptyList(),
        deletions: List<VaultBody.Deletion> = emptyList(),
    ) : this(items, deletions, JsonObject(emptyMap()))

    /** Keeps [preserved] across an edit, so re-saving does not drop a newer writer's fields. */
    fun withItems(items: List<VaultItem>): VaultContents =
        VaultContents(items, deletions, preserved)

    /**
     * The same, for an edit that also changes what has been deleted.
     *
     * Separate from [withItems] rather than folded into it, because every caller that moves
     * items around must not have to think about tombstones, and the one caller that does
     * must say so.
     */
    fun with(items: List<VaultItem>, deletions: List<VaultBody.Deletion>): VaultContents =
        VaultContents(items, deletions, preserved)

    internal companion object {
        fun of(
            items: List<VaultItem>,
            deletions: List<VaultBody.Deletion>,
            preserved: JsonObject,
        ) = VaultContents(items, deletions, preserved)
    }
}

/** The outcome of reading a file's structure, before any key is involved. */
sealed interface VaultParse {

    /** Structurally sound. Opening it is a separate step that needs a key. */
    class Sealed internal constructor(
        val descriptor: VaultDescriptor,
        val slots: List<WrapSlot>,
        private val bodyRecord: ByteArray,
        private val associatedData: ByteArray,
    ) : VaultParse {

        /**
         * Derives a key from the passphrase, tries the slots that could accept it, and opens
         * the body.
         *
         * Slots are filtered by kind *before* being tried. Trying every slot blindly would
         * mean one full Argon2 derivation per slot on every wrong passphrase, which turns a
         * typo into several seconds and hands an attacker a way to make the device do more
         * work than they do.
         */
        fun openWithPassphrase(
            passphrase: Secret,
            pepper: Secret? = null,
        ): VaultOpen {
            val candidates = slots.filter { it.kind == WrapSlot.KindPassphrase }
            if (candidates.isEmpty()) return VaultOpen.Unopenable
            VaultKeys.deriveKeyEncryptionKey(passphrase, descriptor.salt, descriptor.kdf, pepper)
                .use { kek ->
                    for (slot in candidates) {
                        val vaultKey = VaultKeys.unwrap(kek, slot.body) ?: continue
                        val contents = openWithVaultKey(vaultKey)
                        if (contents == null) {
                            vaultKey.destroy()
                            return VaultOpen.Unopenable
                        }
                        return VaultOpen.Opened(vaultKey, contents)
                    }
                }
            return VaultOpen.Unopenable
        }

        /**
         * Opens the body with the vault key directly — the path a biometric or keystore
         * unlock takes, where no passphrase was ever entered.
         */
        fun openWithVaultKey(vaultKey: Secret): VaultContents? {
            val nonce = bodyRecord.copyOfRange(0, AesGcm.NonceSize)
            val sealed = bodyRecord.copyOfRange(AesGcm.NonceSize, bodyRecord.size)
            return PmVault.bodyKey(vaultKey).use { key ->
                // The decrypted body is erased on the way out of this scope, including when
                // parsing throws. It holds every password in the vault in the clear.
                AesGcm.open(key, nonce, sealed, associatedData)
                    ?.use { plain -> plain.reveal(PmVault::parsePlaintext) }
            }
        }
    }

    /**
     * Written by a version this one cannot read. Reported with the numbers so the
     * application can say which, rather than calling a newer file corrupt.
     */
    data class Unsupported(val container: Int, val schema: Int, val minSchema: Int) : VaultParse

    /** Provably broken, with no key required to prove it. */
    data class Damaged(val what: String, val offset: Int) : VaultParse

    /** Not a vault at all — the magic does not match. */
    data object NotAVault : VaultParse
}

sealed interface VaultOpen {
    /** The caller now owns [vaultKey] and must destroy it. */
    class Opened(val vaultKey: Secret, val contents: VaultContents) : VaultOpen

    /**
     * The passphrase was wrong, or the file was edited. These are indistinguishable, and
     * treating them as one outcome is the point: an application that claimed to know which
     * would be telling an attacker whether their forgery was structurally correct.
     */
    data object Unopenable : VaultOpen
}
