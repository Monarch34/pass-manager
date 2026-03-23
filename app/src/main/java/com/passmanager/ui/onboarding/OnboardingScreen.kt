package com.passmanager.ui.onboarding

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.passmanager.R
import com.passmanager.ui.components.AppShieldLogo
import com.passmanager.ui.components.ErrorSnackbarEffect
import com.passmanager.ui.components.LoadingButton
import com.passmanager.ui.components.PasswordStrengthBar
import com.passmanager.ui.components.SecureTextField

@Composable
fun OnboardingScreen(
    onVaultCreated: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onVaultCreated()
    }

    ErrorSnackbarEffect(
        error = uiState.error,
        onErrorShown = { viewModel.clearError() },
        snackbarHostState = snackbarHostState
    )

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
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppShieldLogo(size = 96.dp, iconSize = 48.dp)
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.onboarding_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(40.dp))

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        SecureTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = stringResource(R.string.onboarding_passphrase_hint),
                            imeAction = ImeAction.Next,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (passphrase.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            PasswordStrengthBar(
                                password = passphrase,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        SecureTextField(
                            value = confirm,
                            onValueChange = { confirm = it },
                            label = stringResource(R.string.onboarding_confirm_hint),
                            imeAction = ImeAction.Done,
                            onImeAction = {
                                if (!uiState.isLoading) {
                                    viewModel.createVault(passphrase.toCharArray(), confirm.toCharArray())
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                LoadingButton(
                    text = stringResource(R.string.onboarding_create_button),
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.createVault(passphrase.toCharArray(), confirm.toCharArray())
                    },
                    isLoading = uiState.isLoading,
                    enabled = passphrase.isNotEmpty() && confirm.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
