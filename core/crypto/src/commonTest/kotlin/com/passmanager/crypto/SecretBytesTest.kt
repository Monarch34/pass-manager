package com.passmanager.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretBytesTest {

    @Test
    fun `equal contents compare equal`() {
        assertTrue(constantTimeEquals(hex("00ff10"), hex("00ff10")))
        assertTrue(constantTimeEquals(ByteArray(0), ByteArray(0)))
    }

    /**
     * A difference must be found wherever it is. The loop has no early exit, so the risk is
     * the opposite of the usual one: an accumulator that loses a difference rather than a
     * comparison that stops too soon. Flipping every bit of every byte in turn is the check
     * that nothing cancels out.
     */
    @Test
    fun `a difference anywhere is detected`() {
        val reference = ByteArray(48) { (it * 5 and 0xff).toByte() }
        for (index in reference.indices) {
            for (bit in 0 until 8) {
                val altered = reference.copyOf()
                altered[index] = (altered[index].toInt() xor (1 shl bit)).toByte()
                assertFalse(
                    constantTimeEquals(reference, altered),
                    "bit $bit of byte $index went unnoticed",
                )
            }
        }
    }

    /**
     * Sign extension is the trap here. Bytes are signed in Kotlin, so a naive comparison
     * that converts to `Int` without masking can make 0x80 and 0xFF interact in ways that
     * cancel. These are the values where that shows up.
     */
    @Test
    fun `high-bit values do not cancel out`() {
        assertFalse(constantTimeEquals(hex("80"), hex("00")))
        assertFalse(constantTimeEquals(hex("ff"), hex("7f")))
        assertFalse(constantTimeEquals(hex("8000"), hex("0080")))
        assertTrue(constantTimeEquals(hex("80ff"), hex("80ff")))
    }

    @Test
    fun `different lengths are not equal`() {
        assertFalse(constantTimeEquals(hex("0011"), hex("001122")))
        assertFalse(constantTimeEquals(ByteArray(0), hex("00")))
    }

    @Test
    fun `wiping clears every byte`() {
        val secret = ByteArray(32) { 0x5a }
        secret.wipe()
        assertEquals(ByteArray(32).toHex(), secret.toHex())
    }
}
