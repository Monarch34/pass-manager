package com.passmanager.ui.desktop

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.passmanager.R
import com.passmanager.app.NotificationPermissionRequester
import com.passmanager.domain.model.DesktopPairingConstants
import com.passmanager.domain.model.PairingSessionState
import com.passmanager.ui.components.PanelCard
import com.passmanager.ui.components.PillButton
import com.passmanager.ui.components.SectionFootnote
import com.passmanager.ui.theme.CardShape
import com.passmanager.ui.theme.FootnoteStyle
import kotlinx.coroutines.delay

/** The one action on this panel is a full-width pill; nothing here competes with it. */
private val ActionButtonPadding = PaddingValues(vertical = 17.dp)

@Composable
internal fun VaultLockedContent() {
    Text(
        text = stringResource(R.string.desktop_link_unlock_hint),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
internal fun SessionContent(uiState: DesktopLinkUiState, viewModel: DesktopLinkViewModel) {
    when (val sessionState = uiState.sessionState) {
        is PairingSessionState.Idle -> IdleContent(uiState, viewModel)
        is PairingSessionState.Pairing -> ConnectingContent()
        is PairingSessionState.Verifying -> VerifyingContent(sessionState)
        is PairingSessionState.Active -> ActiveSessionContent(sessionState, viewModel)
        is PairingSessionState.Ended -> EndedContent(sessionState, viewModel)
        is PairingSessionState.Error -> ErrorContent(sessionState, viewModel)
    }
    // Where the traffic goes, and where it does not. Stated on the panel that opens a socket
    // rather than buried in a help page, and only in the states where nothing is connected yet —
    // an established session has its own line about how it ends.
    if (uiState.sessionState !is PairingSessionState.Active &&
        uiState.sessionState !is PairingSessionState.Verifying &&
        uiState.sessionState !is PairingSessionState.Pairing
    ) {
        SectionFootnote(stringResource(R.string.desktop_link_network_footnote))
    }
}

@Composable
internal fun IdleContent(uiState: DesktopLinkUiState, viewModel: DesktopLinkViewModel) {
    val context = LocalContext.current
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startScanning() else viewModel.onCameraPermissionDenied()
    }
    val scanLabel = stringResource(R.string.desktop_link_scan_to_connect)

    // Quiet placeholder, not a second call to action: the panel subtitle already carries the one
    // instruction, and this card used to restate it word for word on a saturated slab.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 20.dp, vertical = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.desktop_link_not_connected),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    PillButton(
        text = scanLabel,
        icon = Icons.Default.CameraAlt,
        enabled = !uiState.isBusy,
        onClick = {
            // Ask for notifications here rather than at launch: this is the one flow that posts
            // one, so the request arrives with context the user can act on. Without it the
            // "password sent to desktop" notification is dropped silently on Android 13+.
            NotificationPermissionRequester.requestIfNeeded(context)
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                viewModel.startScanning()
            } else {
                cameraPermLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        contentPadding = ActionButtonPadding,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = scanLabel }
    )
}

@Composable
internal fun ConnectingContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
                modifier = Modifier.size(34.dp)
            )
            Text(
                stringResource(R.string.desktop_link_connecting),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun VerifyingContent(state: PairingSessionState.Verifying) {
    val totalMs = DesktopPairingConstants.VERIFY_CODE_TIMEOUT_MS.toFloat()
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(state.expiresAtMs) {
        while (true) {
            delay(500)
            now.longValue = System.currentTimeMillis()
        }
    }

    val remainingMs = (state.expiresAtMs - now.longValue).coerceAtLeast(0)
    val progress = (remainingMs / totalMs).coerceIn(0f, 1f)
    val secondsLeft = ((remainingMs + 999) / 1000).toInt()
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer

    PanelCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Text(
            text = stringResource(R.string.desktop_link_verify_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = onContainer,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Text(
            text = stringResource(R.string.desktop_link_verify_instruction),
            style = FootnoteStyle,
            textAlign = TextAlign.Center,
            color = onContainer.copy(alpha = 0.82f),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 6.dp, bottom = 18.dp)
        )

        val codeParts = remember(state.code) { state.code.chunked(3) }
        val codeStyle = MaterialTheme.typography.displaySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 36.sp,
            letterSpacing = 2.sp
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text(codeParts.getOrElse(0) { "" }, style = codeStyle, color = onContainer)
            Box(
                modifier = Modifier
                    .size(width = 14.dp, height = 2.dp)
                    .background(onContainer.copy(alpha = 0.4f))
            )
            Text(codeParts.getOrElse(1) { "" }, style = codeStyle, color = onContainer)
        }

        Text(
            text = stringResource(R.string.desktop_link_verify_desktop_ip, state.desktopIp),
            style = MaterialTheme.typography.bodySmall,
            color = onContainer.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 14.dp)
        )

        if (state.safetyNumber.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .padding(top = 18.dp, bottom = 16.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(onContainer.copy(alpha = 0.18f))
            )
            Text(
                text = stringResource(R.string.desktop_link_safety_number_label).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                color = onContainer.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                text = state.safetyNumber,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 5.sp
                ),
                color = onContainer,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp, bottom = 6.dp)
            )
            Text(
                text = stringResource(R.string.desktop_link_safety_number_hint),
                style = FootnoteStyle,
                textAlign = TextAlign.Center,
                color = onContainer.copy(alpha = 0.66f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(150),
                label = "countdown"
            )
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.size(80.dp),
                color = if (secondsLeft <= 5) {
                    MaterialTheme.colorScheme.error
                } else {
                    onContainer
                },
                trackColor = onContainer.copy(alpha = 0.22f),
                strokeWidth = 5.dp
            )
            Text(
                text = "${secondsLeft}s",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (secondsLeft <= 5) MaterialTheme.colorScheme.error else onContainer
            )
        }

        Text(
            text = stringResource(
                R.string.desktop_link_verify_attempts,
                state.attemptsRemaining
            ),
            style = MaterialTheme.typography.bodySmall,
            color = onContainer.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 10.dp)
        )
    }
}

@Composable
internal fun ActiveSessionContent(
    sessionState: PairingSessionState.Active,
    viewModel: DesktopLinkViewModel
) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val quota = DesktopPairingConstants.MAX_PW_PER_SESSION

    PanelCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = onContainer,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = stringResource(R.string.desktop_link_connected),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = onContainer,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = sessionState.desktopIp,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = onContainer.copy(alpha = 0.7f)
            )
        }

        Box(
            modifier = Modifier
                .padding(vertical = 16.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(onContainer.copy(alpha = 0.18f))
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.desktop_link_sent_label),
                style = MaterialTheme.typography.bodyMedium,
                color = onContainer
            )
            Text(
                text = stringResource(
                    R.string.desktop_link_sent_of,
                    sessionState.passwordsSent,
                    quota
                ),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = onContainer
            )
        }

        // The quota drawn as the thing it is: a strip that fills up. A bare "3 of 20" made the
        // per-session limit look like a statistic rather than a budget being spent.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            repeat(quota) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (index < sessionState.passwordsSent) {
                                onContainer
                            } else {
                                onContainer.copy(alpha = 0.22f)
                            }
                        )
                )
            }
        }

        sessionState.lastItemTitle?.let { title ->
            Text(
                text = stringResource(R.string.desktop_link_last_item, title),
                style = MaterialTheme.typography.bodySmall,
                color = onContainer.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 13.dp)
            )
        }

        Text(
            text = stringResource(R.string.desktop_link_session_footnote),
            style = FootnoteStyle,
            color = onContainer.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 13.dp)
        )
    }

    PillButton(
        text = stringResource(R.string.desktop_link_disconnect),
        icon = Icons.Default.LinkOff,
        onClick = { viewModel.disconnect() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.primary,
        contentPadding = ActionButtonPadding,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun EndedContent(
    sessionState: PairingSessionState.Ended,
    viewModel: DesktopLinkViewModel
) {
    StatusCard(
        icon = Icons.Default.LinkOff,
        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
        text = stringResource(R.string.desktop_link_session_ended, sessionState.reason),
        textColor = MaterialTheme.colorScheme.onSurface,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        bordered = true
    )
    PillButton(
        text = stringResource(R.string.desktop_link_scan_again),
        icon = Icons.Default.QrCodeScanner,
        onClick = { viewModel.startScanning() },
        contentPadding = ActionButtonPadding,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun ErrorContent(
    sessionState: PairingSessionState.Error,
    viewModel: DesktopLinkViewModel
) {
    StatusCard(
        icon = Icons.Default.ErrorOutline,
        iconTint = MaterialTheme.colorScheme.error,
        text = stringResource(R.string.desktop_link_error_with_message, sessionState.message),
        textColor = MaterialTheme.colorScheme.error,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        bordered = false
    )
    PillButton(
        text = stringResource(R.string.desktop_link_try_again),
        onClick = { viewModel.startScanning() },
        contentPadding = ActionButtonPadding,
        modifier = Modifier.fillMaxWidth()
    )
}

/** A terminal state — ended, failed — said in a card rather than as loose text above a button. */
@Composable
private fun StatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    text: String,
    textColor: Color,
    containerColor: Color,
    bordered: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (bordered) {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
                } else {
                    Modifier
                }
            )
            .clip(CardShape)
            .background(containerColor)
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
    }
}
