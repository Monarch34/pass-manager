package com.passmanager.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeneratePasswordUseCaseTest {

    private val useCase = GeneratePasswordUseCase()

    @Test
    fun `generates password with default length`() {
        val result = useCase()
        assertEquals(20, result.length)
    }

    @Test
    fun `generates password with custom length`() {
        val result = useCase(length = 16)
        assertEquals(16, result.length)
    }

    @Test
    fun `throws when length less than 8`() {
        assertThrows(IllegalArgumentException::class.java) {
            useCase(length = 7)
        }
    }

    @Test
    fun `throws when length greater than 128`() {
        assertThrows(IllegalArgumentException::class.java) {
            useCase(length = 129)
        }
    }

    @Test
    fun `throws when no character set selected`() {
        assertThrows(IllegalArgumentException::class.java) {
            useCase(
                includeUppercase = false,
                includeLowercase = false,
                includeDigits = false,
                includeSymbols = false
            )
        }
    }

    @Test
    fun `includes uppercase when enabled`() {
        repeat(20) {
            val result = useCase(
                length = 50,
                includeUppercase = true,
                includeLowercase = false,
                includeDigits = false,
                includeSymbols = false
            )
            assertTrue(result.any { it in GeneratePasswordUseCase.UPPERCASE })
        }
    }

    @Test
    fun `includes lowercase when enabled`() {
        repeat(20) {
            val result = useCase(
                length = 50,
                includeUppercase = false,
                includeLowercase = true,
                includeDigits = false,
                includeSymbols = false
            )
            assertTrue(result.any { it in GeneratePasswordUseCase.LOWERCASE })
        }
    }

    @Test
    fun `includes digits when enabled`() {
        repeat(20) {
            val result = useCase(
                length = 50,
                includeUppercase = false,
                includeLowercase = false,
                includeDigits = true,
                includeSymbols = false
            )
            assertTrue(result.any { it in GeneratePasswordUseCase.DIGITS })
        }
    }

    @Test
    fun `includes symbols when enabled`() {
        repeat(20) {
            val result = useCase(
                length = 50,
                includeUppercase = false,
                includeLowercase = false,
                includeDigits = false,
                includeSymbols = true
            )
            assertTrue(result.any { it in GeneratePasswordUseCase.SYMBOLS })
        }
    }
}
