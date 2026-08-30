package com.passmanager.vault

import com.passmanager.crypto.Secret
import com.passmanager.crypto.kdf.Argon2Parameters
import com.passmanager.format.PmBlob
import com.passmanager.format.PmVault
import com.passmanager.format.VaultContents
import com.passmanager.format.VaultOpen
import com.passmanager.format.VaultParse

/**
 * Taking a vault off a device, and putting one back.
 *
 * There is no account and no server, so an export is the only way a vault ever leaves — a
 * backup, a move to a new phone, a copy kept somewhere safe. That makes it the one feature
 * whose entire purpose is to put the contents somewhere this application does not control,
 * and every decision here follows from taking that seriously.
 *
 * ### An export is a `.pmvault`
 *
 * The same magic, the same descriptor, the same wrap block, the same records — written by the
 * writer that already exists and read by the reader that already exists. A second format
 * would be a second parser to get right, a second thing to keep compatible forever, and a
 * second place for the two to disagree about what a vault is.
 *
 * The one difference is the only one that matters: **an export never carries the device's own
 * vault key.** It seals under a key drawn for that file alone, wrapped under a passphrase
 * typed at the moment of export. Wrapping the device's key for a new passphrase would make
 * every export ever taken, together with its passphrase, a permanent door into the current
 * vault — revocable only by re-encrypting everything. This way an export is a snapshot, and
 * losing one costs what was in it and nothing more.
 *
 * ### Attachments come too, and are not re-encrypted
 *
 * An export that left the scans behind would not be a copy of the vault. Each travels as a
 * whole `.pmb` file in its own record, with only the sixty bytes wrapping its key rewritten —
 * which is exactly what [PmBlob]'s decision to wrap rather than derive that key was for.
 * Moving five megabytes costs one AES block.
 *
 * ### An import is a merge, and it is shown before it is done
 *
 * Replacing the vault with the file would silently destroy whatever was added since the file
 * was made. Merging cannot, except in the one case where the file records a deletion the
 * device has not seen — which is why [ImportPreview] names the entries that would go rather
 * than counting them.
 */
internal object Transfer {

    /**
     * Reads an export far enough to say what importing it would do.
     *
     * Deliberately does not apply anything. Opening the file costs an Argon2 derivation at
     * export cost, and doing that twice — once to look and once to agree — would make the
     * preview feel like a mistake.
     */
    fun read(
        session: VaultSession,
        file: ByteArray,
        passphrase: Secret,
        now: Long,
    ): ImportRead = when (val parsed = PmVault.parse(file)) {
        is VaultParse.Damaged -> ImportRead.Damaged(parsed.what, parsed.offset)
        is VaultParse.Unsupported ->
            ImportRead.Unsupported(parsed.container, parsed.schema, parsed.minSchema)
        VaultParse.NotAVault -> ImportRead.NotAVault
        is VaultParse.Sealed -> when (val opened = parsed.openWithPassphrase(passphrase)) {
            VaultOpen.Unopenable -> ImportRead.WrongPassphrase
            is VaultOpen.Opened -> ready(session, parsed, opened, now)
        }
    }

    private fun ready(
        session: VaultSession,
        parsed: VaultParse.Sealed,
        opened: VaultOpen.Opened,
        now: Long,
    ): ImportRead {
        val carried = parsed.attachments.mapNotNull { record ->
            PmBlob.identify(record)?.let { it.hex to record }
        }
        // The records sit outside the body's tag, so each is authentic on its own but the
        // *set* of them is not. The manifest inside the body is what binds the set, and a
        // file whose records do not match it has had one added or taken away.
        if (carried.map { it.first }.toSet() != opened.contents.attachments.toSet()) {
            opened.vaultKey.destroy()
            return ImportRead.Incomplete
        }

        val merged = Merge.of(session.contentsForMerge(), opened.contents, now)
        val here = session.blobIds()
        val incoming = carried.filter { (id, record) ->
            if (id in here) return@filter false
            val header = PmBlob.readHeader(opened.vaultKey, record) ?: return@filter false
            merged.keeps(header.itemId)
        }
        merged.preview.attachmentsAdded = incoming.size

        return ImportRead.Ready(session, opened.vaultKey, merged, incoming)
    }
}

/** How reading an export turned out. Mirrors the container's outcomes rather than flattening them. */
sealed interface ImportRead {

    /**
     * The file opened and can be applied. Holds the file's vault key until [apply] or
     * [discard], because the attachments cannot be moved without it.
     */
    class Ready internal constructor(
        private val session: VaultSession,
        private val fileKey: Secret,
        private val merged: MergeResult,
        private val incoming: List<Pair<String, ByteArray>>,
    ) : ImportRead {

        val preview: ImportPreview get() = merged.preview

        private var spent = false

        /** Performs the merge. The vault is written once, at the end, or not at all. */
        fun apply() {
            check(!spent) { "this import was already applied or discarded" }
            spent = true
            try {
                session.applyMerge(merged, fileKey, incoming)
            } finally {
                fileKey.destroy()
            }
        }

        /** Changes nothing, and lets go of the file's key. */
        fun discard() {
            spent = true
            fileKey.destroy()
        }
    }

    /**
     * The passphrase was wrong, or the file was edited. One outcome, as everywhere else:
     * saying which would tell an attacker whether their forgery was structurally sound.
     */
    data object WrongPassphrase : ImportRead

    /** Provably broken, proved without a key. */
    class Damaged(val what: String, val offset: Int) : ImportRead

    /** Written by a version this build cannot read. */
    class Unsupported(val container: Int, val schema: Int, val minSchema: Int) : ImportRead

    data object NotAVault : ImportRead

    /**
     * The file opened, and the attachments it carries are not the ones it says it carries.
     *
     * Refused rather than imported in part. Everything in it is authentic — but something has
     * been removed from it since it was written, and importing "most of" a backup quietly is
     * how someone finds out years later that a scan they were relying on is not there.
     */
    data object Incomplete : ImportRead
}
