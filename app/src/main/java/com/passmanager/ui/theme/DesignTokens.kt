package com.passmanager.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Shapes and text styles the panels share. [AppShapes] carries the Material roles a component
// library reads for itself; these are the sizes the design names directly, and a screen that wants
// "the card radius" should say so rather than reach for whichever Material role happens to equal
// it today.

/** Panel card: the grouped block every screen builds its content out of. */
val CardShape = RoundedCornerShape(20.dp)

/** Text field: a tighter radius than the card, so a field inside a card still reads as a field. */
val FieldShape = RoundedCornerShape(12.dp)

/** Buttons, chips-as-buttons and the search bar — anything whose ends are fully round. */
val PillShape = RoundedCornerShape(100.dp)

/** Category filter chip: square-ish, so it does not compete with the pill buttons. */
val FilterChipShape = RoundedCornerShape(8.dp)

/** The tinted plate behind a category glyph, favicon or photo in a list row. */
val PlateShape = RoundedCornerShape(12.dp)

/**
 * One-sentence explanation under a group of controls. Leading is looser than [Typography.bodySmall]
 * so a sentence that wraps still reads as prose rather than as two stacked labels.
 */
val FootnoteStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp)

/**
 * Section heading over a group of cards — uppercase, tracked out, and small enough that the
 * controls under it stay the loudest thing in the group.
 */
val SectionHeaderStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.labelMedium

/** Sub-screen title: lighter than [Typography.titleLarge], which is the tab-bar weight. */
val SubScreenTitleStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.titleLarge.copy(
        fontWeight = FontWeight.Medium,
        lineHeight = 29.sp
    )

/** Centred body copy in an empty state. */
val EmptyStateBodyStyle: TextStyle
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.typography.bodyMedium.copy(
        lineHeight = 21.sp,
        textAlign = TextAlign.Center
    )
