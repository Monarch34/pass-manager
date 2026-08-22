package com.passmanager.desktop.server

import com.passmanager.desktop.crypto.EncryptedChannel
import com.passmanager.desktop.crypto.HkdfSha256
import com.passmanager.desktop.crypto.SensitiveByteArray
import com.passmanager.desktop.crypto.X25519KeyExchange
import com.passmanager.protocol.HandshakeRequest
import com.passmanager.protocol.HandshakeResponse
import com.passmanager.protocol.PairingQrPayload
import com.passmanager.protocol.SecureMessageCbor
import com.passmanager.protocol.SecureRequest
import com.passmanager.protocol.SecureResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.FrameTooBigException
import io.ktor.websocket.readBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ephemeral Ktor server for desktop pairing.
 *
 * The server binds the port **once** and stays alive for the app lifetime.
 * Each pairing cycle is managed by [PairingSession] — a new session is
 * generated after every disconnect so the phone can re-pair without
 * restarting the desktop app.
 */
class PairingServer(
    private val sessionManager: DesktopSessionManager,
    private val lanIp: String
) : Closeable {

    /** Per-session ephemeral state. Recreated on every [generateSession]. */
    class PairingSession(
        val keyExchange: X25519KeyExchange,
        val publicKeyBase64: String,
        val sessionToken: String,
        val outgoingRequests: Channel<SecureRequest> = Channel(Channel.BUFFERED),
        val incomingResponses: Channel<SecureResponse> = Channel(Channel.BUFFERED),
        val handshakeUsed: AtomicBoolean = AtomicBoolean(false),
        /**
         * One WebSocket per session. Taken after authentication and released when
         * the socket handler exits, so a legitimate reconnect works while a second
         * concurrent socket is rejected. [generateSession] creates a fresh session
         * object, which resets this latch naturally.
         */
        val websocketAttached: AtomicBoolean = AtomicBoolean(false)
    ) {
        fun closeChannels() {
            outgoingRequests.close()
            incomingResponses.close()
        }

        fun closeKeyExchange() {
            keyExchange.close()
        }
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val random = SecureRandom()
    private var engine: ApplicationEngine? = null

    /** Per-IP last-handshake-attempt timestamps for rate limiting. */
    private val handshakeAttemptsByIp = ConcurrentHashMap<String, Long>()

    val port: Int = findAvailablePort()

    // ---- Reactive session state ----

    private val _currentSession = MutableStateFlow<PairingSession?>(null)

    private val _qrContent = MutableStateFlow("")
    /** Reactive QR content — updates automatically when a new session is generated. */
    val qrContent: StateFlow<String> = _qrContent.asStateFlow()

    // ---- Public API ----

    /**
     * Generate a fresh pairing session: new ECDH keypair, new channels, new QR.
     * Old session resources are cleaned up first.
     */
    fun generateSession() {
        val oldSession = _currentSession.value
        oldSession?.closeChannels()
        oldSession?.closeKeyExchange()

        val kx = X25519KeyExchange(random)
        val pubBase64 = Base64.getEncoder().encodeToString(kx.publicKeyBytes)
        val token = generateSessionToken()

        val session = PairingSession(
            keyExchange = kx,
            publicKeyBase64 = pubBase64,
            sessionToken = token
        )
        _currentSession.value = session

        val payload = PairingQrPayload(
            ip = lanIp,
            port = port,
            pub = pubBase64,
            token = token
        )
        _qrContent.value = Json.encodeToString(PairingQrPayload.serializer(), payload)
    }

    /** Send a request to the phone. Returns false if not connected. */
    suspend fun sendToPhone(request: SecureRequest): Boolean {
        val session = _currentSession.value ?: return false
        return try {
            session.outgoingRequests.send(request)
            true
        } catch (_: ClosedSendChannelException) {
            false
        }
    }

    /** Receive the next response from the phone, or null if the session ended. */
    suspend fun receiveFromPhone(): SecureResponse? {
        val session = _currentSession.value ?: return null
        return try {
            session.incomingResponses.receive()
        } catch (_: ClosedReceiveChannelException) {
            null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    fun start() {
        generateSession()

        // Bind only to the detected LAN IP — not all interfaces.
        // Avoids exposing the pairing server on Docker/WSL/VPN adapters or WAN interfaces.
        engine = embeddedServer(CIO, port = port, host = lanIp) {
            install(ContentNegotiation) { json(json) }
            install(WebSockets) {
                // Ktor's default is unlimited: a single huge frame from an
                // unauthenticated peer could OOM the desktop app before the
                // handler ever sees it. Real frames are a few KiB.
                maxFrameSize = MAX_FRAME_SIZE_BYTES
                // No pingPeriod/timeout here on purpose — DesktopSessionManager
                // runs its own application-level heartbeat.
            }

            routing {
                post("/v1/pair/handshake") {
                    // Fixed order: 1) session exists  2) per-IP rate limit
                    // 3) body + constant-time token check  4) single-use latch
                    // 5) ECDH/HKDF. The rate limit stays ahead of the token check
                    // so brute force is throttled; the latch moved behind it so an
                    // unauthenticated request can no longer burn the QR the user is
                    // scanning right now.
                    val session = _currentSession.value
                    if (session == null) {
                        context.respond(
                            HttpStatusCode.ServiceUnavailable,
                            mapOf("error" to "no_session")
                        )
                        return@post
                    }

                    // Rate limit: one handshake attempt per IP per HANDSHAKE_RATE_LIMIT_MS
                    // remoteAddress, not remoteHost: the latter calls InetAddress.getHostName(),
                    // which performs a blocking reverse DNS lookup on every request. On a LAN with
                    // no PTR records that stalls the request thread until the resolver times out,
                    // and it is reachable by an unauthenticated caller.
                    val remoteIp = context.request.local.remoteAddress
                    val now = System.currentTimeMillis()
                    val lastAttempt = handshakeAttemptsByIp[remoteIp] ?: 0L
                    if (now - lastAttempt < HANDSHAKE_RATE_LIMIT_MS) {
                        context.respond(
                            HttpStatusCode.TooManyRequests,
                            mapOf("error" to "rate_limited", "message" to "Too many attempts. Please wait.")
                        )
                        return@post
                    }
                    pruneHandshakeAttempts(now)
                    handshakeAttemptsByIp[remoteIp] = now

                    val body = try {
                        context.receive<HandshakeRequest>()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        context.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "invalid_request")
                        )
                        return@post
                    }

                    if (!tokenMatches(body.token, session.sessionToken)) {
                        // Wrong token: leave the session completely untouched.
                        // No latch is taken, so no compensating reset is needed and
                        // an attacker cannot invalidate the displayed QR.
                        context.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "invalid_session")
                        )
                        return@post
                    }

                    if (!session.handshakeUsed.compareAndSet(false, true)) {
                        // Session already used — generate a fresh one so the new QR
                        // is ready when the phone rescans. This avoids telling the user
                        // to restart the desktop app.
                        generateSession()
                        sessionManager.resetToWaiting()
                        context.respond(
                            HttpStatusCode.Conflict,
                            mapOf("error" to "session_expired", "message" to "Session expired. Scan the new QR code.")
                        )
                        return@post
                    }

                    try {
                        val phonePubBytes = Base64.getDecoder().decode(body.phonePub)
                        require(phonePubBytes.size == 32) {
                            "Phone public key must be 32 bytes, got ${phonePubBytes.size}"
                        }

                        val kx = session.keyExchange
                        val desktopPubBytes = Base64.getDecoder().decode(session.publicKeyBase64)

                        val sharedSecret = kx.deriveSharedSecret(phonePubBytes)
                        kx.close() // zero private key immediately

                        val salt = combinePubKeys(desktopPubBytes, phonePubBytes)
                        val derivedKey = HkdfSha256.derive(
                            ikm = sharedSecret,
                            salt = salt,
                            info = SESSION_INFO,
                            length = 32
                        )
                        sharedSecret.fill(0)

                        sessionManager.setSessionKey(SensitiveByteArray.directFrom(derivedKey))

                        val safetyNumber = computeSafetyNumber(desktopPubBytes, phonePubBytes)
                        sessionManager.setPendingSafetyNumber(safetyNumber)

                        val channel = EncryptedChannel(
                            sessionKey = sessionManager.sessionKeyCopy(),
                            sendDirection = EncryptedChannel.Direction.DESKTOP_TO_PHONE
                        )
                        sessionManager.setChannel(channel)

                        context.respond(HandshakeResponse())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Key agreement failed after the latch was taken — release it
                        // so the phone can retry against the same QR.
                        session.handshakeUsed.set(false)
                        context.respond(
                            HttpStatusCode.InternalServerError,
                            mapOf("error" to "handshake_failed")
                        )
                    }
                }

                webSocket("/v1/session") {
                    // Authentication happens inside the handler: by the time Ktor
                    // enters this block the HTTP upgrade is already complete, so a
                    // rejection is a close frame rather than an HTTP status. Doing it
                    // pre-upgrade would need a pipeline intercept that duplicates the
                    // route match; closing here is equivalent for the contract as long
                    // as a rejected connection touches NO session state — no
                    // onSessionConnected(), no onDisconnected(), no latch taken.

                    // Defence in depth: a legitimate phone client never sends Origin.
                    // Browsers always do — and they cannot set the session header at
                    // all, so this only ever hits a browser-originated request.
                    if (call.request.headers[HttpHeaders.Origin] != null) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "origin_not_allowed"))
                        return@webSocket
                    }

                    val session = _currentSession.value
                        ?: run {
                            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "session_not_ready"))
                            return@webSocket
                        }

                    if (!tokenMatches(call.request.headers[SESSION_TOKEN_HEADER], session.sessionToken)) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "unauthorized"))
                        return@webSocket
                    }

                    val channel = sessionManager.channel
                        ?: run {
                            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "session_not_ready"))
                            return@webSocket
                        }

                    // Single socket per session: the second one is refused without
                    // disturbing the first.
                    if (!session.websocketAttached.compareAndSet(false, true)) {
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "already_connected"))
                        return@webSocket
                    }

                    // Everything past the latch runs inside try/finally. Setting up the jobs can
                    // throw, and this handler can be cancelled outright when the engine stops -
                    // either would strand the latch at true and leave the session unable to ever
                    // accept a socket again, short of restarting the app.
                    var sendJob: Job? = null
                    try {
                        sessionManager.onSessionConnected()

                        val receiveJob = launch {
                            try {
                                while (isActive) {
                                    val frame = incoming.receive()
                                    if (frame is Frame.Binary) {
                                        val plaintext = channel.open(frame.readBytes())
                                        val msg = SecureMessageCbor.decodeResponse(plaintext)
                                        plaintext.fill(0)
                                        try {
                                            session.incomingResponses.send(msg)
                                        } catch (_: ClosedSendChannelException) {
                                            break
                                        }
                                    }
                                }
                            } catch (_: ClosedReceiveChannelException) {
                                sessionManager.onDisconnected("Phone disconnected")
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: FrameTooBigException) {
                                // Distinct from a plain disconnect: the phone tried to send a
                                // vault list larger than MAX_FRAME_BYTES, and the user needs to
                                // know that rather than seeing "phone disconnected".
                                sessionManager.onDisconnected("Vault list too large to transfer")
                            } catch (_: Exception) {
                                sessionManager.onDisconnected("Connection error")
                            }
                        }

                        sendJob = launch {
                            try {
                                for (request in session.outgoingRequests) {
                                    val payload = SecureMessageCbor.encodeRequest(request)
                                    val envelope = channel.seal(payload)
                                    payload.fill(0)
                                    send(Frame.Binary(true, envelope))
                                }
                            } catch (_: ClosedSendChannelException) {
                                // Channel closed - session ended
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                // WebSocket closed - stop sending
                            }
                        }

                        receiveJob.join()
                    } finally {
                        sendJob?.cancel()
                        // Release the latch so a legitimate reconnect can attach.
                        session.websocketAttached.set(false)
                    }
                }
            }
        }.apply { start(wait = false) }
    }

    override fun close() {
        // 1. Close session channels first — unblocks WebSocket send/receive jobs
        _currentSession.value?.let { session ->
            session.closeChannels()
            session.closeKeyExchange()
        }
        _currentSession.value = null

        // 2. Stop Ktor engine: 1s grace for active requests, 2s hard deadline.
        //    stop() blocks until the engine's coroutine scope is cancelled and
        //    HTTP/WS handlers have exited.
        engine?.stop(1000, 2000)
        engine = null
    }

    /**
     * Deterministic ordering: compare byte-by-byte (lexicographic) so the same
     * two keys always produce the same salt regardless of JVM run or hash collisions.
     * contentHashCode() is NOT used — it is non-deterministic across JVM restarts
     * and prone to collisions.
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

    /**
     * SHA-256(min(a,b) || max(a,b)) → first 4 bytes → 8 uppercase hex chars.
     * Must match the phone's computation to detect MITM.
     */
    private fun computeSafetyNumber(a: ByteArray, b: ByteArray): String {
        val combined = combinePubKeys(a, b)
        val digest = MessageDigest.getInstance("SHA-256").digest(combined)
        return digest.take(4).joinToString("") { "%02X".format(it) }
    }

    /**
     * Constant-time session-token comparison. Never use `==` on secrets: String
     * equality short-circuits on the first differing character and leaks the
     * matching prefix through timing. A missing header simply fails.
     */
    private fun tokenMatches(provided: String?, expected: String): Boolean {
        if (provided == null) return false
        return MessageDigest.isEqual(
            provided.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8)
        )
    }

    /**
     * Drops rate-limit entries whose window has already expired, so the map cannot
     * grow without bound as source IPs change. Only runs once the map is larger than
     * a normal pairing session could ever make it, keeping the common path free.
     * If everything is still fresh (a burst from many distinct IPs) the map is
     * cleared outright — losing throttling state is preferable to unbounded growth,
     * and the next attempt from each IP starts the window again.
     */
    private fun pruneHandshakeAttempts(now: Long) {
        if (handshakeAttemptsByIp.size < HANDSHAKE_ATTEMPTS_MAX) return
        handshakeAttemptsByIp.values.removeIf { now - it >= HANDSHAKE_RATE_LIMIT_MS }
        if (handshakeAttemptsByIp.size >= HANDSHAKE_ATTEMPTS_MAX) {
            handshakeAttemptsByIp.clear()
        }
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
        /**
         * Header carrying the pairing token on the WebSocket upgrade. The value is
         * the exact `token` field of the QR payload.
         *
         * A custom header — not a query string, not a subprotocol: query strings end
         * up in proxy and access logs, and browsers cannot attach custom headers to a
         * WebSocket at all, so requiring one blocks cross-site WebSocket hijacking
         * more reliably than an Origin check.
         */
        const val SESSION_TOKEN_HEADER = "X-PassManager-Session"

        private val SESSION_INFO = "passmanager-v1".toByteArray()
        private const val SESSION_TOKEN_BYTES = 18
        /** Minimum ms between handshake attempts from the same IP. */
        private const val HANDSHAKE_RATE_LIMIT_MS = 3_000L
        /** Rate-limit map size that triggers pruning. */
        private const val HANDSHAKE_ATTEMPTS_MAX = 256
        /** Hard cap on a single WebSocket frame (256 KiB); real frames are a few KiB. */
        /**
         * Bounded so an unauthenticated peer cannot OOM the app, but generous enough for a real
         * vault: SendItemListToDesktopUseCase serialises the whole list into ONE SecureResponse.Items
         * frame, so this value is a hard ceiling on vault size. At roughly 150-250 bytes per
         * ItemSummary, 256 KiB would have capped out near a thousand entries - reachable for anyone
         * importing a browser password store.
         */
        const val MAX_FRAME_SIZE_BYTES = 1024L * 1024

        private fun findAvailablePort(): Int {
            ServerSocket(0).use { return it.localPort }
        }
    }

    private fun generateSessionToken(): String {
        val bytes = ByteArray(SESSION_TOKEN_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
