package com.passmanager.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.passmanager.BuildConfig
import com.passmanager.R
import com.passmanager.domain.model.PmVaultFile
import com.passmanager.ui.common.UserMessage
import com.passmanager.ui.common.clearAllFocus
import com.passmanager.ui.common.resolve
import com.passmanager.ui.components.AppSnackbarHost
import com.passmanager.ui.components.BiometricPromptEffect
import com.passmanager.ui.components.DestructiveAction
import com.passmanager.ui.components.ErrorSnackbarEffect
import com.passmanager.ui.components.LoadingButton
import com.passmanager.ui.components.PanelCard
import com.passmanager.ui.components.PanelHeader
import com.passmanager.ui.components.PanelRow
import com.passmanager.ui.components.PanelRowDivider
import com.passmanager.ui.components.PanelSecureTextField
import com.passmanager.ui.components.PasswordStrengthBar
import com.passmanager.ui.components.SectionFootnote
import com.passmanager.ui.components.SectionHeader
import com.passmanager.ui.theme.FootnoteStyle
import com.passmanager.ui.theme.StrengthFairColor
import java.text.DateFormat
import java.util.Date

/** Vertical padding inside a card that groups several rows; each row adds its own. */
private val GroupedCardPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ErrorSnackbarEffect(
        error = (uiState.error as? SettingsError.General)?.message,
        onErrorShown = { viewModel.clearError() },
        snackbarHostState = snackbarHostState
    )

    ErrorSnackbarEffect(
        error = uiState.seedDemoMessage,
        onErrorShown = { viewModel.clearSeedDemoMessage() },
        snackbarHostState = snackbarHostState
    )

    ErrorSnackbarEffect(
        error = uiState.transferMessage,
        onErrorShown = { viewModel.clearTransferMessage() },
        snackbarHostState = snackbarHostState
    )

    // The picker backgrounds the app, so the vault may be locked by the time these fire. The
    // ViewModel decides whether to ask for the passphrase now or after the next unlock — it is
    // never asked before the picker opens.
    val exportFileName = stringResource(R.string.settings_export_file_name)
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(PmVaultFile.MIME_TYPE)
    ) { uri -> if (uri != null) viewModel.onExportFileChosen(uri.toString()) }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.onImportFileChosen(uri.toString()) }

    BiometricPromptEffect(
        cipherFlow = viewModel.pendingBiometricCipherEvent,
        title = stringResource(R.string.settings_biometric_prompt_title),
        subtitle = stringResource(R.string.settings_biometric_prompt_subtitle),
        negativeButtonText = stringResource(R.string.cancel),
        onSuccess = viewModel::onBiometricEnrollmentSuccess,
        onError = viewModel::onBiometricEnrollmentError,
        // Soft failure keeps the prompt open for another attempt; nothing to report yet.
        onFail = {}
    )

    val min1 = stringResource(R.string.settings_auto_lock_1min)
    val min5 = stringResource(R.string.settings_auto_lock_5min)
    val min15 = stringResource(R.string.settings_auto_lock_15min)
    val min30 = stringResource(R.string.settings_auto_lock_30min)
    val autoLockOptions = remember(min1, min5, min15, min30) {
        listOf(60 to min1, 300 to min5, 900 to min15, 1800 to min30)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PanelHeader(
                title = stringResource(R.string.settings_title),
                large = true,
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                navigationContentDescription = stringResource(R.string.action_back),
                onNavigationClick = onNavigateBack
            )
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) }
    ) { paddingValues: PaddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Security ───────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(stringResource(R.string.settings_section_security))

                AutoLockCard(
                    autoLockSeconds = uiState.autoLockSeconds,
                    options = autoLockOptions,
                    onSelect = { viewModel.setAutoLockTimeout(it) }
                )

                PanelCard(
                    contentPadding = GroupedCardPadding,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    PanelRow(
                        title = stringResource(R.string.settings_change_passphrase_title),
                        onClick = { viewModel.openChangePassphraseSheet() }
                    ) {
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (uiState.biometricAvailableOnDevice) {
                        PanelRowDivider()
                        PanelRow(title = stringResource(R.string.settings_biometric_title)) {
                            Switch(
                                checked = uiState.biometricEnabled,
                                onCheckedChange = { viewModel.toggleBiometric() }
                            )
                        }
                    }

                    PanelRowDivider()
                    // Deliberately one-way: there is no downgrade action here. Unsealing a vault
                    // back to passphrase-only would quietly undo the protection the user opted in
                    // to, so a bound vault is a statement of fact rather than a control.
                    PanelRow(
                        title = stringResource(R.string.device_binding_title),
                        supporting = stringResource(
                            if (uiState.isDeviceBound) {
                                R.string.device_binding_status_on
                            } else {
                                R.string.device_binding_status_off
                            }
                        ),
                        onClick = if (uiState.isDeviceBound) {
                            null
                        } else {
                            { viewModel.openDeviceBindingDialog() }
                        }
                    ) {
                        if (!uiState.isDeviceBound) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    PanelRowDivider()
                    PanelRow(
                        title = stringResource(R.string.settings_site_icons_title),
                        supporting = stringResource(R.string.settings_site_icons_short)
                    ) {
                        Switch(
                            checked = uiState.useGoogleFavicons,
                            onCheckedChange = { viewModel.setUseGoogleFavicons(it) }
                        )
                    }
                }

                // The row above says what the setting does in one line; this says exactly which
                // host is contacted and for which categories. That detail is the reason the
                // setting exists, so it stays on the screen — as a footnote, not as a paragraph
                // wedged into a list row.
                SectionFootnote(
                    stringResource(
                        if (uiState.useGoogleFavicons) {
                            R.string.settings_site_icons_subtitle_on
                        } else {
                            R.string.settings_site_icons_subtitle_off
                        }
                    )
                )
            }

            // ── Transfer ───────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionHeader(stringResource(R.string.settings_section_transfer))

                val lastExportLabel = uiState.lastExportAtMs?.let { millis ->
                    remember(millis) {
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(Date(millis))
                    }
                }
                if (lastExportLabel == null) {
                    PanelCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = StrengthFairColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.settings_never_exported),
                                style = FootnoteStyle,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                PanelCard(
                    contentPadding = GroupedCardPadding,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    PanelRow(
                        title = stringResource(R.string.settings_export_title),
                        supporting = lastExportLabel?.let {
                            stringResource(R.string.settings_export_last, it)
                        },
                        onClick = { exportPicker.launch(exportFileName) }
                    ) {
                        Icon(
                            Icons.Default.Upload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    PanelRowDivider()
                    // "*/*": .pmvault has no registered MIME type, so a narrower filter would grey
                    // the file out in the picker on most providers.
                    PanelRow(
                        title = stringResource(R.string.settings_import_title),
                        onClick = { importPicker.launch(arrayOf("*/*")) }
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                SectionFootnote(stringResource(R.string.settings_transfer_footnote))
            }

            if (BuildConfig.DEBUG) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(stringResource(R.string.settings_debug_section))
                    PanelCard(
                        contentPadding = GroupedCardPadding,
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        PanelRow(
                            title = stringResource(R.string.settings_debug_seed_demo_title),
                            supporting = stringResource(R.string.settings_debug_seed_demo_subtitle)
                        ) {
                            TextButton(
                                onClick = { viewModel.seedDemoVaultItems() },
                                enabled = !uiState.isSeedingDemo
                            ) {
                                Text(stringResource(R.string.settings_debug_seed_demo_action))
                            }
                        }
                    }
                }
            }

            DestructiveAction(
                icon = Icons.Default.Lock,
                label = stringResource(R.string.settings_lock_now),
                onClick = viewModel::lock
            )

            Spacer(Modifier.height(20.dp))
        }
    }

    when (val dialog = uiState.transferDialog) {
        is VaultTransferDialog.ExportPassphrase -> ExportPassphraseDialog(
            error = dialog.error,
            isBusy = uiState.isTransferBusy,
            onDismiss = { viewModel.dismissTransferDialog() },
            onConfirm = { passphrase, confirm -> viewModel.exportVault(passphrase, confirm) }
        )
        is VaultTransferDialog.ImportPassphrase -> ImportPassphraseDialog(
            error = dialog.error,
            isBusy = uiState.isTransferBusy,
            onDismiss = { viewModel.dismissTransferDialog() },
            onConfirm = { passphrase -> viewModel.prepareImport(passphrase) }
        )
        is VaultTransferDialog.ImportSummary -> ImportSummaryDialog(
            summary = dialog,
            isBusy = uiState.isTransferBusy,
            onDismiss = { viewModel.dismissTransferDialog() },
            onConfirm = { addOnly -> viewModel.applyImport(addOnly) }
        )
        null -> Unit
    }

    if (uiState.transferDialog == null) {
        when (val dialog = uiState.deviceBindingDialog) {
            is DeviceBindingDialog.Explain -> DeviceBindingExplainDialog(
                backupDone = dialog.backupDone,
                isBusy = uiState.isDeviceBindingBusy,
                onDismiss = { viewModel.dismissDeviceBindingDialog() },
                onExportBackup = {
                    viewModel.onUpgradeExportRequested()
                    exportPicker.launch(exportFileName)
                },
                onContinueWithoutBackup = { viewModel.requestUpgradeWithoutBackup() },
                onConfirm = { viewModel.confirmDeviceBinding() }
            )
            DeviceBindingDialog.ConfirmWithoutBackup -> DeviceBindingSkipBackupDialog(
                isBusy = uiState.isDeviceBindingBusy,
                onDismiss = { viewModel.cancelUpgradeWithoutBackup() },
                onConfirm = { viewModel.confirmDeviceBinding() }
            )
            null -> Unit
        }
    }

    if (uiState.showChangePassphraseSheet) {
        ChangePassphraseSheet(
            isChanging = uiState.isPassphraseChanging,
            error = (uiState.error as? SettingsError.PassphraseChange)?.message,
            onDismiss = { viewModel.dismissChangePassphraseSheet() },
            onConfirm = { current, new, confirm ->
                viewModel.changePassphrase(current, new, confirm)
            }
        )
    }
}

/**
 * Auto-lock: one row whose trailing half is the current value and the chevron that opens the menu.
 * The value used to live in the supporting line as prose — "Lock vault after 5 minutes" — which
 * meant the setting read the same whether or not you could change it.
 */
@Composable
private fun AutoLockCard(
    autoLockSeconds: Int,
    options: List<Pair<Int, String>>,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val selectedLabel = options.find { it.first == autoLockSeconds }?.second
        ?: "${autoLockSeconds}s"

    fun dismiss() {
        expanded = false
        focusManager.clearAllFocus()
    }

    PanelCard(
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
        onClick = { expanded = true }
    ) {
        PanelRow(
            title = stringResource(R.string.settings_auto_lock_title),
            supporting = stringResource(R.string.settings_auto_lock_subtitle)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { dismiss() },
                    modifier = Modifier
                        .widthIn(min = 168.dp)
                        .heightIn(max = 280.dp)
                ) {
                    options.forEach { (seconds, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onSelect(seconds)
                                dismiss()
                            }
                        )
                    }
                }
            }
        }
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
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = null
    ) {
        ChangePassphraseSheetBody(
            isChanging = isChanging,
            error = error,
            currentPass = currentPass,
            newPass = newPass,
            confirmPass = confirmPass,
            onCurrentPassChange = { s: String -> currentPass = s },
            onNewPassChange = { s: String -> newPass = s },
            onConfirmPassChange = { s: String -> confirmPass = s },
            onDismiss = onDismiss,
            onSubmit = {
                onConfirm(
                    currentPass.toCharArray(),
                    newPass.toCharArray(),
                    confirmPass.toCharArray()
                )
            }
        )
    }
}

@Composable
private fun ChangePassphraseSheetBody(
    isChanging: Boolean,
    error: UserMessage?,
    currentPass: String,
    newPass: String,
    confirmPass: String,
    onCurrentPassChange: (String) -> Unit,
    onNewPassChange: (String) -> Unit,
    onConfirmPassChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_change_passphrase_sheet_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp)
        )

        PanelSecureTextField(
            value = currentPass,
            onValueChange = onCurrentPassChange,
            label = stringResource(R.string.settings_current_passphrase_hint),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )

        PanelSecureTextField(
            value = newPass,
            onValueChange = onNewPassChange,
            label = stringResource(R.string.settings_new_passphrase_hint),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )

        if (newPass.isNotEmpty()) {
            PasswordStrengthBar(
                password = newPass,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )
        }

        PanelSecureTextField(
            value = confirmPass,
            onValueChange = onConfirmPassChange,
            label = stringResource(R.string.settings_confirm_passphrase_hint),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )

        if (error != null) {
            Text(
                text = error.resolve(),
                color = MaterialTheme.colorScheme.error,
                style = FootnoteStyle,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        SectionFootnote(stringResource(R.string.settings_change_passphrase_sheet_subtitle))

        LoadingButton(
            text = stringResource(R.string.settings_update_passphrase_button),
            onClick = onSubmit,
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
