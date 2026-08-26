package com.passmanager.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.passmanager.R

/**
 * The v1 → v2 explanation, and the backup gate.
 *
 * The confirm button stays disabled until [DeviceBindingDialog.Explain.backupDone] — device
 * binding is irreversible and unrecoverable without a backup file, so the flow makes the backup
 * the path of least resistance rather than a warning people scroll past. Skipping it is possible,
 * but only through a second confirmation that says exactly what is being risked.
 */
@Composable
fun DeviceBindingExplainDialog(
    backupDone: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onExportBackup: () -> Unit,
    onContinueWithoutBackup: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text(stringResource(R.string.device_binding_dialog_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.device_binding_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.device_binding_dialog_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        if (backupDone) {
                            R.string.device_binding_export_done
                        } else {
                            R.string.device_binding_export_required
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (backupDone) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (!backupDone) {
                    TextButton(
                        onClick = onExportBackup,
                        enabled = !isBusy,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(stringResource(R.string.device_binding_export_action))
                    }
                    TextButton(onClick = onContinueWithoutBackup, enabled = !isBusy) {
                        Text(
                            text = stringResource(R.string.device_binding_skip_backup),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = backupDone && !isBusy) {
                Text(stringResource(R.string.device_binding_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/** The second gate: proceeding with no backup at all, stated in as many words. */
@Composable
fun DeviceBindingSkipBackupDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text(stringResource(R.string.device_binding_skip_backup_title)) },
        text = {
            Text(
                text = stringResource(R.string.device_binding_skip_backup_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isBusy) {
                Text(
                    text = stringResource(R.string.device_binding_skip_backup_confirm),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
