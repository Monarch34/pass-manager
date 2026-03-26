package com.passmanager.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.passmanager.ui.theme.CategoryBankTint
import com.passmanager.ui.theme.CategoryCardTint
import com.passmanager.ui.theme.CategoryIdentityTint
import com.passmanager.ui.theme.CategoryLoginTint
import com.passmanager.ui.theme.CategoryNoteTint

enum class ItemCategory(
    val label: String,
    val icon: ImageVector,
    val tint: Color
) {
    LOGIN("Login", Icons.Default.Key, CategoryLoginTint),
    CARD("Card", Icons.Default.CreditCard, CategoryCardTint),
    NOTE("Note", Icons.Default.NoteAlt, CategoryNoteTint),
    IDENTITY("Identity", Icons.Default.Person, CategoryIdentityTint),
    BANK("Bank", Icons.Default.AccountBalance, CategoryBankTint);

    companion object {
        fun fromString(value: String): ItemCategory =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: LOGIN
    }
}
