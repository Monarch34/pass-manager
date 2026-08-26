package com.passmanager.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.passmanager.R
import com.passmanager.ui.common.UserMessage
import com.passmanager.ui.common.resolve
import com.passmanager.ui.components.PasswordStrengthBar
import com.passmanager.ui.components.SecureTextField

/**
 * Asks for the passphrase that will protect the exported file.
 *
 * It is deliberately a separate secret from the master passphrase, and the copy says so: the file
 * leaves the device, so whoever gets it needs only this one string to open the whole vault.
 */
@Composable
fun ExportPassphraseDialog(
    error: UserMessage?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (passphrase: CharArray, confirm: CharArray) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text(stringResource(R.string.settings_export_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.settings_export_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                SecureTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = stringResource(R.string.settings_export_passphrase_hint),
                    modifier = Modifier.fillMaxWidth()
                )
                if (passphrase.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    PasswordStrengthBar(password = passphrase, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(12.dp))
                SecureTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = stringResource(R.string.settings_export_confirm_hint),
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(passphrase.toCharArray(), confirm.toCharArray()) },
                enabled = !isBusy && passphrase.isNotEmpty() && confirm.isNotEmpty()
            ) {
                Text(stringResource(R.string.settings_export_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** Asks for the passphrase a `.pmvault` file was written with. */
@Composable
fun ImportPassphraseDialog(
    error: UserMessage?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (passphrase: CharArray) -> Unit
) {
    var passphrase by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text(stringResource(R.string.settings_import_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.settings_import_dialog_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                SecureTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = stringResource(R.string.settings_import_passphrase_hint),
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(passphrase.toCharArray()) },
                enabled = !isBusy && passphrase.isNotEmpty()
            ) {
                Text(stringResource(R.string.settings_import_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * The mandatory pre-apply review: how many entries are new, which existing ones the file is about
 * to replace, and the "add only" escape hatch that skips every overwrite.
 */
@Composable
fun ImportSummaryDialog(
    summary: VaultTransferDialog.ImportSummary,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (addOnly: Boolean) -> Unit
) {
    var addOnly by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text(stringResource(R.string.settings_import_summary_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(
                        R.string.settings_import_summary_counts,
                        summary.newCount,
                        summary.overwriteCount
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
                if (summary.newCount == 0 && summary.overwriteCount == 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_import_summary_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (summary.overwrittenTitles.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.settings_import_summary_overwritten),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Column(modifier = Modifier.heightIn(max = 180.dp)) {
                        summary.overwrittenTitles.forEach { title ->
                            Text(
                                text = "• $title",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                if (summary.skippedCount > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(
                            R.string.settings_import_summary_skipped,
                            summary.skippedCount
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (summary.overwriteCount > 0) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Checkbox(
                            checked = addOnly,
                            onCheckedChange = { addOnly = it },
                            enabled = !isBusy
                        )
                        Text(
                            text = stringResource(R.string.settings_import_add_only),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(addOnly) },
                enabled = !isBusy && (summary.newCount > 0 || summary.overwriteCount > 0)
            ) {
                Text(stringResource(R.string.settings_import_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
