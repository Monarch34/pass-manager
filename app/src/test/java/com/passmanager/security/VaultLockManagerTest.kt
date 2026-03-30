package com.passmanager.security

import com.passmanager.domain.model.LockState
import com.passmanager.domain.port.AppSettingsPort
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import io.mockk.every
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VaultLockManagerTest {

    private lateinit var sessionManager: SessionManager
    private lateinit var appSettings: AppSettingsPort
    private lateinit var manager: VaultLockManager

    @Before
    fun setup() {
        sessionManager = SessionManager()
        appSettings = mockk()
        every { appSettings.autoLockTimeoutSeconds } returns flowOf(300)
        manager = VaultLockManager(sessionManager, appSettings)
    }

    @Test
    fun `initial state is ColdLocked`() {
        assertEquals(LockState.ColdLocked, manager.lockState.value)
    }

    @Test
    fun `unlock stores key and transitions to Unlocked`() {
        val key = ByteArray(32) { it.toByte() }
        manager.unlock(key)
        assertEquals(LockState.Unlocked, manager.lockState.value)
    }

    @Test(expected = IllegalStateException::class)
    fun `requireUnlockedKey throws when locked`() {
        manager.requireUnlockedKey()
    }

    @Test
    fun `requireUnlockedKey returns copy not same reference`() {
        val key = ByteArray(32) { it.toByte() }
        manager.unlock(key)

        val returned = manager.requireUnlockedKey()

        assertNotSame(key, returned)
        assertTrue(key.contentEquals(returned))
    }

    @Test
    fun `lock transitions to WarmLocked when session has been unlocked before`() {
        sessionManager.recordSuccessfulUnlock()
        val key = ByteArray(32) { it.toByte() }
        manager.unlock(key)

        manager.lock()

        assertEquals(LockState.WarmLocked, manager.lockState.value)
    }

    @Test
    fun `lock transitions to ColdLocked when session has never unlocked`() {
        val key = ByteArray(32) { it.toByte() }
        manager.unlock(key)

        // sessionManager.hasUnlockedThisSession is still false (unlock() on manager
        // does NOT record session — that is done by UnlockWithPassphraseUseCase)
        manager.lock()

        assertEquals(LockState.ColdLocked, manager.lockState.value)
    }

    @Test
    fun `forceColdLock transitions to ColdLocked and invalidates session`() {
        sessionManager.recordSuccessfulUnlock()
        val key = ByteArray(32) { it.toByte() }
        manager.unlock(key)
        assertTrue(sessionManager.hasUnlockedThisSession)

        manager.forceColdLock()

        assertEquals(LockState.ColdLocked, manager.lockState.value)
        assertTrue(!sessionManager.hasUnlockedThisSession)
    }

    @Test
    fun `requireUnlockedKey throws after lock`() {
        manager.unlock(ByteArray(32))
        manager.lock()

        try {
            manager.requireUnlockedKey()
            throw AssertionError("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // expected
        }
    }
}
