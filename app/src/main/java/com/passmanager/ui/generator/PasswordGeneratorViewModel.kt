package com.passmanager.ui.generator

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.usecase.GeneratePasswordUseCase
import com.passmanager.domain.validation.BankPasswordRules
import com.passmanager.domain.validation.BankPasswordValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.compose.runtime.Immutable
import javax.inject.Inject

/** Navigation argument carrying the category whose password rules the generator must obey. */
const val GENERATOR_CONSTRAINT_CATEGORY_ARG = "constraintCategory"

/** [GeneratePasswordUseCase] rejects anything shorter, whatever the category asks for. */
private const val ABSOLUTE_MIN_LENGTH = 8

/**
 * A random draw can still land on `abc` or `111`. Redrawing is cheaper and keeps full entropy,
 * unlike patching characters into place.
 */
private const val MAX_GENERATION_ATTEMPTS = 12

/**
 * Limits the target category puts on a generated password.
 *
 * Without this the generator hands the bank form a 16-character password that the form then
 * rejects on arrival — the user is sent back and forth with no way to tell what went wrong.
 */
@Immutable
data class GeneratorConstraint(
    val category: ItemCategory,
    val minLength: Int,
    val maxLength: Int,
    val requireUppercase: Boolean,
    val requireLowercase: Boolean,
    val requireDigits: Boolean,
    val allowSymbols: Boolean
) {
    companion object {
        /** Null for categories that accept any password the generator can produce. */
        fun forCategory(category: ItemCategory): GeneratorConstraint? = when (category) {
            // Mirrors BankPasswordValidator: 6–12 characters, mixed case, at least one digit.
            ItemCategory.BANK -> GeneratorConstraint(
                category = ItemCategory.BANK,
                minLength = maxOf(BankPasswordRules.MIN_LENGTH, ABSOLUTE_MIN_LENGTH),
                maxLength = BankPasswordRules.MAX_LENGTH,
                requireUppercase = true,
                requireLowercase = true,
                requireDigits = true,
                allowSymbols = true
            )
            else -> null
        }
    }
}

@Immutable
data class GeneratorUiState(
    val password: String = "",
    val length: Int = 16,
    val includeUppercase: Boolean = true,
    val includeLowercase: Boolean = true,
    val includeDigits: Boolean = true,
    val includeSymbols: Boolean = true,
    val generateTrigger: Int = 0,  // Incremented on each generate for animation key
    val entropyBits: Int = 0,
    /** Non-null when the generator was opened from a form whose category has password rules. */
    val constraint: GeneratorConstraint? = null
)

@HiltViewModel
class PasswordGeneratorViewModel @Inject constructor(
    private val generatePasswordUseCase: GeneratePasswordUseCase,
    private val bankPasswordValidator: BankPasswordValidator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val constraint: GeneratorConstraint? =
        savedStateHandle.get<String>(GENERATOR_CONSTRAINT_CATEGORY_ARG)
            ?.takeIf { it.isNotBlank() }
            ?.let { GeneratorConstraint.forCategory(ItemCategory.fromString(it)) }

    private val _uiState = MutableStateFlow(GeneratorUiState(constraint = constraint))
    val uiState: StateFlow<GeneratorUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { s -> s.conformed().withGenerated() }
    }

    fun setLength(length: Int) {
        _uiState.update { s -> s.copy(length = length).conformed().withGenerated() }
    }

    fun toggleUppercase() {
        _uiState.update { s -> s.copy(includeUppercase = !s.includeUppercase).conformed().withGenerated() }
    }

    fun toggleLowercase() {
        _uiState.update { s -> s.copy(includeLowercase = !s.includeLowercase).conformed().withGenerated() }
    }

    fun toggleDigits() {
        _uiState.update { s -> s.copy(includeDigits = !s.includeDigits).conformed().withGenerated() }
    }

    fun toggleSymbols() {
        _uiState.update { s -> s.copy(includeSymbols = !s.includeSymbols).conformed().withGenerated() }
    }

    fun generate() {
        _uiState.update { s ->
            s.copy(generateTrigger = s.generateTrigger + 1).conformed().withGenerated()
        }
    }

    /**
     * Pulls length and character sets back inside [constraint] before anything is generated, so a
     * setting the category forbids can never reach the form. Without a constraint this is a no-op.
     */
    private fun GeneratorUiState.conformed(): GeneratorUiState {
        val c = this.constraint ?: return this
        return copy(
            length = length.coerceIn(c.minLength, c.maxLength),
            includeUppercase = includeUppercase || c.requireUppercase,
            includeLowercase = includeLowercase || c.requireLowercase,
            includeDigits = includeDigits || c.requireDigits,
            includeSymbols = includeSymbols && c.allowSymbols
        )
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
        var candidate = draw(state)
        var attempt = 1
        while (attempt < MAX_GENERATION_ATTEMPTS && !satisfiesCategoryRules(candidate, state.constraint)) {
            candidate = draw(state)
            attempt++
        }
        // Falling through with a non-conforming draw is vanishingly unlikely; if it happens the
        // form still shows the rule that failed, so nothing is silently accepted.
        return candidate
    }

    private fun draw(state: GeneratorUiState): String = generatePasswordUseCase(
        length           = state.length,
        includeUppercase = state.includeUppercase,
        includeLowercase = state.includeLowercase,
        includeDigits    = state.includeDigits,
        includeSymbols   = state.includeSymbols
    )

    /** Uses the category's own checker as the acceptance test — no second copy of the rules. */
    private fun satisfiesCategoryRules(password: String, constraint: GeneratorConstraint?): Boolean =
        when (constraint?.category) {
            ItemCategory.BANK -> bankPasswordValidator.validate(password).isEmpty()
            else -> true
        }

    /**
     * Asks the generator itself how much entropy its own draw carries, instead of keeping a second
     * copy of the alphabet sizes here. The copy had drifted: it counted 32 symbols against a set
     * of 26, so the default settings advertised 104 bits for a 103-bit password.
     */
    private fun computeEntropyBits(state: GeneratorUiState): Int = GeneratePasswordUseCase.entropyBits(
        length = state.length,
        includeUppercase = state.includeUppercase,
        includeLowercase = state.includeLowercase,
        includeDigits = state.includeDigits,
        includeSymbols = state.includeSymbols
    )
}
