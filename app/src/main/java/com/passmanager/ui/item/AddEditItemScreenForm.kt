package com.passmanager.ui.item

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.passmanager.ui.components.CategoryChip
import com.passmanager.ui.components.PanelCard
import com.passmanager.ui.components.PanelSecureTextField
import com.passmanager.ui.components.PanelTextField
import com.passmanager.ui.components.PasswordStrengthBar
import com.passmanager.ui.components.SectionHeader
import com.passmanager.ui.model.icon
import com.passmanager.ui.model.tint

@Composable
internal fun AddEditItemFormCard(
    uiState: AddEditUiState,
    viewModel: AddEditItemViewModel,
    showValidationHints: Boolean,
    onNavigateToGenerator: () -> Unit
) {
    // The fields sit straight on the canvas rather than inside one big card. Each field already
    // carries its own hairline, so wrapping the lot in a second filled shape drew a box around a
    // column of boxes; only the panels that group several controls under one idea — the bank rule
    // list, the strength readout — still earn a card of their own.
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionHeader(
                text = stringResource(R.string.item_category_label),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(ItemCategory.entries, key = { it.name }) { cat ->
                    CategoryChip(
                        label = cat.label,
                        selected = uiState.category == cat,
                        tint = cat.tint,
                        icon = cat.icon,
                        onClick = { viewModel.onCategoryChange(cat) }
                    )
                }
            }
        }

        PanelTextField(
            value = uiState.title,
            onValueChange = viewModel::onTitleChange,
            label = stringResource(R.string.item_title_hint),
            isError = showValidationHints && uiState.title.isBlank(),
            errorText = stringResource(R.string.item_validation_title_required)
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

        PanelTextField(
            value = uiState.notes,
            onValueChange = viewModel::onNotesChange,
            label = stringResource(R.string.item_notes_hint),
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            keyboardOptions = KeyboardOptions.Default
        )
    }
}

/**
 * The generator button, sitting inside the password field rather than above it. As a chip on its
 * own row it read as a separate step you had to know to take first; in the field it is where the
 * hand already is.
 */
@Composable
private fun GeneratorFieldButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = stringResource(R.string.item_generate_password),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun DefaultFormFields(
    uiState: AddEditUiState,
    viewModel: AddEditItemViewModel,
    showValidationHints: Boolean,
    onNavigateToGenerator: () -> Unit
) {
    PanelTextField(
        value = uiState.username,
        onValueChange = viewModel::onUsernameChange,
        label = stringResource(R.string.item_username_hint)
    )

    PanelTextField(
        value = uiState.address,
        onValueChange = viewModel::onAddressChange,
        label = stringResource(R.string.item_address_hint),
        placeholder = stringResource(R.string.item_address_placeholder)
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PanelSecureTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChange,
            label = stringResource(R.string.item_password_hint),
            isError = showValidationHints && uiState.password.isBlank(),
            errorText = stringResource(R.string.item_validation_password_required),
            extraTrailing = { GeneratorFieldButton(onClick = onNavigateToGenerator) }
        )

        if (uiState.password.isNotEmpty()) {
            PasswordStrengthBar(
                password = uiState.password,
                modifier = Modifier.fillMaxWidth()
            )
        }
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
    val panSupporting: String? = when (val h = panUi.supportingHint) {
        CardPanSupportingHint.None -> null
        CardPanSupportingHint.FifteenDigitAmex -> stringResource(R.string.item_card_pan_fifteen)
        is CardPanSupportingHint.Progress ->
            stringResource(R.string.item_card_pan_progress, h.digitCount)
        CardPanSupportingHint.RequiredWhenHint ->
            stringResource(R.string.item_card_number_invalid_16)
    }
    // The progress hint is the one line here that is not a complaint: it counts digits while the
    // number is still being typed, so it must not be painted in the error colour.
    val panSupportingIsError = when (val h = panUi.supportingHint) {
        is CardPanSupportingHint.Progress -> h.treatAsError
        CardPanSupportingHint.None -> false
        else -> true
    }

    PanelTextField(
        value = uiState.cardNumber,
        onValueChange = viewModel::onCardNumberChange,
        label = stringResource(R.string.item_card_number_hint),
        textStyle = ltrDigitsTextStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        visualTransformation = CardNumberVisualTransformation(),
        isError = panUi.isFieldError,
        errorText = if (panSupportingIsError) panSupporting else null,
        supportingText = if (panSupportingIsError) null else panSupporting
    )

    PanelTextField(
        value = uiState.cardholderName,
        onValueChange = viewModel::onCardholderNameChange,
        label = stringResource(R.string.item_cardholder_hint)
    )

    val expiryUi = cardExpiryFieldUiState(uiState.cardExpiry, showValidationHints)
    val cvcWeak = cardCvcIsWeak(uiState.cardCvc)

    val expirySupporting: String? = when {
        expiryUi.showInvalidSupporting -> stringResource(R.string.item_expiry_invalid)
        expiryUi.showRequiredSupporting -> stringResource(R.string.item_expiry_required)
        else -> null
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            PanelTextField(
                value = uiState.cardExpiry,
                onValueChange = viewModel::onCardExpiryChange,
                label = stringResource(R.string.item_expiry_hint),
                textStyle = ltrDigitsTextStyle,
                visualTransformation = ExpiryMmYyVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = expiryUi.isFieldError,
                errorText = expirySupporting,
                modifier = Modifier.weight(1f)
            )
            PanelTextField(
                value = uiState.cardCvc,
                onValueChange = viewModel::onCardCvcChange,
                label = stringResource(R.string.item_cvc_hint),
                textStyle = ltrDigitsTextStyle,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = cvcWeak,
                errorText = stringResource(R.string.item_card_cvc_hint_validation),
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
    PanelTextField(
        value = uiState.accountNumber,
        onValueChange = viewModel::onAccountNumberChange,
        label = stringResource(R.string.item_account_number_hint)
    )

    PanelTextField(
        value = uiState.bankName,
        onValueChange = viewModel::onBankNameChange,
        label = stringResource(R.string.item_bank_name_hint)
    )

    val bankPwError = (showValidationHints && uiState.bankPassword.isBlank()) ||
        uiState.bankPasswordViolations.isNotEmpty()
    PanelSecureTextField(
        value = uiState.bankPassword,
        onValueChange = viewModel::onBankPasswordChange,
        label = stringResource(R.string.item_password_hint),
        isError = bankPwError,
        errorText = when {
            showValidationHints && uiState.bankPassword.isBlank() ->
                stringResource(R.string.item_validation_password_required)
            uiState.bankPasswordViolations.isNotEmpty() ->
                stringResource(R.string.bank_password_invalid)
            else -> null
        }
    )

    if (uiState.bankPassword.isNotEmpty()) {
        PanelCard(contentPadding = PaddingValues(16.dp)) {
            SectionHeader(
                text = stringResource(R.string.generator_constraint_title),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 0.dp)
            )
            BankPasswordRuleIndicator(
                password = uiState.bankPassword,
                violations = uiState.bankPasswordViolations,
                showReusedRule = uiState.previousPasswords.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
internal fun IdentityFormFields(
    uiState: AddEditUiState,
    viewModel: AddEditItemViewModel
) {
    PanelTextField(
        value = uiState.firstName,
        onValueChange = viewModel::onFirstNameChange,
        label = stringResource(R.string.identity_first_name)
    )
    PanelTextField(
        value = uiState.lastName,
        onValueChange = viewModel::onLastNameChange,
        label = stringResource(R.string.identity_last_name)
    )
    PanelTextField(
        value = uiState.email,
        onValueChange = viewModel::onEmailChange,
        label = stringResource(R.string.identity_email),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
    )
    PanelTextField(
        value = uiState.phone,
        onValueChange = viewModel::onPhoneChange,
        label = stringResource(R.string.identity_phone),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
    )
    PanelTextField(
        value = uiState.identityAddress,
        onValueChange = viewModel::onIdentityAddressChange,
        label = stringResource(R.string.item_address_hint)
    )
    PanelTextField(
        value = uiState.company,
        onValueChange = viewModel::onCompanyChange,
        label = stringResource(R.string.identity_company)
    )
}

@Composable
internal fun NoteFormFields() {
    Text(
        text = stringResource(R.string.note_form_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
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
