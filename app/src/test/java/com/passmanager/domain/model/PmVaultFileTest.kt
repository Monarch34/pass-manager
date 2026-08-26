package com.passmanager.domain.model

import com.passmanager.crypto.model.EncryptedData
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.exception.PmVaultInvalidParametersException
import com.passmanager.domain.exception.PmVaultMalformedException
import com.passmanager.domain.exception.PmVaultUnsupportedVersionException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

/** Container-level checks: layout, and the pre-KDF validation gate from `docs/FORMAT.md`. */
class PmVaultFileTest {

    private val salt = ByteArray(16) { it.toByte() }
    private val iv = ByteArray(12) { (it + 100).toByte() }
    private val ciphertext = ByteArray(48) { (it + 7).toByte() }

    private fun container(headerText: String): ByteArray {
        val headerBytes = headerText.toByteArray(Charsets.UTF_8)
        val out = ByteArray(6 + headerBytes.size + iv.size + ciphertext.size)
        byteArrayOf(0x50, 0x4D, 0x56, 0x54).copyInto(out, 0)
        out[4] = ((headerBytes.size ushr 8) and 0xFF).toByte()
        out[5] = (headerBytes.size and 0xFF).toByte()
        headerBytes.copyInto(out, 6)
        iv.copyInto(out, 6 + headerBytes.size)
        ciphertext.copyInto(out, 6 + headerBytes.size + iv.size)
        return out
    }

    private fun header(
        version: Int = 1,
        saltB64: String = Base64.getEncoder().encodeToString(salt),
        memory: Int = 65536,
        iterations: Int = 3,
        parallelism: Int = 4,
        hashLength: Int = 32
    ) = """{"version":$version,"salt":"$saltB64","kdf":{"memory":$memory,""" +
        """"iterations":$iterations,"parallelism":$parallelism,"hashLength":$hashLength}}"""

    // ── Layout ───────────────────────────────────────

    @Test
    fun `written header starts with the magic and a big-endian length`() {
        val headerBytes = PmVaultFile.headerBytes(salt, KdfParams())

        assertArrayEquals(byteArrayOf(0x50, 0x4D, 0x56, 0x54), headerBytes.copyOfRange(0, 4))
        val declared = ((headerBytes[4].toInt() and 0xFF) shl 8) or (headerBytes[5].toInt() and 0xFF)
        assertEquals(headerBytes.size - 6, declared)
    }

    @Test
    fun `assemble then parse recovers salt, params, iv and ciphertext`() {
        val params = KdfParams()
        val headerBytes = PmVaultFile.headerBytes(salt, params)
        val file = PmVaultFile.assemble(headerBytes, EncryptedData(ciphertext, iv))

        val parsed = PmVaultFile.parse(file)

        assertArrayEquals(salt, parsed.salt)
        assertEquals(params, parsed.kdfParams)
        assertArrayEquals(iv, parsed.body.iv)
        assertArrayEquals(ciphertext, parsed.body.ciphertext)
        // AAD is the first 6 + headerLen bytes, verbatim.
        assertArrayEquals(headerBytes, parsed.aad)
    }

    @Test
    fun `header carries the pinned defaults, not an empty object`() {
        val headerBytes = PmVaultFile.headerBytes(salt, KdfParams())
        val text = String(headerBytes, 6, headerBytes.size - 6, Charsets.UTF_8)

        assertTrue(text, text.contains("\"memory\":65536"))
        assertTrue(text, text.contains("\"iterations\":3"))
        assertTrue(text, text.contains("\"parallelism\":4"))
        assertTrue(text, text.contains("\"hashLength\":32"))
    }

    // ── Malformed input ──────────────────────────────

    @Test
    fun `rejects wrong magic`() {
        val file = container(header()).also { it[1] = 0x58 }
        assertThrows(PmVaultMalformedException::class.java) { PmVaultFile.parse(file) }
    }

    @Test
    fun `rejects a file shorter than the prefix`() {
        assertThrows(PmVaultMalformedException::class.java) {
            PmVaultFile.parse(byteArrayOf(0x50, 0x4D))
        }
    }

    @Test
    fun `rejects a truncated body`() {
        val file = container(header())
        // Leave only 10 bytes after the header — less than the 12-byte iv plus 16-byte tag any
        // container must carry.
        val truncated = file.copyOfRange(0, file.size - iv.size - ciphertext.size + 10)
        assertThrows(PmVaultMalformedException::class.java) { PmVaultFile.parse(truncated) }
    }

    @Test
    fun `rejects a header that is not json`() {
        assertThrows(PmVaultMalformedException::class.java) {
            PmVaultFile.parse(container("this is not json"))
        }
    }

    @Test
    fun `rejects a header missing a required field`() {
        assertThrows(PmVaultMalformedException::class.java) {
            PmVaultFile.parse(container("""{"version":1}"""))
        }
    }

    @Test
    fun `rejects a salt that is not base64`() {
        assertThrows(PmVaultMalformedException::class.java) {
            PmVaultFile.parse(container(header(saltB64 = "!!!not base64!!!")))
        }
    }

    // ── Pre-KDF validation gate ──────────────────────

    @Test
    fun `rejects an unknown version instead of best-effort parsing`() {
        val e = assertThrows(PmVaultUnsupportedVersionException::class.java) {
            PmVaultFile.parse(container(header(version = 2)))
        }
        assertEquals(2, e.version)
    }

    @Test
    fun `rejects memory above the 256 MiB ceiling`() {
        assertThrows(PmVaultInvalidParametersException::class.java) {
            PmVaultFile.parse(container(header(memory = 262_145)))
        }
    }

    @Test
    fun `accepts memory exactly at the ceiling`() {
        val parsed = PmVaultFile.parse(container(header(memory = 262_144)))
        assertEquals(262_144, parsed.kdfParams.memory)
    }

    @Test
    fun `rejects memory below the 8 MiB floor`() {
        assertThrows(PmVaultInvalidParametersException::class.java) {
            PmVaultFile.parse(container(header(memory = 8191)))
        }
    }

    @Test
    fun `accepts memory exactly at the floor`() {
        val parsed = PmVaultFile.parse(container(header(memory = 8192)))
        assertEquals(8192, parsed.kdfParams.memory)
    }

    @Test
    fun `rejects a header argon2 itself would refuse for too little memory per lane`() {
        // m=16, p=8 clears every range bound taken one at a time. Rejecting it at the gate is what
        // keeps the failure inside the typed error set instead of coming back untyped from the KDF.
        assertThrows(PmVaultInvalidParametersException::class.java) {
            PmVaultFile.parse(container(header(memory = 16, parallelism = 8)))
        }
    }

    @Test
    fun `the container bounds never sit outside what KdfParams accepts`() {
        // parse() builds a KdfParams from the validated header. If KdfParams' own bounds were ever
        // tightened past the container's, that constructor would throw untyped from inside a parse
        // that had already declared the header valid.
        assertTrue(PmVaultFile.MIN_KDF_MEMORY_KIB >= KdfParams.MIN_MEMORY)
        assertTrue(PmVaultFile.MAX_KDF_MEMORY_KIB <= KdfParams.MAX_MEMORY)
        assertTrue(PmVaultFile.MIN_KDF_ITERATIONS >= KdfParams.MIN_ITERATIONS)
        assertTrue(PmVaultFile.MAX_KDF_ITERATIONS <= KdfParams.MAX_ITERATIONS)
        assertTrue(PmVaultFile.MIN_KDF_PARALLELISM >= KdfParams.MIN_PARALLELISM)
        assertTrue(PmVaultFile.MAX_KDF_PARALLELISM <= KdfParams.MAX_PARALLELISM)
        assertTrue(PmVaultFile.KDF_HASH_LENGTH in KdfParams.MIN_HASH_LEN..KdfParams.MAX_HASH_LEN)
    }

    @Test
    fun `rejects iterations outside 1 to 16`() {
        assertThrows(PmVaultInvalidParametersException::class.java) {
            PmVaultFile.parse(container(header(iterations = 0)))
        }
        assertThrows(PmVaultInvalidParametersException::class.java) {
            PmVaultFile.parse(container(header(iterations = 17)))
        }
    }

    @Test
    fun `rejects parallelism outside 1 to 8`() {
        assertThrows(PmVaultInvalidParametersException::class.java) {
            PmVaultFile.parse(container(header(parallelism = 0)))
        }
        assertThrows(PmVaultInvalidParametersException::class.java) {
            PmVaultFile.parse(container(header(parallelism = 9)))
        }
    }

    @Test
    fun `rejects a hash length other than 32`() {
        assertThrows(PmVaultInvalidParametersException::class.java) {
            PmVaultFile.parse(container(header(hashLength = 16)))
        }
    }

    @Test
    fun `rejects a salt that is not 16 bytes`() {
        val shortSalt = Base64.getEncoder().encodeToString(ByteArray(8))
        assertThrows(PmVaultInvalidParametersException::class.java) {
            PmVaultFile.parse(container(header(saltB64 = shortSalt)))
        }
    }

    @Test
    fun `rejects a header longer than 4096 bytes`() {
        val padded = header().dropLast(1) + ""","notes":"${"x".repeat(4100)}"}"""
        assertThrows(PmVaultInvalidParametersException::class.java) {
            PmVaultFile.parse(container(padded))
        }
    }

    // ── Body ─────────────────────────────────────────

    @Test
    fun `body round-trips with the at-rest payload schema`() {
        val body = PmVaultBodyJson(
            version = 1,
            exportedAt = 1_787_000_000_000L,
            items = listOf(
                PmVaultItemJson(
                    id = "id-1",
                    category = "login",
                    createdAt = 10L,
                    updatedAt = 20L,
                    payload = ItemPayload.Login(
                        id = "id-1",
                        title = "GitHub",
                        username = "user",
                        address = "https://github.com",
                        password = "secret"
                    )
                )
            )
        )

        val bytes = PmVaultFile.encodeBody(body)
        val text = bytes.toString(Charsets.UTF_8)
        assertTrue(text, text.contains("\"type\":\"login\""))

        assertEquals(body, PmVaultFile.decodeBody(bytes))
    }

    @Test
    fun `body with an unknown version is rejected`() {
        val bytes = """{"version":9,"exportedAt":1,"items":[]}""".toByteArray()
        assertThrows(PmVaultUnsupportedVersionException::class.java) {
            PmVaultFile.decodeBody(bytes)
        }
    }
}
