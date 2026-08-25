package com.passmanager.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.passmanager.desktop.ui.Strings

private val ChipShape = RoundedCornerShape(12.dp)

/**
 * The two site-icon states: Off, which makes no network request at all, and On, which makes exactly
 * one request, to www.google.com. That is now precisely what Android's switch does, so the two
 * platforms teach the same mental model.
 *
 * Uses rounded surfaces + bounded ripple so hover/press isn’t a sharp rectangle.
 *
 * @param compact Smaller padding and a single-line explainer (e.g. footer on vault screen).
 */
@Composable
fun FaviconSourceSection(
    useGoogleFavicons: Boolean,
    onSelectOff: () -> Unit,
    onSelectOn: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val hPad = if (compact) 8.dp else 12.dp
    val vPad = if (compact) 6.dp else 10.dp
    val titleStyle =
        if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
    val explainerStyle =
        if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall
    val chipSpacing = if (compact) 6.dp else 8.dp

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = if (compact) 0.dp else 1.dp
    ) {
        BoxWithConstraints(Modifier.padding(horizontal = hPad, vertical = vPad)) {
            val stackVertically = maxWidth < 340.dp

            // BoxWithConstraints is a Box: children stack in the same slot unless wrapped.
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = Strings.FAVICON_SECTION_TITLE,
                    style = titleStyle,
                    color = MaterialTheme.colorScheme.primary
                )
                if (!compact) {
                    Text(
                        text = Strings.FAVICON_SECTION_HINT,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                }

                if (stackVertically) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(chipSpacing),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (compact) 2.dp else 0.dp)
                    ) {
                        FaviconModeChip(
                            label = Strings.FAVICON_MODE_OFF_LABEL,
                            icon = Icons.Default.Lock,
                            selected = !useGoogleFavicons,
                            onClick = onSelectOff,
                                    compact = compact,
                            modifier = Modifier.fillMaxWidth()
                        )
                        FaviconModeChip(
                            label = Strings.FAVICON_MODE_ON_LABEL,
                            icon = Icons.Default.Public,
                            selected = useGoogleFavicons,
                            onClick = onSelectOn,
                                    compact = compact,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(chipSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (compact) 2.dp else 0.dp)
                    ) {
                        FaviconModeChip(
                            label = Strings.FAVICON_MODE_OFF_LABEL,
                            icon = Icons.Default.Lock,
                            selected = !useGoogleFavicons,
                            onClick = onSelectOff,
                                    compact = compact,
                            modifier = Modifier.weight(1f)
                        )
                        FaviconModeChip(
                            label = Strings.FAVICON_MODE_ON_LABEL,
                            icon = Icons.Default.Public,
                            selected = useGoogleFavicons,
                            onClick = onSelectOn,
                                    compact = compact,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Text(
                    text = if (useGoogleFavicons) {
                        if (compact) Strings.FAVICON_COMPACT_ON_LINE else Strings.FAVICON_MODE_ON_EXPLAINER
                    } else {
                        if (compact) Strings.FAVICON_COMPACT_OFF_LINE else Strings.FAVICON_MODE_OFF_EXPLAINER
                    },
                    style = explainerStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (compact) 2 else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (compact) 8.dp else 10.dp)
                )
            }
        }
    }
}

@Composable
private fun FaviconModeChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    // One control, one accent: the previous per-chip flag selected Off in indigo and On in
    // teal, splitting a single two-state switch across two colour families.
    val containerColor =
        if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
    val contentColor =
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
    val border = if (!selected) BorderStroke(1.dp, outline) else null
    val labelStyle =
        if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelLarge
    val iconSize = if (compact) 16.dp else 18.dp
    val vPadChip = if (compact) 8.dp else 10.dp
    val hPadChip = if (compact) 10.dp else 12.dp

    // Surface(onClick = …) wires indication to [shape], so hover/ripple matches rounded corners on desktop.
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (compact) 40.dp else 44.dp),
        shape = ChipShape,
        color = containerColor,
        border = border,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = hPadChip, vertical = vPadChip),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = contentColor
            )
            Text(
                text = label,
                style = labelStyle,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
    }
}
