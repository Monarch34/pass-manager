package com.passmanager.ui.nudge

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.passmanager.R

/**
 * Hosts the one-time device-binding offer and the backup reminders.
 *
 * Neither prompt does the work itself — both hand off to Settings, so the upgrade and export
 * flows exist in exactly one place, with one export gate and one set of confirmations. A prompt
 * that could also perform the upgrade would be a second, weaker copy of that flow.
 */
@Composable
fun VaultNudgeHost(
    onOpenSettings: () -> Unit,
    viewModel: VaultNudgeViewModel = hiltViewModel()
) {
    val nudge by viewModel.nudge.collectAsStateWithLifecycle()

    when (val current = nudge) {
        VaultNudge.DeviceBinding -> NudgeDialog(
            title = stringResource(R.string.device_binding_prompt_title),
            body = stringResource(R.string.device_binding_prompt_body),
            confirmLabel = stringResource(R.string.device_binding_prompt_review),
            dismissLabel = stringResource(R.string.device_binding_prompt_dismiss),
            onConfirm = {
                // Marks the prompt spent either way: reviewing counts as having been asked.
                viewModel.dismissDeviceBindingPrompt()
                onOpenSettings()
            },
            onDismiss = viewModel::dismissDeviceBindingPrompt
        )

        VaultNudge.FirstBackup -> NudgeDialog(
            title = stringResource(R.string.backup_reminder_title),
            body = stringResource(R.string.backup_reminder_first),
            confirmLabel = stringResource(R.string.backup_reminder_action),
            dismissLabel = stringResource(R.string.backup_reminder_dismiss),
            onConfirm = {
                viewModel.snoozeBackupReminder()
                onOpenSettings()
            },
            onDismiss = viewModel::snoozeBackupReminder
        )

        is VaultNudge.BackupOverdue -> NudgeDialog(
            title = stringResource(R.string.backup_reminder_title),
            body = stringResource(R.string.backup_reminder_overdue, current.days),
            confirmLabel = stringResource(R.string.backup_reminder_action),
            dismissLabel = stringResource(R.string.backup_reminder_dismiss),
            onConfirm = {
                viewModel.snoozeBackupReminder()
                onOpenSettings()
            },
            onDismiss = viewModel::snoozeBackupReminder
        )

        null -> Unit
    }
}

@Composable
private fun NudgeDialog(
    title: String,
    body: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } }
    )
}
