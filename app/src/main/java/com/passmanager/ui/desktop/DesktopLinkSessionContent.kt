package com.passmanager.ui.desktop

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.passmanager.domain.model.DesktopPairingConstants
import com.passmanager.domain.model.PairingSessionState
import kotlinx.coroutines.delay

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
}

@Composable
internal fun IdleContent(uiState: DesktopLinkUiState, viewModel: DesktopLinkViewModel) {
    val context = LocalContext.current
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startScanning()
    }
    val scanLabel = stringResource(R.string.desktop_link_scan_to_connect)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.QrCodeScanner,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.desktop_link_scan_qr_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }

    Button(
        onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                viewModel.startScanning()
            } else {
                cameraPermLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        enabled = !uiState.isBusy,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = scanLabel },
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Icon(
            Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(scanLabel)
    }
}

@Composable
internal fun ConnectingContent() {
    Box(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.desktop_link_connecting),
                style = MaterialTheme.typography.bodyLarge
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.desktop_link_verify_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.desktop_link_verify_instruction),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(12.dp))

            val codeParts = remember(state.code) { state.code.chunked(3) }
            val codeStyle = MaterialTheme.typography.displaySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = codeParts.getOrElse(0) { "" },
                    style = codeStyle,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = codeParts.getOrElse(1) { "" },
                    style = codeStyle,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.desktop_link_verify_desktop_ip, state.desktopIp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )

            if (state.safetyNumber.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.desktop_link_safety_number_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.safetyNumber,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 4.sp
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(R.string.desktop_link_safety_number_hint),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
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
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                    strokeWidth = 5.dp
                )
                Text(
                    text = "${secondsLeft}s",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
internal fun ActiveSessionContent(
    sessionState: PairingSessionState.Active,
    viewModel: DesktopLinkViewModel
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.desktop_link_connected),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.desktop_link_desktop_at, sessionState.desktopIp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(
                    R.string.desktop_link_passwords_sent,
                    sessionState.passwordsSent,
                    DesktopPairingConstants.MAX_PW_PER_SESSION
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            sessionState.lastItemTitle?.let { title ->
                Text(
                    text = stringResource(R.string.desktop_link_last_item, title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }

    FilledTonalButton(
        onClick = { viewModel.disconnect() },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(stringResource(R.string.desktop_link_disconnect))
    }
}

@Composable
internal fun EndedContent(
    sessionState: PairingSessionState.Ended,
    viewModel: DesktopLinkViewModel
) {
    Text(
        text = stringResource(R.string.desktop_link_session_ended, sessionState.reason),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Button(
        onClick = { viewModel.startScanning() },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(stringResource(R.string.desktop_link_scan_again))
    }
}

@Composable
internal fun ErrorContent(
    sessionState: PairingSessionState.Error,
    viewModel: DesktopLinkViewModel
) {
    Text(
        text = stringResource(R.string.desktop_link_error_with_message, sessionState.message),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error
    )
    Button(
        onClick = { viewModel.startScanning() },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Text(stringResource(R.string.desktop_link_try_again))
    }
}
