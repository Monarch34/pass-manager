package com.passmanager.agent

import com.passmanager.crypto.channel.EncryptedChannel
import com.passmanager.protocol.HandshakeRequest
import com.passmanager.protocol.HandshakeResponse
import com.passmanager.protocol.SecureMessageCbor
import com.passmanager.protocol.SecureRequest
import com.passmanager.protocol.SecureResponse
import com.passmanager.domain.exception.DesktopHandshakeException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetAddress
import javax.inject.Inject

/**
 * HTTP + WebSocket client that connects to the desktop pairing server.
 *
 * Lifecycle:
 * 1. [handshake] — POST cleartext public key exchange
 * 2. [runSecureSession] — WebSocket upgrade; all further traffic is encrypted
 * 3. [sendSecure] / [receiveSecure] — bidirectional encrypted messages
 * 4. [close] — tear down WebSocket and HTTP client
 */
class DesktopPairingClient @Inject constructor(private val httpClient: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private var wsSession: WebSocketSession? = null
    private var encryptedChannel: EncryptedChannel? = null

    private val incomingRequests = Channel<SecureRequest>(Channel.BUFFERED)

    fun baseUrl(ip: String, port: Int): String {
        requirePrivateIp(ip)
        return "http://$ip:$port"
    }

    suspend fun handshake(
        ip: String,
        port: Int,
        phonePubBase64: String,
        sessionToken: String
    ): HandshakeResponse {
        val httpResponse = httpClient.post("${baseUrl(ip, port)}/v1/pair/handshake") {
            contentType(ContentType.Application.Json)
            setBody(
                HandshakeRequest(
                    phonePub = phonePubBase64,
                    token = sessionToken
                )
            )
        }
        return when (httpResponse.status) {
            HttpStatusCode.OK -> httpResponse.body()
            HttpStatusCode.Conflict -> {
                // Desktop auto-regenerates the session on 409, so the new QR is
                // already displayed. The phone just needs to rescan.
                throw DesktopHandshakeException("Session expired. Scan the new QR code.")
            }
            else -> {
                val detail = parseErrorJson(httpResponse.bodyAsText())
                throw DesktopHandshakeException(
                    detail ?: "Handshake failed (HTTP ${httpResponse.status.value})"
                )
            }
        }
    }

    private fun parseErrorJson(body: String): String? =
        try {
            json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content
        } catch (_: Exception) {
            body.trim().takeIf { it.isNotEmpty() }?.take(300)
        }

    /**
     * Opens a WebSocket, invokes [onSessionReady] after the socket is connected
     * (use this to send the first encrypted message, e.g. verify), then runs the
     * receive loop until the connection closes.
     *
     * [sessionToken] is the `token` field of the pairing QR payload. The desktop
     * requires it in the [SESSION_HEADER] request header — never in the URL, where
     * it would leak into proxy and access logs. A custom header is also the reason
     * a malicious web page cannot reach this server: browsers cannot attach custom
     * headers to a WebSocket at all.
     *
     * @throws DesktopHandshakeException if the desktop refuses the session
     *   (close code 1008): bad/missing token, or a second socket on one session.
     */
    suspend fun runSecureSession(
        ip: String,
        port: Int,
        channel: EncryptedChannel,
        sessionToken: String,
        onSessionReady: suspend DesktopPairingClient.() -> Unit
    ) {
        requirePrivateIp(ip)
        encryptedChannel = channel
        httpClient.webSocket(
            "ws://$ip:$port/v1/session",
            request = { header(SESSION_HEADER, sessionToken) }
        ) {
            wsSession = this

            // Reaching this block does NOT mean the desktop accepted us: it rejects with a close
            // frame after the upgrade, so the socket looks open for a moment either way. Wait for
            // that frame before publishing the verification code — otherwise the user is shown a
            // 6-digit code to type into a connection that is already dead.
            withTimeoutOrNull(REJECT_WINDOW_MS) { closeReason.await() }
                ?.let { throw rejectionFor(it) }

            onSessionReady()

            val receiveJob = launch {
                try {
                    while (isActive) {
                        val frame = incoming.receive()
                        if (frame is Frame.Binary) {
                            val plaintext = channel.open(frame.readBytes())
                            val request = SecureMessageCbor.decodeRequest(plaintext)
                            plaintext.fill(0)
                            incomingRequests.send(request)
                        }
                    }
                } catch (_: ClosedReceiveChannelException) {
                    // Desktop closed the connection
                }
            }

            receiveJob.join()

            // The desktop can also refuse mid-session (for example when the session is
            // regenerated underneath us), which otherwise looks like an ordinary disconnect.
            withTimeoutOrNull(CLOSE_REASON_TIMEOUT_MS) { closeReason.await() }
                ?.takeIf { it.code == CloseReason.Codes.VIOLATED_POLICY.code }
                ?.let { throw rejectionFor(it) }
        }
    }

    /**
     * Maps a 1008 close frame to something the user can act on. The desktop puts a machine
     * readable reason in the close message; anything else (or a non-1008 code) is treated as a
     * plain refusal rather than guessed at.
     */
    private fun rejectionFor(reason: CloseReason): DesktopHandshakeException {
        if (reason.code != CloseReason.Codes.VIOLATED_POLICY.code) {
            return DesktopHandshakeException("Desktop closed the session.")
        }
        val message = when (reason.message) {
            "already_connected" ->
                "This desktop is already paired with another phone. Disconnect it first."
            "session_not_ready" ->
                "The desktop is not ready to pair yet. Scan the QR code again."
            else ->
                "Desktop rejected this session. Scan the new QR code."
        }
        return DesktopHandshakeException(message)
    }

    suspend fun sendSecure(response: SecureResponse) {
        val ch = encryptedChannel ?: throw IllegalStateException("Session not open")
        val session = wsSession ?: throw IllegalStateException("WebSocket not connected")
        val plaintext = SecureMessageCbor.encodeResponse(response)
        val envelope = ch.seal(plaintext)
        plaintext.fill(0)
        session.send(Frame.Binary(true, envelope))
    }

    /**
     * Suspends until the desktop sends an encrypted request.
     */
    suspend fun receiveSecureRequest(): SecureRequest = incomingRequests.receive()

    suspend fun close() {
        try { wsSession?.close() } catch (_: Exception) {}
        wsSession = null
        encryptedChannel = null
        incomingRequests.close()
    }

    companion object {
        /** Request header carrying the pairing session token on the WebSocket upgrade. */
        const val SESSION_HEADER = "X-PassManager-Session"

        /** Upper bound on waiting for the close reason after the receive loop ends. */
        private const val CLOSE_REASON_TIMEOUT_MS = 2_000L

        /**
         * How long to wait for a rejection close frame before treating the socket as accepted.
         * A successful pairing pays this once, which is invisible in an interactive flow; the
         * alternative is showing the user a verification code for a connection the desktop
         * already refused.
         */
        private const val REJECT_WINDOW_MS = 250L

        private fun requirePrivateIp(ip: String) {
            val addr = InetAddress.getByName(ip)
            require(addr.isSiteLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress) {
                "Refusing to connect to non-private IP: $ip"
            }
        }
    }
}
