package com.passmanager.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.passmanager.R
import com.passmanager.ui.common.resolve
import com.passmanager.ui.components.LoadingButton

/**
 * Shown when a device-bound vault's Keystore key is permanently gone.
 *
 * A screen rather than a dialog or a toast on purpose: this is a dead end that needs explaining,
 * not a transient notice. It says plainly that the passphrase is not the problem, and offers the
 * single action that can move the user forward — erasing the vault so a backup can be imported
 * into a fresh one.
 */
@Composable
fun VaultRecoveryScreen(
    onNavigateBack: () -> Unit,
    onVaultReset: () -> Unit,
    viewModel: VaultRecoveryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val confirmationWord = stringResource(R.string.recovery_confirmation_word)

    LaunchedEffect(uiState.isReset) {
        if (uiState.isReset) onVaultReset()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.height(40.dp)
            )
            Text(
                text = stringResource(R.string.recovery_headline),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.recovery_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.recovery_next_steps),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.recovery_type_to_confirm, confirmationWord),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            OutlinedTextField(
                value = uiState.typedConfirmation,
                onValueChange = viewModel::onConfirmationChanged,
                label = { Text(stringResource(R.string.recovery_input_hint)) },
                singleLine = true,
                enabled = !uiState.isResetting,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            uiState.error?.let { error ->
                Text(
                    text = error.resolve(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            LoadingButton(
                text = stringResource(R.string.recovery_reset_action),
                onClick = { viewModel.resetVault(confirmationWord) },
                isLoading = uiState.isResetting,
                enabled = uiState.typedConfirmation.trim() == confirmationWord,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.fillMaxWidth()
            )

            TextButton(
                onClick = onNavigateBack,
                enabled = !uiState.isResetting,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.recovery_cancel))
            }
        }
    }
}
