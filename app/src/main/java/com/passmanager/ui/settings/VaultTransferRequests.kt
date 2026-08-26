package com.passmanager.ui.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class VaultTransferKind { EXPORT, IMPORT }

/** A document the user picked that still needs a passphrase before anything can happen to it. */
data class PendingVaultTransfer(val kind: VaultTransferKind, val uri: String)

/**
 * Carries a picked document across a lock.
 *
 * The system document picker backgrounds the app, which starts the auto-lock timer, so the
 * `ActivityResult` can easily arrive at a locked vault — and locking pops the whole main graph,
 * taking the Settings ViewModel with it. This holder is application-scoped so the chosen Uri
 * survives that, and the passphrase is only ever asked for once the vault is unlocked again.
 */
@Singleton
class VaultTransferRequests @Inject constructor() {

    private val _pending = MutableStateFlow<PendingVaultTransfer?>(null)
    val pending: StateFlow<PendingVaultTransfer?> = _pending.asStateFlow()

    fun stash(kind: VaultTransferKind, uri: String) {
        _pending.value = PendingVaultTransfer(kind, uri)
    }

    fun clear() {
        _pending.value = null
    }
}
