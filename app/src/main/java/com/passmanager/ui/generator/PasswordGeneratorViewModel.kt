package com.passmanager.ui.generator

import androidx.lifecycle.ViewModel
import com.passmanager.domain.usecase.GeneratePasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.compose.runtime.Immutable
import javax.inject.Inject

@Immutable
data class GeneratorUiState(
    val password: String = "",
    val length: Int = 16,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSymbols: Boolean = true,
    val generateTrigger: Int = 0,  // Incremented on each generate for animation key
    val entropyBits: Int = 0
)

@HiltViewModel
class PasswordGeneratorViewModel @Inject constructor(
    private val generatePasswordUseCase: GeneratePasswordUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(GeneratorUiState())
    val uiState: StateFlow<GeneratorUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { s -> s.withGenerated() }
    }

    fun setLength(length: Int) {
        _uiState.update { s -> s.copy(length = length).withGenerated() }
    }

    fun toggleUppercase() {
        _uiState.update { s -> s.copy(includeUppercase = !s.includeUppercase).withGenerated() }
    }

    fun toggleLowercase() {
        _uiState.update { s -> s.copy(includeLowercase = !s.includeLowercase).withGenerated() }
    }

    fun toggleDigits() {
        _uiState.update { s -> s.copy(includeDigits = !s.includeDigits).withGenerated() }
    }

    fun toggleSymbols() {
        _uiState.update { s -> s.copy(includeSymbols = !s.includeSymbols).withGenerated() }
    }

    fun generate() {
        _uiState.update { s ->
            s.copy(generateTrigger = s.generateTrigger + 1).withGenerated()
        }
    }

    /** Returns a copy with [password] and [entropyBits] freshly computed from current settings. */
    private fun GeneratorUiState.withGenerated(): GeneratorUiState {
        val newPassword = generateFrom(this)
        val newEntropy  = computeEntropyBits(this)
        return copy(password = newPassword, entropyBits = newEntropy)
    }

    private fun generateFrom(state: GeneratorUiState): String {
        if (!state.includeUppercase && !state.includeLowercase &&
            !state.includeDigits && !state.includeSymbols) {
            return state.password
        }
        return generatePasswordUseCase(
            length           = state.length,
            includeUppercase = state.includeUppercase,
            includeLowercase = state.includeLowercase,
            includeDigits    = state.includeDigits,
            includeSymbols   = state.includeSymbols
        )
    }

    private fun computeEntropyBits(state: GeneratorUiState): Int {
        var poolSize = 0
        if (state.includeUppercase) poolSize += 26
        if (state.includeLowercase) poolSize += 26
        if (state.includeDigits)    poolSize += 10
        if (state.includeSymbols)   poolSize += 32
        return if (poolSize <= 0) 0
        else (state.length * (kotlin.math.ln(poolSize.toDouble()) / kotlin.math.ln(2.0))).toInt()
    }
}
