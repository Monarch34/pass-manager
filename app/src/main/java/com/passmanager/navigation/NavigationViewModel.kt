package com.passmanager.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.BuildConfig
import com.passmanager.domain.model.LockState
import com.passmanager.domain.port.LockStateProvider
import com.passmanager.domain.usecase.CheckVaultSetupUseCase
import com.passmanager.ui.settings.PendingVaultTransfer
import com.passmanager.ui.settings.VaultTransferRequests
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface NavReady {
    data object Loading : NavReady
    data class Ready(val isVaultSetup: Boolean) : NavReady
}

@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val lockStateProvider: LockStateProvider,
    private val checkVaultSetupUseCase: CheckVaultSetupUseCase,
    transferRequests: VaultTransferRequests
) : ViewModel() {

    val lockState: StateFlow<LockState> = lockStateProvider.lockState

    /**
     * A document picked in the file picker that the auto-lock interrupted. Non-null means the main
     * graph should open Settings on entry so the export/import flow can finish where it started.
     */
    val pendingVaultTransfer: StateFlow<PendingVaultTransfer?> = transferRequests.pending

    private val _navReady = MutableStateFlow<NavReady>(NavReady.Loading)
    val navReady: StateFlow<NavReady> = _navReady.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val isSetup = checkVaultSetupUseCase()
                _navReady.value = NavReady.Ready(isSetup)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    android.util.Log.e("NavigationViewModel", "Failed to check vault setup", e)
                }
                _navReady.value = NavReady.Ready(false)
            }
        }
    }
}
