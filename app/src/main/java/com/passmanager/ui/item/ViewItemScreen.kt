package com.passmanager.ui.item

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.passmanager.R
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.ItemPayload
import com.passmanager.domain.validation.formatExpiryMmYy
import com.passmanager.domain.validation.parseExpiryMmYyToMonthYear
import com.passmanager.ui.common.resolve
import com.passmanager.ui.components.AppSnackbarHost
import com.passmanager.ui.components.ConfirmDeleteDialog
import com.passmanager.ui.components.DestructiveAction
import com.passmanager.ui.components.FaviconImage
import com.passmanager.ui.components.PanelCard
import com.passmanager.ui.components.PanelHeader
import com.passmanager.ui.components.PillOutlinedButton
import com.passmanager.ui.components.SectionFootnote
import com.passmanager.ui.model.icon
import com.passmanager.ui.model.tint
import com.passmanager.ui.theme.CardShape
import com.passmanager.ui.util.rememberSecureClipboard
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

enum class ViewItemPresentation {
    /** Full screen with a back arrow in the header. */
    FullScreen,

    /** Shown inside a modal bottom sheet; the header closes it instead of popping a back stack. */
    Sheet
}

/**
 * [onDeleted] is invoked instead of [onNavigateBack] once the item is gone, so the host can both
 * leave this screen and confirm the deletion where the confirmation outlives it (a snackbar on the
 * item list). When it is null this screen confirms with a toast before navigating back — a snackbar
 * hosted here would be torn down with the screen, and staying put is not an option because the
 * observed item is already gone and the content would flip to "item not found".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewItemScreen(
    itemId: String,
    onNavigateBack: () -> Unit,
    onRequestEdit: () -> Unit,
    onDeleted: (() -> Unit)? = null,
    presentation: ViewItemPresentation = ViewItemPresentation.Sheet,
    viewModel: ViewItemViewModel = hiltViewModel(key = itemId)
) {
    LaunchedEffect(itemId) {
        viewModel.loadForItem(itemId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val deletedMessage = stringResource(R.string.item_delete_success)

    LaunchedEffect(uiState.isDeleted) {
        if (!uiState.isDeleted) return@LaunchedEffect
        val hostHandler = onDeleted
        if (hostHandler != null) {
            hostHandler()
        } else {
            Toast.makeText(context, deletedMessage, Toast.LENGTH_SHORT).show()
            onNavigateBack()
        }
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
            // Edit is an icon in the header rather than half of a two-button bar pinned to the
            // bottom. Delete is not its neighbour any more either: putting the destructive action
            // the same size, the same distance from the thumb and the same shape as the one you
            // reach for every time was the arrangement worth changing.
            PanelHeader(
                title = uiState.payload?.title.orEmpty(),
                // The sheet already starts below the status bar; only the full-screen route
                // renders under it.
                insetTop = presentation == ViewItemPresentation.FullScreen,
                navigationIcon = if (presentation == ViewItemPresentation.FullScreen) {
                    Icons.AutoMirrored.Filled.ArrowBack
                } else {
                    Icons.Default.Close
                },
                navigationContentDescription = stringResource(
                    if (presentation == ViewItemPresentation.FullScreen) {
                        R.string.action_back
                    } else {
                        R.string.action_close
                    }
                ),
                onNavigationClick = onNavigateBack
            ) {
                if (uiState.payload != null) {
                    IconButton(onClick = onRequestEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.action_edit),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) }
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
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
                    PillOutlinedButton(
                        text = stringResource(R.string.action_back),
                        onClick = onNavigateBack
                    )
                }
            }

            uiState.payload != null -> {
                val payload = uiState.payload ?: return@Scaffold

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
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Spacer(Modifier.height(4.dp))

                    ItemHero(payload = payload, useGoogleFavicons = uiState.useGoogleFavicons)

                    when (payload) {
                        is ItemPayload.Login -> LoginViewFields(
                            payload = payload,
                            uiState = uiState,
                            viewModel = viewModel,
                            snackbarHostState = snackbarHostState
                        )
                        is ItemPayload.Card -> CardViewFields(
                            payload = payload,
                            snackbarHostState = snackbarHostState
                        )
                        is ItemPayload.Bank -> BankViewFields(
                            payload = payload,
                            uiState = uiState,
                            viewModel = viewModel,
                            snackbarHostState = snackbarHostState
                        )
                        is ItemPayload.SecureNote -> Unit // only notes, rendered below
                        is ItemPayload.Identity -> IdentityViewFields(
                            payload = payload,
                            snackbarHostState = snackbarHostState
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

                    DestructiveAction(
                        icon = Icons.Default.Delete,
                        label = stringResource(R.string.item_delete_action),
                        onClick = { showDeleteDialog = true }
                    )

                    uiState.updatedAtMs?.let { millis ->
                        val stamp = remember(millis) {
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(millis))
                        }
                        SectionFootnote(stringResource(R.string.item_last_updated, stamp))
                    }

                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

// ── Hero ─────────────────────────────────────────
//
// Each category gets the shape that says what it is before a word is read: a card looks like a
// card, a bank account leads with the number it is known by, an identity leads with a face. The
// two that have no such object — a login and a note — share the list row's own layout, so opening
// one is continuous with tapping it.

@Composable
private fun ItemHero(payload: ItemPayload, useGoogleFavicons: Boolean) {
    when (payload) {
        is ItemPayload.Card -> CardHero(payload)
        is ItemPayload.Bank -> BankHero(payload)
        is ItemPayload.Identity -> IdentityHero(payload)
        is ItemPayload.Login -> StripHero(
            title = payload.title,
            subtitle = payload.address.ifBlank { payload.username },
            subtitleMonospace = payload.address.isNotBlank(),
            category = ItemCategory.LOGIN,
            address = payload.address,
            useGoogleFavicons = useGoogleFavicons
        )
        is ItemPayload.SecureNote -> StripHero(
            title = payload.title,
            subtitle = ItemCategory.NOTE.label,
            subtitleMonospace = false,
            category = ItemCategory.NOTE,
            address = "",
            useGoogleFavicons = false
        )
    }
}

@Composable
private fun StripHero(
    title: String,
    subtitle: String,
    subtitleMonospace: Boolean,
    category: ItemCategory,
    address: String,
    useGoogleFavicons: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 7.dp)
                .size(width = 3.dp, height = 30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(category.tint.copy(alpha = 0.55f))
        )
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // The address envelope is handed over raw: FaviconImage gates the lookup on the
            // category itself, so this screen does not get to decide (and cannot get it wrong)
            // which categories are looked up.
            FaviconImage(
                category = category,
                address = address,
                useGoogleFavicons = useGoogleFavicons,
                size = 48.dp,
                plateColor = category.tint.copy(alpha = 0.14f),
                plateShape = RoundedCornerShape(16.dp),
                fallback = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(category.tint.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            category.icon,
                            contentDescription = null,
                            tint = category.tint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        style = if (subtitleMonospace) {
                            MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                        } else {
                            MaterialTheme.typography.bodySmall
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CardHero(payload: ItemPayload.Card) {
    val digits = payload.cardNumber.filter { it.isDigit() }
    val face = ItemCategory.CARD.tint
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(face)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 17.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(width = 34.dp, height = 25.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White.copy(alpha = 0.32f))
            )
            Text(
                text = cardBrandOf(digits),
                style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 1.5.sp),
                color = Color.White
            )
        }
        // Masked on arrival. The number itself lives in the copyable field below, behind its own
        // reveal — the hero is for recognising the card, not for reading it out.
        Text(
            text = maskedPan(digits),
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            color = Color.White
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            CardFaceCell(
                label = stringResource(R.string.item_cardholder_hint),
                value = payload.cardholderName.ifBlank { "—" },
                modifier = Modifier.weight(1f)
            )
            CardFaceCell(
                label = stringResource(R.string.item_expiry_hint),
                value = expiryDisplay(payload.cardExpiry),
                monospace = true
            )
        }
    }
}

@Composable
private fun CardFaceCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    monospace: Boolean = false
) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = if (monospace) {
                MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.titleSmall
            },
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BankHero(payload: ItemPayload.Bank) {
    val tint = ItemCategory.BANK.tint
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.item_account_number_hint).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                color = tint
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = maskedTail(payload.accountNumber),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = payload.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Default.AccountBalance,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun IdentityHero(payload: ItemPayload.Identity) {
    val tint = ItemCategory.IDENTITY.tint
    val fullName = listOf(payload.firstName, payload.lastName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { payload.title }
    val initials = listOf(payload.firstName, payload.lastName)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { payload.title.take(1).uppercase() }

    PanelCard(
        contentPadding = PaddingValues(22.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(68.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.headlineSmall,
                color = tint
            )
        }
        Text(
            text = fullName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        if (payload.email.isNotBlank()) {
            Text(
                text = payload.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

/** Issuer from the leading digits — enough to label the card face, and nothing is sent anywhere. */
private fun cardBrandOf(digits: String): String = when (digits.firstOrNull()) {
    '4' -> "VISA"
    '5', '2' -> "MASTERCARD"
    '3' -> "AMEX"
    '6' -> "DISCOVER"
    else -> "CARD"
}

private fun maskedPan(digits: String): String {
    val tail = digits.takeLast(4).ifBlank { "••••" }
    return "•••• •••• •••• $tail"
}

private fun maskedTail(accountNumber: String): String {
    val digits = accountNumber.filter { it.isLetterOrDigit() }
    return "•••• " + digits.takeLast(4).ifBlank { "••••" }
}

private fun expiryDisplay(raw: String): String =
    parseExpiryMmYyToMonthYear(raw)?.let { (m, y) -> formatExpiryMmYy(m, y) } ?: raw.ifBlank { "—" }

// ── Login fields ─────────────────────────────────

@Composable
private fun LoginViewFields(
    payload: ItemPayload.Login,
    uiState: ViewItemUiState,
    viewModel: ViewItemViewModel,
    snackbarHostState: SnackbarHostState
) {
    val copy = rememberFieldCopier(snackbarHostState)

    if (payload.username.isNotBlank()) {
        FieldBlock(
            label = stringResource(R.string.item_username_hint),
            onCopy = { copy(payload.username) }
        ) {
            FieldValue(payload.username)
        }
    }

    if (payload.address.isNotBlank()) {
        FieldBlock(
            label = stringResource(R.string.item_address_hint),
            onCopy = { copy(payload.address) }
        ) {
            FieldValue(payload.address)
        }
    }

    PasswordField(
        password = payload.password,
        passwordVisible = uiState.passwordVisible,
        onToggle = viewModel::togglePasswordVisible,
        snackbarHostState = snackbarHostState
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
    val copy = rememberFieldCopier(snackbarHostState)

    if (payload.accountNumber.isNotBlank()) {
        FieldBlock(
            label = stringResource(R.string.item_account_number_hint),
            onCopy = { copy(payload.accountNumber) }
        ) {
            FieldValue(payload.accountNumber, monospace = true)
        }
    }

    if (payload.bankName.isNotBlank()) {
        FieldBlock(
            label = stringResource(R.string.item_bank_name_hint),
            onCopy = { copy(payload.bankName) }
        ) {
            FieldValue(payload.bankName)
        }
    }

    PasswordField(
        password = payload.password,
        passwordVisible = uiState.passwordVisible,
        onToggle = viewModel::togglePasswordVisible,
        snackbarHostState = snackbarHostState
    )
}

// ── Card fields ──────────────────────────────────

@Composable
private fun CardViewFields(
    payload: ItemPayload.Card,
    snackbarHostState: SnackbarHostState
) {
    val copy = rememberFieldCopier(snackbarHostState)
    var cardNumberVisible by remember { mutableStateOf(false) }
    var cvcVisible by remember { mutableStateOf(false) }

    if (payload.cardNumber.isNotBlank()) {
        FieldBlock(
            label = stringResource(R.string.item_card_number_hint),
            onCopy = { copy(payload.cardNumber.filter { it.isDigit() }) },
            extraTrailing = {
                RevealButton(visible = cardNumberVisible) {
                    cardNumberVisible = !cardNumberVisible
                }
            }
        ) {
            val digits = payload.cardNumber.filter { it.isDigit() }
            FieldValue(
                text = if (cardNumberVisible) {
                    digits.chunked(4).joinToString(" ")
                } else {
                    maskedPan(digits)
                },
                monospace = true
            )
        }
    }

    if (payload.cardholderName.isNotBlank()) {
        FieldBlock(
            label = stringResource(R.string.item_cardholder_hint),
            onCopy = { copy(payload.cardholderName) }
        ) {
            FieldValue(payload.cardholderName)
        }
    }

    if (payload.cardExpiry.isNotBlank()) {
        FieldBlock(
            label = stringResource(R.string.item_expiry_hint),
            onCopy = { copy(payload.cardExpiry) }
        ) {
            FieldValue(expiryDisplay(payload.cardExpiry), monospace = true)
        }
    }

    if (payload.cardCvc.isNotBlank()) {
        FieldBlock(
            label = stringResource(R.string.item_cvc_hint),
            onCopy = { copy(payload.cardCvc) },
            extraTrailing = {
                RevealButton(visible = cvcVisible) { cvcVisible = !cvcVisible }
            }
        ) {
            FieldValue(
                text = if (cvcVisible) payload.cardCvc else "•".repeat(payload.cardCvc.length),
                monospace = cvcVisible
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
    val copy = rememberFieldCopier(snackbarHostState)

    val fields = listOf(
        stringResource(R.string.identity_first_name) to payload.firstName,
        stringResource(R.string.identity_last_name) to payload.lastName,
        stringResource(R.string.identity_email) to payload.email,
        stringResource(R.string.identity_phone) to payload.phone,
        stringResource(R.string.item_address_hint) to payload.address,
        stringResource(R.string.identity_company) to payload.company
    )

    for ((label, value) in fields) {
        if (value.isNotBlank()) {
            FieldBlock(label = label, onCopy = { copy(value) }) {
                FieldValue(value)
            }
        }
    }
}

// ── Shared field parts ───────────────────────────

/**
 * One copy handler for the whole panel: haptic, secure clipboard, and the snackbar that tells the
 * user the copy clears itself. Every field used to carry its own four-line version of this.
 */
@Composable
private fun rememberFieldCopier(snackbarHostState: SnackbarHostState): (String) -> Unit {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val clipboard = rememberSecureClipboard()
    return { value ->
        view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        clipboard.copy(value)
        scope.launch {
            snackbarHostState.showSnackbar(
                context.getString(R.string.item_clipboard_copied_clears)
            )
        }
    }
}

@Composable
private fun FieldValue(text: String, monospace: Boolean = false) {
    Text(
        text = text,
        style = if (monospace) {
            MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
        } else {
            MaterialTheme.typography.bodyLarge
        },
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun RevealButton(visible: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
        Icon(
            imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
            contentDescription = if (visible) {
                stringResource(R.string.action_hide)
            } else {
                stringResource(R.string.action_show)
            },
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(21.dp)
        )
    }
}

@Composable
private fun PasswordField(
    password: String,
    passwordVisible: Boolean,
    onToggle: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val copy = rememberFieldCopier(snackbarHostState)

    if (password.isNotBlank()) {
        FieldBlock(
            label = stringResource(R.string.item_password_hint),
            onCopy = { copy(password) },
            extraTrailing = { RevealButton(visible = passwordVisible, onClick = onToggle) }
        ) {
            FieldValue(
                text = if (passwordVisible) password else "•".repeat(minOf(password.length, 24)),
                monospace = passwordVisible
            )
        }
    }
}

/**
 * A read-only field: label, value, and the actions that apply to it. The label is
 * onSurfaceVariant, not primary — on an item with six fields, six primary-coloured labels made
 * the accent mean nothing, and the copy and reveal buttons beside them are what the accent is for.
 */
@Composable
private fun FieldBlock(
    label: String,
    onCopy: (() -> Unit)?,
    extraTrailing: (@Composable () -> Unit)? = null,
    value: @Composable () -> Unit
) {
    PanelCard(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                value()
            }
            extraTrailing?.invoke()
            if (onCopy != null) {
                IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy, label),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
