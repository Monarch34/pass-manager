package com.passmanager.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import com.passmanager.domain.item.ItemCategory
import com.passmanager.domain.item.ItemId
import com.passmanager.domain.item.ItemPayload
import com.passmanager.domain.item.SecretText
import com.passmanager.domain.item.VaultItem
import com.passmanager.ui.CategoryOrder
import com.passmanager.ui.VaultViewModel
import com.passmanager.ui.components.CategoryChip
import com.passmanager.ui.components.PanelField
import com.passmanager.ui.components.PillButton
import com.passmanager.ui.label

@Composable
fun AddEditItemScreen(
    model: VaultViewModel,
    existing: VaultItem?,
    onDone: () -> Unit,
) {
    var category by remember { mutableStateOf(existing?.category ?: ItemCategory.LOGIN) }
    val fields = remember(existing) { EditableFields(existing) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(top = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onDone) { Text("Cancel") }
            Text(
                if (existing == null) "New entry" else "Edit",
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(
                enabled = fields.title.isNotBlank(),
                onClick = {
                    model.save(fields.toItem(category, existing))
                    onDone()
                },
            ) { Text("Save") }
        }

        // The kind is fixed once an entry exists. Changing it would mean discarding the
        // fields the old kind had and inventing the new one's, which is a delete and a
        // create wearing one button.
        if (existing == null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CategoryOrder.forEach { option ->
                    CategoryChip(option.label, category == option) { category = option }
                }
            }
        }

        PanelField("Title", fields.title, { fields.title = it })

        when (category) {
            ItemCategory.LOGIN -> {
                PanelField("Username", fields.username, { fields.username = it })
                PanelField("Address", fields.address, { fields.address = it })
                PanelField("Password", fields.password, { fields.password = it }, secret = true)
            }
            ItemCategory.CARD -> {
                PanelField("Cardholder", fields.cardholderName, { fields.cardholderName = it })
                PanelField("Number", fields.cardNumber, { fields.cardNumber = it }, secret = true)
                PanelField("Security code", fields.cardCvc, { fields.cardCvc = it }, secret = true)
                PanelField("Expires", fields.cardExpiry, { fields.cardExpiry = it })
            }
            ItemCategory.BANK -> {
                PanelField("Bank", fields.bankName, { fields.bankName = it })
                PanelField("Account", fields.accountNumber, { fields.accountNumber = it }, secret = true)
                PanelField("Password", fields.password, { fields.password = it }, secret = true)
            }
            ItemCategory.IDENTITY -> {
                PanelField("First name", fields.firstName, { fields.firstName = it })
                PanelField("Last name", fields.lastName, { fields.lastName = it })
                PanelField("Email", fields.email, { fields.email = it })
                PanelField("Phone", fields.phone, { fields.phone = it })
                PanelField("Address", fields.address, { fields.address = it })
                PanelField("Company", fields.company, { fields.company = it })
            }
            ItemCategory.NOTE -> Unit
        }

        PanelField("Notes", fields.notes, { fields.notes = it }, singleLine = false)

        PillButton(
            "Save",
            {
                model.save(fields.toItem(category, existing))
                onDone()
            },
            enabled = fields.title.isNotBlank(),
        )
    }
}

/** One holder for every field any kind might use, so switching kind keeps what was typed. */
private class EditableFields(existing: VaultItem?) {
    var title by mutableStateOf(existing?.payload?.title.orEmpty())
    var notes by mutableStateOf(existing?.payload?.notes?.reveal { it }.orEmpty())

    var username by mutableStateOf("")
    var address by mutableStateOf("")
    var password by mutableStateOf("")
    var cardholderName by mutableStateOf("")
    var cardNumber by mutableStateOf("")
    var cardCvc by mutableStateOf("")
    var cardExpiry by mutableStateOf("")
    var bankName by mutableStateOf("")
    var accountNumber by mutableStateOf("")
    var firstName by mutableStateOf("")
    var lastName by mutableStateOf("")
    var email by mutableStateOf("")
    var phone by mutableStateOf("")
    var company by mutableStateOf("")

    /** Links have no editor yet, so they are carried rather than shown. See [toItem]. */
    private var cardIds = (existing?.payload as? ItemPayload.Bank)?.cardIds.orEmpty()
    private var previousPasswords =
        (existing?.payload as? ItemPayload.Bank)?.previousPasswords.orEmpty()

    init {
        when (val p = existing?.payload) {
            is ItemPayload.Login -> {
                username = p.username
                address = p.address
                password = p.password.reveal { it }
            }
            is ItemPayload.Card -> {
                cardholderName = p.cardholderName
                cardNumber = p.cardNumber.reveal { it }
                cardCvc = p.cardCvc.reveal { it }
                cardExpiry = p.cardExpiry
            }
            is ItemPayload.Bank -> {
                bankName = p.bankName
                accountNumber = p.accountNumber.reveal { it }
                password = p.password.reveal { it }
            }
            is ItemPayload.Identity -> {
                firstName = p.firstName
                lastName = p.lastName
                email = p.email
                phone = p.phone
                address = p.address
                company = p.company
            }
            else -> Unit
        }
    }

    fun toItem(category: ItemCategory, existing: VaultItem?): VaultItem {
        val now = System.currentTimeMillis()
        val secretNotes = SecretText.of(notes)
        val payload = when (category) {
            ItemCategory.LOGIN -> ItemPayload.Login(
                title = title, notes = secretNotes,
                username = username, address = address,
                password = SecretText.of(password),
            )
            ItemCategory.CARD -> ItemPayload.Card(
                title = title, notes = secretNotes,
                cardholderName = cardholderName,
                cardNumber = SecretText.of(cardNumber),
                cardCvc = SecretText.of(cardCvc),
                cardExpiry = cardExpiry,
            )
            ItemCategory.BANK -> ItemPayload.Bank(
                title = title, notes = secretNotes,
                bankName = bankName,
                accountNumber = SecretText.of(accountNumber),
                password = SecretText.of(password),
                // Both are carried forward though nothing here shows them. An editor that
                // saved only the fields it rendered would silently delete every card this
                // account names and every old password it remembers.
                previousPasswords = previousPasswords,
                cardIds = cardIds,
            )
            ItemCategory.IDENTITY -> ItemPayload.Identity(
                title = title, notes = secretNotes,
                firstName = firstName, lastName = lastName,
                email = email, phone = phone, address = address, company = company,
            )
            ItemCategory.NOTE -> ItemPayload.Note(title = title, notes = secretNotes)
        }
        return VaultItem(
            id = existing?.id ?: ItemId.random(),
            // An edit keeps the original creation time; only a new entry takes now.
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            payload = payload,
        )
    }
}
