package com.passmanager.security

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopPairingSessionTest {

    private lateinit var session: DesktopPairingSession

    @Before
    fun setup() {
        session = DesktopPairingSession()
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
    fun `rate limiter enforces cooldown between passwords`() {
        activateSessionForTest()
        session.recordPasswordSent("Item1")
        assertFalse(session.canSendPassword())
    }

    @Test
    fun `rate limiter enforces max passwords per session`() {
        activateSessionForTest()
        repeat(DesktopPairingSession.MAX_PW_PER_SESSION - 1) {
            session.recordPasswordSent("Item$it")
            setLastPasswordRequestTimeMsForTest(0L)
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

    private fun setLastPasswordRequestTimeMsForTest(value: Long) {
        val field = DesktopPairingSession::class.java.getDeclaredField("lastPasswordRequestTimeMs")
        field.isAccessible = true
        field.setLong(session, value)
    }
}
