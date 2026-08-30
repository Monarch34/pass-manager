package com.passmanager.format

import com.passmanager.crypto.Secret
import com.passmanager.crypto.aead.AesGcm
import com.passmanager.crypto.kdf.hkdfSha256
import com.passmanager.crypto.random.secureRandomBytes
import com.passmanager.crypto.wipe
import com.passmanager.domain.item.ItemId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One attachment, as its own file.
 *
 * ```
 * magic "PMB2"          4
 * container             1
 * blobId               16
 * wrappedBlobKey       60      the blob's own key, sealed under one derived from the vault key
 * header record         4 + 12 + sealed     itemId, filename, type, size, thumbnail
 * content record        4 + 12 + sealed     the bytes
 * ```
 *
 * ### Why attachments are not in the vault file
 *
 * The vault is one sealed document rewritten on every save. A five-megabyte scan inside it
 * would make every edit to every password rewrite five megabytes, and would put the whole
 * thing in memory twice to do it. Keeping each attachment in its own file means attaching
 * one does not touch the vault at all.
 *
 * ### The ownership pointer runs blob to item, and only that way
 *
 * The item does not list its attachments; each attachment names its item. That is the
 * opposite of the obvious design and it is deliberate. If the item body held the list, an
 * attachment added on one device would vanish the moment the other device's copy of that
 * item won a merge — the file surviving on disk, orphaned and unreachable, while the owner
 * watched it disappear. Pointing the other way, an attachment can only be lost by deleting
 * the attachment.
 *
 * **Which is why `itemId` is not in the associated data.** It cannot be: ownership lives
 * inside the seal, so nothing knows the item until the header is open. Only `blobId` is
 * authenticated. Nothing is weakened by that — the header's own tag still stops an
 * attachment being re-parented, and a different vault key still cannot open it at all.
 *
 * ### The blob's key is wrapped, not derived
 *
 * A derived key would mean rotating the vault key re-encrypts every attachment byte a user
 * owns. A wrapped one makes rotation sixty bytes per attachment and no file rewritten. It is
 * the same argument the vault's own two-key model makes, one level down.
 */
object PmBlob {

    val Magic = byteArrayOf('P'.code.toByte(), 'M'.code.toByte(), 'B'.code.toByte(), '2'.code.toByte())

    const val Container = 1

    const val IdSize = 16

    /**
     * Five mebibytes. Not a guess about what people attach, but the bound that keeps a
     * single `AesGcm.seal` — which takes and returns whole arrays on every platform —
     * from being the thing that runs a phone out of memory.
     */
    const val MaxContentSize = 5 * 1024 * 1024

    /**
     * Sixteen kibibytes: a name, a type, and a thumbnail bounded at eight before encoding.
     *
     * This is not a generous ceiling and must not be, because it is not really a limit on
     * headers — it is what a listing reads *per attachment*. HeaderPrefixSize is built from
     * it, so at 64 KiB, drawing an item with eight attachments read half a megabyte to
     * render eight rows, and the KDoc's promise of "a few kilobytes each" was false by a
     * factor of sixteen.
     */
    const val MaxHeaderSize = 16 * 1024

    /** Magic, container, identifier and wrapped key: everything before the first record. */
    private const val FixedPrefix = 4 + 1 + IdSize + 60

    /** Enough of a file to be certain the header is included. */
    const val HeaderPrefixSize = FixedPrefix + 4 + AesGcm.NonceSize + MaxHeaderSize

    private val KeyContext = "passmanager.blob-key.v2".encodeToByteArray()
    private val HeaderContext = "passmanager.blob.header.v2".encodeToByteArray()
    private val ContentContext = "passmanager.blob.content.v2".encodeToByteArray()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    /**
     * Seals an attachment. Returns the whole file; the caller decides where it goes.
     *
     * Neither [vaultKey] nor [content] is destroyed here.
     */
    fun create(
        vaultKey: Secret,
        id: BlobId,
        header: BlobHeader,
        content: Secret,
    ): ByteArray {
        require(content.size <= MaxContentSize) {
            "an attachment is ${content.size} bytes; the limit is $MaxContentSize"
        }

        val headerJson = json.encodeToString(BlobHeader.serializer(), header).encodeToByteArray()
        require(headerJson.size <= MaxHeaderSize) { "the attachment's details are too large" }

        return Secret.random(AesGcm.KeySize).use { blobKey ->
            val wrapped = wrappingKey(vaultKey, id).use { wrapper ->
                val nonce = secureRandomBytes(AesGcm.NonceSize)
                nonce + AesGcm.seal(wrapper, nonce, blobKey, KeyContext + id.bytes)
            }

            val headerNonce = secureRandomBytes(AesGcm.NonceSize)
            val sealedHeader = Secret.adopt(headerJson).use {
                AesGcm.seal(blobKey, headerNonce, it, HeaderContext + id.bytes)
            }
            val contentNonce = secureRandomBytes(AesGcm.NonceSize)
            val sealedContent = AesGcm.seal(blobKey, contentNonce, content, ContentContext + id.bytes)

            val out = ByteArray(
                FixedPrefix +
                    4 + AesGcm.NonceSize + sealedHeader.size +
                    4 + AesGcm.NonceSize + sealedContent.size
            )
            var at = 0
            Magic.copyInto(out, at); at += Magic.size
            out.putU8(at, Container); at += 1
            id.bytes.copyInto(out, at); at += IdSize
            wrapped.copyInto(out, at); at += wrapped.size
            out.putU32(at, (AesGcm.NonceSize + sealedHeader.size).toLong()); at += 4
            headerNonce.copyInto(out, at); at += AesGcm.NonceSize
            sealedHeader.copyInto(out, at); at += sealedHeader.size
            out.putU32(at, (AesGcm.NonceSize + sealedContent.size).toLong()); at += 4
            contentNonce.copyInto(out, at); at += AesGcm.NonceSize
            sealedContent.copyInto(out, at)
            out
        }
    }

    /**
     * Reads the details without decrypting the attachment itself.
     *
     * [bytes] may be a prefix of the file — [HeaderPrefixSize] is always enough. This is what
     * lets a list of an item's attachments cost a few kilobytes each rather than the whole of
     * every one of them.
     */
    fun readHeader(vaultKey: Secret, bytes: ByteArray): BlobHeader? {
        val id = identify(bytes) ?: return null
        val record = record(bytes, FixedPrefix) ?: return null
        return blobKey(vaultKey, bytes, id)?.use { key ->
            AesGcm.open(key, record.nonce, record.sealed, HeaderContext + id.bytes)?.use { plain ->
                runCatching {
                    json.decodeFromString(BlobHeader.serializer(), plain.reveal { it.decodeToString() })
                }.getOrNull()
            }
        }
    }

    /** Decrypts the attachment. The caller owns the result and must destroy it. */
    fun readContent(vaultKey: Secret, bytes: ByteArray): Secret? {
        val id = identify(bytes) ?: return null
        val headerRecord = record(bytes, FixedPrefix) ?: return null
        val contentRecord = record(bytes, FixedPrefix + 4 + headerRecord.length) ?: return null
        return blobKey(vaultKey, bytes, id)?.use { key ->
            AesGcm.open(key, contentRecord.nonce, contentRecord.sealed, ContentContext + id.bytes)
        }
    }

    /**
     * The same attachment, opened by a different vault key.
     *
     * Sixty bytes change and the attachment itself is not touched. That is the whole payoff
     * of wrapping the blob's key rather than deriving it: moving a five-megabyte scan into an
     * export costs one AES block, not a re-encryption, and the header and content stay
     * sealed under the key they were sealed under when they were written.
     *
     * The identifier is preserved deliberately. It is the associated data of both records, so
     * changing it would invalidate them — and it is what lets an import recognise an
     * attachment it already has rather than storing a second copy.
     *
     * Null when [fromVaultKey] does not open this attachment, which is the same answer as
     * for a file that has been tampered with.
     */
    fun rewrap(fromVaultKey: Secret, toVaultKey: Secret, bytes: ByteArray): ByteArray? {
        val id = identify(bytes) ?: return null
        return blobKey(fromVaultKey, bytes, id)?.use { blobKey ->
            val wrapped = wrappingKey(toVaultKey, id).use { wrapper ->
                val nonce = secureRandomBytes(AesGcm.NonceSize)
                nonce + AesGcm.seal(wrapper, nonce, blobKey, KeyContext + id.bytes)
            }
            val out = bytes.copyOf()
            wrapped.copyInto(out, 4 + 1 + IdSize)
            out
        }
    }

    /** The identifier, readable without any key — it is the filename. */
    fun identify(bytes: ByteArray): BlobId? {
        if (!bytes.has(0, FixedPrefix)) return null
        for (i in Magic.indices) if (bytes[i] != Magic[i]) return null
        if (bytes.u8(4) != Container) return null
        return BlobId(bytes.copyOfRange(5, 5 + IdSize))
    }

    /**
     * Per-attachment domain separation. Two attachments under one vault key derive different
     * wrapping keys, so a header sealed for one can never be opened as another's.
     */
    private fun wrappingKey(vaultKey: Secret, id: BlobId): Secret =
        hkdfSha256(vaultKey, ByteArray(0), KeyContext + id.bytes, AesGcm.KeySize)

    private fun blobKey(vaultKey: Secret, bytes: ByteArray, id: BlobId): Secret? {
        val at = 4 + 1 + IdSize
        val nonce = bytes.copyOfRange(at, at + AesGcm.NonceSize)
        val sealed = bytes.copyOfRange(at + AesGcm.NonceSize, at + 60)
        return wrappingKey(vaultKey, id).use { wrapper ->
            AesGcm.open(wrapper, nonce, sealed, KeyContext + id.bytes)
        }
    }

    private class Record(val length: Int, val nonce: ByteArray, val sealed: ByteArray)

    private fun record(bytes: ByteArray, at: Int): Record? {
        if (!bytes.has(at, 4)) return null
        val length = bytes.u32(at)
        if (length < (AesGcm.NonceSize + AesGcm.TagSize).toLong()) return null
        if (length > (MaxContentSize + AesGcm.NonceSize + AesGcm.TagSize).toLong()) return null
        val start = at + 4
        if (!bytes.has(start, length.toInt())) return null
        return Record(
            length = length.toInt(),
            nonce = bytes.copyOfRange(start, start + AesGcm.NonceSize),
            sealed = bytes.copyOfRange(start + AesGcm.NonceSize, start + length.toInt()),
        )
    }
}

/**
 * An attachment's identifier: sixteen random bytes, and the name of its file.
 *
 * Random rather than a hash of the contents. A content hash would make two items holding the
 * same file share an identifier, so anyone who could see the directory would learn that they
 * match — and the filename is the one thing about an attachment that is not encrypted.
 */
class BlobId(val bytes: ByteArray) {

    init {
        require(bytes.size == PmBlob.IdSize) { "a blob id is ${PmBlob.IdSize} bytes" }
    }

    val hex: String = buildString(bytes.size * 2) {
        for (byte in bytes) {
            val value = byte.toInt() and 0xff
            append(HexDigits[value shr 4])
            append(HexDigits[value and 0xf])
        }
    }

    override fun toString(): String = hex

    override fun equals(other: Any?): Boolean = other is BlobId && other.hex == hex

    override fun hashCode(): Int = hex.hashCode()

    companion object {
        private const val HexDigits = "0123456789abcdef"

        fun random(): BlobId = BlobId(secureRandomBytes(PmBlob.IdSize))

        fun parse(hex: String): BlobId? {
            if (hex.length != PmBlob.IdSize * 2) return null
            val bytes = ByteArray(PmBlob.IdSize)
            for (i in bytes.indices) {
                val high = HexDigits.indexOf(hex[2 * i])
                val low = HexDigits.indexOf(hex[2 * i + 1])
                if (high < 0 || low < 0) return null
                bytes[i] = ((high shl 4) or low).toByte()
            }
            return BlobId(bytes)
        }
    }
}

/**
 * What an attachment is, other than its bytes. All of it inside the seal.
 *
 * Nothing here sits beside the file in the clear. A plaintext name and size would hand
 * anyone with the directory the item-to-attachment graph and the exact size of every file,
 * which is the disclosure the container's design exists to remove.
 */
@Serializable
data class BlobHeader(
    /** The item this belongs to. Inside the seal, which is why it cannot be in the tag. */
    val itemId: ItemId,
    val filename: String = "",
    val mimeType: String = "",
    /** The real size, so a list can be drawn without decrypting the content. */
    val size: Long = 0,
    val createdAt: Long = 0,
    /**
     * A small preview, sealed with everything else.
     *
     * Base64 rather than raw bytes because the header is JSON; a thumbnail is a few
     * kilobytes, so the third it adds is not worth a second encoding for the body to learn.
     */
    val thumbnail: String? = null,
)
