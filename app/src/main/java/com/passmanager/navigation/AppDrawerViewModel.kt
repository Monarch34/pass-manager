package com.passmanager.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.passmanager.domain.model.PairingSessionState
import com.passmanager.domain.port.DesktopPairingPort
import com.passmanager.domain.port.LockStateProvider
import com.passmanager.domain.usecase.ObserveVaultHeadersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * What the navigation drawer knows about the session it is a menu for: how much is in the vault,
 * and whether a desktop is on the other end of the link.
 *
 * Both are read straight from flows the app already keeps hot — the header list is the same one
 * the vault list observes, and the pairing state is the session object's own. Neither costs a
 * decrypt: the count is `size` on a list of row headers, and it is only ever shown next to the
 * word "unlocked", which is the one claim the drawer makes that the user cannot verify by looking.
 */
@HiltViewModel
class AppDrawerViewModel @Inject constructor(
    observeVaultHeadersUseCase: ObserveVaultHeadersUseCase,
    desktopPairingPort: DesktopPairingPort,
    private val lockStateProvider: LockStateProvider
) : ViewModel() {

    val itemCount: StateFlow<Int> = observeVaultHeadersUseCase()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val desktopConnected: StateFlow<Boolean> = desktopPairingPort.pairingState
        .map { it is PairingSessionState.Active }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun lock() {
        lockStateProvider.lock()
    }
}
