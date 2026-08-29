package com.passmanager.vault

/**
 * Stores that keep everything in memory, so the session's behaviour can be tested on every
 * target without a filesystem — and so a test can look at exactly what was written.
 */
internal class InMemoryVaultStore : VaultFileStore {
    var bytes: ByteArray? = null
    var writes = 0

    override fun exists() = bytes != null
    override fun read() = bytes ?: error("no vault")
    override fun write(bytes: ByteArray) {
        this.bytes = bytes
        writes++
    }
    override fun delete() {
        bytes = null
    }
}

internal class InMemoryBlobs : BlobFileStore {
    val files = LinkedHashMap<String, ByteArray>()

    /** The largest single read, so a test can prove a listing did not read whole files. */
    var largestRead = 0

    override fun list() = files.keys.toList()

    override fun read(id: String): ByteArray {
        val bytes = files[id] ?: error("no such attachment")
        largestRead = maxOf(largestRead, bytes.size)
        return bytes
    }

    override fun readPrefix(id: String, maxBytes: Int): ByteArray {
        val bytes = files[id] ?: error("no such attachment")
        val taken = bytes.copyOf(minOf(maxBytes, bytes.size))
        largestRead = maxOf(largestRead, taken.size)
        return taken
    }

    override fun write(id: String, bytes: ByteArray) {
        files[id] = bytes
    }

    override fun delete(id: String) {
        files.remove(id)
    }
}
