package com.passmanager.security

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks whether the user has completed a full passphrase unlock this process lifetime.
 *
 * This is intentionally NOT persisted. A process kill (force-stop, OOM kill, etc.)
 * resets this to false, which is the cold lock detection mechanism.
 * No disk I/O needed — memory IS the security boundary.
 */
@Singleton
class SessionManager @Inject constructor() {

    @Volatile
    var hasPassphraseUnlockedThisSession: Boolean = false
        private set

    fun recordPassphraseUnlock() {
        hasPassphraseUnlockedThisSession = true
    }
}
