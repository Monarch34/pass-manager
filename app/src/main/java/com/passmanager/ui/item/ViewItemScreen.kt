package com.passmanager.ui.item

import com.passmanager.R
import com.passmanager.ui.util.copyToClipboard
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.passmanager.domain.model.ItemCategory
import com.passmanager.ui.components.ErrorSnackbarEffect
import com.passmanager.ui.components.FaviconImage
import kotlinx.coroutines.launch

enum class ViewItemPresentation {
    /** Full screen with top app bar (back). */
    FullScreen,
    /** Shown inside [ModalBottomSheet]; no top bar — dismiss via sheet swipe/scrim/back. */
    Sheet
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewItemScreen(
    itemId: String,
    onNavigateBack: () -> Unit,
    onRequestEdit: () -> Unit,
    presentation: ViewItemPresentation = ViewItemPresentation.Sheet,
    viewModel: ViewItemViewModel = hiltViewModel(key = itemId)
) {
    LaunchedEffect(itemId) {
        viewModel.loadForItem(itemId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val view = LocalView.current

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) onNavigateBack()
    }

    ErrorSnackbarEffect(
        error = uiState.error,
        onErrorShown = { viewModel.clearError() },
        snackbarHostState = snackbarHostState
    )

    if (showDeleteDialog) {
        Dialog(onDismissRequest = { showDeleteDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 420.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp
                ) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val stackButtons = maxWidth < 340.dp
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = stringResource(R.string.item_delete_confirm_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.item_delete_confirm_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                            Spacer(Modifier.height(24.dp))
                            if (stackButtons) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { showDeleteDialog = false },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                    Button(
                                        onClick = {
                                            showDeleteDialog = false
                                            viewModel.delete()
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        )
                                    ) {
                                        Text(stringResource(R.string.item_delete_button))
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = { showDeleteDialog = false },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Text(stringResource(R.string.cancel))
                                    }
                                    Button(
                                        onClick = {
                                            showDeleteDialog = false
                                            viewModel.delete()
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        )
                                    ) {
                                        Text(stringResource(R.string.item_delete_button))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val rootModifier = when (presentation) {
        ViewItemPresentation.Sheet -> Modifier.fillMaxWidth().fillMaxHeight()
        ViewItemPresentation.FullScreen -> Modifier.fillMaxSize()
    }

    Scaffold(
        modifier = rootModifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (presentation == ViewItemPresentation.FullScreen) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    )
                )
            }
        },
        bottomBar = {
            if (uiState.item != null) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                            )
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.item_delete_button),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Button(
                            onClick = onRequestEdit,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.action_edit),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when {
            uiState.isLoading -> {
                Column(
                    modifier = Modifier
                        .then(
                            if (presentation == ViewItemPresentation.Sheet) {
                                Modifier.fillMaxWidth().heightIn(min = 160.dp)
                            } else {
                                Modifier.fillMaxSize()
                            }
                        )
                        .padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(48.dp))
                    CircularProgressIndicator()
                }
            }

            uiState.item != null -> {
                val item = uiState.item!!
                val category = ItemCategory.fromString(uiState.category)

                Column(
                    modifier = Modifier
                        .then(
                            if (presentation == ViewItemPresentation.Sheet) {
                                Modifier.fillMaxWidth().fillMaxHeight()
                            } else {
                                Modifier.fillMaxSize()
                            }
                        )
                        .padding(padding)
                        .verticalScroll(rememberScrollState()),
                ) {
                    // ── Item header ────────────────────────────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(category.tint.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.address.isNotBlank()) {
                                FaviconImage(
                                    url = item.address,
                                    useGoogleFavicons = uiState.useGoogleFavicons,
                                    size = 60.dp,
                                    fallback = {
                                        Icon(
                                            category.icon,
                                            contentDescription = null,
                                            tint = category.tint,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                )
                            } else {
                                Icon(
                                    category.icon,
                                    contentDescription = null,
                                    tint = category.tint,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        // Title + category chip — this is the only place the name appears
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(category.tint.copy(alpha = 0.10f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    category.icon,
                                    contentDescription = null,
                                    tint = category.tint,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = category.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = category.tint
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // ── Fields cards ────────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (item.username.isNotBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                tonalElevation = 1.dp
                            ) {
                                FieldBlock(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                                    label = stringResource(R.string.item_username_hint),
                                    onCopy = {
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                                        scope.launch {
                                            copyToClipboard(context, "Username", item.username)
                                            snackbarHostState.showSnackbar(context.getString(R.string.item_username_copied))
                                        }
                                    }
                                ) {
                                    Text(
                                        text = item.username,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        if (item.address.isNotBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                tonalElevation = 1.dp
                            ) {
                                FieldBlock(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                                    label = stringResource(R.string.item_address_hint),
                                    onCopy = {
                                        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                                        scope.launch {
                                            copyToClipboard(context, "Address", item.address)
                                            snackbarHostState.showSnackbar(context.getString(R.string.item_address_copied))
                                        }
                                    }
                                ) {
                                    Text(
                                        text = item.address,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 1.dp
                        ) {
                            FieldBlock(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                                label = stringResource(R.string.item_password_hint),
                                onCopy = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                                    scope.launch {
                                        copyToClipboard(
                                            context,
                                            "Password",
                                            item.password,
                                            clearAfterMs = 15_000L,
                                            scope = scope
                                        )
                                        snackbarHostState.showSnackbar(context.getString(R.string.item_password_copied))
                                    }
                                },
                                extraTrailing = {
                                    IconButton(onClick = viewModel::togglePasswordVisible) {
                                        Icon(
                                            imageVector = if (uiState.passwordVisible) Icons.Default.VisibilityOff
                                            else Icons.Default.Visibility,
                                            contentDescription = if (uiState.passwordVisible)
                                                stringResource(R.string.action_hide)
                                            else
                                                stringResource(R.string.action_show)
                                        )
                                    }
                                }
                            ) {
                                Text(
                                    text = if (uiState.passwordVisible) item.password
                                    else "•".repeat(minOf(item.password.length, 24)),
                                    style = if (uiState.passwordVisible) {
                                        MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                                    } else {
                                        MaterialTheme.typography.bodyLarge
                                    },
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (item.notes.isNotBlank()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                tonalElevation = 1.dp
                            ) {
                                FieldBlock(
                                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                                    label = stringResource(R.string.item_notes_hint),
                                    onCopy = null
                                ) {
                                    Text(
                                        text = item.notes,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun FieldBlock(
    modifier: Modifier = Modifier,
    label: String,
    onCopy: (() -> Unit)?,
    extraTrailing: (@Composable () -> Unit)? = null,
    value: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                value()
            }
            extraTrailing?.invoke()
            if (onCopy != null) {
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy, label),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
