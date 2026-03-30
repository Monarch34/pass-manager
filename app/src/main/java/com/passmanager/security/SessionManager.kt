package com.passmanager.security

import com.passmanager.domain.port.UnlockSessionRecorder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionManager @Inject constructor() : UnlockSessionRecorder {

    @Volatile
    var hasUnlockedThisSession: Boolean = false
        private set

    override fun recordSuccessfulUnlock() {
        hasUnlockedThisSession = true
    }

    fun invalidate() {
        hasUnlockedThisSession = false
    }
}
