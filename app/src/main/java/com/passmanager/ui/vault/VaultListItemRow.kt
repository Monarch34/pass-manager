package com.passmanager.ui.vault

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.passmanager.domain.model.VaultItemHeader
import com.passmanager.ui.components.FaviconImage
import com.passmanager.ui.model.icon
import com.passmanager.ui.model.tint
import com.passmanager.ui.theme.CardShape
import com.passmanager.ui.theme.PlateShape

// One VectorPainter instance must never be shared between draw sites: a painter drawn at 22.dp in
// the leading tile and at 14.dp beside the label keeps one internal draw cache, and the two sizes
// composite into each other — every row rendered its category glyph twice, overlapped. Each Icon
// below therefore takes the ImageVector itself and owns its painter. The subcomposition that costs
// is real but small, and correctness beat it on screen.

/**
 * The category marker down the left edge. Inset from the card rather than flush with it, and short
 * rather than full-height: flush and full-height it read as a second border on a card that already
 * has an edge, and a list of five categories became five stripes competing with the tinted tiles
 * that carry the same information.
 */
private val CategoryStripWidth = 3.dp
private val CategoryStripHeight = 28.dp
private val CategoryStripInset = 7.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VaultListItemRow(
    item: VaultItemHeader,
    title: String,
    address: String,
    useGoogleFavicons: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onOpenItem: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelection: () -> Unit
) {
    val category = item.category
    val stripColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        category.tint.copy(alpha = 0.55f)
    }
    // Selection borrows the brand at the same low alpha the filter chips and the drawer use, so
    // the app has one selected-looking thing rather than three. The 2.dp stroke is the state
    // marker; an unselected row carries no stroke at all.
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    // A Material Surface at zero elevation adds nothing that background() does not: it only costs a
    // second rounded clip and two CompositionLocal providers per row. Every text and icon below
    // carries an explicit colour, so LocalContentColor is never read here.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isSelected) {
                    Modifier.border(
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        CardShape
                    )
                } else {
                    Modifier
                }
            )
            .clip(CardShape)
            .background(containerColor)
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection() else onOpenItem() },
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = CategoryStripInset)
                .size(width = CategoryStripWidth, height = CategoryStripHeight)
                .clip(RoundedCornerShape(2.dp))
                .background(stripColor)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        "${title.ifEmpty { "Loading title" }}, ${category.label}. Tap to open."
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(PlateShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                // The plate is the same tinted tile the fallback draws, so a list where only some
                // rows resolved an icon still reads as one column of tiles rather than two kinds
                // of thing. Only what sits inside it changes: the site's icon, or the category
                // glyph.
                FaviconImage(
                    category = category,
                    address = address,
                    useGoogleFavicons = useGoogleFavicons,
                    size = 40.dp,
                    plateColor = category.tint.copy(alpha = 0.14f),
                    plateShape = PlateShape,
                    fallback = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(PlateShape)
                                .background(category.tint.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                category.icon,
                                contentDescription = null,
                                tint = category.tint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.ifEmpty { "…" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = category.tint.copy(alpha = 0.55f)
                    )
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!isSelectionMode) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
