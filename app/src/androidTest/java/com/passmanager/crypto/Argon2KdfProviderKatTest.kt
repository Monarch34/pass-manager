package com.passmanager.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.passmanager.crypto.kdf.Argon2KdfProvider
import com.passmanager.crypto.model.KdfParams
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Known-answer tests for the KDF the shipping app actually derives every vault key with.
 *
 * Until this existed, `argon2kt` was the one unpinned link in the chain. The JVM suite pins
 * BouncyCastle to RFC 9106 and the iOS suite pins its vendored phc-winner-argon2 to the same
 * vector, and the two agree through the interop fixture — but that fixture is *generated*
 * with BouncyCastle, because argon2kt is JNI-backed and cannot load on a desktop JVM. So the
 * implementation that opens real users' vaults was never checked against anything. It
 * happened to agree only because both default to Argon2 v1.3.
 *
 * "Happened to" is the problem. [Argon2KdfProvider] does not pass a version to
 * `Argon2Kt.hash`, so it takes whatever that library defaults to. If a future release
 * changed that default to v1.0, every key derived on Android would silently stop matching
 * the one iOS derives from the same passphrase — and the only symptom would be users unable
 * to open their own vaults after switching platforms, with a correct passphrase.
 *
 * This runs on a device because that is the only place the library loads.
 *
 * Test 1 is the real known-answer test: a vector from the Argon2 reference implementation's
 * own suite, run straight through the shipping provider with nothing in between. The app's
 * production cost has no published vector — the reference suite's Argon2id cases stop at
 * p=2, and RFC 9106 uses a secret and associated data that this API cannot express — so
 * tests 2 and 3 reach it indirectly, pinning BouncyCastle to RFC 9106 and then asserting
 * argon2kt agrees with it at m=65536/t=3/p=4. Test 4 pins that answer so the two cannot
 * drift together without saying so.
 */
@RunWith(AndroidJUnit4::class)
class Argon2KdfProviderKatTest {

    private val provider = Argon2KdfProvider()

    /**
     * Fixed inputs for the cross-check. Not secret and not a vault passphrase — they exist
     * so the expected output below is reproducible by anyone with an Argon2id implementation.
     */
    private val passphrase = "CrossPlatform-Fixture-2026".toByteArray(Charsets.UTF_8)
    private val salt = ByteArray(16) { (it * 7 + 1).toByte() }

    // ── 1. A published vector, straight through the shipping provider ──

    /**
     * The strongest form this can take: a third-party vector run through the exact code path
     * production uses, with no reference implementation in the middle.
     *
     * From the Argon2 reference implementation's own test suite
     * (P-H-C/phc-winner-argon2, `src/test.c`). Its Argon2id cases use only password and
     * salt — no secret, no associated data — which is the subset `Argon2Kt.hash` can
     * actually express. The published encoded form is
     * `$argon2id$v=19$m=65536,t=2,p=1$c29tZXNhbHQ$CTFhFdXPJO1aFaMaO6Mm5c8y7cJHAph8ArZWb2GRPPc`,
     * and the `v=19` in that string is what pins the Argon2 version independently of the
     * raw tag below.
     *
     * p=1 rather than the app's p=4 because the reference suite's Argon2id vectors do not
     * go above p=2. The production cost has no published vector at all, which is what the
     * cross-check below exists for.
     */
    @Test
    fun argon2kt_reproduces_the_reference_implementation_vector() {
        val derived = provider.deriveKey(
            passphrase = "password".toByteArray(Charsets.UTF_8),
            salt = "somesalt".toByteArray(Charsets.UTF_8),
            params = KdfParams(memory = 65536, iterations = 2, parallelism = 1, hashLength = 32)
        )
        assertEquals(
            "the shipping KDF disagrees with the Argon2 reference test suite",
            "09316115d5cf24ed5a15a31a3ba326e5cf32edc24702987c02b6566f61913cf7",
            derived.toHex()
        )
    }

    // ── 2. The reference implementation used for the cross-check is itself correct ──

    @Test
    fun bouncycastle_reproduces_the_rfc_9106_vector() {
        // RFC 9106 section 5.3. argon2kt's API exposes neither `secret` nor `associatedData`,
        // so this vector cannot be run through it directly — which is exactly why the chain
        // needs a reference implementation in the middle rather than one direct assertion.
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(3)
            .withMemoryAsKB(32)
            .withParallelism(4)
            .withSalt(ByteArray(16) { 0x02 })
            .withSecret(ByteArray(8) { 0x03 })
            .withAdditional(ByteArray(12) { 0x04 })
            .build()
        val out = ByteArray(32)
        Argon2BytesGenerator().apply { init(params) }.generateBytes(ByteArray(32) { 0x01 }, out)

        assertEquals(
            "0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659",
            out.toHex()
        )
    }

    // ── 3. The shipping KDF agrees with it at the cost real vaults use ──

    @Test
    fun argon2kt_agrees_with_the_reference_at_production_cost() {
        val params = KdfParams()
        assertEquals("production memory changed; re-pin this test", 65536, params.memory)
        assertEquals("production iterations changed; re-pin this test", 3, params.iterations)
        assertEquals("production parallelism changed; re-pin this test", 4, params.parallelism)

        // Deliberately calls the provider exactly as production does — no version argument —
        // so this asserts the library's *default* version is v1.3 rather than asserting
        // something production never does.
        val fromApp = provider.deriveKey(passphrase, salt, params)
        val fromReference = reference(params)

        assertArrayEquals(
            "argon2kt disagrees with RFC-pinned Argon2id at m=${params.memory} t=${params.iterations} " +
                "p=${params.parallelism}. The most likely cause is a change to the library's " +
                "default Argon2 version (v1.3 vs v1.0). Every vault key on Android depends on this.",
            fromReference,
            fromApp
        )
    }

    // ── 4. And that answer is pinned, so the two cannot drift together ──

    @Test
    fun argon2kt_matches_the_pinned_answer() {
        val derived = provider.deriveKey(passphrase, salt, KdfParams())
        assertEquals(
            "the shipping KDF no longer produces the pinned key for fixed inputs",
            PINNED_PRODUCTION_KEY,
            derived.toHex()
        )
    }

    // ── 5. Sanity properties a stub would pass but a real KDF must not ──

    @Test
    fun hash_length_is_honoured_and_output_is_salt_dependent() {
        val shorter = provider.deriveKey(passphrase, salt, KdfParams(hashLength = 16))
        assertEquals(16, shorter.size)

        val otherSalt = ByteArray(16) { (it * 7 + 2).toByte() }
        val a = provider.deriveKey(passphrase, salt, KdfParams(memory = 8192, iterations = 1))
        val b = provider.deriveKey(passphrase, otherSalt, KdfParams(memory = 8192, iterations = 1))
        assertNotEquals(a.toHex(), b.toHex())
    }

    // ── Helpers ──────────────────────────────────────

    private fun reference(params: KdfParams): ByteArray {
        val argonParams = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(params.iterations)
            .withMemoryAsKB(params.memory)
            .withParallelism(params.parallelism)
            .withSalt(salt)
            .build()
        val out = ByteArray(params.hashLength)
        Argon2BytesGenerator().apply { init(argonParams) }.generateBytes(passphrase, out)
        return out
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        /**
         * Argon2id v1.3, m=65536 KiB, t=3, p=4, len=32, over the passphrase and salt above.
         * Derived by the reference implementation this file pins to RFC 9106, and confirmed
         * to equal what `argon2kt` produces on a device.
         */
        const val PINNED_PRODUCTION_KEY =
            "23602b28d59d4f0028459e79766a4edb548048cd2533e8673e90f5aa5f97019b"
    }
}
