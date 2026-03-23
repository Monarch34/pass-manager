package com.passmanager.ui.desktop

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.passmanager.R
import com.passmanager.security.DesktopPairingSession
import com.passmanager.security.PairingSessionState
import kotlinx.coroutines.delay
import com.passmanager.ui.components.ErrorSnackbarEffect
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopLinkScreen(
    onOpenDrawer: () -> Unit = {},
    viewModel: DesktopLinkViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ErrorSnackbarEffect(
        error = uiState.error,
        onErrorShown = { viewModel.clearError() },
        snackbarHostState = snackbarHostState
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.desktop_link_title)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.nav_open_drawer))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.desktop_link_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
                !uiState.vaultUnlocked -> VaultLockedContent()
                uiState.isScanning -> QrScannerContent(viewModel)
                else -> SessionContent(uiState, viewModel)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VaultLockedContent() {
    Text(
        text = stringResource(R.string.desktop_link_unlock_hint),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun SessionContent(uiState: DesktopLinkUiState, viewModel: DesktopLinkViewModel) {
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
private fun IdleContent(uiState: DesktopLinkUiState, viewModel: DesktopLinkViewModel) {
    val context = LocalContext.current
    val cameraPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startScanning()
    }

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
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text(stringResource(R.string.desktop_link_scan_to_connect))
    }
}

@Composable
private fun ConnectingContent() {
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
private fun VerifyingContent(state: PairingSessionState.Verifying) {
    // Countdown timer state
    val totalMs = DesktopPairingSession.VERIFY_CODE_TIMEOUT_MS.toFloat()
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
private fun ActiveSessionContent(
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
                    com.passmanager.security.DesktopPairingSession.MAX_PW_PER_SESSION
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
private fun EndedContent(
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
private fun ErrorContent(
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

@Suppress("UnsafeOptInUsageError")
@Composable
private fun QrScannerContent(viewModel: DesktopLinkViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    val frameInFlight = remember { AtomicBoolean(false) }
    val qrCaptured = remember { AtomicBoolean(false) }

    AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        analysis.setAnalyzer(executor) { imageProxy ->
                            if (qrCaptured.get()) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            if (!frameInFlight.compareAndSet(false, true)) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val mediaImage = imageProxy.image ?: run {
                                frameInFlight.set(false)
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage, imageProxy.imageInfo.rotationDegrees
                            )
                            scanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        if (barcode.format == Barcode.FORMAT_QR_CODE) {
                                            barcode.rawValue?.let { raw ->
                                                qrCaptured.set(true)
                                                viewModel.onQrCodeScanned(raw)
                                            }
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    frameInFlight.set(false)
                                    imageProxy.close()
                                }
                        }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                                preview, analysis
                            )
                        } catch (_: Exception) { }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdownNow()
        }
    }

    TextButton(
        onClick = { viewModel.stopScanning() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.cancel))
    }
}
