package com.passmanager.crypto.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KdfParamsTest {

    // Mirrors the codec in MetadataRepositoryImpl — see the note on `encodeDefaults` there.
    private val kdfJson = Json { encodeDefaults = true }

    @Test
    fun `default params construct successfully`() {
        val params = KdfParams()
        assertEquals(65536, params.memory)
        assertEquals(3, params.iterations)
        assertEquals(4, params.parallelism)
        assertEquals(32, params.hashLength)
    }

    @Test
    fun `default params serialize with every field spelled out`() {
        val encoded = kdfJson.encodeToString(KdfParams.serializer(), KdfParams())

        // The regression this guards: without encodeDefaults the whole object collapsed
        // to "{}" and the vault lost the cost it was created with.
        assertTrue("encoded params must not be empty: $encoded", encoded != "{}")
        assertTrue("missing memory: $encoded", encoded.contains("\"memory\":65536"))
        assertTrue("missing iterations: $encoded", encoded.contains("\"iterations\":3"))
        assertTrue("missing parallelism: $encoded", encoded.contains("\"parallelism\":4"))
        assertTrue("missing hashLength: $encoded", encoded.contains("\"hashLength\":32"))
    }

    @Test
    fun `round trip preserves non-default params`() {
        val original = KdfParams(memory = 19456, iterations = 2, parallelism = 1, hashLength = 64)
        val decoded = kdfJson.decodeFromString(
            KdfParams.serializer(),
            kdfJson.encodeToString(KdfParams.serializer(), original)
        )
        assertEquals(original, decoded)
    }

    @Test
    fun `empty object decodes to defaults`() {
        // Backwards compatibility for rows MIGRATION_7_8 has not touched yet.
        val decoded = kdfJson.decodeFromString(KdfParams.serializer(), "{}")
        assertEquals(KdfParams(), decoded)
    }

    @Test
    fun `partial object decodes with defaults for missing fields`() {
        val decoded = kdfJson.decodeFromString(KdfParams.serializer(), """{"iterations":10}""")
        assertEquals(10, decoded.iterations)
        assertEquals(65536, decoded.memory)
        assertEquals(4, decoded.parallelism)
        assertEquals(32, decoded.hashLength)
    }

    @Test
    fun `params back-filled by MIGRATION_7_8 decode to the pre-v8 defaults`() {
        // Literal copy of the string the migration writes; it must keep decoding to the
        // cost those vaults were actually created with, whatever the defaults become.
        val backFilled = """{"memory":65536,"iterations":10,"parallelism":4,"hashLength":32}"""
        val decoded = kdfJson.decodeFromString(KdfParams.serializer(), backFilled)
        assertEquals(KdfParams(memory = 65536, iterations = 10, parallelism = 4, hashLength = 32), decoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `decoding out-of-bounds params throws`() {
        kdfJson.decodeFromString(KdfParams.serializer(), """{"memory":1024}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `memory below minimum throws`() {
        KdfParams(memory = KdfParams.MIN_MEMORY - 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `memory above maximum throws`() {
        KdfParams(memory = KdfParams.MAX_MEMORY + 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `iterations below minimum throws`() {
        KdfParams(iterations = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parallelism above maximum throws`() {
        KdfParams(parallelism = KdfParams.MAX_PARALLELISM + 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hashLength below minimum throws`() {
        KdfParams(hashLength = KdfParams.MIN_HASH_LEN - 1)
    }

    @Test
    fun `boundary values are accepted`() {
        KdfParams(memory = KdfParams.MIN_MEMORY)
        KdfParams(memory = KdfParams.MAX_MEMORY)
        KdfParams(iterations = KdfParams.MIN_ITERATIONS)
        KdfParams(iterations = KdfParams.MAX_ITERATIONS)
        KdfParams(parallelism = KdfParams.MIN_PARALLELISM)
        KdfParams(parallelism = KdfParams.MAX_PARALLELISM)
        KdfParams(hashLength = KdfParams.MIN_HASH_LEN)
        KdfParams(hashLength = KdfParams.MAX_HASH_LEN)
    }
}
