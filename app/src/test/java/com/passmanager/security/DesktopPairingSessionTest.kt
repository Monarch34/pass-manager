package com.passmanager.security

import com.passmanager.domain.model.DesktopPairingConstants
import com.passmanager.domain.model.PairingSessionState
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import io.mockk.mockk
import io.mockk.every
import javax.inject.Provider
import com.passmanager.agent.DesktopPairingClient

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopPairingSessionTest {

    private lateinit var session: DesktopPairingSession

    @Before
    fun setup() {
        val client = mockk<DesktopPairingClient>(relaxed = true)
        val provider = mockk<Provider<DesktopPairingClient>>()
        every { provider.get() } returns client
        session = DesktopPairingSession(provider)
    }

    @Test
    fun `initial state is Idle`() {
        assertEquals(PairingSessionState.Idle, session.state.value)
    }

    @Test
    fun `canSendPassword returns false until session is active`() {
        assertFalse(session.canSendPassword())
    }

    @Test
    fun `vault list refresh from desktop is rate limited while active`() {
        assertFalse(session.canAcceptVaultListRequestFromDesktop())
        activateSessionForTest()
        assertTrue(session.canAcceptVaultListRequestFromDesktop())
        session.recordVaultListRequestFromDesktop()
        assertFalse(session.canAcceptVaultListRequestFromDesktop())
        setRateLimiterAtomicLong("lastVaultListRequestMs", 0L)
        assertTrue(session.canAcceptVaultListRequestFromDesktop())
    }

    @Test
    fun `rate limiter enforces cooldown between passwords`() {
        activateSessionForTest()
        session.recordPasswordSent("Item1")
        assertFalse(session.canSendPassword())
    }

    @Test
    fun `rate limiter enforces max passwords per session`() {
        activateSessionForTest()
        repeat(DesktopPairingConstants.MAX_PW_PER_SESSION - 1) {
            session.recordPasswordSent("Item$it")
            setRateLimiterAtomicLong("lastPasswordRequestMs", 0L)
            assertTrue(session.canSendPassword())
        }
        session.recordPasswordSent("Item-final")
        assertFalse(session.canSendPassword())
    }

    @Test
    fun `endSession transitions to Ended`() = runTest {
        session.endSession("test reason")
        val state = session.state.value
        assertTrue(state is PairingSessionState.Ended)
        assertEquals("test reason", (state as PairingSessionState.Ended).reason)
    }

    @Test
    fun `recordPasswordSent updates Active state`() {
        activateSessionForTest()
        session.recordPasswordSent("GitHub")
        val state = session.state.value
        if (state is PairingSessionState.Active) {
            assertEquals(1, state.passwordsSent)
            assertEquals("GitHub", state.lastItemTitle)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun activateSessionForTest() {
        val ipField = DesktopPairingSession::class.java.getDeclaredField("desktopIp")
        ipField.isAccessible = true
        ipField.set(session, "127.0.0.1")

        val stateField = DesktopPairingSession::class.java.getDeclaredField("_state")
        stateField.isAccessible = true
        val stateFlow = stateField.get(session) as MutableStateFlow<PairingSessionState>
        stateFlow.value = PairingSessionState.Active(
            desktopIp = "127.0.0.1",
            passwordsSent = 0,
            lastItemTitle = null
        )
    }

    private fun getRateLimiter(): DesktopSessionRateLimiter {
        val field = DesktopPairingSession::class.java.getDeclaredField("rateLimiter")
        field.isAccessible = true
        return field.get(session) as DesktopSessionRateLimiter
    }

    private fun setRateLimiterAtomicLong(fieldName: String, value: Long) {
        val rateLimiter = getRateLimiter()
        val field = DesktopSessionRateLimiter::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        (field.get(rateLimiter) as AtomicLong).set(value)
    }
}
