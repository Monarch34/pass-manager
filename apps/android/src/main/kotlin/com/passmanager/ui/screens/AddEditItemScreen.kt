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
import com.passmanager.ui.components.PanelCard
import com.passmanager.ui.components.PanelField
import com.passmanager.ui.components.PanelRow
import com.passmanager.ui.components.PanelRowDivider
import com.passmanager.ui.components.PillButton
import com.passmanager.ui.components.SectionFootnote
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
                CardLinks(model, fields)
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

/**
 * The cards an account issues, chosen from the cards this vault already holds.
 *
 * There is no free-text field here and there cannot be: a link is an identifier, and one
 * typed by hand would name nothing. So the editor offers what exists, which also means a
 * bank can only be linked to a card that has already been entered.
 */
@Composable
private fun CardLinks(model: VaultViewModel, fields: EditableFields) {
    val cards = model.items.filter { it.category == ItemCategory.CARD }
    val linked = fields.cardIds.mapNotNull { id -> cards.firstOrNull { it.id == id } }
    val unlinked = cards.filterNot { it.id in fields.cardIds }

    Text("Cards", style = MaterialTheme.typography.titleSmall)

    if (cards.isEmpty()) {
        SectionFootnote("Add a card entry first, then it can be linked to this account.")
        return
    }

    PanelCard {
        linked.forEachIndexed { index, card ->
            PanelRow {
                Text(card.payload.title, Modifier.fillMaxWidth(0.72f))
                TextButton({ fields.unlink(card.id) }) { Text("Unlink") }
            }
            if (index < linked.size - 1) PanelRowDivider()
        }
        if (linked.isNotEmpty() && unlinked.isNotEmpty()) PanelRowDivider()
        unlinked.forEachIndexed { index, card ->
            PanelRow {
                Text(
                    card.payload.title,
                    Modifier.fillMaxWidth(0.72f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton({ fields.link(card.id) }) { Text("Link") }
            }
            if (index < unlinked.size - 1) PanelRowDivider()
        }
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

    /**
     * The cards this account issues.
     *
     * Held whole, including identifiers that name nothing in this vault. Those are never
     * rendered — there is nothing to render — and [link] and [unlink] cannot reach them,
     * which is the point: a card deleted on another device leaves every bank that names it
     * dangling here, and an editor that saved only what it could resolve would delete those
     * links permanently the first time anyone opened this screen.
     */
    var cardIds by mutableStateOf((existing?.payload as? ItemPayload.Bank)?.cardIds.orEmpty())
        private set

    private val earlierBank = existing?.payload as? ItemPayload.Bank

    fun link(id: ItemId) {
        if (id !in cardIds) cardIds = cardIds + id
    }

    fun unlink(id: ItemId) {
        cardIds = cardIds - id
    }

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
            // The history is not a field this screen renders, so it is not one this screen
            // decides. withHistoryFrom carries the old list forward and captures the password
            // being replaced, in the model, where both applications reach the same answer.
            ItemCategory.BANK -> ItemPayload.Bank(
                title = title, notes = secretNotes,
                bankName = bankName,
                accountNumber = SecretText.of(accountNumber),
                password = SecretText.of(password),
                cardIds = cardIds,
            ).withHistoryFrom(earlierBank)
            ItemCategory.IDENTITY -> ItemPayload.Identity(
                title = title, notes = secretNotes,
                firstName = firstName, lastName = lastName,
                email = email, phone = phone, address = address, company = company,
            )
            ItemCategory.NOTE -> ItemPayload.Note(title = title, notes = secretNotes)
        }
        // An edit keeps the identity, the creation time, and the modification time of an
        // entry nothing was actually typed into. All three rules live on the item.
        return existing?.edited(payload, now)
            ?: VaultItem(id = ItemId.random(), createdAt = now, updatedAt = now, payload = payload)
    }
}
