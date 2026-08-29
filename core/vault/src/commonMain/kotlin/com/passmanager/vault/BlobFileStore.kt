package com.passmanager.vault

/**
 * Where attachments live. One file each, named by identifier.
 *
 * Separate from [VaultFileStore] because the access patterns have nothing in common: the
 * vault is one file read whole and rewritten whole, and attachments are many files, mostly
 * never read, occasionally read in part.
 *
 * [readPrefix] is the reason this is not simply a map of identifier to bytes. Listing an
 * item's attachments needs each one's name, size and thumbnail and none of their contents,
 * and reading five megabytes to draw a row would make opening an item with a few scans
 * attached noticeably slow for no reason.
 */
interface BlobFileStore {
    /** Every attachment on this device, by identifier. Order is not significant. */
    fun list(): List<String>

    fun read(id: String): ByteArray

    /**
     * The first [maxBytes] of an attachment, or all of it if it is shorter. Implementations
     * must not read the rest.
     */
    fun readPrefix(id: String, maxBytes: Int): ByteArray

    fun write(id: String, bytes: ByteArray)

    fun delete(id: String)
}

/** An attachment, as a list needs it: everything but the bytes. */
class Attachment(
    val id: String,
    val filename: String,
    val mimeType: String,
    val size: Long,
    val createdAt: Long,
    /** A small preview, already decrypted. Null if the attachment has none. */
    val thumbnail: ByteArray?,
)
