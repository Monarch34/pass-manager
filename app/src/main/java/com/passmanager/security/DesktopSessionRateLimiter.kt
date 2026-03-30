package com.passmanager.security

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class DesktopSessionRateLimiter(
    private val maxPasswords: Int,
    private val passwordCooldownMs: Long,
    private val vaultListCooldownMs: Long
) {
    private val passwordsSent = AtomicInteger(0)
    private val lastPasswordRequestMs = AtomicLong(0L)
    private val lastVaultListRequestMs = AtomicLong(0L)

    val sentCount: Int get() = passwordsSent.get()

    fun canSendPassword(): Boolean {
        if (passwordsSent.get() >= maxPasswords) return false
        return System.currentTimeMillis() - lastPasswordRequestMs.get() >= passwordCooldownMs
    }

    fun recordPasswordSent(): Int {
        lastPasswordRequestMs.set(System.currentTimeMillis())
        return passwordsSent.incrementAndGet()
    }

    fun canAcceptVaultListRequest(): Boolean {
        return System.currentTimeMillis() - lastVaultListRequestMs.get() >= vaultListCooldownMs
    }

    fun recordVaultListRequest() {
        lastVaultListRequestMs.set(System.currentTimeMillis())
    }

    fun reset() {
        passwordsSent.set(0)
        lastPasswordRequestMs.set(0L)
        lastVaultListRequestMs.set(0L)
    }
}
