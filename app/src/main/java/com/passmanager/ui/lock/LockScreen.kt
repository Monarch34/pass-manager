package com.passmanager.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import com.passmanager.ui.components.AppSnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.passmanager.R
import com.passmanager.ui.components.AppShieldLogo
import com.passmanager.ui.components.BiometricPromptEffect
import com.passmanager.ui.components.ErrorSnackbarEffect
import com.passmanager.ui.components.LoadingButton
import com.passmanager.ui.components.PillOutlinedButton
import com.passmanager.ui.components.PillPassphraseField
import com.passmanager.ui.theme.CardShape
import com.passmanager.ui.components.shakeOnTrigger

@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    onDeviceKeyLost: () -> Unit,
    viewModel: LockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var passphrase by remember { mutableStateOf("") }
    var shakeCount by remember { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    val unlockButtonLabel = stringResource(R.string.lock_unlock_button)
    val biometricButtonLabel = stringResource(R.string.lock_biometric_button)

    LaunchedEffect(uiState.shouldShakePassphraseField) {
        if (uiState.shouldShakePassphraseField) {
            shakeCount++
            viewModel.onPassphraseShakeConsumed()
        }
    }

    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked) onUnlocked()
    }

    ErrorSnackbarEffect(
        error = uiState.error,
        onErrorShown = { viewModel.clearError() },
        snackbarHostState = snackbarHostState
    )

    BiometricPromptEffect(
        cipherFlow = viewModel.biometricCipherEvent,
        title = stringResource(R.string.lock_biometric_prompt_title),
        subtitle = stringResource(R.string.lock_biometric_prompt_subtitle),
        negativeButtonText = stringResource(R.string.lock_biometric_prompt_cancel),
        onSuccess = viewModel::onBiometricSuccess,
        onError = viewModel::onBiometricError,
        onFail = viewModel::onBiometricFail
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { AppSnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppShieldLogo(size = 112.dp)

                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.lock_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.lock_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                // A permanently lost device key is not something a snackbar can carry: the user
                // needs a standing explanation and a way out, so it stays on screen with the one
                // action that leads anywhere.
                if (uiState.deviceKeyLost) {
                    Spacer(Modifier.height(24.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = CardShape,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.lock_device_key_lost),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = onDeviceKeyLost) {
                                Text(stringResource(R.string.lock_device_key_lost_action))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(26.dp))

                PillPassphraseField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = stringResource(R.string.lock_passphrase_hint),
                    imeAction = ImeAction.Done,
                    onImeAction = {
                        if (!uiState.isLoading && uiState.lockoutRemainingSeconds == 0) {
                            viewModel.unlockWithPassphrase(passphrase.toCharArray())
                            passphrase = ""
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .shakeOnTrigger(shakeCount)
                )

                if (uiState.lockoutRemainingSeconds > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            R.string.lock_throttled_countdown,
                            uiState.lockoutRemainingSeconds
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(Modifier.height(16.dp))

                LoadingButton(
                    text = unlockButtonLabel,
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.unlockWithPassphrase(passphrase.toCharArray())
                        passphrase = ""
                    },
                    isLoading = uiState.isLoading,
                    enabled = passphrase.isNotEmpty() && uiState.lockoutRemainingSeconds == 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = unlockButtonLabel
                        }
                )

                // No "or" rule between the two buttons. They are alternatives, not a fork: the
                // divider made the second one look like a fallback for when the first had failed.
                if (uiState.biometricAvailable) {
                    Spacer(Modifier.height(14.dp))
                    PillOutlinedButton(
                        text = stringResource(R.string.lock_biometric_button),
                        icon = Icons.Default.Fingerprint,
                        enabled = !uiState.isLoading,
                        onClick = {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                            viewModel.prepareBiometricCipher()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics {
                                contentDescription = biometricButtonLabel
                            }
                    )
                }
            }
        }
    }
}
