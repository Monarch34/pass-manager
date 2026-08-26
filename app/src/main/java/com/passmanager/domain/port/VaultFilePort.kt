package com.passmanager.domain.port

/**
 * Reads and writes the document the user picked in the system file picker.
 *
 * A port rather than a direct `ContentResolver` call so the transfer flow in the Settings
 * ViewModel stays free of Android URI parsing — the whole export/import path is then testable on
 * the JVM. Implemented by [com.passmanager.data.file.ContentResolverVaultFilePort].
 */
interface VaultFilePort {
    /** @param uri the string form of a SAF document Uri. */
    suspend fun read(uri: String): ByteArray
    suspend fun write(uri: String, bytes: ByteArray)
}
