package com.passmanager.fixture

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.kdf.KdfProvider
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.model.ItemPayload
import com.passmanager.domain.model.PayloadJson
import com.passmanager.domain.model.PmVaultBodyJson
import com.passmanager.domain.model.PmVaultFile
import com.passmanager.domain.model.PmVaultItemJson
import com.passmanager.domain.usecase.ExportVaultUseCase
import com.passmanager.domain.usecase.ImportVaultUseCase
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

/**
 * Produces and then guards `fixtures/android-export-v1.pmvault` — the artifact the iOS reader is
 * checked against. Nothing else in this repo proves cross-platform parity: iOS has its own
 * `.pmvault` implementation, and the only way to know the two agree is for one to read a file the
 * other actually wrote.
 *
 * Behaviour: if the committed fixture is missing, unreadable, or no longer decrypts to the body
 * this file describes, it is regenerated through the production [ExportVaultUseCase] and written
 * out. Otherwise the run only verifies the committed bytes, which keeps a random salt and IV per
 * export from churning the file on every test run. Pass `-Dpmvault.fixture.regenerate=true` to
 * force a rewrite.
 *
 * The derivation is BouncyCastle's Argon2id rather than the app's argon2kt: argon2kt is JNI-backed
 * and cannot load on a desktop JVM. Argon2id is a deterministic specification, so a conformant
 * implementation is interchangeable here — and the first test below pins that conformance to the
 * RFC 9106 vector so the fixture cannot be sealed under a subtly wrong key.
 */
class CrossPlatformFixtureTest {

    private companion object {
        /** The passphrase the iOS test must use to open the fixture. Do not change it lightly. */
        const val PASSPHRASE = "CrossPlatform-Fixture-2026"
        const val FIXTURE_NAME = "android-export-v1.pmvault"
        const val EXPECTED_NAME = "android-export-v1.expected.json"
        const val EXPORTED_AT = 1_787_000_000_000L
        const val REGENERATE_PROPERTY = "pmvault.fixture.regenerate"
    }

    private val cipher = AesGcmCipher()

    /** Arbitrary but fixed: the vault key never reaches the file, it only seals the source rows. */
    private val vaultKey = ByteArray(32) { (it * 11 + 3).toByte() }

    private val kdf: KdfProvider = object : KdfProvider {
        override fun deriveKey(passphrase: ByteArray, salt: ByteArray, params: KdfParams): ByteArray {
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
    }

    /**
     * One item per category. The awkward parts are deliberate: Turkish dotted/dotless i in titles,
     * a URL whose slashes some JSON writers escape as `\/`, notes carrying a newline, a tab, an
     * embedded quote, a backslash and a four-byte emoji, two items whose empty `notes` the writer
     * omits entirely, a bank item with a populated `previousPasswords` array, and a distinct
     * `createdAt`/`updatedAt` on every row so timestamp preservation is observable.
     */
    private val fixtureItems: List<Triple<ItemPayload, Long, Long>> = listOf(
        Triple(
            ItemPayload.Login(
                id = "a1000000-0000-4000-8000-000000000001",
                title = "GitHub",
                notes = "",
                username = "octocat",
                address = "https://github.com",
                password = "hunter2"
            ),
            1_735_689_600_000L,
            1_767_225_600_000L
        ),
        Triple(
            ItemPayload.Card(
                id = "a1000000-0000-4000-8000-000000000002",
                title = "Kadıköy Bankası Kartı",
                notes = "Ay sonu ödemesi",
                cardholderName = "Ayşe Yılmaz",
                cardNumber = "4111111111111111",
                cardCvc = "123",
                cardExpiry = "12/29"
            ),
            1_736_000_000_000L,
            1_770_000_000_000L
        ),
        Triple(
            ItemPayload.SecureNote(
                id = "a1000000-0000-4000-8000-000000000003",
                title = "Kurtarma kodları",
                notes = "satır 1\nsatır 2\tsekmeli\n\"tırnaklı\" ve ters bölü \\ ile 🔐"
            ),
            1_737_000_000_000L,
            1_771_000_000_000L
        ),
        Triple(
            ItemPayload.Identity(
                id = "a1000000-0000-4000-8000-000000000004",
                title = "Pasaport bilgileri",
                notes = "",
                firstName = "Ayşe",
                lastName = "Yılmaz",
                email = "ayse@example.com",
                phone = "+90 555 000 00 00",
                address = "Kadıköy, İstanbul",
                company = "ACME Yazılım A.Ş."
            ),
            1_738_000_000_000L,
            1_772_000_000_000L
        ),
        Triple(
            ItemPayload.Bank(
                id = "a1000000-0000-4000-8000-000000000005",
                title = "Kadıköy Bankası",
                notes = "Ortak hesap",
                accountNumber = "TR33 0006 1005 1978 6457 8413 26",
                bankName = "Kadıköy Bankası",
                password = "Bank-2026!x",
                previousPasswords = listOf("Eski-2025!a", "Daha-Eski-2024!b")
            ),
            1_739_000_000_000L,
            1_773_000_000_000L
        )
    )

    private val expectedBody = PmVaultBodyJson(
        version = PmVaultFile.VERSION,
        exportedAt = EXPORTED_AT,
        items = fixtureItems.map { (payload, createdAt, updatedAt) ->
            PmVaultItemJson(
                id = payload.id,
                category = payload.category.dbKey,
                createdAt = createdAt,
                updatedAt = updatedAt,
                payload = payload
            )
        }
    )

    // ── Conformance ──────────────────────────────────

    @Test
    fun `the fixture's argon2id matches the RFC 9106 test vector`() {
        // RFC 9106 section 5.3. If this passes, a key derived here is the same key the reference
        // phc-winner-argon2 the iOS side vendors would derive.
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
        val fixturesDir = File(repoRoot(), "fixtures")
        val fixtureFile = File(fixturesDir, FIXTURE_NAME)
        val expectedFile = File(fixturesDir, EXPECTED_NAME)
        val expectedJson = PmVaultFile.encodeBody(expectedBody)

        if (needsRegeneration(fixtureFile, expectedJson)) {
            fixturesDir.mkdirs()
            fixtureFile.writeBytes(export())
            expectedFile.writeBytes(expectedJson)
        }

        // 1. The container still parses, and under the agreed passphrase its body is byte-for-byte
        //    what the sidecar promises. That is the exact comparison the iOS test performs.
        assertArrayEquals(expectedJson, decryptBody(fixtureFile.readBytes()))
        assertArrayEquals(expectedJson, expectedFile.readBytes())

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
            PASSPHRASE.toCharArray(),
            now = 9_000_000_000_000L
        )
        assertEquals(fixtureItems.size, plan.insertCount)
        importer.apply(plan)

        fixtureItems.forEach { (payload, createdAt, updatedAt) ->
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
            expectedBody.items.map { it.category }
        )
    }

    // ── Helpers ──────────────────────────────────────

    private suspend fun export(): ByteArray {
        val source = FakeVaultRepository()
        fixtureItems.forEach { (payload, createdAt, updatedAt) ->
            source.seedItem(cipher, vaultKey, payload, createdAt, updatedAt)
        }
        return ExportVaultUseCase(
            vaultRepository = source,
            metadataRepository = FakeMetadataRepository(),
            cipher = cipher,
            vaultKeyProvider = FakeKeyProvider(vaultKey),
            kdfProvider = kdf
        ).invoke(PASSPHRASE.toCharArray(), exportedAt = EXPORTED_AT)
    }

    private fun decryptBody(file: ByteArray): ByteArray {
        val parsed = PmVaultFile.parse(file)
        val key = kdf.deriveKey(PASSPHRASE.toByteArray(Charsets.UTF_8), parsed.salt, parsed.kdfParams)
        return try {
            cipher.decrypt(parsed.body, key, parsed.aad)
        } finally {
            key.fill(0)
        }
    }

    private fun needsRegeneration(fixtureFile: File, expectedJson: ByteArray): Boolean {
        if (System.getProperty(REGENERATE_PROPERTY) == "true") return true
        if (!fixtureFile.isFile) return true
        return runCatching { decryptBody(fixtureFile.readBytes()) }
            .getOrNull()
            ?.contentEquals(expectedJson) != true
    }

    /** Walks up from the Gradle test working directory (`app/`) to the repository root. */
    private fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return requireNotNull(dir) {
            "could not locate settings.gradle.kts above ${File("").absolutePath}"
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
