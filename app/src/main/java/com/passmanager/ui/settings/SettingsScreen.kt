package com.passmanager.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.passmanager.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.passmanager.security.biometric.BiometricHelper
import com.passmanager.ui.common.resolve
import com.passmanager.ui.common.UserMessage
import com.passmanager.ui.components.ErrorSnackbarEffect
import com.passmanager.ui.components.LoadingButton
import com.passmanager.ui.components.PasswordStrengthBar
import com.passmanager.ui.components.SecureTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onVaultLocked: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    ErrorSnackbarEffect(
        error = uiState.error,
        onErrorShown = { viewModel.clearError() },
        snackbarHostState = snackbarHostState
    )

    LaunchedEffect(Unit) {
        viewModel.pendingBiometricCipherEvent.collect { cipher ->
            val activity = context as? FragmentActivity ?: return@collect
            val helper = BiometricHelper(context)
            helper.showPrompt(
                activity = activity,
                cipher = cipher,
                title = context.getString(R.string.settings_biometric_prompt_title),
                subtitle = context.getString(R.string.settings_biometric_prompt_subtitle),
                negativeButtonText = context.getString(R.string.cancel),
                onSuccess = { authenticatedCipher -> viewModel.onBiometricEnrollmentSuccess(authenticatedCipher) },
                onError = { _ -> }
            )
        }
    }

    LaunchedEffect(uiState.vaultLocked) {
        if (uiState.vaultLocked) onVaultLocked()
    }

    val min1  = stringResource(R.string.settings_auto_lock_1min)
    val min5  = stringResource(R.string.settings_auto_lock_5min)
    val min15 = stringResource(R.string.settings_auto_lock_15min)
    val min30 = stringResource(R.string.settings_auto_lock_30min)
    val autoLockOptions = remember(min1, min5, min15, min30) {
        listOf(60 to min1, 300 to min5, 900 to min15, 1800 to min30)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.settings_section_security),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            if (uiState.biometricAvailableOnDevice) {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.settings_biometric_title), style = MaterialTheme.typography.titleSmall)
                    },
                    supportingContent = {
                        Text(
                            stringResource(R.string.settings_biometric_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        SettingIconBox(
                            icon = Icons.Default.Fingerprint,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = uiState.biometricEnabled,
                            onCheckedChange = { viewModel.toggleBiometric() }
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.settings_change_passphrase_title), style = MaterialTheme.typography.titleSmall)
                },
                supportingContent = {
                    Text(
                        stringResource(R.string.settings_change_passphrase_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    SettingIconBox(
                        icon = Icons.Default.VpnKey,
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        iconTint = MaterialTheme.colorScheme.tertiary
                    )
                },
                modifier = Modifier.clickable { viewModel.openChangePassphraseSheet() }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            Text(
                text = stringResource(R.string.settings_section_preferences),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.settings_site_icons_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                },
                supportingContent = {
                    Text(
                        stringResource(
                            if (uiState.useGoogleFavicons) {
                                R.string.settings_site_icons_subtitle_google
                            } else {
                                R.string.settings_site_icons_subtitle_private
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    SettingIconBox(
                        icon = Icons.Default.Public,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Switch(
                        checked = uiState.useGoogleFavicons,
                        onCheckedChange = { viewModel.setUseGoogleFavicons(it) }
                    )
                }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            var dropdownExpanded by remember { mutableStateOf(false) }
            val selectedLabel = autoLockOptions.find { it.first == uiState.autoLockSeconds }?.second
                ?: "${uiState.autoLockSeconds}s"

            ListItem(
                headlineContent = {
                    Text(stringResource(R.string.settings_auto_lock_title), style = MaterialTheme.typography.titleSmall)
                },
                supportingContent = {
                    Text(
                        stringResource(R.string.settings_auto_lock_subtitle, selectedLabel),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    SettingIconBox(
                        icon = Icons.Default.Timer,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        iconTint = MaterialTheme.colorScheme.secondary
                    )
                }
            )

            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = selectedLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                // Material3 1.3.x (Compose BOM 2024.09) exposes DropdownMenu here; ExposedDropdownMenu() is added in newer material3.
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    autoLockOptions.forEach { (seconds, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                viewModel.setAutoLockTimeout(seconds)
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }

    if (uiState.showChangePassphraseSheet) {
        ChangePassphraseSheet(
            isChanging = uiState.isPassphraseChanging,
            error = uiState.passphraseChangeError,
            onDismiss = { viewModel.dismissChangePassphraseSheet() },
            onConfirm = { current, new, confirm ->
                viewModel.changePassphrase(current, new, confirm)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangePassphraseSheet(
    isChanging: Boolean,
    error: UserMessage?,
    onDismiss: () -> Unit,
    onConfirm: (current: CharArray, new: CharArray, confirm: CharArray) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var currentPass by remember { mutableStateOf("") }
    var newPass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_change_passphrase_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = stringResource(R.string.settings_change_passphrase_sheet_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            SecureTextField(
                value = currentPass,
                onValueChange = { currentPass = it },
                label = stringResource(R.string.settings_current_passphrase_hint),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            SecureTextField(
                value = newPass,
                onValueChange = { newPass = it },
                label = stringResource(R.string.settings_new_passphrase_hint),
                modifier = Modifier.fillMaxWidth()
            )

            if (newPass.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                PasswordStrengthBar(
                    password = newPass,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            SecureTextField(
                value = confirmPass,
                onValueChange = { confirmPass = it },
                label = stringResource(R.string.settings_confirm_passphrase_hint),
                modifier = Modifier.fillMaxWidth()
            )

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error.resolve(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(16.dp))

            LoadingButton(
                text = stringResource(R.string.settings_update_passphrase_button),
                onClick = {
                    onConfirm(
                        currentPass.toCharArray(),
                        newPass.toCharArray(),
                        confirmPass.toCharArray()
                    )
                },
                isLoading = isChanging,
                enabled = currentPass.isNotEmpty() && newPass.isNotEmpty() && confirmPass.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )

            TextButton(
                onClick = onDismiss,
                enabled = !isChanging,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun SettingIconBox(
    icon: ImageVector,
    containerColor: androidx.compose.ui.graphics.Color,
    iconTint: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
