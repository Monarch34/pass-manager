package com.passmanager.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NoteAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.passmanager.domain.model.ItemCategory
import com.passmanager.ui.theme.CategoryBankTint
import com.passmanager.ui.theme.CategoryCardTint
import com.passmanager.ui.theme.CategoryIdentityTint
import com.passmanager.ui.theme.CategoryLoginTint
import com.passmanager.ui.theme.CategoryNoteTint

/**
 * UI presentation extensions for [ItemCategory].
 * Keeps Compose/Material icon and color dependencies out of the domain layer.
 */

val ItemCategory.icon: ImageVector
    get() = when (this) {
        ItemCategory.LOGIN -> Icons.Default.Key
        ItemCategory.CARD -> Icons.Default.CreditCard
        ItemCategory.NOTE -> Icons.Default.NoteAlt
        ItemCategory.IDENTITY -> Icons.Default.Person
        ItemCategory.BANK -> Icons.Default.AccountBalance
    }

val ItemCategory.tint: Color
    get() = when (this) {
        ItemCategory.LOGIN -> CategoryLoginTint
        ItemCategory.CARD -> CategoryCardTint
        ItemCategory.NOTE -> CategoryNoteTint
        ItemCategory.IDENTITY -> CategoryIdentityTint
        ItemCategory.BANK -> CategoryBankTint
    }
