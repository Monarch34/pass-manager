package com.passmanager.crypto.kdf

import com.passmanager.crypto.Secret
import com.passmanager.crypto.repeatedByte
import com.passmanager.crypto.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

/**
 * Argon2id against published tags.
 *
 * This is the gate for the whole module. Everything else here can be cross-checked against
 * a platform implementation that already exists; Argon2 cannot, because no platform has
 * one. Its only external reference is the specification, so these vectors are the sole
 * evidence that what this project derives is Argon2id and not something that merely behaves
 * like it — and, because they run on all three targets, that a phone and a desktop derive
 * the same key from the same passphrase.
 */
class Argon2idTest {

    /**
     * RFC 9106 section 5.3, the Argon2id test vector, in full: a secret and associated data
     * as well as a password and salt, four lanes, and the version 1.3 rule that later passes
     * mix into memory rather than overwriting it.
     *
     * It is the only vector here that exercises multiple lanes, the second-pass XOR, and the
     * non-empty secret and associated-data fields — the three parts of the specification an
     * implementation can get wrong while still producing stable, plausible-looking output.
     */
    @Test
    fun `RFC 9106 Argon2id vector`() {
        val tag = argon2id(
            password = Secret.of(repeatedByte(0x01, 32)),
            salt = repeatedByte(0x02, 16),
            parameters = Argon2Parameters(memoryKib = 32, iterations = 3, parallelism = 4),
            tagLength = 32,
            pepper = Secret.of(repeatedByte(0x03, 8)),
            associatedData = repeatedByte(0x04, 12),
        )
        assertEquals(
            "0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659",
            tag.toByteArray().toHex(),
        )
    }

    /**
     * The reference implementation's own command-line vector, and the one this project has
     * independently confirmed: version 1 of this application derived exactly this key on a
     * physical device through a JNI binding to the reference C, cross-checked against a
     * third implementation pinned to the same RFC.
     *
     * It is the counterpart to the vector above. That one is small, wide and unusual; this
     * one has the shape real use has — one lane, 64 MiB, a passphrase — so it also
     * demonstrates that the production configuration completes in a sane time on every
     * target, including a simulator.
     */
    @Test
    fun `reference implementation vector at production cost`() {
        val tag = argon2id(
            password = Secret.ofUtf8("password"),
            salt = "somesalt".encodeToByteArray(),
            parameters = Argon2Parameters(memoryKib = 65536, iterations = 2, parallelism = 1),
        )
        assertEquals(
            "09316115d5cf24ed5a15a31a3ba326e5cf32edc24702987c02b6566f61913cf7",
            tag.toByteArray().toHex(),
        )
    }

    /**
     * Every parameter is bound into the initial hash, so changing any one of them must
     * change the tag. A parameter that is accepted but silently ignored is the failure this
     * catches: it would let a vault be opened at a cost far below the one it recorded.
     */
    @Test
    fun `every input changes the tag`() {
        val password = Secret.ofUtf8("correct horse battery staple")
        val salt = repeatedByte(0x11, 16)
        val parameters = Argon2Parameters(memoryKib = 64, iterations = 2, parallelism = 2)
        val baseline = argon2id(password, salt, parameters).toByteArray().toHex()

        assertNotEquals(baseline, argon2id(Secret.adopt(password.toByteArray() + 0x21.toByte()), salt, parameters).toByteArray().toHex(), "password")
        assertNotEquals(baseline, argon2id(password, repeatedByte(0x12, 16), parameters).toByteArray().toHex(), "salt")
        assertNotEquals(
            baseline,
            argon2id(password, salt, parameters.copy(memoryKib = 128)).toByteArray().toHex(),
            "memory",
        )
        assertNotEquals(
            baseline,
            argon2id(password, salt, parameters.copy(iterations = 3)).toByteArray().toHex(),
            "iterations",
        )
        assertNotEquals(
            baseline,
            argon2id(password, salt, parameters.copy(parallelism = 1)).toByteArray().toHex(),
            "parallelism",
        )
        assertNotEquals(
            baseline,
            argon2id(password, salt, parameters, pepper = Secret.of(repeatedByte(0x33, 8))).toByteArray().toHex(),
            "pepper",
        )
        assertNotEquals(
            baseline,
            argon2id(password, salt, parameters, associatedData = repeatedByte(0x44, 4)).toByteArray().toHex(),
            "associated data",
        )
    }

    /**
     * A tag longer than BLAKE2b's 64-byte ceiling goes through the chained construction in
     * RFC 9106 section 3.3 rather than a single digest, and that construction is also what
     * produces every 1 KiB memory block. A 64-byte tag and a 65-byte tag take different
     * paths through it.
     */
    @Test
    fun `tag length is honoured on both sides of the digest ceiling`() {
        val parameters = Argon2Parameters(memoryKib = 32, iterations = 1, parallelism = 1)
        for (length in intArrayOf(4, 16, 32, 63, 64, 65, 96, 128, 1024)) {
            val tag = argon2id(
                password = Secret.ofUtf8("p"),
                salt = repeatedByte(0x55, 16),
                parameters = parameters,
                tagLength = length,
            )
            assertEquals(length, tag.size, "tag length $length")
        }
    }

    /**
     * The memory cost is rounded down to a whole number of segments, but the value written
     * into the initial hash is the one the caller asked for. Two requests that round to the
     * same amount of memory must therefore still produce different keys — otherwise a vault
     * recording 65 MiB could be opened by deriving 64 MiB, and the recorded cost would be a
     * suggestion rather than a fact.
     */
    @Test
    fun `the requested memory is bound into the hash rather than the rounded value`() {
        val password = Secret.ofUtf8("p")
        val salt = repeatedByte(0x66, 16)
        val eight = argon2id(password, salt, Argon2Parameters(8, 1, 1)).toByteArray().toHex()
        val nine = argon2id(password, salt, Argon2Parameters(9, 1, 1)).toByteArray().toHex()
        val ten = argon2id(password, salt, Argon2Parameters(10, 1, 1)).toByteArray().toHex()
        assertNotEquals(eight, nine)
        assertNotEquals(nine, ten)
    }

    @Test
    fun `rejects parameters below the specified floors`() {
        assertFailsWith<IllegalArgumentException> { Argon2Parameters(7, 1, 1) }
        assertFailsWith<IllegalArgumentException> { Argon2Parameters(8, 0, 1) }
        assertFailsWith<IllegalArgumentException> { Argon2Parameters(8, 1, 0) }
        assertFailsWith<IllegalArgumentException> { Argon2Parameters(16, 1, 4) }
    }

    @Test
    fun `rejects a salt shorter than the specification allows`() {
        val parameters = Argon2Parameters(8, 1, 1)
        assertFailsWith<IllegalArgumentException> {
            argon2id(Secret.ofUtf8("p"), repeatedByte(0, 7), parameters)
        }
    }

    @Test
    fun `the shipped default is the cost this project intends`() {
        assertEquals(64 * 1024, Argon2Parameters.Default.memoryKib)
        assertEquals(3, Argon2Parameters.Default.iterations)
        assertEquals(1, Argon2Parameters.Default.parallelism)
    }
}
