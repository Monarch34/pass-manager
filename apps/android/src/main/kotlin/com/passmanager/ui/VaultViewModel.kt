package com.passmanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.passmanager.crypto.Secret
import android.net.Uri
import com.passmanager.data.AndroidBlobFileStore
import com.passmanager.data.Thumbnails
import com.passmanager.data.AndroidVaultFileStore
import com.passmanager.data.BiometricVaultKey
import javax.crypto.Cipher
import com.passmanager.domain.item.ItemId
import com.passmanager.domain.item.VaultItem
import androidx.lifecycle.viewModelScope
import com.passmanager.vault.ImportPreview
import com.passmanager.vault.ImportRead
import com.passmanager.vault.UnlockResult
import com.passmanager.vault.Vault
import com.passmanager.vault.Attachment
import com.passmanager.vault.VaultSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The application's state, which is almost entirely `core:vault`'s state.
 *
 * Everything about what a vault holds and what changing it means lives in the shared module;
 * this holds the open session, turns its outcomes into something a screen can render, and
 * decides when to let go of the key.
 */
class VaultViewModel(application: Application) : AndroidViewModel(application) {

    enum class Phase { Empty, Locked, Unlocked }

    private val store = AndroidVaultFileStore(application)
    private val blobs = AndroidBlobFileStore(application)
    private val biometrics = BiometricVaultKey(application)
    private var session: VaultSession? = null

    val biometricAvailability get() = biometrics.availability()
    val biometricsEnabled: Boolean get() = biometrics.isEnabled()

    var phase by mutableStateOf(if (store.exists()) Phase.Locked else Phase.Empty)
        private set

    var items by mutableStateOf<List<VaultItem>>(emptyList())
        private set

    var failure by mutableStateOf<String?>(null)
        private set

    var query by mutableStateOf("")

    /**
     * Set while a system screen this app asked for is in front — the file picker.
     *
     * Leaving the app locks it, and launching the document picker stops the activity, so
     * without this the vault would lock the instant "Add attachment" was tapped and the file
     * would come back to a session that no longer exists. Attaching would have been
     * impossible rather than merely awkward.
     *
     * It is a real, narrow carve-out: if the user walks away while the picker is open, the
     * vault stays unlocked behind it. Cleared the moment the result arrives, so the window is
     * one file choice long.
     */
    var awaitingPicker by mutableStateOf(false)
        private set

    fun pickerOpened() {
        awaitingPicker = true
    }

    fun pickerClosed() {
        awaitingPicker = false
    }

    val visibleItems: List<VaultItem>
        get() = session?.takeIf { !it.isLocked }?.search(query) ?: emptyList()

    fun create(passphrase: String) = guard {
        session = Secret.ofUtf8(passphrase).use { Vault.create(store, blobs, it) }
        onOpened()
    }

    fun unlock(passphrase: String) = guard {
        when (val result = Secret.ofUtf8(passphrase).use { Vault.unlock(store, blobs, it) }) {
            is UnlockResult.Unlocked -> {
                session = result.session
                onOpened()
            }
            // Wrong passphrase and a tampered file are one outcome, and saying so is the
            // point: claiming to know which would tell an attacker whether their forgery
            // was structurally correct.
            UnlockResult.WrongPassphrase -> failure = "That passphrase does not open this vault."
            is UnlockResult.Damaged ->
                failure = "This vault is damaged at byte ${result.offset}: ${result.what}."
            is UnlockResult.Unsupported ->
                failure = "This vault was written by a newer version of the app."
            UnlockResult.NotAVault -> failure = "That file is not a PassManager vault."
            UnlockResult.NoVault -> phase = Phase.Empty
        }
    }

    /**
     * A cipher to authenticate before storing the vault key, or null if there is no open
     * vault to store. The caller shows the prompt and returns the authenticated cipher to
     * [completeEnableBiometrics].
     */
    fun cipherToEnableBiometrics(): Cipher? =
        if (session == null) null else runCatching { biometrics.cipherForStoring() }.getOrNull()

    fun completeEnableBiometrics(cipher: Cipher) = guard {
        val open = session ?: error("the vault is locked")
        open.useVaultKey { key -> biometrics.store(cipher, key.toByteArray()) }
    }

    /** A cipher to authenticate before reading the vault key back. */
    fun cipherToUnlock(): Cipher? = biometrics.cipherForLoading().getOrElse {
        // An invalidated key has already removed itself; say why rather than silently
        // dropping the option the user just tapped.
        failure = "Fingerprint or face enrolment changed, so the saved key was discarded. " +
            "Unlock with your passphrase to set it up again."
        null
    }

    fun completeUnlockWithBiometrics(cipher: Cipher) = guard {
        val key = Secret.adopt(biometrics.load(cipher))
        when (val result = Vault.unlockWithVaultKey(store, blobs, key)) {
            is UnlockResult.Unlocked -> {
                session = result.session
                onOpened()
            }
            else -> {
                key.destroy()
                // A stored key that no longer opens this vault fails every future attempt
                // while still looking like an option, so it is removed rather than kept.
                biometrics.remove()
                failure = "The saved key does not open this vault. Use your passphrase."
            }
        }
    }

    fun disableBiometrics() {
        biometrics.remove()
    }

    fun biometricFailed(message: String?) {
        if (message != null) failure = message
    }

    fun lock() {
        // An export or a half-agreed import is vault contents held outside the vault. Locking
        // means the key stops existing, and these must not outlive it.
        discardImport()
        discardExport()
        session?.lock()
        session = null
        items = emptyList()
        query = ""
        failure = null
        phase = Phase.Locked
    }

    /**
     * Deletes the vault, which is the only honest answer to a forgotten passphrase: nothing
     * here can open one without its key.
     */
    fun startOver() {
        session?.lock()
        session = null
        items = emptyList()
        Vault.destroy(store, blobs)
        // The stored key would otherwise outlive the vault it opens, and would then fail
        // against whatever vault is created next while still offering itself as a way in.
        biometrics.remove()
        failure = null
        phase = Phase.Empty
    }

    fun save(item: VaultItem) = guard {
        session?.save(item)
        items = session?.items.orEmpty()
    }

    fun delete(id: ItemId) = guard {
        session?.delete(id, System.currentTimeMillis())
        items = session?.items.orEmpty()
    }

    fun item(id: String): VaultItem? = items.firstOrNull { it.id.value == id }

    fun dismissFailure() {
        failure = null
    }

    // ── Leaving the device, and coming back ─────────────────────────────────

    /**
     * Set while an export or an import is deriving a key.
     *
     * Argon2 at export cost is several seconds of deliberate work, so it cannot run on the
     * thread that draws the screen and the screen has to say something while it does not.
     */
    var busy by mutableStateOf(false)
        private set

    /** The bytes of an export, waiting for the user to choose where they go. */
    var pendingExport by mutableStateOf<ByteArray?>(null)
        private set

    /** An import that has been read and previewed, waiting to be agreed to or refused. */
    var pendingImport by mutableStateOf<ImportRead.Ready?>(null)
        private set

    val importPreview: ImportPreview? get() = pendingImport?.preview

    fun export(passphrase: String) {
        val open = session ?: return
        busy = true
        viewModelScope.launch {
            val file = runCatching {
                withContext(Dispatchers.Default) {
                    Secret.ofUtf8(passphrase).use { open.export(it) }
                }
            }
            busy = false
            file.onSuccess { pendingExport = it }
                .onFailure { failure = it.message ?: "The export could not be written." }
        }
    }

    /** Writes the export the user has now chosen a place for, and forgets it either way. */
    fun writeExport(uri: Uri) = guard {
        val bytes = pendingExport ?: return@guard
        getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: error("that location could not be written to")
        discardExport()
    }

    /**
     * Drops the export, whether it was saved or abandoned.
     *
     * It is the whole vault in memory, sealed but decryptable by whoever knows the passphrase
     * the user just typed. There is no reason to keep it a moment past the save.
     */
    fun discardExport() {
        pendingExport = null
    }

    fun readImport(uri: Uri, passphrase: String) {
        val open = session ?: return
        busy = true
        viewModelScope.launch {
            val outcome = runCatching {
                val bytes = getApplication<Application>().contentResolver
                    .openInputStream(uri)?.use { it.readBytes() }
                    ?: error("that file could not be read")
                withContext(Dispatchers.Default) {
                    Secret.ofUtf8(passphrase).use { open.read(bytes, it, System.currentTimeMillis()) }
                }
            }
            busy = false
            outcome.onFailure { failure = it.message ?: "That file could not be read." }
            outcome.onSuccess { read ->
                when (read) {
                    is ImportRead.Ready -> pendingImport = read
                    // The same answer for both, for the same reason as an unlock: saying
                    // which would tell an attacker whether their forgery was sound.
                    ImportRead.WrongPassphrase ->
                        failure = "That passphrase does not open this file."
                    is ImportRead.Damaged ->
                        failure = "This file is damaged at byte ${read.offset}: ${read.what}."
                    is ImportRead.Unsupported ->
                        failure = "This file was written by a newer version of the app."
                    ImportRead.NotAVault -> failure = "That is not a PassManager vault file."
                    ImportRead.Incomplete ->
                        failure = "This file is missing attachments it says it carries, so " +
                            "importing it would quietly lose them. Use an unaltered copy."
                }
            }
        }
    }

    fun applyImport() = guard {
        pendingImport?.apply()
        pendingImport = null
        items = session?.items.orEmpty()
    }

    fun discardImport() {
        pendingImport?.discard()
        pendingImport = null
    }

    // ── Attachments ─────────────────────────────────────────────────────────

    fun attachments(item: VaultItem): List<Attachment> =
        runCatching { session?.attachments(item.id).orEmpty() }.getOrDefault(emptyList())

    /**
     * Reads a file the user picked and seals it onto an item.
     *
     * The bytes go straight from the content resolver into a Secret and are never held as
     * anything else, so the only copy this application makes is one it can erase.
     */
    fun attach(item: VaultItem, uri: Uri) = guard {
        val open = session ?: error("the vault is locked")
        val resolver = getApplication<Application>().contentResolver

        val name = resolver.query(uri, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        } ?: "attachment"

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("that file could not be read")
        // Made before the bytes are sealed away, because afterwards making one would mean
        // decrypting the whole attachment again just to draw a row.
        val thumbnail = Thumbnails.of(bytes)
        Secret.adopt(bytes).use { content ->
            open.attach(
                itemId = item.id,
                filename = name,
                mimeType = resolver.getType(uri) ?: "application/octet-stream",
                content = content,
                createdAt = System.currentTimeMillis(),
                thumbnail = thumbnail,
            )
        }
    }

    /** The decrypted bytes, for sharing or saving. The caller must destroy the result. */
    fun openAttachment(id: String): Secret? = session?.openAttachment(id)

    fun deleteAttachment(id: String) = guard { session?.deleteAttachment(id) }

    private fun onOpened() {
        // Files left by a delete that was interrupted are inert, but they are still the
        // user's data sitting on disk with nothing pointing at them. Cleared once, here,
        // rather than during any operation the user is waiting on.
        runCatching { session?.sweepOrphanedAttachments() }
        items = session?.items.orEmpty()
        failure = null
        phase = Phase.Unlocked
    }

    /**
     * Turns a thrown failure into something the screen can show.
     *
     * Deliberately narrow in what it reports: the message of an exception raised while
     * writing a vault can name a path but never its contents, and nothing here logs it.
     */
    private inline fun guard(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            failure = error.message ?: "Something went wrong."
        }
    }
}
