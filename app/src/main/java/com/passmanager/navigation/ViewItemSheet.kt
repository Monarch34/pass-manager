package com.passmanager.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.passmanager.ui.item.ViewItemPresentation
import com.passmanager.ui.item.ViewItemScreen
import com.passmanager.ui.item.ViewItemViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewItemSheet(
    itemId: String,
    onDismiss: () -> Unit,
    onRequestEdit: (String) -> Unit
) {
    val viewItemVm: ViewItemViewModel = hiltViewModel(key = "viewItem_$itemId")
    val configuration = LocalConfiguration.current
    val maxSheetHeight = (configuration.screenHeightDp * 0.70f).dp
    val scope = rememberCoroutineScope()

    LaunchedEffect(itemId) {
        viewItemVm.loadForItem(itemId)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f),
        dragHandle = null
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
        ) {
            ViewItemScreen(
                itemId = itemId,
                presentation = ViewItemPresentation.Sheet,
                onNavigateBack = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                },
                onRequestEdit = {
                    onDismiss()
                    onRequestEdit(itemId)
                },
                viewModel = viewItemVm
            )
        }
    }
}
