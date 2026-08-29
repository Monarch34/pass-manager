package com.passmanager.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.passmanager.domain.item.ItemCategory
import com.passmanager.domain.item.ItemPayload
import com.passmanager.domain.item.VaultItem

/** The order the filter row shows them in, stated rather than left to enum declaration order. */
val CategoryOrder = listOf(
    ItemCategory.LOGIN,
    ItemCategory.CARD,
    ItemCategory.NOTE,
    ItemCategory.IDENTITY,
    ItemCategory.BANK,
)

val ItemCategory.label: String
    get() = when (this) {
        ItemCategory.LOGIN -> "Login"
        ItemCategory.CARD -> "Card"
        ItemCategory.NOTE -> "Note"
        ItemCategory.IDENTITY -> "Identity"
        ItemCategory.BANK -> "Bank"
    }

/**
 * One colour per kind, so a row reads as a card or a bank before its text is read.
 *
 * Fixed values rather than roles from the scheme: these carry meaning, and a palette that
 * re-tinted them would make two kinds of entry look alike.
 */
@Composable
@ReadOnlyComposable
fun ItemCategory.color(): Color = when (this) {
    ItemCategory.LOGIN -> MaterialTheme.colorScheme.primary
    ItemCategory.CARD -> Color(0xFF7C3AED)
    ItemCategory.NOTE -> Color(0xFFB45309)
    ItemCategory.IDENTITY -> Color(0xFF0284C7)
    ItemCategory.BANK -> Color(0xFF15803D)
}

/**
 * The line under an entry's title in the list.
 *
 * Never a secret. This is the screen that is on display in a coffee shop, and the one the
 * recents thumbnail would have captured if `FLAG_SECURE` were not set.
 */
fun VaultItem.listSubtitle(): String = when (val p = payload) {
    is ItemPayload.Login -> p.username.ifEmpty { p.address }
    is ItemPayload.Card -> p.cardholderName
    is ItemPayload.Bank -> p.bankName
    is ItemPayload.Identity -> p.email.ifEmpty { "${p.firstName} ${p.lastName}".trim() }
    is ItemPayload.Note -> ""
}
