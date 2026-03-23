package com.passmanager.security

/**
 * Represents the current vault lock state.
 *
 * State machine:
 *   ColdLocked → (passphrase unlock) → Unlocked
 *   Unlocked   → (app background / timeout) → WarmLocked
 *   WarmLocked → (biometric OR passphrase) → Unlocked
 *   ColdLocked → (biometric OR passphrase) → Unlocked
 *
 * [WarmLocked] vs [ColdLocked] affects whether a subsequent [VaultLockManager.lock] keeps
 * the session as warm; biometric unlock is available on the lock screen whenever it is
 * enabled in metadata and the keystore key exists (see [LockViewModel]).
 */
sealed interface LockState {

    /** Fresh process start or session without a successful unlock this process. */
    data object ColdLocked : LockState

    /** Locked after a successful unlock this process; next lock stays warm. */
    data object WarmLocked : LockState

    /**
     * Vault key is in memory and ready for use.
     * toString is redacted to prevent key leakage in logs.
     */
    class Unlocked(val vaultKey: ByteArray) : LockState {
        override fun toString(): String = "Unlocked(vaultKey=[REDACTED])"
        override fun equals(other: Any?) = other is Unlocked && vaultKey.contentEquals(other.vaultKey)
        override fun hashCode() = vaultKey.contentHashCode()
    }
}
