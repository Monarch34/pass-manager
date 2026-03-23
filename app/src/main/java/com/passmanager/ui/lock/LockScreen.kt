package com.passmanager.ui.lock

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.passmanager.R
import com.passmanager.ui.components.AppShieldLogo
import com.passmanager.security.biometric.BiometricHelper
import com.passmanager.ui.components.ErrorSnackbarEffect
import com.passmanager.ui.components.LoadingButton
import com.passmanager.ui.components.SecureTextField
import com.passmanager.ui.components.shakeOnTrigger

@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    viewModel: LockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var passphrase by remember { mutableStateOf("") }
    var shakeCount by remember { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val view = LocalView.current

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

    LaunchedEffect(Unit) {
        viewModel.biometricCipherEvent.collect { cipher ->
            val activity = context as? FragmentActivity ?: return@collect
            val helper = BiometricHelper(context)
            helper.showPrompt(
                activity = activity,
                cipher = cipher,
                title = context.getString(R.string.lock_biometric_prompt_title),
                subtitle = context.getString(R.string.lock_biometric_prompt_subtitle),
                negativeButtonText = context.getString(R.string.lock_biometric_prompt_cancel),
                onSuccess = { authenticatedCipher -> viewModel.onBiometricSuccess(authenticatedCipher) },
                onError = { _ -> }
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                AppShieldLogo(size = 112.dp, iconSize = 56.dp)

                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.lock_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.lock_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(36.dp))

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .shakeOnTrigger(shakeCount)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        SecureTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = stringResource(R.string.lock_passphrase_hint),
                            imeAction = ImeAction.Done,
                            onImeAction = {
                                if (!uiState.isLoading) {
                                    viewModel.unlockWithPassphrase(passphrase.toCharArray())
                                    passphrase = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                LoadingButton(
                    text = stringResource(R.string.lock_unlock_button),
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.unlockWithPassphrase(passphrase.toCharArray())
                        passphrase = ""
                    },
                    isLoading = uiState.isLoading,
                    enabled = passphrase.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (uiState.biometricAvailable) {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.lock_or_divider),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(
                        onClick = {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                            viewModel.prepareBiometricCipher()
                        },
                        enabled = !uiState.isLoading,
                        shape = MaterialTheme.shapes.extraLarge,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.lock_biometric_button))
                    }
                }
            }
        }
    }
}
