package com.passmanager.agent

import com.passmanager.crypto.channel.EncryptedChannel
import com.passmanager.crypto.channel.HandshakeRequest
import com.passmanager.crypto.channel.HandshakeResponse
import com.passmanager.crypto.channel.SecureMessageCbor
import com.passmanager.crypto.channel.SecureRequest
import com.passmanager.crypto.channel.SecureResponse
import com.passmanager.domain.usecase.DesktopHandshakeException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * HTTP + WebSocket client that connects to the desktop pairing server.
 *
 * Lifecycle:
 * 1. [handshake] — POST cleartext public key exchange
 * 2. [runSecureSession] — WebSocket upgrade; all further traffic is encrypted
 * 3. [sendSecure] / [receiveSecure] — bidirectional encrypted messages
 * 4. [close] — tear down WebSocket and HTTP client
 */
class DesktopPairingClient {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 20_000
            requestTimeoutMillis = 60_000
            socketTimeoutMillis = 60_000
        }
        install(ContentNegotiation) { json(json) }
        install(WebSockets)
    }

    private var wsSession: WebSocketSession? = null
    private var encryptedChannel: EncryptedChannel? = null

    private val incomingRequests = Channel<SecureRequest>(Channel.BUFFERED)

    fun baseUrl(ip: String, port: Int) = "http://$ip:$port"

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
     */
    suspend fun runSecureSession(
        ip: String,
        port: Int,
        channel: EncryptedChannel,
        onSessionReady: suspend DesktopPairingClient.() -> Unit
    ) {
        encryptedChannel = channel
        httpClient.webSocket("ws://$ip:$port/v1/session") {
            wsSession = this

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
        }
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
        httpClient.close()
    }
}
