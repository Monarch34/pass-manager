package com.passmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.passmanager.ui.theme.CardShape
import com.passmanager.ui.theme.EmptyStateBodyStyle
import com.passmanager.ui.theme.FieldShape
import com.passmanager.ui.theme.FilterChipShape
import com.passmanager.ui.theme.FootnoteStyle
import com.passmanager.ui.theme.PillShape
import com.passmanager.ui.theme.SectionHeaderStyle
import com.passmanager.ui.theme.SubScreenTitleStyle

// The blocks every panel is built out of. They exist so the surface stays quiet: a card is one
// filled shape with no border, a field is one hairline with no fill behind the label, and the
// prose under a group is always the same size. Screens that hand-rolled these drifted apart —
// three card radii and two footnote sizes were live at once, and the drift read as noise.

/** Uppercase heading over a group of cards. Sits inside the group's own horizontal margin. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        text = text.uppercase(),
        style = SectionHeaderStyle,
        color = color,
        modifier = modifier.padding(horizontal = 4.dp)
    )
}

/**
 * The one sentence that explains a group. Deliberately footnote-sized: it is there for the reader
 * who stopped, not for the one scanning, and at body size it out-shouted the controls above it.
 */
@Composable
fun SectionFootnote(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign: TextAlign? = null
) {
    Text(
        text = text,
        style = FootnoteStyle,
        color = color,
        textAlign = textAlign,
        modifier = modifier.padding(horizontal = 4.dp)
    )
}

/**
 * A grouped block of content. No border and no elevation — the fill alone separates it from the
 * canvas, and a card that also carried a hairline read as two nested boxes.
 */
@Composable
fun PanelCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(containerColor)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content
    )
}

/**
 * A text input and its label inside one hairline box. The label lives inside the border rather
 * than floating through it: a floating label has to erase the stroke behind itself, and against
 * the card fill the erased notch was visible.
 */
@Composable
fun LabeledField(
    label: String,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, FieldShape)
            .clip(FieldShape)
            .background(containerColor)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = labelColor,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            content()
        }
        trailing?.invoke()
    }
}

/**
 * Destructive action — delete, lock now. Outlined rather than filled: it is the only red on the
 * screen, and a filled red block at the foot of every detail panel read as an error state.
 */
@Composable
fun DestructiveAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardShape)
            .clip(CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Panel header. [large] is the top-level voice — Vault, Generator, Settings, the places a drawer
 * lands on; anything pushed onto the stack uses the quieter title, so the reader can tell at a
 * glance whether Back goes anywhere.
 *
 * The window is edge-to-edge, so a header at the top of a Scaffold has to hold itself clear of the
 * status bar the way Material's own app bar does — [insetTop] is that inset, and it is off for a
 * header inside a bottom sheet, where the sheet is already below the bar.
 */
@Composable
fun PanelHeader(
    title: String,
    modifier: Modifier = Modifier,
    large: Boolean = false,
    insetTop: Boolean = true,
    navigationIcon: ImageVector? = null,
    navigationContentDescription: String? = null,
    onNavigationClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (insetTop) Modifier.statusBarsPadding() else Modifier)
            .padding(
                start = if (navigationIcon != null) 8.dp else 20.dp,
                end = 8.dp,
                top = if (large) 12.dp else 8.dp,
                bottom = 4.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (navigationIcon != null && onNavigationClick != null) {
            IconButton(onClick = onNavigationClick) {
                Icon(
                    navigationIcon,
                    contentDescription = navigationContentDescription,
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }
        Text(
            text = title,
            style = if (large) MaterialTheme.typography.headlineLarge else SubScreenTitleStyle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        actions()
    }
}

/**
 * The category filter chip. Unselected it is an outline only; selected it fills with the
 * category's own tint at low alpha and drops the stroke, so the selected chip gains weight without
 * gaining a second colour. A solid tint fill needed white or black type on top of it, and which
 * one won the contrast check changed from category to category.
 */
@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val contentColor = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = modifier
            .height(32.dp)
            .clip(FilterChipShape)
            .then(
                if (selected) {
                    Modifier.background(tint.copy(alpha = 0.16f))
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, FilterChipShape)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1
        )
    }
}

/** Filled pill — the one affirmative action on a panel. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(containerColor.copy(alpha = alpha))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = contentColor.copy(alpha = alpha),
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor.copy(alpha = alpha),
            maxLines = 1
        )
    }
}

/** Outlined pill — the secondary of a pair, or an action that is not the point of the panel. */
@Composable
fun PillOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 15.dp)
) {
    val alpha = if (enabled) 1f else 0.38f
    Row(
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = alpha), PillShape)
            .clip(PillShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = contentColor.copy(alpha = alpha),
                modifier = Modifier.size(18.dp)
            )
        }
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor.copy(alpha = alpha),
            maxLines = 1
        )
    }
}

/**
 * Centred empty state. The glyph is drawn in the outline colour rather than a category tint: an
 * empty vault is not a category, and tinting it made the emptiest screen the most saturated one.
 */
@Composable
fun PanelEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // The bottom inset lifts the block off centre by half the floating-action-button
            // gutter, so it sits in the middle of the space the reader can actually see.
            modifier = Modifier.padding(horizontal = 40.dp).padding(bottom = 80.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(44.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = message,
                style = EmptyStateBodyStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A row inside a grouped card — label, optional supporting line, and a trailing control. The
 * divider is drawn by the caller between rows, not around them, so a group of three rows carries
 * two hairlines rather than four.
 */
@Composable
fun PanelRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    minHeight: Dp = 0.dp,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = minHeight)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (supporting != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = supporting,
                    style = FootnoteStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing()
    }
}

/** The hairline between two [PanelRow]s in the same card. */
@Composable
fun PanelRowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
