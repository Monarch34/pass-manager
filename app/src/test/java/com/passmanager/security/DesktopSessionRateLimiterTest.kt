package com.passmanager.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class DesktopSessionRateLimiterTest {

    private val MAX_PASSWORDS = 5
    private val COOLDOWN_MS = 500L
    private val VAULT_LIST_COOLDOWN_MS = 1000L

    private lateinit var limiter: DesktopSessionRateLimiter

    @Before
    fun setup() {
        limiter = DesktopSessionRateLimiter(
            maxPasswords = MAX_PASSWORDS,
            passwordCooldownMs = COOLDOWN_MS,
            vaultListCooldownMs = VAULT_LIST_COOLDOWN_MS
        )
    }

    @Test
    fun `fresh limiter allows password send`() {
        assertTrue(limiter.canSendPassword())
    }

    @Test
    fun `cooldown blocks password send immediately after record`() {
        limiter.recordPasswordSent()
        assertFalse(limiter.canSendPassword())
    }

    @Test
    fun `password allowed after cooldown timestamp reset`() {
        limiter.recordPasswordSent()
        assertFalse(limiter.canSendPassword())
        setAtomicLong("lastPasswordRequestMs", 0L)
        assertTrue(limiter.canSendPassword())
    }

    @Test
    fun `max password count blocks sends regardless of cooldown`() {
        repeat(MAX_PASSWORDS) {
            setAtomicLong("lastPasswordRequestMs", 0L)
            limiter.recordPasswordSent()
        }
        setAtomicLong("lastPasswordRequestMs", 0L)
        assertFalse(limiter.canSendPassword())
        assertEquals(MAX_PASSWORDS, limiter.sentCount)
    }

    @Test
    fun `recordPasswordSent returns incremented count`() {
        assertEquals(1, limiter.recordPasswordSent())
        assertEquals(2, limiter.recordPasswordSent())
        assertEquals(3, limiter.recordPasswordSent())
    }

    @Test
    fun `fresh limiter allows vault list request`() {
        assertTrue(limiter.canAcceptVaultListRequest())
    }

    @Test
    fun `vault list cooldown blocks request immediately after record`() {
        limiter.recordVaultListRequest()
        assertFalse(limiter.canAcceptVaultListRequest())
    }

    @Test
    fun `vault list allowed after timestamp reset`() {
        limiter.recordVaultListRequest()
        assertFalse(limiter.canAcceptVaultListRequest())
        setAtomicLong("lastVaultListRequestMs", 0L)
        assertTrue(limiter.canAcceptVaultListRequest())
    }

    @Test
    fun `reset clears all counters and timestamps`() {
        repeat(MAX_PASSWORDS) { limiter.recordPasswordSent() }
        limiter.recordVaultListRequest()

        limiter.reset()

        assertEquals(0, limiter.sentCount)
        assertTrue(limiter.canAcceptVaultListRequest())
        assertTrue(limiter.canSendPassword())
    }

    private fun setAtomicLong(fieldName: String, value: Long) {
        val field = DesktopSessionRateLimiter::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        (field.get(limiter) as AtomicLong).set(value)
    }
}
