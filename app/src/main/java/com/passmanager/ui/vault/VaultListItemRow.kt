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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.dp
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.VaultItemHeader
import com.passmanager.ui.components.FaviconImage
import com.passmanager.ui.model.icon
import com.passmanager.ui.model.tint

// One VectorPainter instance must never be shared between draw sites: a painter drawn at 22.dp in
// the leading tile and at 14.dp beside the label keeps one internal draw cache, and the two sizes
// composite into each other — every row rendered its category glyph twice, overlapped. Each Icon
// below therefore takes the ImageVector itself and owns its painter. The subcomposition that costs
// is real but small, and correctness beat it on screen.

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
    val cardShape = MaterialTheme.shapes.large
    val outlineStroke = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    }
    val stripColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        category.tint.copy(alpha = 0.55f)
    }
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    // A Material Surface at zero elevation adds nothing that background() does not: it only costs a
    // second rounded clip and two CompositionLocal providers per row. Every text and icon below
    // carries an explicit colour, so LocalContentColor is never read here. The modifier order
    // matches what Surface produced (border outside the clip, ripple inside it), so the row looks
    // the same as before.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(outlineStroke, cardShape)
            .clip(cardShape)
            .background(containerColor)
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggleSelection() else onOpenItem() },
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(4.dp)
                .fillMaxHeight()
                .background(stripColor)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 14.dp)
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
                        .clip(RoundedCornerShape(12.dp))
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
                FaviconImage(
                    url = address,
                    useGoogleFavicons = useGoogleFavicons,
                    size = 40.dp,
                    fallback = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        category.icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = category.tint.copy(alpha = 0.75f)
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
