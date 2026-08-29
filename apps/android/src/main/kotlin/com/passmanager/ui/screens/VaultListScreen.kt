package com.passmanager.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.passmanager.domain.item.ItemCategory
import com.passmanager.domain.item.VaultItem
import com.passmanager.ui.CategoryOrder
import com.passmanager.ui.VaultViewModel
import com.passmanager.ui.color
import com.passmanager.ui.components.CategoryChip
import com.passmanager.ui.components.CategoryStripe
import com.passmanager.ui.components.PanelCard
import com.passmanager.ui.components.PanelEmptyState
import com.passmanager.ui.components.PanelField
import com.passmanager.ui.components.PanelRow
import com.passmanager.ui.components.PanelRowDivider
import com.passmanager.ui.label
import com.passmanager.ui.listSubtitle
import com.passmanager.ui.theme.CardShape

@Composable
fun VaultListScreen(
    model: VaultViewModel,
    onOpen: (VaultItem) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
) {
    var filter by remember { mutableStateOf<ItemCategory?>(null) }
    val visible = model.visibleItems.filter { filter == null || it.category == filter }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 48.dp,
                bottom = 96.dp,
            ),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Vault", style = MaterialTheme.typography.headlineMedium)
                    Row {
                        TextButton(onSettings) { Text("Settings") }
                        TextButton({ model.lock() }) { Text("Lock") }
                    }
                }
            }

            item {
                PanelField("Search", model.query, { model.query = it })
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryChip("All", filter == null) { filter = null }
                    CategoryOrder.forEach { category ->
                        CategoryChip(category.label, filter == category) {
                            filter = if (filter == category) null else category
                        }
                    }
                }
            }

            if (visible.isEmpty()) {
                item {
                    PanelEmptyState(
                        title = if (model.items.isEmpty()) "Nothing saved yet" else "Nothing matches",
                        body = if (model.items.isEmpty()) {
                            "Add your first entry with the button below."
                        } else {
                            "Try a different search or category."
                        },
                    )
                }
            } else {
                item {
                    PanelCard {
                        visible.forEachIndexed { index, entry ->
                            PanelRow(onClick = { onOpen(entry) }) {
                                CategoryStripe(entry.category.color())
                                Column(Modifier.fillMaxWidth()) {
                                    Text(
                                        entry.payload.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val subtitle = entry.listSubtitle()
                                    if (subtitle.isNotEmpty()) {
                                        Text(
                                            subtitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            if (index < visible.size - 1) PanelRowDivider()
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAdd,
            shape = CardShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
    }
}
