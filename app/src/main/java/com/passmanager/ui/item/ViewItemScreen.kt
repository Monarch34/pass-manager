package com.passmanager.ui.item

import com.passmanager.R
import com.passmanager.ui.util.copyToClipboard
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.ItemPayload
import com.passmanager.ui.common.resolve
import com.passmanager.ui.components.ConfirmDeleteDialog
import com.passmanager.ui.components.FaviconImage
import com.passmanager.ui.model.icon
import com.passmanager.ui.model.tint
import com.passmanager.domain.validation.formatExpiryMmYy
import com.passmanager.domain.validation.parseExpiryMmYyToMonthYear
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

    // Snackbar only when an error occurs while content is shown (e.g. delete failed).
    // Load/decrypt errors use inline UI so the message is not cleared immediately by snackbar.
    LaunchedEffect(uiState.error, uiState.payload) {
        val err = uiState.error ?: return@LaunchedEffect
        if (uiState.payload != null) {
            snackbarHostState.showSnackbar(err.resolve(context))
            viewModel.clearError()
        }
    }

    if (showDeleteDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.item_delete_confirm_title),
            message = stringResource(R.string.item_delete_confirm_message),
            onConfirm = {
                showDeleteDialog = false
                viewModel.delete()
            },
            onDismiss = { showDeleteDialog = false }
        )
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
            if (uiState.payload != null) {
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

            !uiState.isLoading && uiState.payload == null && uiState.error != null -> {
                val err = uiState.error!!
                Column(
                    modifier = Modifier
                        .then(
                            if (presentation == ViewItemPresentation.Sheet) {
                                Modifier.fillMaxWidth().heightIn(min = 200.dp)
                            } else {
                                Modifier.fillMaxSize()
                            }
                        )
                        .padding(padding)
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = err.resolve(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    OutlinedButton(onClick = onNavigateBack) {
                        Text(stringResource(R.string.action_back))
                    }
                }
            }

            uiState.payload != null -> {
                val payload = uiState.payload ?: return@Scaffold
                val category = uiState.category

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
                    // Item header
                    val faviconUrl = when (payload) {
                        is ItemPayload.Login -> payload.address
                        else -> ""
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(category.tint.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (faviconUrl.isNotBlank()) {
                                FaviconImage(
                                    url = faviconUrl,
                                    useGoogleFavicons = uiState.useGoogleFavicons,
                                    size = 60.dp,
                                    fallback = {
                                        Icon(category.icon, contentDescription = null, tint = category.tint, modifier = Modifier.size(30.dp))
                                    }
                                )
                            } else {
                                Icon(category.icon, contentDescription = null, tint = category.tint, modifier = Modifier.size(30.dp))
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = payload.title,
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
                                Icon(category.icon, contentDescription = null, tint = category.tint, modifier = Modifier.size(15.dp))
                                Text(text = category.label, style = MaterialTheme.typography.labelMedium, color = category.tint)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Category-specific fields
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (payload) {
                            is ItemPayload.Login -> LoginViewFields(
                                payload = payload, uiState = uiState, viewModel = viewModel, snackbarHostState = snackbarHostState
                            )
                            is ItemPayload.Card -> CardViewFields(
                                payload = payload, snackbarHostState = snackbarHostState
                            )
                            is ItemPayload.Bank -> BankViewFields(
                                payload = payload, uiState = uiState, viewModel = viewModel, snackbarHostState = snackbarHostState
                            )
                            is ItemPayload.SecureNote -> { /* only notes, rendered below */ }
                            is ItemPayload.Identity -> IdentityViewFields(
                                payload = payload, snackbarHostState = snackbarHostState
                            )
                        }

                        if (payload.notes.isNotBlank()) {
                            FieldBlock(
                                label = stringResource(R.string.item_notes_hint),
                                onCopy = null
                            ) {
                                Text(
                                    text = payload.notes,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

// ── Login fields ─────────────────────────────────

@Composable
private fun LoginViewFields(
    payload: ItemPayload.Login,
    uiState: ViewItemUiState,
    viewModel: ViewItemViewModel,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    if (payload.username.isNotBlank()) {
        FieldBlock(
            label = stringResource(R.string.item_username_hint),
            onCopy = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                scope.launch {
                    copyToClipboard(
                        context, "Username", payload.username,
                        clearAfterMs = 15_000L, scope = scope
                    )
                    snackbarHostState.showSnackbar(context.getString(R.string.item_clipboard_copied_clears))
                }
            }
        ) {
            Text(text = payload.username, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }

    if (payload.address.isNotBlank()) {
        FieldBlock(
            label = stringResource(R.string.item_address_hint),
            onCopy = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                scope.launch {
                    copyToClipboard(
                        context, "Address", payload.address,
                        clearAfterMs = 15_000L, scope = scope
                    )
                    snackbarHostState.showSnackbar(context.getString(R.string.item_clipboard_copied_clears))
                }
            }
        ) {
            Text(text = payload.address, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }

    PasswordField(
        password = payload.password, passwordVisible = uiState.passwordVisible,
        onToggle = viewModel::togglePasswordVisible, snackbarHostState = snackbarHostState
    )
}

// ── Bank fields ──────────────────────────────────

@Composable
private fun BankViewFields(
    payload: ItemPayload.Bank,
    uiState: ViewItemUiState,
    viewModel: ViewItemViewModel,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    if (payload.accountNumber.isNotBlank()) {
        FieldBlock(
            label = stringResource(R.string.item_account_number_hint),
            onCopy = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                scope.launch {
                    copyToClipboard(
                        context, "Account", payload.accountNumber,
                        clearAfterMs = 15_000L, scope = scope
                    )
                    snackbarHostState.showSnackbar(context.getString(R.string.item_clipboard_copied_clears))
                }
            }
        ) {
            Text(text = payload.accountNumber, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }

    if (payload.bankName.isNotBlank()) {
        FieldBlock(
            label = stringResource(R.string.item_bank_name_hint),
            onCopy = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                scope.launch {
                    copyToClipboard(
                        context, "Bank", payload.bankName,
                        clearAfterMs = 15_000L, scope = scope
                    )
                    snackbarHostState.showSnackbar(context.getString(R.string.item_clipboard_copied_clears))
                }
            }
        ) {
            Text(text = payload.bankName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }

    PasswordField(
        password = payload.password, passwordVisible = uiState.passwordVisible,
        onToggle = viewModel::togglePasswordVisible, snackbarHostState = snackbarHostState
    )
}

// ── Card fields ──────────────────────────────────

@Composable
private fun CardViewFields(
    payload: ItemPayload.Card,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var cardNumberVisible by remember { mutableStateOf(false) }
    var cvcVisible by remember { mutableStateOf(false) }

    if (payload.cardNumber.isNotBlank()) {
        FieldBlock(
            label = stringResource(R.string.item_card_number_hint),
            onCopy = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                scope.launch {
                    copyToClipboard(context, "Card number", payload.cardNumber.filter { it.isDigit() }, clearAfterMs = 15_000L, scope = scope)
                    snackbarHostState.showSnackbar(context.getString(R.string.item_clipboard_copied_clears))
                }
            },
            extraTrailing = {
                IconButton(onClick = { cardNumberVisible = !cardNumberVisible }) {
                    Icon(imageVector = if (cardNumberVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                }
            }
        ) {
            val displayNumber = if (cardNumberVisible) {
                payload.cardNumber.filter { it.isDigit() }.chunked(4).joinToString(" ")
            } else {
                val digits = payload.cardNumber.filter { it.isDigit() }
                val last4 = digits.takeLast(4).ifEmpty { "••••" }
                "\u2022\u2022\u2022\u2022 \u2022\u2022\u2022\u2022 \u2022\u2022\u2022\u2022 $last4"
            }
            Text(text = displayNumber, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurface)
        }
    }

    if (payload.cardholderName.isNotBlank()) {
        FieldBlock(
            label = stringResource(R.string.item_cardholder_hint),
            onCopy = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                scope.launch {
                    copyToClipboard(
                        context, "Cardholder", payload.cardholderName,
                        clearAfterMs = 15_000L, scope = scope
                    )
                    snackbarHostState.showSnackbar(context.getString(R.string.item_clipboard_copied_clears))
                }
            }
        ) {
            Text(text = payload.cardholderName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }

    if (payload.cardExpiry.isNotBlank()) {
        FieldBlock(
            label = stringResource(R.string.item_expiry_hint),
            onCopy = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                scope.launch {
                    copyToClipboard(
                        context, "Expiry", payload.cardExpiry,
                        clearAfterMs = 15_000L, scope = scope
                    )
                    snackbarHostState.showSnackbar(context.getString(R.string.item_clipboard_copied_clears))
                }
            }
        ) {
            val expiryText = parseExpiryMmYyToMonthYear(payload.cardExpiry)?.let { (m, y) -> formatExpiryMmYy(m, y) } ?: payload.cardExpiry
            Text(text = expiryText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }

    if (payload.cardCvc.isNotBlank()) {
        FieldBlock(
            label = stringResource(R.string.item_cvc_hint),
            onCopy = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                scope.launch {
                    copyToClipboard(context, "CVC", payload.cardCvc, clearAfterMs = 15_000L, scope = scope)
                    snackbarHostState.showSnackbar(context.getString(R.string.item_clipboard_copied_clears))
                }
            },
            extraTrailing = {
                IconButton(onClick = { cvcVisible = !cvcVisible }) {
                    Icon(imageVector = if (cvcVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                }
            }
        ) {
            Text(
                text = if (cvcVisible) payload.cardCvc else "\u2022".repeat(payload.cardCvc.length),
                style = if (cvcVisible) MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ── Identity fields ──────────────────────────────

@Composable
private fun IdentityViewFields(
    payload: ItemPayload.Identity,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val fields = listOf(
        stringResource(R.string.identity_first_name) to payload.firstName,
        stringResource(R.string.identity_last_name)  to payload.lastName,
        stringResource(R.string.identity_email)       to payload.email,
        stringResource(R.string.identity_phone)       to payload.phone,
        stringResource(R.string.item_address_hint)    to payload.address,
        stringResource(R.string.identity_company)     to payload.company
    )

    for ((label, value) in fields) {
        if (value.isNotBlank()) {
            FieldBlock(
                label = label,
                onCopy = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                    scope.launch {
                        copyToClipboard(
                            context, label, value,
                            clearAfterMs = 15_000L, scope = scope
                        )
                        snackbarHostState.showSnackbar(context.getString(R.string.item_clipboard_copied_clears))
                    }
                }
            ) {
                Text(text = value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

// ── Shared password field ────────────────────────

@Composable
private fun PasswordField(
    password: String,
    passwordVisible: Boolean,
    onToggle: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    if (password.isNotBlank()) {
        FieldBlock(
            label = stringResource(R.string.item_password_hint),
            onCopy = {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
                scope.launch {
                    copyToClipboard(context, "Password", password, clearAfterMs = 15_000L, scope = scope)
                    snackbarHostState.showSnackbar(context.getString(R.string.item_clipboard_copied_clears))
                }
            },
            extraTrailing = {
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) stringResource(R.string.action_hide) else stringResource(R.string.action_show)
                    )
                }
            }
        ) {
            Text(
                text = if (passwordVisible) password else "\u2022".repeat(minOf(password.length, 24)),
                style = if (passwordVisible) MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun FieldBlock(
    label: String,
    onCopy: (() -> Unit)?,
    extraTrailing: (@Composable () -> Unit)? = null,
    value: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
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
}
