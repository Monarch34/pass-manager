package com.passmanager.fixture

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.model.PayloadJson
import com.passmanager.domain.model.PmVaultFile
import com.passmanager.domain.usecase.ExportVaultUseCase
import com.passmanager.domain.usecase.ImportVaultUseCase
import com.passmanager.fixture.CrossPlatformFixtures.toHex
import com.passmanager.test.FakeKeyProvider
import com.passmanager.test.FakeMetadataRepository
import com.passmanager.test.FakeVaultRepository
import com.passmanager.test.seedItem
import kotlinx.coroutines.test.runTest
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Produces and then guards `fixtures/android-export-v1.pmvault` — the artifact the iOS
 * reader is checked against. Nothing else on this side proves cross-platform parity: iOS
 * has its own `.pmvault` implementation, and the only way to know the two agree is for one
 * to read a file the other actually wrote. The reverse direction lives in
 * [IosFixtureInteropTest].
 *
 * REGENERATION IS EXPLICIT. This test used to rewrite the fixture whenever the committed
 * bytes stopped decrypting to the body it describes — which meant a change to the export
 * format silently replaced the shared artifact and still reported green, while the iOS
 * suite that pins the old digest broke on a different machine with no indication why. Now
 * a stale or missing fixture is a failure, and only `-Dpmvault.fixture.regenerate=true`
 * writes anything. Regenerating draws a fresh random salt and IV, so it always produces
 * new bytes and always requires updating the digests pinned in
 * `CrossPlatformInteropTests.swift`; the failure message prints them.
 */
class CrossPlatformFixtureTest {

    private companion object {
        const val REGENERATE_PROPERTY = "pmvault.fixture.regenerate"
    }

    private val cipher = AesGcmCipher()
    private val vaultKey = CrossPlatformFixtures.vaultKey
    private val kdf = CrossPlatformFixtures.kdf

    // ── Conformance ──────────────────────────────────

    @Test
    fun `the fixture's argon2id matches the RFC 9106 test vector`() {
        // RFC 9106 section 5.3. If this passes, a key derived here is the same key the
        // reference phc-winner-argon2 the iOS side vendors would derive.
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

    // ── The artifact ─────────────────────────────────

    @Test
    fun `the cross-platform fixture is present, current and readable`() = runTest {
        val fixtureFile = CrossPlatformFixtures.fixture(CrossPlatformFixtures.ANDROID_FIXTURE)
        val expectedFile = CrossPlatformFixtures.fixture(CrossPlatformFixtures.ANDROID_EXPECTED)
        val expectedJson = PmVaultFile.encodeBody(CrossPlatformFixtures.expectedBody)

        if (System.getProperty(REGENERATE_PROPERTY) == "true") {
            fixtureFile.parentFile.mkdirs()
            fixtureFile.writeBytes(export())
            expectedFile.writeBytes(expectedJson)
            println(regeneratedNotice(fixtureFile, expectedFile))
        }

        assertTrue(
            "missing ${fixtureFile.name}. Run with -D$REGENERATE_PROPERTY=true to write it.",
            fixtureFile.isFile
        )

        // 1. The container still parses, and under the agreed passphrase its body is
        //    byte-for-byte what the sidecar promises. That is the exact comparison the iOS
        //    test performs.
        val decrypted = runCatching { decryptBody(fixtureFile.readBytes()) }.getOrNull()
        assertTrue(staleMessage(fixtureFile), decrypted != null)
        assertArrayEquals(staleMessage(fixtureFile), expectedJson, decrypted)
        assertArrayEquals(staleMessage(expectedFile), expectedJson, expectedFile.readBytes())

        // 2. The header is the pinned v1 shape with a real 16-byte salt.
        val parsed = PmVaultFile.parse(fixtureFile.readBytes())
        assertEquals(KdfParams(), parsed.kdfParams)
        assertEquals(PmVaultFile.SALT_LENGTH, parsed.salt.size)
        assertEquals(PmVaultFile.IV_LENGTH, parsed.body.iv.size)

        // 3. The production importer takes it into an empty vault with every field intact.
        val target = FakeVaultRepository()
        val importer = ImportVaultUseCase(
            vaultRepository = target,
            metadataRepository = FakeMetadataRepository(),
            cipher = cipher,
            vaultKeyProvider = FakeKeyProvider(vaultKey),
            kdfProvider = kdf
        )
        val plan = importer.plan(
            fixtureFile.readBytes(),
            CrossPlatformFixtures.PASSPHRASE.toCharArray(),
            now = 9_000_000_000_000L
        )
        assertEquals(CrossPlatformFixtures.items.size, plan.insertCount)
        importer.apply(plan)

        CrossPlatformFixtures.items.forEach { (payload, createdAt, updatedAt) ->
            val row = requireNotNull(target.rows[payload.id]) { "missing ${payload.id}" }.item
            assertEquals(payload.category, row.category)
            assertEquals(createdAt, row.createdAt)
            assertEquals(updatedAt, row.updatedAt)
            assertEquals(
                payload,
                PayloadJson.decode(
                    cipher.decrypt(row.encryptedData, vaultKey).decodeToString(),
                    categoryHint = row.category
                )
            )
        }

        // 4. What the sidecar must let iOS check without decrypting anything.
        val text = expectedJson.toString(Charsets.UTF_8)
        assertTrue("forward slashes must not be escaped", text.contains("https://github.com"))
        assertTrue("Turkish characters must survive as UTF-8", text.contains("Kadıköy Bankası"))
        assertTrue("empty notes are omitted, not emitted", !text.contains("\"notes\":\"\""))
        assertEquals(
            "every category must appear",
            listOf("login", "card", "note", "identity", "bank"),
            CrossPlatformFixtures.expectedBody.items.map { it.category }
        )
    }

    // ── Helpers ──────────────────────────────────────

    private suspend fun export(): ByteArray {
        val source = FakeVaultRepository()
        CrossPlatformFixtures.items.forEach { (payload, createdAt, updatedAt) ->
            source.seedItem(cipher, vaultKey, payload, createdAt, updatedAt)
        }
        return ExportVaultUseCase(
            vaultRepository = source,
            metadataRepository = FakeMetadataRepository(),
            cipher = cipher,
            vaultKeyProvider = FakeKeyProvider(vaultKey),
            kdfProvider = kdf
        ).invoke(
            CrossPlatformFixtures.PASSPHRASE.toCharArray(),
            exportedAt = CrossPlatformFixtures.EXPORTED_AT
        )
    }

    private fun decryptBody(file: ByteArray): ByteArray {
        val parsed = PmVaultFile.parse(file)
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

    private fun staleMessage(file: File): String =
        "${file.name} is not the artifact this test describes. If the export format changed " +
            "on purpose, rerun with -D$REGENERATE_PROPERTY=true and paste the printed digests " +
            "into CrossPlatformInteropTests.swift. Do not edit the fixture by hand — iOS pins " +
            "its bytes."

    private fun regeneratedNotice(fixtureFile: File, expectedFile: File): String = buildString {
        appendLine()
        appendLine("REGENERATED the cross-platform fixture. Update the pins in")
        appendLine("ios/PassVaultCore/Tests/PassVaultCoreTests/CrossPlatformInteropTests.swift,")
        appendLine("and copy the files into that target's Fixtures/ directory:")
        listOf(fixtureFile, expectedFile).forEach {
            appendLine("  ${it.name}  ${sha256(it.readBytes())}  (${it.length()} bytes)")
        }
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
}
