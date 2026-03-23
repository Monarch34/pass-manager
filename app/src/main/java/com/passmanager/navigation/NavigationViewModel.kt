package com.passmanager.navigation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.security.LockState
import com.passmanager.security.VaultLockManager
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
    private val vaultLockManager: VaultLockManager,
    private val metadataRepository: MetadataRepository
) : ViewModel() {

    val lockState: StateFlow<LockState> = vaultLockManager.lockState

    private val _navReady = MutableStateFlow<NavReady>(NavReady.Loading)
    val navReady: StateFlow<NavReady> = _navReady.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val isSetup = metadataRepository.isVaultSetup()
                _navReady.value = NavReady.Ready(isSetup)
            } catch (e: Exception) {
                Log.e("NavigationViewModel", "Failed to check vault setup", e)
                _navReady.value = NavReady.Ready(false)
            }
        }
    }
}
