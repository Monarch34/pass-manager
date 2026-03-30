package com.passmanager.domain.port

import com.passmanager.domain.model.LockState
import kotlinx.coroutines.flow.StateFlow

interface LockStateProvider {
    val lockState: StateFlow<LockState>
    fun lock()
}
