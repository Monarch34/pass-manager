package com.passmanager.ui.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.passmanager.R
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.validation.cardCvcIsWeak
import com.passmanager.ui.components.BankPasswordRuleIndicator
import com.passmanager.ui.components.PasswordStrengthBar
import com.passmanager.ui.components.SecureTextField

@Composable
internal fun AddEditItemFormCard(
    uiState: AddEditUiState,
    viewModel: AddEditItemViewModel,
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
                FilterChip(
                    selected = uiState.category == cat,
                    onClick = { viewModel.onCategoryChange(cat) },
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

                when (uiState.category) {
                    ItemCategory.CARD -> CardFormFields(
                        uiState = uiState,
                        viewModel = viewModel,
                        showValidationHints = showValidationHints
                    )
                    ItemCategory.BANK -> BankFormFields(
                        uiState = uiState,
                        viewModel = viewModel,
                        showValidationHints = showValidationHints
                    )
                    ItemCategory.NOTE -> NoteFormFields()
                    ItemCategory.IDENTITY -> IdentityFormFields(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                    else -> DefaultFormFields(
                        uiState = uiState,
                        viewModel = viewModel,
                        showValidationHints = showValidationHints,
                        onNavigateToGenerator = onNavigateToGenerator
                    )
                }

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
internal fun DefaultFormFields(
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
internal fun CardFormFields(
    uiState: AddEditUiState,
    viewModel: AddEditItemViewModel,
    showValidationHints: Boolean
) {
    val ltrDigitsTextStyle = MaterialTheme.typography.bodyLarge.merge(
        TextStyle(textDirection = TextDirection.Ltr)
    )
    val panUi = cardPanFieldUiState(uiState.cardNumber, showValidationHints)
    val panSupporting: (@Composable () -> Unit)? = when (val h = panUi.supportingHint) {
        CardPanSupportingHint.None -> null
        CardPanSupportingHint.FifteenDigitAmex -> {
            {
                Text(
                    stringResource(R.string.item_card_pan_fifteen),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        is CardPanSupportingHint.Progress -> {
            {
                Text(
                    stringResource(R.string.item_card_pan_progress, h.digitCount),
                    color = if (h.treatAsError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        CardPanSupportingHint.RequiredWhenHint -> {
            {
                Text(
                    stringResource(R.string.item_card_number_invalid_16),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
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
        isError = panUi.isFieldError,
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
    val expiryUi = cardExpiryFieldUiState(expiryDigits, showValidationHints)
    val cvcWeak = cardCvcIsWeak(uiState.cardCvc)

    val expirySupporting: (@Composable () -> Unit)? = when {
        expiryUi.showInvalidSupporting -> {
            {
                Text(
                    stringResource(R.string.item_expiry_invalid),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        expiryUi.showRequiredSupporting -> {
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
                isError = expiryUi.isFieldError,
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
internal fun BankFormFields(
    uiState: AddEditUiState,
    viewModel: AddEditItemViewModel,
    showValidationHints: Boolean
) {
    OutlinedTextField(
        value = uiState.accountNumber,
        onValueChange = viewModel::onAccountNumberChange,
        label = { Text(stringResource(R.string.item_account_number_hint)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        value = uiState.bankName,
        onValueChange = viewModel::onBankNameChange,
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

    val bankPwError = (showValidationHints && uiState.bankPassword.isBlank()) ||
        uiState.bankPasswordViolations.isNotEmpty()
    SecureTextField(
        value = uiState.bankPassword,
        onValueChange = viewModel::onBankPasswordChange,
        label = stringResource(R.string.item_password_hint),
        isError = bankPwError,
        supportingText = when {
            showValidationHints && uiState.bankPassword.isBlank() -> {
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

    if (uiState.bankPassword.isNotEmpty()) {
        BankPasswordRuleIndicator(
            password = uiState.bankPassword,
            violations = uiState.bankPasswordViolations,
            showReusedRule = uiState.previousPasswords.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun IdentityFormFields(
    uiState: AddEditUiState,
    viewModel: AddEditItemViewModel
) {
    OutlinedTextField(
        value = uiState.firstName,
        onValueChange = viewModel::onFirstNameChange,
        label = { Text(stringResource(R.string.identity_first_name)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = uiState.lastName,
        onValueChange = viewModel::onLastNameChange,
        label = { Text(stringResource(R.string.identity_last_name)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = uiState.email,
        onValueChange = viewModel::onEmailChange,
        label = { Text(stringResource(R.string.identity_email)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = uiState.phone,
        onValueChange = viewModel::onPhoneChange,
        label = { Text(stringResource(R.string.identity_phone)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = uiState.identityAddress,
        onValueChange = viewModel::onIdentityAddressChange,
        label = { Text(stringResource(R.string.item_address_hint)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = uiState.company,
        onValueChange = viewModel::onCompanyChange,
        label = { Text(stringResource(R.string.identity_company)) },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun NoteFormFields() {
    Text(
        text = stringResource(R.string.note_form_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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
