package com.passmanager.desktop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.passmanager.desktop.clipboard.ClipboardManager
import com.passmanager.desktop.preferences.DesktopPreferences
import com.passmanager.protocol.SecureRequest
import com.passmanager.protocol.SecureResponse
import com.passmanager.desktop.server.DesktopSessionManager
import com.passmanager.desktop.server.DesktopSessionState
import com.passmanager.desktop.server.PairingServer
import com.passmanager.desktop.ui.PairScreen
import com.passmanager.desktop.ui.Strings
import com.passmanager.desktop.ui.clearDesktopFaviconMemoryCaches
import com.passmanager.desktop.ui.VaultBrowserScreen
import com.passmanager.desktop.ui.VerifyScreen
import com.passmanager.desktop.ui.theme.PassManagerDesktopTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.awt.Dimension
import java.net.Inet4Address
import java.net.NetworkInterface
import kotlin.system.exitProcess

fun main() = application {
    val scope = rememberCoroutineScope()
    val lanIp = remember { detectLanIp() }
    val sessionManager = remember { DesktopSessionManager() }
    val server = remember { PairingServer(sessionManager, lanIp) }
    val clipboardManager = remember { ClipboardManager(scope) }
    var isDarkTheme by remember { mutableStateOf(true) }
    var useGoogleFavicons by remember {
        mutableStateOf(DesktopPreferences.getUseGoogleFavicons())
    }

    // Reactive QR content — updates when a new session is generated
    val qrContent by server.qrContent.collectAsState()

    DisposableEffect(Unit) {
        // Wire callbacks BEFORE starting the server
        sessionManager.onSessionEnded = {
            server.generateSession()
            sessionManager.resetToWaiting()
        }
        sessionManager.onHeartbeatNeeded = {
            server.sendToPhone(SecureRequest.Heartbeat())
        }

        server.start()

        // Response loop — continuously drains responses from the current session
        val responseJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val response = server.receiveFromPhone() ?: continue
                handlePhoneResponse(response, sessionManager, clipboardManager, server)
            }
        }

        onDispose {
            responseJob.cancel()
            clipboardManager.clearNow()
            server.close()
            sessionManager.close()
        }
    }

    Window(
        onCloseRequest = {
            gracefulShutdown(clipboardManager, server, sessionManager)
            exitApplication()
        },
        title = Strings.APP_TITLE,
        state = WindowState(width = 520.dp, height = 720.dp),
        resizable = true
    ) {
        val density = LocalDensity.current
        SideEffect {
            with(density) {
                window.minimumSize = Dimension(
                    360.dp.roundToPx().coerceAtLeast(280),
                    480.dp.roundToPx().coerceAtLeast(320)
                )
            }
        }
        PassManagerDesktopTheme(darkTheme = isDarkTheme) {
            // Compose Desktop does not paint Material background on the window by default.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                AppContent(
                    sessionManager = sessionManager,
                    qrContent = qrContent,
                    lanIp = lanIp,
                    serverPort = server.port,
                    clipboardManager = clipboardManager,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { isDarkTheme = !isDarkTheme },
                    useGoogleFavicons = useGoogleFavicons,
                    onFaviconSourceChange = { useGoogle ->
                        if (useGoogle != useGoogleFavicons) {
                            clearDesktopFaviconMemoryCaches()
                            useGoogleFavicons = useGoogle
                            DesktopPreferences.setUseGoogleFavicons(useGoogle)
                        }
                    },
                    onRefreshVaultList = {
                        scope.launch {
                            val sent = server.sendToPhone(SecureRequest.ListItems)
                            if (!sent) {
                                sessionManager.setClipboardStatus(Strings.NOT_CONNECTED)
                            }
                        }
                    },
                    onRequestPassword = { itemId ->
                        scope.launch {
                            val sent = server.sendToPhone(SecureRequest.GetPassword(itemId))
                            if (!sent) {
                                sessionManager.setClipboardStatus(Strings.NOT_CONNECTED)
                            }
                        }
                    },
                    onDisconnect = {
                        scope.launch {
                            // Send disconnect and wait briefly for ack
                            server.sendToPhone(SecureRequest.Disconnect)
                            withTimeoutOrNull(2000) {
                                server.receiveFromPhone()
                            }
                            sessionManager.onDisconnected("User disconnected")
                            clipboardManager.clearNow()
                        }
                    },
                    onCodeSubmitted = { code ->
                        scope.launch {
                            server.sendToPhone(SecureRequest.Verify(code = code))
                        }
                    },
                    onCancelVerification = {
                        scope.launch {
                            // Notify phone so it doesn't stay stuck in Verifying state
                            server.sendToPhone(SecureRequest.Disconnect)
                            withTimeoutOrNull(2000) {
                                server.receiveFromPhone()
                            }
                            sessionManager.onDisconnected("Verification cancelled")
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AppContent(
    sessionManager: DesktopSessionManager,
    qrContent: String,
    lanIp: String,
    serverPort: Int,
    clipboardManager: ClipboardManager,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    useGoogleFavicons: Boolean,
    onFaviconSourceChange: (useGoogle: Boolean) -> Unit,
    onRefreshVaultList: () -> Unit,
    onRequestPassword: (String) -> Unit,
    onDisconnect: () -> Unit,
    onCodeSubmitted: (String) -> Unit,
    onCancelVerification: () -> Unit
) {
    val sessionState by sessionManager.state.collectAsState()
    val items by sessionManager.items.collectAsState()
    val clipboardStatus by sessionManager.clipboardStatus.collectAsState()

    Crossfade(
        targetState = sessionState,
        animationSpec = tween(300),
        label = "screen-transition"
    ) { state ->
        when (state) {
            is DesktopSessionState.WaitingForPhone -> {
                PairScreen(
                    qrContent = qrContent,
                    lanIp = lanIp,
                    port = serverPort,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme
                )
            }
            is DesktopSessionState.VerifyingCode -> {
                VerifyScreen(
                    attemptsRemaining = state.attemptsRemaining,
                    error = state.error,
                    safetyNumber = state.safetyNumber,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onCodeSubmitted = { code ->
                        onCodeSubmitted(code)
                    },
                    onCancel = onCancelVerification
                )
            }
            is DesktopSessionState.Connected -> {
                VaultBrowserScreen(
                    items = items,
                    clipboardStatus = clipboardStatus,
                    onCopyPassword = onRequestPassword,
                    onDisconnect = onDisconnect,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    useGoogleFavicons = useGoogleFavicons,
                    onFaviconSourceChange = onFaviconSourceChange,
                    onRefreshVault = onRefreshVaultList
                )
            }
            is DesktopSessionState.Disconnected -> {
                PairScreen(
                    qrContent = qrContent,
                    lanIp = lanIp,
                    port = serverPort,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = onToggleTheme
                )
            }
        }
    }
}

private suspend fun handlePhoneResponse(
    response: SecureResponse,
    sessionManager: DesktopSessionManager,
    clipboardManager: ClipboardManager,
    server: PairingServer
) {
    when (response) {
        is SecureResponse.VerifyOk -> {
            val expected = (sessionManager.state.value as? DesktopSessionState.VerifyingCode)
                ?.safetyNumber ?: ""
            if (expected.isNotEmpty() && response.safetyNumber != expected) {
                // Safety number mismatch — the phone and desktop derived different keys,
                // indicating a MITM attack on the QR handshake. Abort immediately.
                sessionManager.onDisconnected("Security error: safety number mismatch — possible MITM attack")
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
            clipboardManager.copyPassword(response.password)
            sessionManager.onPasswordReceived(response.itemId)
            sessionManager.setClipboardStatus("Copied! Clipboard clears in 30s")
        }
        is SecureResponse.HeartbeatAck -> {
            sessionManager.onHeartbeatAckReceived()
        }
        is SecureResponse.DisconnectAck -> {
            sessionManager.onDisconnected("Phone disconnected")
            clipboardManager.clearNow()
        }
        is SecureResponse.Error -> {
            // Don't expose raw server error text — it may contain internal details.
            sessionManager.setClipboardStatus(Strings.ERROR_GENERIC)
        }
        is SecureResponse.RateLimited -> {
            val msg = response.message.trim()
            sessionManager.setClipboardStatus(
                if (msg.isNotEmpty()) msg else Strings.RATE_LIMITED_FALLBACK
            )
        }
    }
}

/**
 * Ordered graceful shutdown:
 *
 * 1. Clear clipboard (sensitive data first)
 * 2. Close server (stop Ktor engine, zero crypto, close channels)
 * 3. Close session manager (cancel scopes, zero session key)
 * 4. Start a daemon watchdog — if Ktor's non-daemon CIO threads don't
 *    terminate within [SHUTDOWN_TIMEOUT_MS], the watchdog forces exit.
 *    Because the watchdog is a daemon thread, it does NOT block JVM exit
 *    on its own. It only fires if other non-daemon threads are stuck.
 */
private fun gracefulShutdown(
    clipboardManager: ClipboardManager,
    server: PairingServer,
    sessionManager: DesktopSessionManager
) {
    // Phase 1: zero sensitive state
    clipboardManager.clearNow()

    // Phase 2: stop network + crypto
    server.close()

    // Phase 3: cancel scopes
    sessionManager.close()

    // Phase 4: daemon watchdog — safety net for stuck Ktor CIO threads.
    // If all threads terminate naturally, the JVM exits and this watchdog
    // never fires because daemon threads don't prevent JVM shutdown.
    Thread {
        try {
            Thread.sleep(SHUTDOWN_TIMEOUT_MS)
            // If we reach here, non-daemon threads are still alive.
            exitProcess(0)
        } catch (_: InterruptedException) {
            // JVM shutting down normally — watchdog interrupted, nothing to do.
        }
    }.apply {
        isDaemon = true
        name = "shutdown-watchdog"
        start()
    }
}

private const val SHUTDOWN_TIMEOUT_MS = 3000L

/**
 * Picks an IPv4 address the phone can actually reach.
 *
 * The naive "first non-loopback IPv4" often returns **WSL / Hyper-V / Docker** adapters
 * (e.g. `172.30.x.x`), which are not reachable from a phone on Wi-Fi — causing
 * handshake connect timeouts.
 */
private fun detectLanIp(): String {
    val preferred = mutableListOf<Pair<String, Int>>()
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
        for (ni in interfaces) {
            if (ni.isLoopback || !ni.isUp) continue
            if (isLikelyVirtualOrHostOnlyInterface(ni)) continue
            for (addr in ni.inetAddresses) {
                if (addr !is Inet4Address || addr.isLoopbackAddress) continue
                val host = addr.hostAddress ?: continue
                val octets = host.split('.').mapNotNull { it.toIntOrNull() }
                if (octets.size != 4) continue
                val rank = lanIpPreferenceRank(octets)
                preferred.add(host to rank)
            }
        }
    } catch (_: Exception) { }

    if (preferred.isNotEmpty()) {
        return preferred.minWith(compareBy({ it.second }, { it.first })).first
    }

    // Fallback: any IPv4 (same as legacy behavior) if everything was filtered
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
        for (ni in interfaces) {
            if (ni.isLoopback || !ni.isUp) continue
            for (addr in ni.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    return addr.hostAddress ?: "127.0.0.1"
                }
            }
        }
    } catch (_: Exception) { }
    return "127.0.0.1"
}

/** Lower rank = more likely to be the real LAN (Wi-Fi / Ethernet). */
private fun lanIpPreferenceRank(octets: List<Int>): Int {
    val a = octets[0]
    val b = octets[1]
    return when {
        a == 192 && b == 168 -> 0
        a == 10 -> 1
        a == 172 && b in 16..31 -> 2
        else -> 4
    }
}

private fun isLikelyVirtualOrHostOnlyInterface(ni: NetworkInterface): Boolean {
    val label = "${ni.name} ${ni.displayName}".lowercase()
    return listOf(
        "wsl",
        "vethernet",
        "hyper-v",
        "hyperv",
        "virtualbox",
        "vmware",
        "virtual ",
        "docker",
        "vbox",
        "npcap",
        "tun",
        "tap",
        "pseudo",
        "ppp",
        "bluetooth",
        "default switch",
        "internal",
        "only-",
        "host-only"
    ).any { label.contains(it) }
}
