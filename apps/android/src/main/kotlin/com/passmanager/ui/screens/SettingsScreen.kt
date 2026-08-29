package com.passmanager.ui.screens

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.passmanager.ui.components.SectionFootnote
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

        model.failure?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
