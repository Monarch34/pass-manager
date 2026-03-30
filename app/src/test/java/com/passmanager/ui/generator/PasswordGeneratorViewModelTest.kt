package com.passmanager.ui.generator

import com.passmanager.domain.usecase.GeneratePasswordUseCase
import com.passmanager.test.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordGeneratorViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial state uses generated password from use case`() = runTest {
        val useCase = mockk<GeneratePasswordUseCase>()
        every {
            useCase(
                length = 16,
                includeUppercase = true,
                includeLowercase = true,
                includeDigits = true,
                includeSymbols = true
            )
        } returns "generated-secret"

        val vm = PasswordGeneratorViewModel(useCase)
        advanceUntilIdle()

        assertEquals("generated-secret", vm.uiState.value.password)
        assertEquals(16, vm.uiState.value.length)
        assertTrue(vm.uiState.value.entropyBits >= 0)
    }

    @Test
    fun `generate increments trigger and refreshes password`() = runTest {
        val useCase = mockk<GeneratePasswordUseCase>()
        every {
            useCase(
                length = 16,
                includeUppercase = true,
                includeLowercase = true,
                includeDigits = true,
                includeSymbols = true
            )
        } returnsMany listOf("first", "second")

        val vm = PasswordGeneratorViewModel(useCase)
        advanceUntilIdle()
        assertEquals(0, vm.uiState.value.generateTrigger)

        vm.generate()
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.generateTrigger)
        assertEquals("second", vm.uiState.value.password)
    }
}
