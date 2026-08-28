package com.passmanager.fixture

import com.passmanager.crypto.kdf.KdfProvider
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.model.ItemPayload
import com.passmanager.domain.model.PmVaultBodyJson
import com.passmanager.domain.model.PmVaultFile
import com.passmanager.domain.model.PmVaultItemJson
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.File

/**
 * The one definition of the five records both platforms must agree on.
 *
 * `fixtures/android-export-v1.pmvault` is generated from this list by
 * [CrossPlatformFixtureTest]; `fixtures/ios-export-v1.pmvault` was written by the Swift
 * package and is checked against this same list by [IosFixtureInteropTest]. Keeping one
 * list means a change here regenerates one artifact and *fails* the other, which is the
 * signal that the two implementations have stopped agreeing — two copies would simply
 * drift.
 *
 * The iOS package holds its own hand-written copy of these expectations on purpose: it is
 * testing a decoder, so decoding its expectation with the decoder under test would be
 * circular. That reasoning does not apply here, because this list is the *source* the
 * Android artifact is produced from rather than a restatement of it.
 */
internal object CrossPlatformFixtures {

    /** The passphrase both platforms' fixtures are sealed under. Do not change it lightly. */
    const val PASSPHRASE = "CrossPlatform-Fixture-2026"

    const val EXPORTED_AT = 1_787_000_000_000L

    const val ANDROID_FIXTURE = "android-export-v1.pmvault"
    const val ANDROID_EXPECTED = "android-export-v1.expected.json"
    const val IOS_FIXTURE = "ios-export-v1.pmvault"

    /** Arbitrary but fixed: the vault key never reaches the file, it only seals the source rows. */
    val vaultKey: ByteArray get() = ByteArray(32) { (it * 11 + 3).toByte() }

    /**
     * One item per category. The awkward parts are deliberate: Turkish dotted/dotless i in
     * titles, a URL whose slashes some JSON writers escape as `\/`, notes carrying a
     * newline, a tab, an embedded quote, a backslash and a four-byte emoji, two items whose
     * empty `notes` the writer omits entirely, a bank item with a populated
     * `previousPasswords` array, and a distinct `createdAt`/`updatedAt` on every row so
     * timestamp preservation is observable.
     */
    val items: List<Triple<ItemPayload, Long, Long>> = listOf(
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

    val expectedBody = PmVaultBodyJson(
        version = PmVaultFile.VERSION,
        exportedAt = EXPORTED_AT,
        items = items.map { (payload, createdAt, updatedAt) ->
            PmVaultItemJson(
                id = payload.id,
                category = payload.category.dbKey,
                createdAt = createdAt,
                updatedAt = updatedAt,
                payload = payload
            )
        }
    )

    /**
     * BouncyCastle's Argon2id rather than the app's argon2kt: argon2kt is JNI-backed and
     * cannot load on a desktop JVM. Argon2id is a deterministic specification, so a
     * conformant implementation is interchangeable here — and
     * [CrossPlatformFixtureTest.the fixture's argon2id matches the RFC 9106 test vector]
     * pins that conformance so no fixture can be sealed under a subtly wrong key. The
     * shipping Android KDF is pinned separately, on a device, by
     * `Argon2KdfProviderKatTest`.
     */
    val kdf: KdfProvider = object : KdfProvider {
        override fun deriveKey(
            passphrase: ByteArray,
            salt: ByteArray,
            params: KdfParams
        ): ByteArray {
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

    /** Walks up from the Gradle test working directory (`app/`) to the repository root. */
    fun repoRoot(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return requireNotNull(dir) {
            "could not locate settings.gradle.kts above ${File("").absolutePath}"
        }
    }

    fun fixture(name: String): File = File(File(repoRoot(), "fixtures"), name)

    fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
