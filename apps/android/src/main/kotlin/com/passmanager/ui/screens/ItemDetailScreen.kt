package com.passmanager.ui.screens

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.passmanager.domain.item.ItemPayload
import com.passmanager.domain.item.SecretText
import com.passmanager.domain.item.VaultItem
import com.passmanager.ui.VaultViewModel
import com.passmanager.ui.components.PanelCard
import com.passmanager.ui.components.PanelRow
import com.passmanager.ui.components.PanelRowDivider
import com.passmanager.ui.components.SectionFootnote
import com.passmanager.vault.Attachment
import com.passmanager.vault.VaultSession
import com.passmanager.ui.label

private class DetailField(val label: String, val value: String, val secret: Boolean)

@Composable
fun ItemDetailScreen(
    model: VaultViewModel,
    item: VaultItem,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmingDelete by remember { mutableStateOf(false) }
    var viewing by remember(item.id) { mutableStateOf<Attachment?>(null) }
    val fields = remember(item) { item.detailFields() }
    val attachments = remember(item.id) { mutableStateListOf<Attachment>() }

    // Loaded once per item. Every attachment on the device has its header read to find the
    // ones that belong here, which is the cost of an attachment naming its item rather than
    // the other way round — and the reason an item's attachments cannot be orphaned by an
    // edit to the item.
    LaunchedEffect(item.id) {
        attachments.clear()
        attachments.addAll(model.attachments(item))
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        model.pickerClosed()
        if (uri != null) {
            model.attach(item, uri)
            attachments.clear()
            attachments.addAll(model.attachments(item))
        }
    }

    // Full screen and inside this activity's window, so the attachment is covered by the
    // same FLAG_SECURE as everything else. A dialog would be its own window and would not be.
    viewing?.let { open ->
        AttachmentViewerScreen(model, open) { viewing = null }
        return
    }

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
            TextButton(onEdit) { Text("Edit") }
        }

        Text(item.payload.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            item.category.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        PanelCard {
            fields.forEachIndexed { index, field ->
                DetailRow(field)
                if (index < fields.size - 1) PanelRowDivider()
            }
        }

        Text("Attachments", style = MaterialTheme.typography.titleSmall)
        PanelCard {
            if (attachments.isEmpty()) {
                Text(
                    "Nothing attached yet.",
                    Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                attachments.forEachIndexed { index, attachment ->
                    PanelRow(onClick = { viewing = attachment }) {
                        Column(Modifier.fillMaxWidth(0.72f)) {
                            Text(
                                attachment.filename.ifEmpty { "Attachment" },
                                style = MaterialTheme.typography.bodyLarge,
                                // Coloured because the row opens it. Nothing else in this
                                // screen is tappable in the same way, so the affordance has
                                // to come from somewhere.
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                readableSize(attachment.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton({
                            model.deleteAttachment(attachment.id)
                            attachments.clear()
                            attachments.addAll(model.attachments(item))
                        }) { Text("Remove") }
                    }
                    if (index < attachments.size - 1) PanelRowDivider()
                }
            }
        }

        if (attachments.size < VaultSession.MaxAttachmentsPerItem) {
            TextButton({
                model.pickerOpened()
                picker.launch("*/*")
            }) { Text("Add attachment") }
        } else {
            SectionFootnote(
                "An item holds at most ${VaultSession.MaxAttachmentsPerItem} attachments."
            )
        }

        TextButton({ confirmingDelete = true }) {
            Text("Delete", color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete this entry?") },
            confirmButton = {
                TextButton({
                    confirmingDelete = false
                    model.delete(item.id)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton({ confirmingDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailRow(field: DetailField) {
    var shown by remember { mutableStateOf(false) }
    val context = LocalContext.current

    PanelRow {
        Column(Modifier.fillMaxWidth(0.7f)) {
            Text(
                field.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                if (field.secret && !shown) "•".repeat(minOf(field.value.length, 12)) else field.value,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (field.secret) {
            TextButton({ shown = !shown }) { Text(if (shown) "Hide" else "Show") }
        }
        TextButton({ context.copySensitive(field.label, field.value, field.secret) }) {
            Text("Copy")
        }
    }
}

/**
 * Puts a value on the clipboard, marked so the system does not preview it.
 *
 * The clipboard is shared with every other application on the device, which is why the
 * eventual answer is autofill rather than copying at all. Until then this is damage control:
 * `EXTRA_IS_SENSITIVE` keeps the value out of the paste preview that Android 13 shows, and
 * on Android 13 and later the platform expires clipboard contents on its own.
 */
private fun Context.copySensitive(label: String, value: String, secret: Boolean) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, value)
    if (secret && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
}

/** Sizes as a person reads them, not as a machine stores them. */
internal fun readableSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes bytes"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${(bytes * 10 / (1024 * 1024)).toDouble() / 10} MB"
}

/** Only the fields this kind actually has, and only the ones that are filled in. */
private fun VaultItem.detailFields(): List<DetailField> {
    val fields = mutableListOf<DetailField>()
    fun plain(label: String, value: String) = fields.add(DetailField(label, value, false))
    fun hidden(label: String, value: SecretText) =
        fields.add(DetailField(label, value.reveal { it }, true))

    when (val p = payload) {
        is ItemPayload.Login -> {
            plain("Username", p.username)
            plain("Address", p.address)
            hidden("Password", p.password)
        }
        is ItemPayload.Card -> {
            plain("Cardholder", p.cardholderName)
            hidden("Number", p.cardNumber)
            hidden("Security code", p.cardCvc)
            plain("Expires", p.cardExpiry)
        }
        is ItemPayload.Bank -> {
            plain("Bank", p.bankName)
            hidden("Account", p.accountNumber)
            hidden("Password", p.password)
        }
        is ItemPayload.Identity -> {
            plain("Name", "${p.firstName} ${p.lastName}".trim())
            plain("Email", p.email)
            plain("Phone", p.phone)
            plain("Address", p.address)
            plain("Company", p.company)
        }
        is ItemPayload.Note -> Unit
    }
    hidden("Notes", payload.notes)
    return fields.filter { it.value.isNotEmpty() }
}
