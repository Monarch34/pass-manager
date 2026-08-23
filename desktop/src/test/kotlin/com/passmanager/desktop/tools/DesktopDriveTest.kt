package com.passmanager.desktop.tools

import com.passmanager.desktop.server.DesktopSessionManager
import com.passmanager.desktop.server.DesktopSessionState
import com.passmanager.desktop.server.PairingServer
import com.passmanager.desktop.ui.Strings
import com.passmanager.protocol.ItemSummary
import com.passmanager.protocol.SecureRequest
import com.passmanager.protocol.SecureResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end drive of the desktop through a real pairing session, using [FakePhone].
 *
 * These are the states behind the two screens that were previously untestable: the desktop
 * only leaves `WaitingForPhone` when a phone completes the handshake, and only reaches
 * `Connected` — the state `Main.kt` renders as `VaultBrowserScreen` — after the code round
 * trip. Everything here goes over a real loopback socket against the shipped
 * [PairingServer]; nothing in `desktop/src/main` is modified or bypassed.
 *
 * Determinism: no sleeps. Every wait is a `withTimeout` around a `StateFlow` predicate, so a
 * regression fails with a timeout on a named condition instead of flaking on a slow machine.
 * `127.0.0.1` keeps the socket off the LAN while [PairingServer] picks its own free port.
 */
class DesktopDriveTest {

    private lateinit var sessionManager: DesktopSessionManager
    private lateinit var server: PairingServer
    private lateinit var pump: DesktopResponsePump
    private lateinit var phone: FakePhone

    @BeforeTest
    fun setUp() {
        sessionManager = DesktopSessionManager()
        server = PairingServer(sessionManager, LOOPBACK)

        // Same wiring Main.kt installs before starting the server. Without it the session
        // manager's heartbeat has no transport and a finished session never rotates the QR,
        // so the harness would not be driving the shipped behaviour.
        sessionManager.onSessionEnded = {
            server.generateSession()
            sessionManager.resetToWaiting()
        }
        sessionManager.onHeartbeatNeeded = {
            server.sendToPhone(SecureRequest.Heartbeat())
        }

        server.start()
        awaitServerReady(server.port)

        pump = DesktopResponsePump(server, sessionManager)
        pump.start()
        phone = FakePhone(server.qrContent.value)
    }

    @AfterTest
    fun tearDown() {
        // Guarded: a startup failure in setUp leaves the later fields unassigned, and an
        // uninitialized-field crash here would hide the real cause.
        if (::phone.isInitialized) phone.close()
        if (::pump.isInitialized) pump.stop()
        if (::server.isInitialized) server.close()
        if (::sessionManager.isInitialized) sessionManager.close()
    }

    // ---- Pairing and verification ----

    @Test
    fun `pairing brings the desktop to the verify screen`() = runBlocking {
        val paired = phone.pair()

        val verifying = awaitState<DesktopSessionState.VerifyingCode>()

        assertNotNull(sessionManager.channel, "the handshake did not install an encrypted channel")
        // The desktop derived the fingerprint from its own half of the exchange. Matching
        // the phone's independently computed value is the assertion that the whole
        // ECDH + HKDF path agreed — a MITM or a salt-ordering bug would show up right here.
        assertEquals(paired.safetyNumber, verifying.safetyNumber)
        assertEquals(8, verifying.safetyNumber.length)
        // VerifyScreen renders this counter, so it is part of the screen's contract.
        assertEquals(3, verifying.attemptsRemaining)
    }

    @Test
    fun `the correct code brings the desktop to the vault browser state`() = runBlocking {
        val paired = phone.pair()
        awaitState<DesktopSessionState.VerifyingCode>()

        // The phone waits for the request first, then the desktop "types" the code —
        // exactly what Main.kt does from VerifyScreen's onCodeSubmitted.
        val answering = launch { phone.sendVerifyOk(VERIFY_CODE) }
        assertTrue(
            server.sendToPhone(SecureRequest.Verify(VERIFY_CODE)),
            "the desktop could not send the verify request"
        )
        answering.join()

        // Connected is the state Main.kt renders as VaultBrowserScreen.
        val connected = awaitState<DesktopSessionState.Connected>()
        assertEquals(paired.safetyNumber, connected.safetyNumber)
    }

    // ---- Vault traffic ----

    @Test
    fun `the vault list from the phone reaches the desktop`() = runBlocking {
        val items = listOf(
            ItemSummary(id = "1", title = "GitHub", url = "https://github.com"),
            ItemSummary(id = "2", title = "Fastmail", url = "https://fastmail.com"),
            ItemSummary(id = "3", title = "Bank", url = "https://example-bank.test", category = "finance")
        )
        val serving = connectPhone { request ->
            if (request is SecureRequest.ListItems) SecureResponse.Items(items) else null
        }

        assertTrue(server.sendToPhone(SecureRequest.ListItems), "the desktop could not ask for the list")

        val received = withTimeout(TIMEOUT_MS) { sessionManager.items.first { it.isNotEmpty() } }
        assertEquals(items.map { it.id }, received.map { it.id })
        assertEquals(items.map { it.title }, received.map { it.title })

        serving.cancelAndJoin()
    }

    @Test
    fun `a password request reaches the phone and updates the clipboard status`() = runBlocking {
        val serving = connectPhone { request ->
            when (request) {
                // A fresh copy per request: the desktop's clipboard path zeroes the array
                // it is handed, so a shared buffer would be blank on a second request.
                is SecureRequest.GetPassword ->
                    SecureResponse.Password(request.itemId, PASSWORD.toByteArray())
                else -> null
            }
        }

        assertTrue(
            server.sendToPhone(SecureRequest.GetPassword(PASSWORD_ITEM_ID)),
            "the desktop could not ask for the password"
        )

        val status = withTimeout(TIMEOUT_MS) { sessionManager.clipboardStatus.first { it != null } }
        assertEquals(CLIPBOARD_COPIED_STATUS, status)
        assertEquals(PASSWORD, pump.lastCopiedPassword)
        assertEquals(PASSWORD_ITEM_ID, pump.lastPasswordItemId)

        serving.cancelAndJoin()
    }

    @Test
    fun `a 500 item vault list fits in one frame and reaches the desktop`() = runBlocking {
        // The phone serialises the whole vault into a single SecureResponse.Items frame, so
        // MAX_FRAME_SIZE_BYTES is a hard ceiling on vault size rather than a transport
        // detail. This pins both halves of that: the envelope stays under the cap, and the
        // desktop accepts the list instead of tearing the session down with
        // "Vault list too large to transfer".
        val items = (1..LARGE_ITEM_COUNT).map { index ->
            ItemSummary(
                id = "item-$index",
                title = "Account number $index",
                url = "https://service-$index.example.com/login",
                category = "login"
            )
        }
        val serving = connectPhone { request ->
            if (request is SecureRequest.ListItems) SecureResponse.Items(items) else null
        }

        assertTrue(server.sendToPhone(SecureRequest.ListItems), "the desktop could not ask for the list")

        val received = withTimeout(TIMEOUT_MS) {
            sessionManager.items.first { it.size == LARGE_ITEM_COUNT }
        }
        assertEquals(items.last().title, received.last().title)

        val frameSize = phone.lastSentFrameSize
        assertTrue(frameSize > 0, "no sealed frame was recorded")
        assertTrue(
            frameSize < PairingServer.MAX_FRAME_SIZE_BYTES,
            "a $LARGE_ITEM_COUNT item list sealed to $frameSize bytes, over the " +
                "${PairingServer.MAX_FRAME_SIZE_BYTES} byte frame cap"
        )

        // A rejected frame would have moved the desktop to Disconnected.
        val state = sessionManager.state.value
        assertTrue(state is DesktopSessionState.Connected, "expected Connected, got $state")

        serving.cancelAndJoin()
    }

    // ---- Helpers ----

    /**
     * Suspends until the session state is a [T], or fails the test on timeout. Replaces
     * sleeping on a guessed round-trip time: the predicate is the actual condition under
     * test, so a hang reports which state never arrived.
     */
    private suspend inline fun <reified T : DesktopSessionState> awaitState(): T =
        withTimeout(TIMEOUT_MS) { sessionManager.state.first { it is T } as T }

    /**
     * Pairs, walks the desktop through code verification and leaves a serve loop running so
     * the session stays alive (heartbeats included) for the rest of the test. The returned
     * [Job] must be cancelled by the caller.
     */
    private suspend fun CoroutineScope.connectPhone(
        handler: (SecureRequest) -> SecureResponse? = { null }
    ): Job {
        val paired = phone.pair()
        val verifying = awaitState<DesktopSessionState.VerifyingCode>()
        assertEquals(paired.safetyNumber, verifying.safetyNumber)

        val answering = launch { phone.sendVerifyOk(VERIFY_CODE) }
        assertTrue(
            server.sendToPhone(SecureRequest.Verify(VERIFY_CODE)),
            "the desktop could not send the verify request"
        )
        answering.join()
        awaitState<DesktopSessionState.Connected>()

        return launch { phone.serveOnRequest(handler) }
    }

    /**
     * `start(wait = false)` returns before the engine has necessarily bound the port, so the
     * socket is polled instead of sleeping on a guessed startup time.
     */
    private fun awaitServerReady(port: Int) {
        val deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS
        var lastFailure: IOException? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                Socket().use { it.connect(InetSocketAddress(LOOPBACK, port), CONNECT_TIMEOUT_MS) }
                return
            } catch (e: IOException) {
                lastFailure = e
                Thread.sleep(STARTUP_POLL_MS)
            }
        }
        throw IllegalStateException("Pairing server never accepted on port $port", lastFailure)
    }

    companion object {
        private const val LOOPBACK = "127.0.0.1"

        /** Any 6-digit value: the phone generates it for real, the driver owns it here. */
        private const val VERIFY_CODE = "424242"

        private const val PASSWORD_ITEM_ID = "2"
        private const val PASSWORD = "correct-horse-battery-staple"

        /** Roughly a browser password-store import; well inside the frame cap by design. */
        private const val LARGE_ITEM_COUNT = 500

        /** Upper bound for a loopback round-trip; a hang fails the test instead of stalling. */
        private const val TIMEOUT_MS = 10_000L
        private const val STARTUP_TIMEOUT_MS = 15_000L
        private const val STARTUP_POLL_MS = 25L
        private const val CONNECT_TIMEOUT_MS = 500
    }
}

/**
 * The one status string Main.kt writes as a literal rather than through [Strings]. Shared by
 * the pump and the assertions so the two cannot drift apart within this file — but it still
 * has to be kept in step with Main.kt by hand.
 */
private const val CLIPBOARD_COPIED_STATUS = "Copied! Clipboard clears in 30s"

/**
 * The desktop's response loop, as `Main.kt` runs it.
 *
 * `Main.kt` keeps this dispatch inside `application { }` and a private `handlePhoneResponse`,
 * neither of which a test can reach, so the branch table is mirrored here. Two deliberate
 * differences, both about the test environment rather than the protocol:
 *
 *  - the clipboard is recorded instead of written. `ClipboardManager` goes through
 *    `Toolkit.getDefaultToolkit()`, which throws on a headless JVM; the password bytes are
 *    zeroed here just as the real manager zeroes them.
 *  - there is no `Strings`-based UI text beyond the status the assertions check.
 *
 * If `handlePhoneResponse` ever changes, this copy has to change with it — see the report
 * note about extracting it into a testable top-level function.
 */
private class DesktopResponsePump(
    private val server: PairingServer,
    private val sessionManager: DesktopSessionManager
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Last password the desktop would have put on the clipboard. */
    @Volatile
    var lastCopiedPassword: String? = null
        private set

    @Volatile
    var lastPasswordItemId: String? = null
        private set

    fun start() {
        scope.launch {
            while (isActive) {
                val response = server.receiveFromPhone() ?: continue
                handle(response)
            }
        }
    }

    fun stop() {
        scope.cancel()
    }

    private fun handle(response: SecureResponse) {
        when (response) {
            is SecureResponse.VerifyOk -> {
                val expected = (sessionManager.state.value as? DesktopSessionState.VerifyingCode)
                    ?.safetyNumber ?: ""
                if (expected.isNotEmpty() && response.safetyNumber != expected) {
                    sessionManager.onDisconnected(
                        "Security error: safety number mismatch — possible MITM attack"
                    )
                } else {
                    sessionManager.onCodeVerified(response.safetyNumber.ifEmpty { expected })
                }
            }
            is SecureResponse.VerifyFailed -> {
                sessionManager.onVerifyFailed(response.error, response.attemptsRemaining)
            }
            is SecureResponse.Items -> {
                sessionManager.onItemsReceived(response.items)
            }
            is SecureResponse.Password -> {
                lastCopiedPassword = response.password.decodeToString()
                response.password.fill(0)
                lastPasswordItemId = response.itemId
                sessionManager.onPasswordReceived(response.itemId)
                sessionManager.setClipboardStatus(CLIPBOARD_COPIED_STATUS)
            }
            is SecureResponse.HeartbeatAck -> {
                sessionManager.onHeartbeatAckReceived()
            }
            is SecureResponse.DisconnectAck -> {
                sessionManager.onDisconnected("Phone disconnected")
            }
            is SecureResponse.Error -> {
                // Raw server error text is never surfaced — it may carry internal details.
                sessionManager.setClipboardStatus(Strings.ERROR_GENERIC)
            }
            is SecureResponse.RateLimited -> {
                val message = response.message.trim()
                sessionManager.setClipboardStatus(
                    if (message.isNotEmpty()) message else Strings.RATE_LIMITED_FALLBACK
                )
            }
        }
    }
}
