package com.passmanager.fixture

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.exception.PmVaultAuthenticationException
import com.passmanager.domain.model.PayloadJson
import com.passmanager.domain.model.PmVaultFile
import com.passmanager.domain.usecase.ImportVaultUseCase
import com.passmanager.fixture.CrossPlatformFixtures.toHex
import com.passmanager.test.FakeKeyProvider
import com.passmanager.test.FakeMetadataRepository
import com.passmanager.test.FakeVaultRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * The other half of the interop proof: bytes **iOS wrote**, read by the production Android
 * importer.
 *
 * Until this existed, "Android can read what iOS writes" had never been executed anywhere.
 * `fixtures/ios-export-v1.pmvault` was committed for Android to read and then only ever
 * read by `CrossPlatformInteropTests.swift` — that is, iOS checking its own output. The
 * forward direction ([CrossPlatformFixtureTest]) proved the opposite claim and was quietly
 * taken as proof of both.
 *
 * The load-bearing assertion here is
 * [android decodes the type discriminator wherever iOS puts it]. The two writers disagree
 * about key order — `docs/FORMAT.md` fixes none, Android's kotlinx encoder happens to emit
 * `"type"` first, and Swift's `.sortedKeys` encoder puts it in the middle. A decoder that
 * assumed the discriminator came first would pass every Android-written fixture and fail
 * on every real iOS export.
 */
class IosFixtureInteropTest {

    private companion object {
        /** Pinned in `CrossPlatformInteropTests.swift`; the two copies must stay identical. */
        const val DIGEST = "ad5aabb5f61c904685645563169624b69e4b672be56a4867a86a99eb223eb1a0"
        const val SIZE_BYTES = 1910L
    }

    private val cipher = AesGcmCipher()
    private val vaultKey = CrossPlatformFixtures.vaultKey
    private val kdf = CrossPlatformFixtures.kdf

    private val file get() = CrossPlatformFixtures.fixture(CrossPlatformFixtures.IOS_FIXTURE)

    // ── The artifact ─────────────────────────────────

    @Test
    fun `the committed iOS fixture is the artifact iOS pinned`() {
        assertTrue("missing ${file.name} — copy it from the iOS test bundle", file.isFile)
        assertEquals("size", SIZE_BYTES, file.length())
        assertEquals(
            "digest differs from the value pinned in CrossPlatformInteropTests.swift",
            DIGEST,
            MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toHex()
        )
    }

    @Test
    fun `the iOS container is the pinned v1 header shape`() {
        val bytes = file.readBytes()
        assertEquals("PMVT", bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))

        val parsed = PmVaultFile.parse(bytes)
        assertEquals("iOS must write the pinned cost, not whatever was cheap", KdfParams(), parsed.kdfParams)
        assertEquals(PmVaultFile.SALT_LENGTH, parsed.salt.size)
        assertEquals(PmVaultFile.IV_LENGTH, parsed.body.iv.size)
    }

    // ── The records ──────────────────────────────────

    @Test
    fun `the iOS export carries the same five records, field for field`() {
        val body = PmVaultFile.decodeBody(decryptBody())

        assertEquals(PmVaultFile.VERSION, body.version)
        assertEquals(CrossPlatformFixtures.EXPORTED_AT, body.exportedAt)
        assertEquals(CrossPlatformFixtures.items.size, body.items.size)

        body.items.zip(CrossPlatformFixtures.items).forEach { (actual, expected) ->
            val (payload, createdAt, updatedAt) = expected
            assertEquals(payload.id, actual.id)
            assertEquals(payload.category.dbKey, actual.category)
            assertEquals("createdAt for ${payload.id}", createdAt, actual.createdAt)
            assertEquals("updatedAt for ${payload.id}", updatedAt, actual.updatedAt)
            assertEquals("payload for ${payload.id}", payload, actual.payload)
        }
    }

    /**
     * The reason this file cannot be compared byte-for-byte against Android's own encoder,
     * and the reason it is worth having at all.
     */
    @Test
    fun `android decodes the type discriminator wherever iOS puts it`() {
        val text = decryptBody().toString(Charsets.UTF_8)

        // Guard against a vacuous pass: if iOS ever started emitting Android's key order,
        // this test would still go green while proving nothing. Assert the property it is
        // actually here to exercise.
        assertFalse(
            "the iOS fixture no longer exercises a non-leading discriminator",
            text.contains("{\"type\":")
        )
        assertTrue("the discriminator must still be present", text.contains("\"type\":\"login\""))

        // And the production decoder handles it. categoryHint is deliberately wrong for
        // four of the five: with a discriminator present it must never be consulted, so a
        // decoder that fell back to the hint would produce the wrong payload type here.
        PmVaultFile.decodeBody(decryptBody()).items.forEach { item ->
            val encoded = PayloadJson.encode(item.payload)
            assertEquals(
                "re-decoding ${item.id} must be stable",
                item.payload,
                PayloadJson.decode(encoded, categoryHint = com.passmanager.domain.model.ItemCategory.LOGIN)
            )
        }
    }

    @Test
    fun `multibyte and escaped text survives the round trip into Android types`() {
        val body = PmVaultFile.decodeBody(decryptBody())

        val note = body.items[2].payload
        assertEquals("Kurtarma kodları", note.title)
        assertTrue(note.notes.contains("\n"))
        assertTrue(note.notes.contains("\t"))
        assertTrue(note.notes.contains("\"tırnaklı\""))
        assertTrue(note.notes.contains("\\"))
        // One astral-plane scalar: two UTF-16 units, four UTF-8 bytes. A truncation
        // anywhere in Argon2 → AES-GCM → JSON → String shows up here.
        assertEquals(1, note.notes.codePoints().filter { it == 0x1F510 }.count())

        val identity = body.items[3].payload
        assertTrue(identity.notes.isEmpty())
    }

    // ── The production path ──────────────────────────

    @Test
    fun `the production importer takes the iOS export with every field intact`() = runTest {
        val target = FakeVaultRepository()
        val importer = ImportVaultUseCase(
            vaultRepository = target,
            metadataRepository = FakeMetadataRepository(),
            cipher = cipher,
            vaultKeyProvider = FakeKeyProvider(vaultKey),
            kdfProvider = kdf
        )

        val plan = importer.plan(
            file.readBytes(),
            CrossPlatformFixtures.PASSPHRASE.toCharArray(),
            now = 9_000_000_000_000L
        )
        assertEquals(CrossPlatformFixtures.items.size, plan.insertCount)
        assertEquals(0, plan.overwriteCount)
        assertEquals(CrossPlatformFixtures.EXPORTED_AT, plan.exportedAt)
        importer.apply(plan)

        CrossPlatformFixtures.items.forEach { (payload, createdAt, updatedAt) ->
            val row = requireNotNull(target.rows[payload.id]) { "missing ${payload.id}" }.item
            assertEquals(payload.category, row.category)
            assertEquals("createdAt for ${payload.id}", createdAt, row.createdAt)
            assertEquals("updatedAt for ${payload.id}", updatedAt, row.updatedAt)
            assertEquals(
                payload,
                PayloadJson.decode(
                    cipher.decrypt(row.encryptedData, vaultKey).decodeToString(),
                    categoryHint = row.category
                )
            )
        }
    }

    @Test
    fun `a wrong passphrase on the iOS fixture is rejected, not silently emptied`() {
        val importer = ImportVaultUseCase(
            vaultRepository = FakeVaultRepository(),
            metadataRepository = FakeMetadataRepository(),
            cipher = cipher,
            vaultKeyProvider = FakeKeyProvider(vaultKey),
            kdfProvider = kdf
        )
        assertThrows(PmVaultAuthenticationException::class.java) {
            runTest {
                importer.plan(file.readBytes(), "not the passphrase".toCharArray())
            }
        }
    }

    // ── Helpers ──────────────────────────────────────

    private fun decryptBody(): ByteArray {
        val parsed = PmVaultFile.parse(file.readBytes())
        val key = kdf.deriveKey(
            CrossPlatformFixtures.PASSPHRASE.toByteArray(Charsets.UTF_8),
            parsed.salt,
            parsed.kdfParams
        )
        return try {
            cipher.decrypt(parsed.body, key, parsed.aad)
        } finally {
            key.fill(0)
        }
    }
}
