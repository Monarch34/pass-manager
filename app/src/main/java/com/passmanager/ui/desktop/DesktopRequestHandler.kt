package com.passmanager.ui.desktop

import android.content.Context
import com.passmanager.R
import com.passmanager.domain.model.PairingSessionState
import com.passmanager.domain.exception.DesktopRateLimitException
import com.passmanager.domain.usecase.SendItemListToDesktopUseCase
import com.passmanager.domain.usecase.SendPasswordToDesktopUseCase
import com.passmanager.protocol.SecureRequest
import com.passmanager.protocol.SecureResponse
import com.passmanager.security.DesktopPairingSession
import com.passmanager.ui.common.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles incoming desktop requests during an active or verifying pairing session.
 */
class DesktopRequestHandler @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val pairingSession: DesktopPairingSession,
    private val sendItemListToDesktopUseCase: SendItemListToDesktopUseCase,
    private val sendPasswordToDesktopUseCase: SendPasswordToDesktopUseCase,
    private val pairingNotificationHelper: DesktopPairingNotificationHelper
) {

    private var requestLoopJob: Job? = null

    init {
        pairingNotificationHelper.ensureChannelCreated()
    }

    /**
     * Starts the request loop listening for desktop commands.
     * Cancels any previously running loop.
     */
    fun startRequestLoop(scope: CoroutineScope) {
        requestLoopJob?.cancel()
        requestLoopJob = scope.launch {
            while (isActive) {
                when (val result = pairingSession.receiveSecureRequest()) {
                    is DesktopPairingSession.ReceiveResult.Success ->
                        handleDesktopRequest(result.request)
                    is DesktopPairingSession.ReceiveResult.ConnectionClosed -> {
                        pairingSession.endSession(
                            appContext.getString(R.string.desktop_session_connection_closed)
                        )
                        break
                    }
                    is DesktopPairingSession.ReceiveResult.Error -> {
                        pairingSession.endSession(
                            appContext.getString(R.string.desktop_session_connection_error)
                        )
                        break
                    }
                }
            }
        }
    }

    /** Cancels the ongoing request loop. */
    fun stopLoop() {
        requestLoopJob?.cancel()
    }

    private suspend fun handleDesktopRequest(request: SecureRequest) {
        val state = pairingSession.state.value
        val requestAllowed = when (state) {
            is PairingSessionState.Active -> true
            is PairingSessionState.Verifying ->
                request is SecureRequest.Verify ||
                request is SecureRequest.Heartbeat ||
                request is SecureRequest.Disconnect
            else ->
                request is SecureRequest.Heartbeat ||
                request is SecureRequest.Disconnect
        }
        if (!requestAllowed) {
            sendErrorSafely(
                appContext.getString(R.string.desktop_error_verification_required),
                "rejecting disallowed request ${request::class.simpleName}"
            )
            return
        }

        when (request) {
            is SecureRequest.GetPassword -> {
                try {
                    val title = sendPasswordToDesktopUseCase(request.itemId)
                    showPasswordNotification(title)
                } catch (_: DesktopRateLimitException) {
                    // [SecureResponse.RateLimited] already sent — do not send [Error] or desktop shows a false "bug".
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to send password", e)
                    sendErrorSafely(
                        appContext.getString(R.string.desktop_error_password_send_failed),
                        "sending password retrieval error"
                    )
                }
            }
            is SecureRequest.ListItems -> {
                if (!pairingSession.canAcceptVaultListRequestFromDesktop()) {
                    pairingSession.sendSecure(
                        SecureResponse.RateLimited(
                            appContext.getString(R.string.desktop_rate_limited_list_refresh)
                        )
                    )
                    return
                }
                pairingSession.recordVaultListRequestFromDesktop()
                try {
                    sendItemListToDesktopUseCase()
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to send item list", e)
                    sendErrorSafely(
                        appContext.getString(R.string.desktop_error_list_refresh_failed),
                        "sending item list refresh error"
                    )
                }
            }
            is SecureRequest.Heartbeat -> {
                pairingSession.respondToHeartbeat()
            }
            is SecureRequest.Disconnect -> {
                pairingSession.endSession(appContext.getString(R.string.desktop_session_desktop_disconnected))
            }
            is SecureRequest.Verify -> {
                pairingSession.handleVerifyRequest(request.code)
            }
        }
    }

    private suspend fun sendErrorSafely(message: String, context: String) {
        try {
            pairingSession.sendSecure(SecureResponse.Error(message))
        } catch (sendError: Exception) {
            AppLogger.w(TAG, "Failed $context", sendError)
        }
    }

    private fun showPasswordNotification(itemTitle: String) {
        pairingNotificationHelper.notifyPasswordSent(itemTitle)
    }

    companion object {
        private const val TAG = "DesktopRequestHandler"
    }
}
