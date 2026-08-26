package com.passmanager.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * The reported strength of a generated password, pinned to what the generator actually draws.
 *
 * `docs/IOS_PARITY.md` makes this a cross-platform contract: `round(length * log2(poolSize))`
 * where poolSize is the size of the alphabet in use. Both platforms adopt the corrected formula
 * together, so a change here is a change to a shared number, not a local detail.
 */
class PasswordEntropyTest {

    @Test
    fun `the symbol alphabet is the 26 characters the generator draws from`() {
        // The number the old display hard-coded was 32. If this ever legitimately changes, the
        // entropy figure must move with it — which the drift test below enforces.
        assertEquals(26, GeneratePasswordUseCase.SYMBOLS.length)
        assertEquals(26, GeneratePasswordUseCase.UPPERCASE.length)
        assertEquals(26, GeneratePasswordUseCase.LOWERCASE.length)
        assertEquals(10, GeneratePasswordUseCase.DIGITS.length)
    }

    @Test
    fun `the default settings report 103 bits, not 104`() {
        // 16 chars from a pool of 88 is 103.35 bits. The old code counted a pool of 94 and
        // truncated, printing 104 — a password manager may not overstate its own strength.
        assertEquals(103, GeneratePasswordUseCase.entropyBits(length = 16))
        assertEquals(88, GeneratePasswordUseCase.poolSize())
    }

    @Test
    fun `the pool is the sum of the enabled classes and nothing else`() {
        assertEquals(26, GeneratePasswordUseCase.poolSize(true, false, false, false))
        assertEquals(52, GeneratePasswordUseCase.poolSize(true, true, false, false))
        assertEquals(62, GeneratePasswordUseCase.poolSize(true, true, true, false))
        assertEquals(88, GeneratePasswordUseCase.poolSize(true, true, true, true))
        assertEquals(0, GeneratePasswordUseCase.poolSize(false, false, false, false))
    }

    @Test
    fun `entropy is rounded, not truncated`() {
        // 20 chars from 88 is 129.19 bits -> 129; 12 chars is 77.51 -> 78, which truncation
        // would have reported as 77. Rounding is the only part of this that can round *up*, and
        // it is bounded by half a bit.
        assertEquals(129, GeneratePasswordUseCase.entropyBits(length = 20))
        assertEquals(78, GeneratePasswordUseCase.entropyBits(length = 12))
    }

    @Test
    fun `entropy tracks the alphabet, so the two cannot drift apart again`() {
        // Recomputed here from the live constants rather than asserted as a literal: if someone
        // adds a symbol, this stays true and the hard-coded expectations above fail loudly
        // instead of the display quietly lying.
        for (length in listOf(8, 16, 32, 64)) {
            val pool = GeneratePasswordUseCase.poolSize()
            val expected = (length * (ln(pool.toDouble()) / ln(2.0))).roundToInt()
            assertEquals(
                "length=$length",
                expected,
                GeneratePasswordUseCase.entropyBits(length = length)
            )
        }
    }

    @Test
    fun `dropping a character class lowers the reported entropy`() {
        val all = GeneratePasswordUseCase.entropyBits(length = 16)
        val noSymbols = GeneratePasswordUseCase.entropyBits(length = 16, includeSymbols = false)

        assertNotEquals(all, noSymbols)
        assertEquals(95, noSymbols) // 16 * log2(62) = 95.27
    }

    @Test
    fun `an empty pool or a zero length reports nothing rather than a nonsense number`() {
        assertEquals(0, GeneratePasswordUseCase.entropyBits(length = 16, includeUppercase = false,
            includeLowercase = false, includeDigits = false, includeSymbols = false))
        assertEquals(0, GeneratePasswordUseCase.entropyBits(length = 0))
    }
}
