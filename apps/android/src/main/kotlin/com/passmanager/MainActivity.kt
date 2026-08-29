package com.passmanager

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.passmanager.ui.VaultViewModel
import com.passmanager.ui.screens.AddEditItemScreen
import com.passmanager.ui.screens.CreateVaultScreen
import com.passmanager.ui.screens.ItemDetailScreen
import com.passmanager.ui.screens.LockScreen
import com.passmanager.ui.screens.VaultListScreen
import com.passmanager.ui.theme.PassManagerTheme

class MainActivity : ComponentActivity() {

    private val model: VaultViewModel by lazy {
        androidx.lifecycle.ViewModelProvider(this)[VaultViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keeps the vault out of the recents thumbnail and out of screenshots and screen
        // recordings. It is set for the whole activity rather than per screen, because the
        // list shows entry titles and the recents thumbnail is taken on the way out.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            PassManagerTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) { Root() }
            }
        }
    }

    /**
     * Leaving the app locks it.
     *
     * `onStop` rather than `onPause`: `onPause` also fires for a permission dialog or the
     * notification shade, and locking behind those would be unusable. `onStop` means the app
     * is genuinely no longer on screen, which is when the key should stop existing.
     */
    override fun onStop() {
        super.onStop()
        model.lock()
    }
}

@Composable
private fun Root() {
    val model: VaultViewModel = viewModel()
    var editing by remember { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }

    when (model.phase) {
        VaultViewModel.Phase.Empty -> CreateVaultScreen(model)
        VaultViewModel.Phase.Locked -> LockScreen(model)
        VaultViewModel.Phase.Unlocked -> {
            val open = viewing?.let(model::item)
            val edited = editing?.let(model::item)
            when {
                adding || edited != null -> AddEditItemScreen(
                    model = model,
                    existing = edited,
                    onDone = { adding = false; editing = null },
                )
                open != null -> ItemDetailScreen(
                    model = model,
                    item = open,
                    onEdit = { editing = open.id.value },
                    onBack = { viewing = null },
                )
                else -> VaultListScreen(
                    model = model,
                    onOpen = { viewing = it.id.value },
                    onAdd = { adding = true },
                )
            }
        }
    }
}
