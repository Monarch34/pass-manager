package com.passmanager.ui.vault

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.passmanager.R
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.VaultSortOrder
import com.passmanager.ui.components.AppShieldLogo
import com.passmanager.ui.model.tint

/** Width of the fade drawn over an edge of a chip row that has more chips past it. */
private val ChipRowFadeWidth = 24.dp

/** Breathing room so the first and last chip are not glued to the screen edge. */
private val ChipRowEdgePadding = 16.dp

@Composable
fun VaultListTopBar(
    isSelectionMode: Boolean,
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onOpenDrawer: () -> Unit,
    onSetSortOrder: (VaultSortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    if (isSelectionMode) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClearSelection) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.vault_selected_count, selectedCount),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onShowDeleteDialog) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.item_delete_button),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = stringResource(R.string.nav_open_drawer),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.vault_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            var sortMenuExpanded by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { sortMenuExpanded = true }) {
                    Icon(
                        Icons.Default.SwapVert,
                        contentDescription = stringResource(R.string.vault_sort_menu_cd),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.vault_sort_name_az)) },
                        onClick = {
                            onSetSortOrder(VaultSortOrder.NAME_ASC)
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.vault_sort_date_newest)) },
                        onClick = {
                            onSetSortOrder(VaultSortOrder.DATE_NEWEST)
                            sortMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.vault_sort_date_oldest)) },
                        onClick = {
                            onSetSortOrder(VaultSortOrder.DATE_OLDEST)
                            sortMenuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun VaultListFiltersRow(
    categoryFilter: ItemCategory?,
    onFilterChange: (ItemCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.vault_group_filter_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 2.dp)
        )
        val listState = rememberLazyListState()
        // Six chips do not fit on a 1080px screen. Fading only the side that actually has more
        // chips behind it says "keep scrolling" without shrinking the labels.
        val startFade by animateFloatAsState(
            targetValue = if (listState.canScrollBackward) 1f else 0f,
            label = "chipRowStartFade"
        )
        val endFade by animateFloatAsState(
            targetValue = if (listState.canScrollForward) 1f else 0f,
            label = "chipRowEndFade"
        )

        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = ChipRowEdgePadding, vertical = 4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalEdgeFade(startFraction = startFade, endFraction = endFade)
        ) {
            item(key = "all") {
                FilterChip(
                    selected = categoryFilter == null,
                    onClick = { onFilterChange(null) },
                    label = { Text(stringResource(R.string.vault_group_all)) }
                )
            }
            items(ItemCategory.entries, key = { it.name }) { category ->
                FilterChip(
                    selected = categoryFilter == category,
                    onClick = {
                        onFilterChange(
                            if (categoryFilter == category) null else category
                        )
                    },
                    label = { Text(category.label) },
                    colors = categoryFilterChipColors(category)
                )
            }
        }
    }
}

/**
 * Paints the selected chip in the category's own tint, the same one its rows and icons use, so a
 * category does not appear in two unrelated colors on the same screen.
 */
@Composable
private fun categoryFilterChipColors(category: ItemCategory) = FilterChipDefaults.filterChipColors(
    selectedContainerColor = category.tint,
    selectedLabelColor = onTintColor(category.tint)
)

/** Black or white on [tint] — whichever wins the WCAG contrast ratio against it. */
private fun onTintColor(tint: Color): Color =
    if (contrastRatio(Color.Black, tint) >= contrastRatio(Color.White, tint)) Color.Black
    else Color.White

private fun contrastRatio(a: Color, b: Color): Float {
    val lighter = maxOf(a.luminance(), b.luminance()) + 0.05f
    val darker = minOf(a.luminance(), b.luminance()) + 0.05f
    return lighter / darker
}

/**
 * Fades the horizontal edges of a scrollable row. [startFraction] and [endFraction] scale each
 * side independently (0 = no fade), so an animated value can bring the fade in only when that
 * side can still be scrolled. Offscreen compositing is what lets [BlendMode.DstIn] erase pixels
 * of this layer instead of the whole window.
 */
internal fun Modifier.horizontalEdgeFade(
    startFraction: Float,
    endFraction: Float,
    fadeWidth: Dp = ChipRowFadeWidth
): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        if (size.width <= 0f) return@drawWithContent
        val start = startFraction.coerceIn(0f, 1f)
        val end = endFraction.coerceIn(0f, 1f)
        if (start == 0f && end == 0f) return@drawWithContent
        // Keeps the two inner stops apart even on a very narrow row.
        val fade = (fadeWidth.toPx() / size.width).coerceIn(0.01f, 0.45f)
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Black.copy(alpha = 1f - start),
                fade to Color.Black,
                1f - fade to Color.Black,
                1f to Color.Black.copy(alpha = 1f - end)
            ),
            blendMode = BlendMode.DstIn
        )
    }

@Composable
fun VaultListEmptyState(
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppShieldLogo(
                size = 72.dp,
                modifier = Modifier.alpha(0.45f)
            )
            Text(
                text = if (searchQuery.isBlank()) stringResource(R.string.vault_empty_title)
                else stringResource(R.string.vault_no_results, searchQuery),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (searchQuery.isBlank()) {
                Text(
                    text = stringResource(R.string.vault_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}
