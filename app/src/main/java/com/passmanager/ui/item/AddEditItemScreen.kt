package com.passmanager.ui.item

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import com.passmanager.ui.components.AppSnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.passmanager.R
import com.passmanager.domain.validation.AddEditSaveFailure
import com.passmanager.ui.components.ErrorSnackbarEffect
import com.passmanager.ui.components.LoadingButton

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
        if (uiState.isSaved) {
            viewModel.clearSavedFlag()
            onNavigateBack()
        }
    }

    ErrorSnackbarEffect(
        error = uiState.error,
        onErrorShown = { viewModel.clearError() },
        snackbarHostState = snackbarHostState
    )

    var showValidationHints by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    // A half-filled card number or identity is worth one tap to confirm; a clean form is not.
    val requestLeave: () -> Unit = {
        if (uiState.isDirty) {
            showDiscardDialog = true
        } else {
            onNavigateBack()
        }
    }
    BackHandler(enabled = uiState.isDirty) { showDiscardDialog = true }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (itemId == null) stringResource(R.string.item_add_title) else stringResource(R.string.item_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = requestLeave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            )
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) }
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
                enabled = uiState.canSave,
                modifier = Modifier.fillMaxWidth()
            )

            // A disabled save button with no explanation reads as a broken app. Kept off a
            // pristine new form, where "title is required" would only be nagging.
            val blockedReason = uiState.saveBlockedReason
            if (blockedReason != null && !uiState.isLoading &&
                (itemId != null || uiState.isDirty || showValidationHints)
            ) {
                Text(
                    text = stringResource(saveBlockedMessageRes(blockedReason)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (showValidationHints) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.item_discard_title)) },
            text = { Text(stringResource(R.string.item_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.item_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.item_discard_cancel))
                }
            }
        )
    }
}

/** Turns the save-time failure into the one line that explains the disabled save button. */
@StringRes
private fun saveBlockedMessageRes(failure: AddEditSaveFailure): Int = when (failure) {
    AddEditSaveFailure.TitleRequired -> R.string.item_title_required
    AddEditSaveFailure.CardPanInvalid -> R.string.item_card_number_invalid_16
    AddEditSaveFailure.CardExpiryInvalid -> R.string.item_expiry_invalid
    AddEditSaveFailure.PasswordRequired -> R.string.item_password_required
    is AddEditSaveFailure.BankInvalid -> R.string.bank_password_invalid
}
