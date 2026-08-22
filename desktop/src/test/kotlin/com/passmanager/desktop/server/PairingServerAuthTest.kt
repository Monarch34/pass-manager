package com.passmanager.desktop.server

import com.passmanager.desktop.crypto.X25519KeyExchange
import com.passmanager.protocol.HandshakeRequest
import com.passmanager.protocol.PairingQrPayload
import com.passmanager.protocol.SecureRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Authentication tests for the desktop server layer: the WebSocket session gate and the
 * handshake ordering.
 *
 * Test shape: `PairingServer.start()` builds its own `embeddedServer`, so the routes are not
 * reachable from `testApplication` without duplicating the whole routing block — and a copied
 * route would test the copy, not the shipped code. The real server is therefore started on the
 * loopback interface and driven with a real Ktor client, which exercises the actual upgrade,
 * the actual header handling and the actual close frames. `127.0.0.1` keeps the socket off the
 * LAN (no firewall prompt, no exposure) while `PairingServer` picks its own free port.
 *
 * Every assertion targets a distinguishable outcome: rejections are matched on the close
 * *reason string*, not just on code 1008, so a test cannot pass because the server refused the
 * connection for some unrelated reason.
 */
class PairingServerAuthTest {

    private lateinit var sessionManager: DesktopSessionManager
    private lateinit var server: PairingServer
    private lateinit var client: HttpClient

    private val json = Json { ignoreUnknownKeys = true }

    @BeforeTest
    fun setUp() {
        sessionManager = DesktopSessionManager()
        // Every field is assigned before the first operation that can fail, so a startup
        // failure surfaces as itself rather than as an uninitialized field in tearDown.
        client = HttpClient(CIO) {
            install(WebSockets)
        }
        server = PairingServer(sessionManager, LOOPBACK)
        server.start()
        awaitServerReady(server.port)
    }

    @AfterTest
    fun tearDown() {
        client.close()
        server.close()
        sessionManager.close()
    }

    // ---- WebSocket authentication ----

    @Test
    fun `websocket with the QR token is accepted`() = runBlocking {
        val token = qrToken()
        assertEquals(HttpStatusCode.OK, completeHandshake(token).first)

        val frames = Channel<Frame>(Channel.UNLIMITED)
        val socket = launch { pumpAuthenticatedSocket(token, frames) }

        // A sealed frame can only come from the send loop, and the send loop only starts
        // after the handler has authenticated the socket. Waiting for one is therefore a
        // deterministic "the connection was accepted" signal — no sleeping on a guess.
        server.sendToPhone(SecureRequest.ListItems)
        val frame = withTimeout(TIMEOUT_MS) { frames.receive() }
        assertTrue(frame is Frame.Binary, "expected a sealed binary frame, got $frame")

        // onSessionConnected() runs before the send loop, so by now it must have fired.
        val state = sessionManager.state.value
        assertTrue(state is DesktopSessionState.VerifyingCode, "expected VerifyingCode, got $state")

        socket.cancelAndJoin()
    }

    @Test
    fun `websocket with a wrong token is rejected with 1008`() = runBlocking {
        val token = qrToken()
        assertEquals(HttpStatusCode.OK, completeHandshake(token).first)

        val reason = connectExpectingRejection(wrongToken(token))

        assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, reason?.code)
        // The channel exists at this point, so "session_not_ready" is ruled out: only the
        // token comparison can produce this reason.
        assertEquals("unauthorized", reason?.message)
        // A failed attempt must not touch session state — the legitimate session survives.
        assertEquals(DesktopSessionState.WaitingForPhone, sessionManager.state.value)
        assertNotNull(sessionManager.channel)
        assertEquals(token, qrToken())
    }

    @Test
    fun `websocket without the session header is rejected with 1008`() = runBlocking {
        val token = qrToken()
        assertEquals(HttpStatusCode.OK, completeHandshake(token).first)

        val reason = connectExpectingRejection(null)

        assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, reason?.code)
        assertEquals("unauthorized", reason?.message)
        assertNotNull(sessionManager.channel)
        assertEquals(DesktopSessionState.WaitingForPhone, sessionManager.state.value)
    }

    @Test
    fun `websocket carrying an Origin header is rejected with 1008`() = runBlocking {
        val token = qrToken()
        assertEquals(HttpStatusCode.OK, completeHandshake(token).first)

        // Correct token, but a browser-style Origin: a real phone client never sends one.
        val reason = connectExpectingRejection(token, origin = "http://evil.example")

        assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, reason?.code)
        assertEquals("origin_not_allowed", reason?.message)
        assertEquals(DesktopSessionState.WaitingForPhone, sessionManager.state.value)
    }

    @Test
    fun `second concurrent websocket is rejected and the first one survives`() = runBlocking {
        val token = qrToken()
        assertEquals(HttpStatusCode.OK, completeHandshake(token).first)

        val frames = Channel<Frame>(Channel.UNLIMITED)
        val socket = launch { pumpAuthenticatedSocket(token, frames) }

        server.sendToPhone(SecureRequest.ListItems)
        val firstFrame = withTimeout(TIMEOUT_MS) { frames.receive() }
        assertTrue(firstFrame is Frame.Binary, "expected a sealed binary frame, got $firstFrame")

        // Same, valid token: the rejection can only come from the single-connection latch.
        val reason = connectExpectingRejection(token)
        assertEquals(CloseReason.Codes.VIOLATED_POLICY.code, reason?.code)
        assertEquals("already_connected", reason?.message)

        // The refused attempt must not have disturbed the live socket: it still receives.
        server.sendToPhone(SecureRequest.ListItems)
        val secondFrame = withTimeout(TIMEOUT_MS) { frames.receive() }
        assertTrue(secondFrame is Frame.Binary, "the live socket stopped receiving: $secondFrame")
        val state = sessionManager.state.value
        assertTrue(state is DesktopSessionState.VerifyingCode, "expected VerifyingCode, got $state")

        socket.cancelAndJoin()
    }

    // ---- Handshake ordering ----

    @Test
    fun `handshake with an invalid token returns 401 and leaves the QR untouched`() = runBlocking {
        val qrBefore = server.qrContent.value
        val token = json.decodeFromString(PairingQrPayload.serializer(), qrBefore).token

        val (status, body) = postHandshake(freshPhonePub(), wrongToken(token))

        assertEquals(HttpStatusCode.Unauthorized, status)
        assertTrue(body.contains("invalid_session"), "unexpected body: $body")
        // The point of the ordering fix: an unauthenticated request must not be able to
        // burn the QR the user is scanning right now. Same QR, same token, still usable.
        assertEquals(qrBefore, server.qrContent.value)
        assertNull(sessionManager.channel)
        assertEquals(DesktopSessionState.WaitingForPhone, sessionManager.state.value)
    }

    @Test
    fun `an invalid token does not consume the single-use session`() = runBlocking {
        val token = qrToken()

        assertEquals(HttpStatusCode.Unauthorized, postHandshake(freshPhonePub(), wrongToken(token)).first)

        // The invariant the ordering fix buys: the single-use latch is only ever taken by a
        // caller that proved it holds the token. Previously the latch was taken first and
        // released again on the invalid path, which left a window where a legitimate handshake
        // arriving in between was answered with 409 and rotated the QR mid-scan. Here the
        // legitimate phone must still be able to complete on the very same QR.
        val (status, _) = postHandshakePastRateLimit(freshPhonePub(), token)
        assertEquals(HttpStatusCode.OK, status)
        assertTrue(sessionManager.channel != null, "the legitimate handshake did not open a channel")
    }

    @Test
    fun `second handshake with a valid token returns 409 and issues a new QR`() = runBlocking {
        val qrBefore = server.qrContent.value
        val token = qrToken()

        assertEquals(HttpStatusCode.OK, completeHandshake(token).first)

        val (status, body) = postHandshakePastRateLimit(freshPhonePub(), token)

        assertEquals(HttpStatusCode.Conflict, status)
        assertTrue(body.contains("session_expired"), "unexpected body: $body")
        // The used session is replaced so the phone can rescan without restarting the app.
        assertNotEquals(qrBefore, server.qrContent.value)
    }

    // ---- Helpers ----

    private fun qrToken(): String =
        json.decodeFromString(PairingQrPayload.serializer(), server.qrContent.value).token

    /** A token of the same shape that is guaranteed to differ in exactly one character. */
    private fun wrongToken(token: String): String {
        val replacement = if (token.last() == 'A') 'B' else 'A'
        return token.dropLast(1) + replacement
    }

    /** Base64 X25519 public key for a throw-away "phone" side of the exchange. */
    private fun freshPhonePub(): String {
        val keyExchange = X25519KeyExchange()
        try {
            return Base64.getEncoder().encodeToString(keyExchange.publicKeyBytes)
        } finally {
            keyExchange.close()
        }
    }

    private suspend fun postHandshake(phonePub: String, token: String): Pair<HttpStatusCode, String> {
        val response = client.post("http://$LOOPBACK:${server.port}$HANDSHAKE_PATH") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    HandshakeRequest.serializer(),
                    HandshakeRequest(phonePub = phonePub, token = token)
                )
            )
        }
        return response.status to response.bodyAsText()
    }

    /**
     * The server rate-limits handshakes per source IP and every request here comes from the
     * same loopback address, so a test that needs two handshakes has to outlast the window.
     * A 429 answer is returned *before* the timestamp is refreshed, so polling converges
     * instead of extending the window — and the poll needs no knowledge of the window length.
     */
    private suspend fun postHandshakePastRateLimit(
        phonePub: String,
        token: String
    ): Pair<HttpStatusCode, String> {
        val deadline = System.currentTimeMillis() + RATE_LIMIT_WAIT_MS
        while (true) {
            val result = postHandshake(phonePub, token)
            if (result.first != HttpStatusCode.TooManyRequests) return result
            if (System.currentTimeMillis() >= deadline) return result
            delay(RATE_LIMIT_POLL_MS)
        }
    }

    /** Runs a complete ECDH handshake so that [DesktopSessionManager.channel] exists. */
    private suspend fun completeHandshake(token: String): Pair<HttpStatusCode, String> =
        postHandshake(freshPhonePub(), token)

    /** Holds an authenticated socket open, forwarding every frame to [frames]. */
    private suspend fun pumpAuthenticatedSocket(token: String, frames: Channel<Frame>) {
        client.webSocket(
            method = HttpMethod.Get,
            host = LOOPBACK,
            port = server.port,
            path = SESSION_PATH,
            request = { header(PairingServer.SESSION_TOKEN_HEADER, token) }
        ) {
            for (frame in incoming) {
                frames.send(frame)
            }
        }
    }

    /**
     * Connects with [token] (omitting the header entirely when null) and returns the close
     * reason the server sent back, or null if the server never closed the socket — which
     * fails every caller's assertion.
     */
    private suspend fun connectExpectingRejection(
        token: String?,
        origin: String? = null
    ): CloseReason? {
        var reason: CloseReason? = null
        try {
            client.webSocket(
                method = HttpMethod.Get,
                host = LOOPBACK,
                port = server.port,
                path = SESSION_PATH,
                request = {
                    if (token != null) header(PairingServer.SESSION_TOKEN_HEADER, token)
                    if (origin != null) header(HttpHeaders.Origin, origin)
                }
            ) {
                reason = withTimeout(TIMEOUT_MS) { closeReason.await() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // An immediate server-side close can surface as an IO failure while the client
            // tears the session down; the captured close reason is what is asserted on.
        }
        return reason
    }

    /**
     * `start(wait = false)` returns before the engine has necessarily bound the port, so the
     * tests poll the socket instead of sleeping on a guessed startup time.
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
        private const val SESSION_PATH = "/v1/session"
        private const val HANDSHAKE_PATH = "/v1/pair/handshake"

        /** Upper bound for a loopback round-trip; a hang fails the test instead of stalling. */
        private const val TIMEOUT_MS = 10_000L
        private const val STARTUP_TIMEOUT_MS = 15_000L
        private const val STARTUP_POLL_MS = 25L
        private const val CONNECT_TIMEOUT_MS = 500

        /** Comfortably above the server's per-IP handshake window. */
        private const val RATE_LIMIT_WAIT_MS = 15_000L
        private const val RATE_LIMIT_POLL_MS = 100L
    }
}
