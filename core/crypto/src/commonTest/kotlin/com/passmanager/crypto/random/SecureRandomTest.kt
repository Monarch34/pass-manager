package com.passmanager.crypto.random

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * These run on every target, which is most of the point of having them.
 *
 * A CSPRNG cannot be tested for randomness by a unit test, and pretending otherwise
 * produces flaky suites and false confidence. What *can* be tested is the set of failures
 * that have actually happened to real implementations: returning the requested length but
 * leaving the buffer untouched, returning the same bytes twice because a generator was
 * seeded once and cached, and ignoring an error status. The first two are checked here on
 * every platform; the third is structural, in the Apple actual.
 */
class SecureRandomTest {

    @Test
    fun returns_exactly_the_requested_length() {
        for (size in listOf(1, 12, 16, 32, 64, 1024)) {
            assertEquals(size, secureRandomBytes(size).size, "size $size")
        }
    }

    /**
     * The all-zero buffer is the shape of every failure mode that matters here: an
     * unchecked error status, an allocation nothing wrote into, a stubbed implementation.
     * Thirty-two genuinely random bytes are all zero with probability 2^-256, so this is a
     * real assertion rather than a statistical one.
     */
    @Test
    fun does_not_return_an_untouched_buffer() {
        assertFalse(secureRandomBytes(32).all { it == 0.toByte() })
    }

    /**
     * Catches a generator that is seeded once and then replays, which is the failure a
     * cached or misconfigured instance produces.
     */
    @Test
    fun successive_requests_differ() {
        val first = secureRandomBytes(32)
        val second = secureRandomBytes(32)
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun rejects_a_non_positive_size() {
        assertFailsWith<IllegalArgumentException> { secureRandomBytes(0) }
        assertFailsWith<IllegalArgumentException> { secureRandomBytes(-1) }
    }

    /**
     * The bound exists so an unchecked length from a parsed file cannot become an
     * allocation the size of the heap. Asserted in common code so it holds on every target
     * rather than on whichever one happened to be tested.
     */
    @Test
    fun rejects_an_absurd_size() {
        assertFailsWith<IllegalArgumentException> { secureRandomBytes(Int.MAX_VALUE) }
    }

    @Test
    fun distinct_sizes_are_all_serviceable() {
        assertTrue((1..64).all { secureRandomBytes(it).size == it })
    }
}
