package com.passmanager.data

import android.content.Context
import com.passmanager.vault.BlobFileStore
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Attachments, one file each, in a directory of their own.
 *
 * Separate from the vault file so that a directory listing is exactly what it appears to be —
 * a count of attachments and nothing else. The names are the random identifiers, and every
 * other fact about an attachment is inside its seal.
 *
 * The same write protocol as the vault: whole file to a temporary name, forced to the
 * device, then renamed. An attachment is a scan of something the user may no longer have, so
 * a half-written one is worth avoiding as much as a half-written vault.
 */
class AndroidBlobFileStore(context: Context) : BlobFileStore {

    private val directory = File(context.filesDir, "blobs").apply { mkdirs() }

    override fun list(): List<String> =
        directory.listFiles()?.filter { it.isFile && !it.name.endsWith(Writing) }?.map { it.name }
            ?: emptyList()

    override fun read(id: String): ByteArray = file(id).readBytes()

    /**
     * Reads at most [maxBytes], and genuinely stops there.
     *
     * `readBytes().copyOf(n)` would satisfy the signature and defeat the purpose: the point
     * of a prefix read is that listing an item's attachments does not pull five megabytes
     * off the disk per row.
     */
    override fun readPrefix(id: String, maxBytes: Int): ByteArray {
        RandomAccessFile(file(id), "r").use { handle ->
            val length = minOf(maxBytes.toLong(), handle.length()).toInt()
            val buffer = ByteArray(length)
            handle.readFully(buffer)
            return buffer
        }
    }

    override fun write(id: String, bytes: ByteArray) {
        val temporary = File(directory, id + Writing)
        temporary.delete()
        FileOutputStream(temporary).use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
        check(temporary.renameTo(file(id))) { "the attachment could not be saved" }
    }

    override fun delete(id: String) {
        file(id).delete()
    }

    /**
     * Rejects anything that is not a bare identifier.
     *
     * The name comes from inside a file this application wrote, but a container is a thing
     * an attacker can hand over: without this, a crafted identifier of `../databases/x`
     * would make a read or a delete escape this directory entirely.
     */
    private fun file(id: String): File {
        require(id.isNotEmpty() && id.all { it in '0'..'9' || it in 'a'..'f' }) {
            "an attachment identifier is lower-case hexadecimal"
        }
        return File(directory, id)
    }

    private companion object {
        const val Writing = ".writing"
    }
}
