package com.passmanager.vault

/**
 * Where the vault's bytes live. The one thing about a vault that cannot be shared.
 *
 * Everything else in this module — what is in the vault, how it is searched, what a save
 * does — is the same on every platform. Reading and writing a file is not: Android has a
 * files directory and iOS has Application Support, and each has its own idea of an atomic
 * replacement and of what "protected while locked" means. So that, and only that, is a port.
 *
 * Four methods and no more. An earlier design sketched a store that also reported free space
 * and swept its own temporary files; both existed only to clean up after a mechanism the
 * port itself invented, and a free-space check races anything else on the disk while giving
 * a worse guarantee than simply failing the write.
 *
 * **Implementations must replace atomically.** [write] either leaves the previous vault
 * intact or installs the new one; it never leaves a partial file. A password manager that
 * loses everything usually does it here.
 */
interface VaultFileStore {
    fun exists(): Boolean

    /** Throws if there is nothing to read. Callers check [exists] first. */
    fun read(): ByteArray

    fun write(bytes: ByteArray)

    fun delete()
}
