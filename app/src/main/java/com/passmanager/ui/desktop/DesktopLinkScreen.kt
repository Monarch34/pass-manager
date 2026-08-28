package com.passmanager.ui.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import com.passmanager.ui.components.AppSnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.passmanager.R
import com.passmanager.ui.components.ErrorSnackbarEffect
import com.passmanager.ui.components.PanelHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopLinkScreen(
    onOpenDrawer: () -> Unit = {},
    viewModel: DesktopLinkViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    ErrorSnackbarEffect(
        error = uiState.error,
        onErrorShown = { viewModel.clearError() },
        snackbarHostState = snackbarHostState
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PanelHeader(
                title = stringResource(R.string.desktop_link_title),
                large = true,
                navigationIcon = Icons.Default.Menu,
                navigationContentDescription = stringResource(R.string.nav_open_drawer),
                onNavigationClick = onOpenDrawer
            )
        },
        snackbarHost = { AppSnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(0.dp))

            Text(
                text = stringResource(R.string.desktop_link_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
                !uiState.vaultUnlocked -> VaultLockedContent()
                uiState.isScanning -> QrScannerContent(viewModel)
                else -> SessionContent(uiState, viewModel)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
