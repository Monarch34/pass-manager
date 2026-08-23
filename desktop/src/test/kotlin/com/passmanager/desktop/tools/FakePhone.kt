package com.passmanager.desktop.tools

import com.passmanager.desktop.crypto.EncryptedChannel
import com.passmanager.desktop.crypto.HkdfSha256
import com.passmanager.desktop.crypto.X25519KeyExchange
import com.passmanager.desktop.server.PairingServer
import com.passmanager.protocol.HandshakeRequest
import com.passmanager.protocol.ItemSummary
import com.passmanager.protocol.PairingQrPayload
import com.passmanager.protocol.SecureMessageCbor
import com.passmanager.protocol.SecureRequest
import com.passmanager.protocol.SecureResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.security.MessageDigest
import java.util.Base64

/**
 * The phone half of the pairing protocol, implemented in test code.
 *
 * `VerifyScreen` and `VaultBrowserScreen` are only reachable from a real pairing session:
 * the phone has to scan the QR, run the ECDH handshake and then hold an authenticated
 * WebSocket open. Adding a desktop-side bypass to reach those screens would leave a
 * permanent backdoor in the shipped app, so this class speaks the actual protocol
 * instead — the production code stays exactly as it ships.
 *
 * Every step mirrors `app/src/main/java/com/passmanager/security/DesktopPairingSession.kt`:
 * the same key agreement, the same HKDF salt and info, the same safety-number derivation
 * and the same channel direction. The desktop's own crypto classes are reused for the
 * phone side because both ends implement the identical algorithms; if the two ever
 * diverged the tests would fail with a decrypt error rather than quietly passing.
 *
 * Not thread-safe, and deliberately so: one caller drives the socket at a time, which is
 * also how the real client behaves.
 *
 * ```
 * val phone = FakePhone(server.qrContent.value)
 * val paired = phone.pair()                        // desktop moves to VerifyingCode
 * phone.sendVerifyOk(code)                         // desktop moves to Connected
 * val serving = launch { phone.serveItems(items) } // answers ListItems
 * ...
 * serving.cancelAndJoin()
 * phone.close()
 * ```
 */
class FakePhone(private val qrJson: String) : Closeable {

    /** What [pair] produces: everything a test needs to reason about the live session. */
    data class PairResult(
        /** 8 uppercase hex chars, derived exactly as the desktop derives it. */
        val safetyNumber: String,
        val desktopIp: String,
        val desktopPort: Int
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = HttpClient(CIO) {
        install(WebSockets)
    }

    private var channel: EncryptedChannel? = null
    private var socket: DefaultClientWebSocketSession? = null
    private var safetyNumber: String = ""

    /**
     * Size in bytes of the last sealed frame this phone put on the wire.
     *
     * The desktop caps a single frame at [PairingServer.MAX_FRAME_SIZE_BYTES] and the whole
     * vault list travels in ONE `SecureResponse.Items` frame, so a large-vault test needs to
     * see the real envelope size instead of estimating it. Volatile because the serve loop
     * writes it from its own coroutine while the test reads it from another.
     */
    @Volatile
    var lastSentFrameSize: Int = -1
        private set

    // ---- Pairing ----

    /**
     * Runs the complete phone-side pairing sequence in the order the desktop expects:
     * X25519 keypair, `POST /v1/pair/handshake`, shared secret, HKDF session key, safety
     * number, encrypted channel, then the authenticated WebSocket upgrade.
     *
     * The handshake has to finish before the socket is opened: the desktop's WebSocket
     * handler refuses the upgrade with `session_not_ready` until the handshake has installed
     * a channel on [com.passmanager.desktop.server.DesktopSessionManager].
     */
    suspend fun pair(): PairResult {
        val payload = json.decodeFromString(PairingQrPayload.serializer(), qrJson)

        val keyExchange = X25519KeyExchange()
        val phonePubBytes = keyExchange.publicKeyBytes.copyOf()
        try {
            val phonePubBase64 = Base64.getEncoder().encodeToString(phonePubBytes)
            val desktopPubBytes = Base64.getDecoder().decode(payload.pub)
            require(desktopPubBytes.size == 32) {
                "Desktop public key must be 32 bytes, got ${desktopPubBytes.size}"
            }

            val response = client.post("http://${payload.ip}:${payload.port}$HANDSHAKE_PATH") {
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        HandshakeRequest.serializer(),
                        HandshakeRequest(phonePub = phonePubBase64, token = payload.token)
                    )
                )
            }
            if (response.status != HttpStatusCode.OK) {
                // The body carries the server's reason code (invalid_session, rate_limited,
                // session_expired), which is the only useful thing to report from here.
                throw IllegalStateException(
                    "Handshake rejected: ${response.status} ${response.bodyAsText()}"
                )
            }

            val sharedSecret = keyExchange.deriveSharedSecret(desktopPubBytes)
            keyExchange.close() // zero the private key as soon as it has done its job

            // Both sides sort the two public keys before hashing, so phone-order and
            // desktop-order arguments produce the same salt and the same safety number.
            val salt = combinePubKeys(phonePubBytes, desktopPubBytes)
            safetyNumber = computeSafetyNumber(phonePubBytes, desktopPubBytes)

            val derivedKey = HkdfSha256.derive(
                ikm = sharedSecret,
                salt = salt,
                info = SESSION_INFO,
                length = 32
            )
            sharedSecret.fill(0)

            // EncryptedChannel moves the key off-heap and zeroes the array it was handed,
            // so derivedKey needs no separate wipe here.
            channel = EncryptedChannel(
                sessionKey = derivedKey,
                sendDirection = EncryptedChannel.Direction.PHONE_TO_DESKTOP
            )

            // A real phone client never sends Origin, and the token travels in the custom
            // header rather than in the query string — the desktop enforces both.
            socket = client.webSocketSession(
                method = HttpMethod.Get,
                host = payload.ip,
                port = payload.port,
                path = SESSION_PATH
            ) {
                header(PairingServer.SESSION_TOKEN_HEADER, payload.token)
            }

            return PairResult(
                safetyNumber = safetyNumber,
                desktopIp = payload.ip,
                desktopPort = payload.port
            )
        } catch (e: Throwable) {
            // A half-built session would leave key material alive for the rest of the JVM run.
            keyExchange.close()
            close()
            throw e
        } finally {
            phonePubBytes.fill(0)
        }
    }

    // ---- Session traffic ----

    /**
     * Waits for the desktop's `Verify` request and answers it with `VerifyOk`.
     *
     * On a real phone the user compares the code on screen; here the test driver owns the
     * code and passes it in, so the comparison still happens — against the value the driver
     * typed into the desktop — and a mismatch fails loudly instead of accepting whatever
     * arrived.
     */
    suspend fun sendVerifyOk(code: String) {
        val request = receiveRequest()
            ?: throw IllegalStateException("Socket closed while waiting for the verify request")
        check(request is SecureRequest.Verify) { "Expected Verify, got $request" }
        check(request.code == code) {
            "Desktop submitted code ${request.code}, driver expected $code"
        }
        sendResponse(SecureResponse.VerifyOk(safetyNumber = safetyNumber))
    }

    /**
     * Answers every `ListItems` request with [items]. Runs until the socket closes, so
     * callers launch it in their own coroutine and cancel it when the test is done.
     */
    suspend fun serveItems(items: List<ItemSummary>) {
        serveOnRequest { request ->
            if (request is SecureRequest.ListItems) SecureResponse.Items(items) else null
        }
    }

    /**
     * General request/response loop. Runs until the socket closes.
     *
     * [handler] returns the response to send, or null to fall through to the protocol
     * defaults: `Heartbeat` is acked and `Disconnect` is acknowledged, exactly as the real
     * phone does. Leaving heartbeats unanswered would let
     * [com.passmanager.desktop.server.DesktopSessionManager] time the session out mid-test,
     * so a handler that only cares about vault traffic still keeps the session alive.
     */
    suspend fun serveOnRequest(handler: (SecureRequest) -> SecureResponse?) {
        while (true) {
            val request = receiveRequest() ?: return
            val response = handler(request) ?: defaultResponseFor(request) ?: continue
            sendResponse(response)
        }
    }

    /** Protocol obligations the real phone always honours, whatever the test is about. */
    private fun defaultResponseFor(request: SecureRequest): SecureResponse? = when (request) {
        is SecureRequest.Heartbeat -> SecureResponse.HeartbeatAck(ts = System.currentTimeMillis())
        is SecureRequest.Disconnect -> SecureResponse.DisconnectAck
        else -> null
    }

    /** Next decrypted request, or null once the desktop has closed the socket. */
    private suspend fun receiveRequest(): SecureRequest? {
        val session = requireSocket()
        val ch = requireChannel()
        while (true) {
            val frame = try {
                session.incoming.receive()
            } catch (_: ClosedReceiveChannelException) {
                return null
            }
            // Ping/pong/close frames carry no protocol payload.
            if (frame !is Frame.Binary) continue
            val plaintext = ch.open(frame.readBytes())
            try {
                return SecureMessageCbor.decodeRequest(plaintext)
            } finally {
                plaintext.fill(0)
            }
        }
    }

    private suspend fun sendResponse(response: SecureResponse) {
        val session = requireSocket()
        val ch = requireChannel()
        val payload = SecureMessageCbor.encodeResponse(response)
        val envelope = try {
            ch.seal(payload)
        } finally {
            payload.fill(0)
        }
        lastSentFrameSize = envelope.size
        session.send(Frame.Binary(true, envelope))
    }

    // ---- Teardown ----

    /**
     * Drops the socket and zeroes the session key. `terminate()` rather than a close
     * handshake keeps this a plain function: tests call it from `@AfterTest`, and the
     * desktop treats an abrupt drop the same way it treats a phone leaving Wi-Fi.
     */
    override fun close() {
        socket?.terminate()
        socket = null

        channel?.close()
        channel = null

        safetyNumber = ""
        client.close()
    }

    private fun requireSocket(): DefaultClientWebSocketSession =
        socket ?: throw IllegalStateException("Not paired — call pair() first")

    private fun requireChannel(): EncryptedChannel =
        channel ?: throw IllegalStateException("Not paired — call pair() first")

    // ---- Safety number (mirrors DesktopPairingSession) ----

    /**
     * Deterministic ordering: the smaller key first, byte-by-byte. Both ends sort, so the
     * salt and the fingerprint match regardless of which side computed them.
     */
    private fun combinePubKeys(a: ByteArray, b: ByteArray): ByteArray {
        val combined = ByteArray(a.size + b.size)
        val aFirst = compareArrays(a, b) <= 0
        val first = if (aFirst) a else b
        val second = if (aFirst) b else a
        System.arraycopy(first, 0, combined, 0, first.size)
        System.arraycopy(second, 0, combined, first.size, second.size)
        return combined
    }

    /** SHA-256(min(a,b) || max(a,b)) → first 4 bytes → 8 uppercase hex chars. */
    private fun computeSafetyNumber(phonePub: ByteArray, desktopPub: ByteArray): String {
        val combined = combinePubKeys(phonePub, desktopPub)
        val digest = MessageDigest.getInstance("SHA-256").digest(combined)
        return digest.take(4).joinToString("") { "%02X".format(it) }
    }

    private fun compareArrays(a: ByteArray, b: ByteArray): Int {
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (diff != 0) return diff
        }
        return a.size - b.size
    }

    companion object {
        private const val HANDSHAKE_PATH = "/v1/pair/handshake"
        private const val SESSION_PATH = "/v1/session"

        /** Must byte-match the desktop's and the phone's HKDF info parameter. */
        private val SESSION_INFO = "passmanager-v1".toByteArray()
    }
}
