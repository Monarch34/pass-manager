package com.passmanager.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.passmanager.R
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.VaultSortOrder
import com.passmanager.ui.components.AppShieldLogo
import com.passmanager.ui.components.ConfirmDeleteDialog
import com.passmanager.ui.components.ErrorSnackbarEffect
import com.passmanager.ui.components.SkeletonLoading

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultListScreen(
    /** When the vault group filter is a specific category, add-item opens with that category selected. */
    onNavigateToAddItem: (filterCategory: ItemCategory?) -> Unit,
    onNavigateToViewItem: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    viewModel: VaultListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ErrorSnackbarEffect(
        error = uiState.error?.message,
        onErrorShown = { viewModel.clearError() },
        snackbarHostState = snackbarHostState
    )

    val filteredItems = uiState.filteredItems
    val listState = rememberLazyListState()
    val vaultListFling = rememberVaultListFlingBehavior()

    // Bug #1 — Search bar focus management
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var isSearchFocused by remember { mutableStateOf(false) }

    BackHandler(enabled = isSearchFocused) {
        focusManager.clearFocus()
    }

    // Feature #6 — Selection mode back handler
    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.clearSelection()
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.vault_delete_selected_title),
            message = stringResource(R.string.vault_delete_selected_message, uiState.selectedIds.size),
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteSelected()
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            AnimatedVisibility(
                visible = !uiState.isSelectionMode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallFloatingActionButton(
                        onClick = { viewModel.lock() },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = stringResource(R.string.vault_lock_button)
                        )
                    }
                    FloatingActionButton(
                        onClick = { onNavigateToAddItem(uiState.categoryFilter) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.item_add_title))
                    }
                    SmallFloatingActionButton(
                        onClick = onNavigateToSettings,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.nav_settings)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            VaultListTopBar(
                isSelectionMode = uiState.isSelectionMode,
                selectedCount = uiState.selectedIds.size,
                onClearSelection = { viewModel.clearSelection() },
                onShowDeleteDialog = { showDeleteDialog = true },
                onOpenDrawer = onOpenDrawer,
                onSetSortOrder = { viewModel.setSortOrder(it) }
            )

            // Bug #1 — Search bar with focus management
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text(stringResource(R.string.vault_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { isSearchFocused = it.isFocused }
            )

            VaultListFiltersRow(
                categoryFilter = uiState.categoryFilter,
                onFilterChange = { viewModel.setCategoryFilter(it) }
            )

            // Feature #5 — Item count
            if (!uiState.isLoading) {
                Text(
                    text = stringResource(R.string.vault_item_count, filteredItems.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp)
                )
            }

            if (uiState.isLoading) {
                SkeletonLoading(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            } else if (filteredItems.isEmpty()) {
                VaultListEmptyState(
                    searchQuery = uiState.searchQuery,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
            } else {
                LazyColumn(
                    state = listState,
                    flingBehavior = vaultListFling,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    items(
                        items = filteredItems,
                        key = { it.id },
                        contentType = { _ -> "vault_row" }
                    ) { item ->
                        val onOpenItem = remember(item.id) { { onNavigateToViewItem(item.id) } }
                        val onLongClick = remember(item.id) { { viewModel.enterSelectionMode(item.id) } }
                        val onToggleSelection = remember(item.id) { { viewModel.toggleSelection(item.id) } }
                        VaultListItemRow(
                            item = item,
                            title = uiState.headerDisplayCache.titles[item.id] ?: "",
                            address = uiState.headerDisplayCache.addresses[item.id] ?: "",
                            useGoogleFavicons = uiState.useGoogleFavicons,
                            isSelected = item.id in uiState.selectedIds,
                            isSelectionMode = uiState.isSelectionMode,
                            onOpenItem = onOpenItem,
                            onLongClick = onLongClick,
                            onToggleSelection = onToggleSelection
                        )
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

