package com.passmanager.ui.item

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.passmanager.R
import com.passmanager.domain.model.ItemCategory
import com.passmanager.ui.components.BankPasswordRuleIndicator
import com.passmanager.ui.components.ErrorSnackbarEffect
import com.passmanager.ui.components.LoadingButton
import com.passmanager.ui.components.PasswordStrengthBar
import com.passmanager.ui.components.SecureTextField
import com.passmanager.ui.item.CARD_NUMBER_DIGITS
import com.passmanager.ui.item.parseExpiryFieldDigits

/**
 * Add/edit flow: [AddEditItemViewModel] is obtained via [hiltViewModel] on this route's
 * [androidx.navigation.NavBackStackEntry], so navigating to the password generator and back keeps
 * the same ViewModel instance and form state. Generated passwords are applied via the previous
 * entry's [androidx.lifecycle.SavedStateHandle] in [com.passmanager.navigation.MainTabNavHost].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditItemScreen(
    itemId: String?,
    onNavigateBack: () -> Unit,
    onNavigateToGenerator: () -> Unit,
    viewModel: AddEditItemViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    ErrorSnackbarEffect(
        error = uiState.error,
        onErrorShown = { viewModel.clearError() },
        snackbarHostState = snackbarHostState
    )

    val isCard = uiState.category.equals("card", ignoreCase = true)
    val isBank = uiState.category.equals("bank", ignoreCase = true)
    var showValidationHints by remember { mutableStateOf(false) }

    val saveEnabled = when {
        uiState.title.isBlank() -> false
        isCard -> {
            val panOk = uiState.cardNumber.filter { it.isDigit() }.length == CARD_NUMBER_DIGITS
            val expOk = parseExpiryFieldDigits(uiState.cardExpiry) != null
            panOk && expOk
        }
        isBank -> uiState.password.isNotBlank() && uiState.bankPasswordViolations.isEmpty()
        else -> uiState.password.isNotBlank()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (itemId == null) stringResource(R.string.item_add_title) else stringResource(R.string.item_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Text(
                text = if (itemId == null)
                    stringResource(R.string.item_add_subtitle)
                else
                    stringResource(R.string.item_edit_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AddEditItemFormCard(
                uiState = uiState,
                viewModel = viewModel,
                isCard = isCard,
                isBank = isBank,
                showValidationHints = showValidationHints,
                onNavigateToGenerator = onNavigateToGenerator
            )

            LoadingButton(
                text = stringResource(R.string.item_save_button),
                onClick = {
                    showValidationHints = true
                    viewModel.save()
                },
                isLoading = uiState.isLoading,
                enabled = saveEnabled,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AddEditItemFormCard(
    uiState: AddEditUiState,
    viewModel: AddEditItemViewModel,
    isCard: Boolean,
    isBank: Boolean,
    showValidationHints: Boolean,
    onNavigateToGenerator: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.vault_group_filter_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(ItemCategory.entries, key = { it.name }) { cat ->
                val selected = uiState.category.equals(cat.name, ignoreCase = true)
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.onCategoryChange(cat.name.lowercase()) },
                    label = { Text(cat.label) }
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title — always shown
                OutlinedTextField(
                    value = uiState.title,
                    onValueChange = viewModel::onTitleChange,
                    label = { Text(stringResource(R.string.item_title_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    isError = showValidationHints && uiState.title.isBlank(),
                    supportingText = if (showValidationHints && uiState.title.isBlank()) {
                        {
                            Text(
                                stringResource(R.string.item_validation_title_required),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isCard) {
                    CardFormFields(
                        uiState = uiState,
                        viewModel = viewModel,
                        showValidationHints = showValidationHints
                    )
                } else if (isBank) {
                    BankFormFields(
                        uiState = uiState,
                        viewModel = viewModel,
                        showValidationHints = showValidationHints
                    )
                } else {
                    DefaultFormFields(
                        uiState = uiState,
                        viewModel = viewModel,
                        showValidationHints = showValidationHints,
                        onNavigateToGenerator = onNavigateToGenerator
                    )
                }

                // Notes — always shown
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = viewModel::onNotesChange,
                    label = { Text(stringResource(R.string.item_notes_hint)) },
                    minLines = 3,
                    maxLines = 6,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun DefaultFormFields(
    uiState: AddEditUiState,
    viewModel: AddEditItemViewModel,
    showValidationHints: Boolean,
    onNavigateToGenerator: () -> Unit
) {
    OutlinedTextField(
        value = uiState.username,
        onValueChange = viewModel::onUsernameChange,
        label = { Text(stringResource(R.string.item_username_hint)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = uiState.address,
        onValueChange = viewModel::onAddressChange,
        label = { Text(stringResource(R.string.item_address_hint)) },
        singleLine = true,
        placeholder = { Text(stringResource(R.string.item_address_placeholder)) },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.item_password_hint),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        AssistChip(
            onClick = onNavigateToGenerator,
            label = { Text(stringResource(R.string.item_generate_password)) },
            leadingIcon = {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                )
            },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        )
    }

    SecureTextField(
        value = uiState.password,
        onValueChange = viewModel::onPasswordChange,
        label = stringResource(R.string.item_password_hint),
        isError = showValidationHints && uiState.password.isBlank(),
        supportingText = if (showValidationHints && uiState.password.isBlank()) {
            {
                Text(
                    stringResource(R.string.item_validation_password_required),
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else null,
        modifier = Modifier.fillMaxWidth()
    )

    if (uiState.password.isNotEmpty()) {
        PasswordStrengthBar(
            password = uiState.password,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CardFormFields(
    uiState: AddEditUiState,
    viewModel: AddEditItemViewModel,
    showValidationHints: Boolean
) {
    // LTR + explicit text direction stops RTL locales from reordering digits around “/” (e.g. 3232 → 2323).
    val ltrDigitsTextStyle = MaterialTheme.typography.bodyLarge.merge(
        TextStyle(textDirection = TextDirection.Ltr)
    )
    val panDigits = uiState.cardNumber.filter { it.isDigit() }
    val panHasError = panDigits.isNotEmpty() && panDigits.length != CARD_NUMBER_DIGITS
    val panSupporting: (@Composable () -> Unit)? = when {
        panDigits.length == 15 -> {
            {
                Text(
                    stringResource(R.string.item_card_pan_fifteen),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        panHasError && panDigits.isNotEmpty() -> {
            {
                Text(
                    stringResource(R.string.item_card_pan_progress, panDigits.length),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        panDigits.isNotEmpty() -> {
            {
                Text(
                    stringResource(R.string.item_card_pan_progress, panDigits.length),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        showValidationHints -> {
            {
                Text(
                    stringResource(R.string.item_card_number_invalid_16),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        else -> null
    }

    OutlinedTextField(
        value = uiState.cardNumber,
        onValueChange = viewModel::onCardNumberChange,
        label = { Text(stringResource(R.string.item_card_number_hint)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        textStyle = ltrDigitsTextStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        visualTransformation = CardNumberVisualTransformation(),
        isError = panHasError || (showValidationHints && panDigits.isEmpty()),
        supportingText = panSupporting,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = uiState.cardholderName,
        onValueChange = viewModel::onCardholderNameChange,
        label = { Text(stringResource(R.string.item_cardholder_hint)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    )

    val expiryDigits = uiState.cardExpiry
    val mmPartial =
        if (expiryDigits.length >= 2) expiryDigits.take(2).toIntOrNull() else null
    val expiryMonthOutOfRange = mmPartial != null && mmPartial !in 1..12
    val expiryParseOk = parseExpiryFieldDigits(uiState.cardExpiry) != null
    val expiryHasError = expiryMonthOutOfRange ||
        (expiryDigits.length == 4 && !expiryParseOk)
    val cvcDigits = uiState.cardCvc.filter { it.isDigit() }
    val cvcWeak = cvcDigits.isNotEmpty() && cvcDigits.length < 3

    val expirySupporting: (@Composable () -> Unit)? = when {
        expiryHasError -> {
            {
                Text(
                    stringResource(R.string.item_expiry_invalid),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        showValidationHints && expiryDigits.isEmpty() -> {
            {
                Text(
                    stringResource(R.string.item_expiry_required),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        else -> null
    }
    val cvcSupporting: (@Composable () -> Unit)? = if (cvcWeak) {
        {
            Text(
                stringResource(R.string.item_card_cvc_hint_validation),
                color = MaterialTheme.colorScheme.error
            )
        }
    } else null

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            OutlinedTextField(
                value = uiState.cardExpiry,
                onValueChange = viewModel::onCardExpiryChange,
                label = { Text(stringResource(R.string.item_expiry_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                textStyle = ltrDigitsTextStyle,
                visualTransformation = ExpiryMmYyVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = expiryHasError || (showValidationHints && expiryDigits.isEmpty()),
                supportingText = expirySupporting,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = uiState.cardCvc,
                onValueChange = viewModel::onCardCvcChange,
                label = { Text(stringResource(R.string.item_cvc_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                textStyle = ltrDigitsTextStyle,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = cvcWeak,
                supportingText = cvcSupporting,
                modifier = Modifier.weight(0.85f)
            )
        }
    }
}

@Composable
private fun BankFormFields(
    uiState: AddEditUiState,
    viewModel: AddEditItemViewModel,
    showValidationHints: Boolean
) {
    OutlinedTextField(
        value = uiState.username,
        onValueChange = viewModel::onUsernameChange,
        label = { Text(stringResource(R.string.item_account_number_hint)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = uiState.address,
        onValueChange = viewModel::onAddressChange,
        label = { Text(stringResource(R.string.item_bank_name_hint)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    )

    Text(
        text = stringResource(R.string.item_password_hint),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface
    )

    val bankPwError = (showValidationHints && uiState.password.isBlank()) ||
        uiState.bankPasswordViolations.isNotEmpty()
    SecureTextField(
        value = uiState.password,
        onValueChange = viewModel::onPasswordChange,
        label = stringResource(R.string.item_password_hint),
        isError = bankPwError,
        supportingText = when {
            showValidationHints && uiState.password.isBlank() -> {
                {
                    Text(
                        stringResource(R.string.item_validation_password_required),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            uiState.bankPasswordViolations.isNotEmpty() -> {
                {
                    Text(
                        stringResource(R.string.bank_password_invalid),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            else -> null
        },
        modifier = Modifier.fillMaxWidth()
    )

    if (uiState.password.isNotEmpty()) {
        BankPasswordRuleIndicator(
            password = uiState.password,
            violations = uiState.bankPasswordViolations,
            showReusedRule = uiState.previousPasswords.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Inserts `/` after MM while the model holds four digits only — fixes cursor sticking on `/`. */
private class ExpiryMmYyVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val formatted = buildString {
            if (digits.isEmpty()) return@buildString
            append(digits.take(2))
            if (digits.length > 2) {
                append('/')
                append(digits.drop(2))
            }
        }
        val n = digits.length
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val o = offset.coerceIn(0, n)
                if (n <= 2) return o
                if (o <= 2) return if (o < 2) o else 3
                return o + 1
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (n <= 2) return offset.coerceIn(0, n)
                val len = formatted.length
                val o = offset.coerceIn(0, len)
                if (o <= 2) return o.coerceAtMost(2)
                if (o >= len) return n
                if (formatted.getOrNull(o) == '/') return 2
                return o - 1
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

private class CardNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val formatted = buildString {
            digits.forEachIndexed { i, c ->
                if (i > 0 && i % 4 == 0) append(' ')
                append(c)
            }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                return offset + (offset - 1) / 4
            }
            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 0) return 0
                val spacesBeforeOffset = (0 until offset).count { formatted.getOrNull(it) == ' ' }
                return (offset - spacesBeforeOffset).coerceIn(0, digits.length)
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}
