package com.passmanager.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.passmanager.crypto.Secret
import com.passmanager.data.AndroidVaultFileStore
import com.passmanager.domain.item.ItemId
import com.passmanager.domain.item.VaultItem
import com.passmanager.vault.UnlockResult
import com.passmanager.vault.Vault
import com.passmanager.vault.VaultSession

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
    private var session: VaultSession? = null

    var phase by mutableStateOf(if (store.exists()) Phase.Locked else Phase.Empty)
        private set

    var items by mutableStateOf<List<VaultItem>>(emptyList())
        private set

    var failure by mutableStateOf<String?>(null)
        private set

    var query by mutableStateOf("")

    val visibleItems: List<VaultItem>
        get() = session?.takeIf { !it.isLocked }?.search(query) ?: emptyList()

    fun create(passphrase: String) = guard {
        session = Secret.ofUtf8(passphrase).use { Vault.create(store, it) }
        adopt()
    }

    fun unlock(passphrase: String) = guard {
        when (val result = Secret.ofUtf8(passphrase).use { Vault.unlock(store, it) }) {
            is UnlockResult.Unlocked -> {
                session = result.session
                adopt()
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

    fun lock() {
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
        store.delete()
        failure = null
        phase = Phase.Empty
    }

    fun save(item: VaultItem) = guard {
        session?.save(item)
        items = session?.items.orEmpty()
    }

    fun delete(id: ItemId) = guard {
        session?.delete(id)
        items = session?.items.orEmpty()
    }

    fun item(id: String): VaultItem? = items.firstOrNull { it.id.value == id }

    fun dismissFailure() {
        failure = null
    }

    private fun adopt() {
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
