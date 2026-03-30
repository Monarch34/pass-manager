package com.passmanager.domain.model

/**
 * Observable vault lock state exposed to UI and navigation layers.
 *
 * Intentionally does NOT carry the vault key — only indicates whether
 * the vault is unlocked. The actual key is only accessible through
 * [com.passmanager.domain.port.VaultKeyProvider.requireUnlockedKey],
 * which returns a defensive copy.
 */
sealed interface LockState {
    data object ColdLocked : LockState
    data object WarmLocked : LockState
    data object Unlocked : LockState
}
