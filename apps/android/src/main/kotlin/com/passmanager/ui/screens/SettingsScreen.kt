package com.passmanager.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.passmanager.data.BiometricVaultKey
import com.passmanager.ui.VaultViewModel
import com.passmanager.ui.components.PanelCard
import com.passmanager.ui.components.PanelField
import com.passmanager.ui.components.PanelRow
import com.passmanager.ui.components.PanelRowDivider
import com.passmanager.ui.components.SectionFootnote
import com.passmanager.vault.ImportPreview
import com.passmanager.ui.promptForCipher

@Composable
fun SettingsScreen(model: VaultViewModel, onBack: () -> Unit) {
    val activity = LocalActivity.current as? FragmentActivity
    val availability = remember { model.biometricAvailability }
    var enabled by remember { mutableStateOf(model.biometricsEnabled) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onBack) { Text("Back") }
            Text("Settings", style = MaterialTheme.typography.titleMedium)
            TextButton({}, enabled = false) { Text("") }
        }

        when (availability) {
            BiometricVaultKey.Availability.Ready -> {
                PanelCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.fillMaxWidth(0.75f)) {
                            Text("Unlock with biometrics", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "A second way into this vault. Your passphrase keeps working.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { wanted ->
                                if (!wanted) {
                                    model.disableBiometrics()
                                    enabled = false
                                    return@Switch
                                }
                                // Storing the key needs an authenticated cipher too, not just
                                // reading it back: the keystore key is created so that it
                                // cannot be used at all without a fresh authentication.
                                val cipher = model.cipherToEnableBiometrics()
                                if (cipher == null || activity == null) return@Switch
                                activity.promptForCipher(
                                    cipher = cipher,
                                    title = "Save your vault key",
                                    subtitle = "So you can unlock without typing your passphrase",
                                    onSuccess = {
                                        model.completeEnableBiometrics(it)
                                        enabled = model.biometricsEnabled
                                    },
                                    onFailure = {
                                        model.biometricFailed(it)
                                        enabled = model.biometricsEnabled
                                    },
                                )
                            },
                        )
                    }
                }

                // What someone is entitled to know before turning it on, said next to the
                // switch rather than in a help page nobody opens.
                SectionFootnote(
                    "The key is sealed by a key this device's hardware holds and never releases. " +
                        "It is not in any backup and cannot be moved to another phone. Adding or " +
                        "removing a fingerprint or face discards it, and your passphrase is the way back in."
                )
            }

            BiometricVaultKey.Availability.NotEnrolled -> PanelCard {
                Text(
                    "No fingerprint or face is set up on this device, so there is nothing to unlock with.",
                    Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> PanelCard {
                Text(
                    "This device cannot use biometric unlock.",
                    Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Transfer(model)

        model.failure?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

/**
 * Taking a copy off the phone, and putting one back.
 *
 * There is no account and no server, so this is the only way a vault ever leaves this device
 * and the only way one arrives. Both sit under one heading because they are two directions of
 * the same thing, and because whoever is looking for "backup" needs to find "restore" beside
 * it rather than discover later that it was never there.
 */
@Composable
private fun Transfer(model: VaultViewModel) {
    var exporting by remember { mutableStateOf(false) }
    var importingFrom by remember { mutableStateOf<Uri?>(null) }

    val saveTo = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        model.pickerClosed()
        if (uri != null) model.writeExport(uri) else model.discardExport()
    }

    val openFrom = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        model.pickerClosed()
        importingFrom = uri
    }

    // The bytes exist before a destination does, so where to put them is asked for the moment
    // they are ready rather than before the passphrase has even been typed.
    LaunchedEffect(model.pendingExport) {
        if (model.pendingExport != null) {
            model.pickerOpened()
            saveTo.launch("PassManager.pmvault")
        }
    }

    ChangePassphrase(model)

    Text("Backup", style = MaterialTheme.typography.titleSmall)
    PanelCard {
        PanelRow(onClick = { if (!model.busy) exporting = true }) {
            Column(Modifier.fillMaxWidth()) {
                Text("Export this vault", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "One file, sealed with a passphrase you choose now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PanelRowDivider()
        PanelRow(onClick = {
            if (!model.busy) {
                model.pickerOpened()
                openFrom.launch("*/*")
            }
        }) {
            Column(Modifier.fillMaxWidth()) {
                Text("Import a vault file", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Merged into this vault. Nothing is removed without asking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (model.busy) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.padding(end = 12.dp))
            SectionFootnote("Working. A file that leaves the phone is deliberately slow to open.")
        }
    }

    SectionFootnote(
        "An export is a snapshot, not a spare key: it cannot open this vault and this vault " +
            "cannot open it. Biometric unlock does not travel with it, so restoring needs the " +
            "passphrase you set here. Anyone holding the file and that passphrase can read " +
            "everything in it, and there is no way to revoke that."
    )

    if (exporting) {
        PassphrasePrompt(
            title = "Passphrase for this export",
            body = "Not your vault passphrase unless you choose it to be. Restoring needs this " +
                "exact passphrase, and nothing can recover it.",
            confirm = "Export",
            onDismiss = { exporting = false },
            onSubmit = { _, typed -> exporting = false; model.export(typed) },
        )
    }

    importingFrom?.let { uri ->
        PassphrasePrompt(
            title = "Passphrase for this file",
            body = "The passphrase that was set when this file was exported.",
            confirm = "Open",
            repeated = false,
            onDismiss = { importingFrom = null },
            onSubmit = { _, typed -> importingFrom = null; model.readImport(uri, typed) },
        )
    }

    model.importPreview?.let { ImportPrompt(model, it) }
}

/**
 * Replacing the passphrase.
 *
 * The current one is asked for even though the vault is already open. An unlocked phone left
 * on a desk must not be enough to lock its owner out of their own vault.
 */
@Composable
private fun ChangePassphrase(model: VaultViewModel) {
    var asking by remember { mutableStateOf(false) }
    var changed by remember { mutableStateOf(false) }

    Text("Passphrase", style = MaterialTheme.typography.titleSmall)
    PanelCard {
        PanelRow(onClick = { if (!model.busy) asking = true }) {
            Column(Modifier.fillMaxWidth()) {
                Text("Change passphrase", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (changed) "Changed. Biometric unlock still works."
                    else "Every other way in keeps working, and nothing is re-encrypted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (asking) {
        PassphrasePrompt(
            title = "Change your passphrase",
            body = "Exports already taken keep their own passphrases and are not affected.",
            confirm = "Change",
            askCurrent = true,
            onDismiss = { asking = false },
            onSubmit = { current, next ->
                asking = false
                model.changePassphrase(current, next) { changed = it }
            },
        )
    }
}

/** What agreeing would do, before it is done. */
@Composable
private fun ImportPrompt(model: VaultViewModel, preview: ImportPreview) {
    AlertDialog(
        onDismissRequest = { model.discardImport() },
        title = { Text(if (preview.isEmpty) "Nothing to import" else "Import this file?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (preview.isEmpty) {
                    Text("This vault already holds everything that file has.")
                } else {
                    if (preview.added.isNotEmpty()) Text("${preview.added.size} to add")
                    if (preview.replaced.isNotEmpty()) {
                        Text("${preview.replaced.size} to update with a newer version")
                    }
                    if (preview.attachmentsAdded > 0) {
                        Text("${preview.attachmentsAdded} attachments to add")
                    }
                    if (preview.removed.isNotEmpty()) {
                        // Named rather than counted. This is the only outcome that destroys
                        // something already here, and there is no undo anywhere in this app.
                        Text(
                            "Deleted on the other device, so they go here too:",
                            color = MaterialTheme.colorScheme.error,
                        )
                        preview.removed.forEach {
                            Text(
                                "\u00b7 " + it.payload.title,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton({ model.applyImport() }) {
                Text(if (preview.isEmpty) "Done" else "Import")
            }
        },
        dismissButton = {
            if (!preview.isEmpty) TextButton({ model.discardImport() }) { Text("Cancel") }
        },
    )
}

@Composable
private fun PassphrasePrompt(
    title: String,
    body: String,
    confirm: String,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
    repeated: Boolean = true,
    askCurrent: Boolean = false,
) {
    var current by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    // A length rule belongs on a passphrase being *chosen*, never on one being *entered*.
    // `repeated` is what distinguishes the two, and applying the minimum to both would make
    // a file whose passphrase is seven characters impossible to open — the file's passphrase
    // is whatever it already is, and this screen does not get a vote.
    val ready = (if (repeated) passphrase.length >= 8 else passphrase.isNotEmpty()) &&
        (!repeated || passphrase == again) &&
        (!askCurrent || current.isNotEmpty())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(body, style = MaterialTheme.typography.bodySmall)
                if (askCurrent) {
                    PanelField("Current passphrase", current, { current = it }, secret = true)
                }
                PanelField(
                    if (askCurrent) "New passphrase" else "Passphrase",
                    passphrase,
                    { passphrase = it },
                    secret = true,
                )
                if (repeated) PanelField("Repeat it", again, { again = it }, secret = true)
                if (repeated && again.isNotEmpty() && passphrase != again) {
                    Text(
                        "The two do not match.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton({ onSubmit(current, passphrase) }, enabled = ready) { Text(confirm) }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

