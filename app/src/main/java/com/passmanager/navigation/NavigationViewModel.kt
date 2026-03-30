package com.passmanager.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.BuildConfig
import com.passmanager.domain.model.LockState
import com.passmanager.domain.port.LockStateProvider
import com.passmanager.domain.usecase.CheckVaultSetupUseCase
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
    private val checkVaultSetupUseCase: CheckVaultSetupUseCase
) : ViewModel() {

    val lockState: StateFlow<LockState> = lockStateProvider.lockState

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
