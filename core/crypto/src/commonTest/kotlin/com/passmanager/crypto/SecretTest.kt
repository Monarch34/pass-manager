package com.passmanager.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SecretTest {

    /**
     * The property the type exists for. A secret caught up in a log line, an exception
     * message or a debugger view must disclose a length and nothing else — and it must hold
     * for string interpolation, which is how it actually happens.
     */
    @Test
    fun `never prints its contents`() {
        val secret = Secret.copyOf(hex("00112233445566778899aabbccddeeff"))
        assertEquals("Secret(16 bytes)", secret.toString())
        assertEquals("key is Secret(16 bytes)", "key is $secret")

        secret.destroy()
        assertEquals("Secret(destroyed)", secret.toString())
    }

    @Test
    fun `reveals the bytes it was given`() {
        val bytes = hex("cafebabe")
        assertEquals("cafebabe", Secret.copyOf(bytes).reveal { it.toHex() })
        assertEquals("cafebabe", Secret.adopt(bytes.copyOf()).copyBytes().toHex())
        assertEquals("passphrase", Secret.copyOfUtf8("passphrase").reveal { it.decodeToString() })
        assertEquals(32, Secret.random(32).size)
    }

    /**
     * `copyOf` leaves the caller's array alone; `adopt` takes it over. Getting these the
     * wrong way round would either erase an array somebody else still holds, or leave a copy
     * nothing erases.
     */
    @Test
    fun `copyOf copies and adopt takes ownership`() {
        val source = hex("0102030405")

        val copied = Secret.copyOf(source)
        copied.destroy()
        assertEquals("0102030405", source.toHex(), "copyOf erased the caller's array")

        val adopted = Secret.adopt(source)
        adopted.destroy()
        assertEquals("0000000000", source.toHex(), "adopt left the adopted array intact")
    }

    @Test
    fun `destroying wipes the bytes and is idempotent`() {
        val bytes = ByteArray(32) { 0x5a }
        val secret = Secret.adopt(bytes)
        assertFalse(secret.isDestroyed)

        secret.destroy()
        secret.destroy()

        assertTrue(secret.isDestroyed)
        assertEquals(ByteArray(32).toHex(), bytes.toHex())
    }

    /** Use-after-destroy is a bug in the caller, and must not silently return zeros. */
    @Test
    fun `revealing a destroyed secret fails loudly`() {
        val secret = Secret.random(16)
        secret.destroy()
        assertFailsWith<IllegalStateException> { secret.reveal { it.size } }
        assertFailsWith<IllegalStateException> { secret.copyBytes() }
    }

    /**
     * `close` is the whole reason this implements `AutoCloseable`: a scope that erases on the
     * way out, including when the way out is an exception.
     */
    @Test
    fun `use erases on the way out - including on failure`() {
        val bytes = ByteArray(16) { 0x11 }
        Secret.adopt(bytes).use { assertEquals(16, it.size) }
        assertEquals(ByteArray(16).toHex(), bytes.toHex())

        val thrownFrom = ByteArray(16) { 0x22 }
        assertFailsWith<IllegalStateException> {
            Secret.adopt(thrownFrom).use { error("boom") }
        }
        assertEquals(ByteArray(16).toHex(), thrownFrom.toHex(), "a throwing block skipped the wipe")
    }

    @Test
    fun `equal contents compare equal`() {
        assertTrue(Secret.copyOf(hex("00ff10")) == Secret.copyOf(hex("00ff10")))
        assertNotEquals(Secret.copyOf(hex("00ff10")), Secret.copyOf(hex("00ff11")))
        assertNotEquals(Secret.copyOf(hex("0011")), Secret.copyOf(hex("001122")))
        assertFalse(Secret.random(16).equals("not a secret"))
    }

    /**
     * Two wiped secrets are both all-zero. Comparing them equal would state something untrue
     * about the values they used to hold, so a destroyed secret equals nothing — not even
     * itself.
     */
    @Test
    fun `a destroyed secret equals nothing`() {
        val secret = Secret.copyOf(hex("00ff10"))
        val same = Secret.copyOf(hex("00ff10"))
        assertTrue(secret == same)

        secret.destroy()
        assertFalse(secret == same)
        assertFalse(same == secret)
        assertFalse(secret == secret)
    }

    /**
     * The hash is the length and nothing else. Hashing the contents would leave a lossy copy
     * of the secret in whatever table it was put in, outliving `destroy`.
     */
    @Test
    fun `hashCode discloses only the length`() {
        assertEquals(Secret.random(32).hashCode(), Secret.random(32).hashCode())
        assertEquals(32, Secret.random(32).hashCode())
        assertNotEquals(Secret.random(32).hashCode(), Secret.random(16).hashCode())
    }

    /**
     * A difference must be found wherever it is. The comparison has no early exit, so the
     * risk is the opposite of the usual one: an accumulator that loses a difference rather
     * than a loop that stops too soon. Flipping every bit of every byte in turn is the check
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
     * Sign extension is the trap. Bytes are signed in Kotlin, so a comparison that converts
     * to `Int` without masking can make 0x80 and 0xFF interact in ways that cancel.
     */
    @Test
    fun `high-bit values do not cancel out`() {
        assertFalse(constantTimeEquals(hex("80"), hex("00")))
        assertFalse(constantTimeEquals(hex("ff"), hex("7f")))
        assertFalse(constantTimeEquals(hex("8000"), hex("0080")))
        assertTrue(constantTimeEquals(hex("80ff"), hex("80ff")))
        assertTrue(constantTimeEquals(ByteArray(0), ByteArray(0)))
        assertFalse(constantTimeEquals(ByteArray(0), hex("00")))
    }

    @Test
    fun `wiping clears every byte`() {
        val secret = ByteArray(32) { 0x5a }
        secret.wipe()
        assertEquals(ByteArray(32).toHex(), secret.toHex())
    }
}
