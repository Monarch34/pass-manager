package com.passmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.passmanager.ui.VaultViewModel
import com.passmanager.ui.components.PanelField
import com.passmanager.ui.components.PillButton
import com.passmanager.ui.components.SectionFootnote

@Composable
fun CreateVaultScreen(model: VaultViewModel) {
    var passphrase by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }

    val tooShort = passphrase.isNotEmpty() && passphrase.length < 8
    val mismatch = repeated.isNotEmpty() && passphrase != repeated
    val ready = passphrase.length >= 8 && passphrase == repeated

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .padding(top = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Create your vault", style = MaterialTheme.typography.headlineMedium)
        SectionFootnote("Everything stays on this device. There is no account and no server.")

        PanelField("Passphrase", passphrase, { passphrase = it }, secret = true)
        PanelField("Repeat it", repeated, { repeated = it }, secret = true)

        // Said once, plainly, before there is anything to lose. It is not a warning about a
        // risky option; it is how the design works.
        SectionFootnote(
            "If you forget this passphrase the vault cannot be opened by anyone, including us. " +
                "There is no reset."
        )

        when {
            tooShort -> Text(
                "At least 8 characters.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            mismatch -> Text(
                "The two do not match.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        PillButton("Create vault", { model.create(passphrase) }, enabled = ready)

        model.failure?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun LockScreen(model: VaultViewModel) {
    var passphrase by remember { mutableStateOf("") }
    var confirmingReset by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .padding(top = 72.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Vault locked", style = MaterialTheme.typography.headlineMedium)

        PanelField("Passphrase", passphrase, { passphrase = it }, secret = true)

        PillButton(
            "Unlock",
            {
                model.unlock(passphrase)
                passphrase = ""
            },
            enabled = passphrase.isNotEmpty(),
        )

        model.failure?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        TextButton({ confirmingReset = true }) { Text("Forgotten passphrase") }
    }

    if (confirmingReset) {
        AlertDialog(
            onDismissRequest = { confirmingReset = false },
            title = { Text("Delete this vault?") },
            text = {
                Text(
                    "A vault cannot be opened without its passphrase. Deleting it and starting " +
                        "again is the only option, and everything in it is lost."
                )
            },
            confirmButton = {
                TextButton({
                    confirmingReset = false
                    model.startOver()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton({ confirmingReset = false }) { Text("Cancel") }
            },
        )
    }
}
