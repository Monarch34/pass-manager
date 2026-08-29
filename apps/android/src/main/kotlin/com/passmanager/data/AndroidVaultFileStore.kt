package com.passmanager.data

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.passmanager.vault.VaultFileStore
import java.io.File
import java.io.FileOutputStream

/**
 * The vault file, in the application's private storage.
 *
 * `filesDir` rather than external storage or a media directory: this is the application's
 * own state and nothing else on the device has any business reading it. The manifest already
 * excludes it from cloud backup and device transfer, so the only way a copy leaves this
 * phone is an export the user asked for.
 *
 * **The write is atomic and durable, in that order.** A password manager that loses
 * everything usually loses it to a torn write, so the new vault is written whole to a
 * temporary file, forced to the device, and only then renamed over the old one. A crash at
 * any point leaves either the previous vault or the new one.
 *
 * `AtomicFile` from the platform is deliberately not used: it renames, but it never syncs
 * the containing directory, so a rename can be lost to a power failure that the file's own
 * contents survived — leaving the vault at its previous version with no indication why.
 */
class AndroidVaultFileStore(context: Context) : VaultFileStore {

    private val directory = context.filesDir
    private val file = File(directory, "vault.pmvault")
    private val temporary = File(directory, "vault.pmvault.writing")

    override fun exists(): Boolean = file.exists()

    override fun read(): ByteArray = file.readBytes()

    override fun write(bytes: ByteArray) {
        temporary.delete()
        FileOutputStream(temporary).use { stream ->
            stream.write(bytes)
            // Ask for the bytes to reach the device, not merely the page cache.
            stream.fd.sync()
        }
        check(temporary.renameTo(file)) { "the vault could not be replaced" }
        syncDirectory()
    }

    override fun delete() {
        file.delete()
        temporary.delete()
        syncDirectory()
    }

    /**
     * Forces the directory entry, so the rename survives a power failure and not just the
     * file's contents.
     *
     * Through `Os.open`, because Java cannot do it: a directory opened as a `FileInputStream`
     * or `FileOutputStream` throws "Is a directory" on every Android filesystem, so the
     * obvious-looking version of this method would be a durability guarantee that never once
     * executed.
     *
     * Failures are swallowed deliberately. The vault has already been written and renamed by
     * this point; refusing the whole save because a durability hint was rejected would turn
     * a weaker guarantee into no save at all.
     */
    private fun syncDirectory() {
        runCatching {
            val descriptor = Os.open(directory.path, OsConstants.O_RDONLY, 0)
            try {
                Os.fsync(descriptor)
            } finally {
                Os.close(descriptor)
            }
        }
    }
}
