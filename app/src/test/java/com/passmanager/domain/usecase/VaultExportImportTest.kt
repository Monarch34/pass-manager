package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.kdf.KdfProvider
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.exception.PmVaultAuthenticationException
import com.passmanager.domain.exception.PmVaultInvalidParametersException
import com.passmanager.domain.exception.PmVaultMalformedException
import com.passmanager.domain.model.ItemPayload
import com.passmanager.domain.model.PayloadJson
import com.passmanager.domain.model.PmVaultFile
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.test.FakeKeyProvider
import com.passmanager.test.FakeMetadataRepository
import com.passmanager.test.FakeVaultRepository
import com.passmanager.test.seedItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * End-to-end cover for the `.pmvault` path: a real [AesGcmCipher] over fake storage, with Argon2
 * replaced by a counting stand-in so the tests can assert *whether* a derivation happened at all.
 */
class VaultExportImportTest {

    private val cipher = AesGcmCipher()
    private val vaultKey = ByteArray(32) { (it * 7).toByte() }

    private val metadata = VaultMetadata(
        currentKeyVersion = 1,
        wrappedVaultKey = EncryptedData(ByteArray(32), ByteArray(12)),
        kdfSalt = ByteArray(16),
        kdfParams = KdfParams(),
        biometricEnabled = false,
        biometricWrappedKey = null
    )

    /** Deterministic and cheap, but still passphrase- and salt-sensitive. */
    private class CountingKdfProvider : KdfProvider {
        var deriveCount = 0
        override fun deriveKey(passphrase: ByteArray, salt: ByteArray, params: KdfParams): ByteArray {
            deriveCount++
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(passphrase)
            digest.update(salt)
            return digest.digest().copyOf(params.hashLength)
        }
    }

    private suspend fun failureOf(block: suspend () -> Unit): Throwable? =
        try {
            block()
            null
        } catch (e: Throwable) {
            e
        }

    private fun seed(
        repo: FakeVaultRepository,
        payload: ItemPayload,
        createdAt: Long,
        updatedAt: Long
    ) = repo.seedItem(cipher, vaultKey, payload, createdAt, updatedAt)

    private fun decrypt(repo: FakeVaultRepository, id: String): ItemPayload {
        val row = requireNotNull(repo.rows[id]) { "no row for $id" }
        return PayloadJson.decode(
            cipher.decrypt(row.item.encryptedData, vaultKey).decodeToString(),
            categoryHint = row.item.category
        )
    }

    private fun exporter(repo: FakeVaultRepository, kdf: KdfProvider) = ExportVaultUseCase(
        vaultRepository = repo,
        metadataRepository = FakeMetadataRepository(metadata),
        cipher = cipher,
        vaultKeyProvider = FakeKeyProvider(vaultKey),
        kdfProvider = kdf
    )

    private fun importer(repo: FakeVaultRepository, kdf: KdfProvider) = ImportVaultUseCase(
        vaultRepository = repo,
        metadataRepository = FakeMetadataRepository(metadata),
        cipher = cipher,
        vaultKeyProvider = FakeKeyProvider(vaultKey),
        kdfProvider = kdf
    )

    /** Rebuilds a container around a replacement header, fixing up the length field. */
    private fun withHeader(file: ByteArray, transform: (String) -> String): ByteArray {
        val headerLen = ((file[4].toInt() and 0xFF) shl 8) or (file[5].toInt() and 0xFF)
        val replacement = transform(String(file, 6, headerLen, Charsets.UTF_8))
            .toByteArray(Charsets.UTF_8)
        val out = ByteArray(6 + replacement.size + (file.size - 6 - headerLen))
        file.copyInto(out, 0, 0, 4)
        out[4] = ((replacement.size ushr 8) and 0xFF).toByte()
        out[5] = (replacement.size and 0xFF).toByte()
        replacement.copyInto(out, 6)
        file.copyInto(out, 6 + replacement.size, 6 + headerLen, file.size)
        return out
    }

    private val samplePayloads = listOf(
        ItemPayload.Login(
            id = "login-1", title = "GitHub", notes = "work account",
            username = "octocat", address = "https://github.com", password = "hunter2"
        ),
        ItemPayload.Card(
            id = "card-1", title = "Visa", notes = "",
            cardholderName = "A Person", cardNumber = "4111111111111111",
            cardCvc = "123", cardExpiry = "12/29"
        ),
        ItemPayload.Bank(
            id = "bank-1", title = "Savings", notes = "joint",
            accountNumber = "TR00 0000", bankName = "Some Bank", password = "Bank12345",
            previousPasswords = listOf("older1", "older2")
        ),
        ItemPayload.SecureNote(id = "note-1", title = "Recovery codes", notes = "abc-def-ghi"),
        ItemPayload.Identity(
            id = "id-1", title = "Passport", notes = "",
            firstName = "A", lastName = "Person", email = "a@example.com",
            phone = "+900000000", address = "Somewhere 1", company = "ACME"
        )
    )

    // ── Round trip ───────────────────────────────────

    @Test
    fun `export then import into an empty vault reproduces every field including timestamps`() = runTest {
        val kdf = CountingKdfProvider()
        val source = FakeVaultRepository()
        samplePayloads.forEachIndexed { index, payload ->
            seed(source, payload, createdAt = 1_000L + index, updatedAt = 5_000L + index)
        }

        val file = exporter(source, kdf).invoke("export-passphrase".toCharArray())

        val target = FakeVaultRepository()
        val importer = importer(target, kdf)
        val plan = importer.plan(file, "export-passphrase".toCharArray(), now = 9_000_000L)
        assertEquals(samplePayloads.size, plan.insertCount)
        assertEquals(0, plan.overwriteCount)

        val result = importer.apply(plan)
        assertEquals(samplePayloads.size, result.inserted)
        assertEquals(0, result.overwritten)

        samplePayloads.forEachIndexed { index, payload ->
            val row = requireNotNull(target.rows[payload.id]).item
            assertEquals(payload.id, row.id)
            assertEquals(payload.category, row.category)
            assertEquals(1_000L + index, row.createdAt)
            assertEquals(5_000L + index, row.updatedAt)
            assertEquals(metadata.currentKeyVersion, row.keyVersion)
            assertEquals(payload, decrypt(target, payload.id))
        }
        // Header columns are written too, or the list would show blank rows after an import.
        assertTrue(target.rows.values.all { it.header != null })
    }

    @Test
    fun `each export uses a fresh salt and iv`() = runTest {
        val kdf = CountingKdfProvider()
        val source = FakeVaultRepository()
        seed(source, samplePayloads[0], createdAt = 1L, updatedAt = 2L)
        val exporter = exporter(source, kdf)

        val first = PmVaultFile.parse(exporter("pass".toCharArray()))
        val second = PmVaultFile.parse(exporter("pass".toCharArray()))

        assertNotEquals(first.salt.toList(), second.salt.toList())
        assertNotEquals(first.body.iv.toList(), second.body.iv.toList())
        assertEquals(KdfParams(), first.kdfParams)
    }

    @Test
    fun `exporting an empty vault produces a readable file with no items`() = runTest {
        val kdf = CountingKdfProvider()
        val file = exporter(FakeVaultRepository(), kdf).invoke("pass".toCharArray())

        val plan = importer(FakeVaultRepository(), kdf).plan(file, "pass".toCharArray())

        assertTrue(plan.entries.isEmpty())
    }

    // ── Failure modes ────────────────────────────────

    @Test
    fun `wrong passphrase is reported as wrong-or-corrupted`() = runTest {
        val kdf = CountingKdfProvider()
        val source = FakeVaultRepository()
        seed(source, samplePayloads[0], createdAt = 1L, updatedAt = 2L)
        val file = exporter(source, kdf).invoke("right".toCharArray())

        val failure = failureOf {
            importer(FakeVaultRepository(), kdf).plan(file, "wrong".toCharArray())
        }

        assertTrue("was $failure", failure is PmVaultAuthenticationException)
    }

    @Test
    fun `a structurally truncated file never reaches the kdf`() = runTest {
        val kdf = CountingKdfProvider()
        val source = FakeVaultRepository()
        seed(source, samplePayloads[0], createdAt = 1L, updatedAt = 2L)
        val file = exporter(source, kdf).invoke("pass".toCharArray())
        val derivesAfterExport = kdf.deriveCount

        val failure = failureOf {
            importer(FakeVaultRepository(), kdf).plan(file.copyOfRange(0, 20), "pass".toCharArray())
        }

        assertTrue("was $failure", failure is PmVaultMalformedException)
        assertEquals(derivesAfterExport, kdf.deriveCount)
    }

    @Test
    fun `a file short by one byte fails the tag check`() = runTest {
        val kdf = CountingKdfProvider()
        val source = FakeVaultRepository()
        seed(source, samplePayloads[0], createdAt = 1L, updatedAt = 2L)
        val file = exporter(source, kdf).invoke("pass".toCharArray())

        val failure = failureOf {
            importer(FakeVaultRepository(), kdf)
                .plan(file.copyOfRange(0, file.size - 1), "pass".toCharArray())
        }

        assertTrue("was $failure", failure is PmVaultAuthenticationException)
    }

    @Test
    fun `a header edited without touching the kdf inputs still fails the tag check`() = runTest {
        val kdf = CountingKdfProvider()
        val source = FakeVaultRepository()
        seed(source, samplePayloads[0], createdAt = 1L, updatedAt = 2L)
        val file = exporter(source, kdf).invoke("pass".toCharArray())

        // Re-pad the header JSON: same salt, same cost parameters, so the derived key comes out
        // byte-identical and only the AAD changed. Without AAD this would decrypt happily.
        val tampered = withHeader(file) { it.dropLast(1) + " }" }

        val failure = failureOf {
            importer(FakeVaultRepository(), kdf).plan(tampered, "pass".toCharArray())
        }

        assertTrue("was $failure", failure is PmVaultAuthenticationException)
    }

    @Test
    fun `out-of-bounds kdf parameters are rejected before any derivation`() = runTest {
        val kdf = CountingKdfProvider()
        val source = FakeVaultRepository()
        seed(source, samplePayloads[0], createdAt = 1L, updatedAt = 2L)
        val file = exporter(source, kdf).invoke("pass".toCharArray())
        val derivesAfterExport = kdf.deriveCount

        // A header demanding 1 GiB of Argon2 memory, with the byte layout kept legal.
        val attack = withHeader(file) { it.replace("\"memory\":65536", "\"memory\":1048576") }

        val failure = failureOf {
            importer(FakeVaultRepository(), kdf).plan(attack, "pass".toCharArray())
        }

        assertTrue("was $failure", failure is PmVaultInvalidParametersException)
        assertEquals(derivesAfterExport, kdf.deriveCount)
    }

    // ── Merge semantics ──────────────────────────────

    @Test
    fun `a newer entry in the file overwrites the local row`() = runTest {
        val kdf = CountingKdfProvider()
        val newer = ItemPayload.Login(
            id = "login-1", title = "GitHub", username = "octocat",
            address = "https://github.com", password = "new-secret"
        )
        val source = FakeVaultRepository()
        seed(source, newer, createdAt = 100L, updatedAt = 5_000L)
        val file = exporter(source, kdf).invoke("pass".toCharArray())

        val target = FakeVaultRepository()
        seed(
            target,
            ItemPayload.Login(id = "login-1", title = "GitHub", password = "old-secret"),
            createdAt = 50L,
            updatedAt = 1_000L
        )
        val importer = importer(target, kdf)
        val plan = importer.plan(file, "pass".toCharArray(), now = 9_000_000L)

        assertEquals(0, plan.insertCount)
        assertEquals(1, plan.overwriteCount)
        assertEquals(listOf("GitHub"), plan.overwrittenTitles)

        val result = importer.apply(plan)

        assertEquals(1, result.overwritten)
        assertEquals(newer, decrypt(target, "login-1"))
        assertEquals(5_000L, target.rows.getValue("login-1").item.updatedAt)
        // An overwrite is an edit, not a re-creation: createdAt stays local.
        assertEquals(50L, target.rows.getValue("login-1").item.createdAt)
    }

    @Test
    fun `an older entry in the file loses and leaves the local row untouched`() = runTest {
        val kdf = CountingKdfProvider()
        val source = FakeVaultRepository()
        seed(
            source,
            ItemPayload.Login(id = "login-1", title = "GitHub", password = "stale"),
            createdAt = 100L,
            updatedAt = 1_000L
        )
        val file = exporter(source, kdf).invoke("pass".toCharArray())

        val local = ItemPayload.Login(id = "login-1", title = "GitHub", password = "current")
        val target = FakeVaultRepository()
        seed(target, local, createdAt = 50L, updatedAt = 8_000L)
        val importer = importer(target, kdf)
        val plan = importer.plan(file, "pass".toCharArray(), now = 9_000_000L)

        assertEquals(1, plan.skippedCount)
        assertEquals(0, plan.overwriteCount)

        val result = importer.apply(plan)

        assertEquals(0, result.overwritten)
        assertEquals(1, result.skipped)
        assertEquals(local, decrypt(target, "login-1"))
        assertEquals(8_000L, target.rows.getValue("login-1").item.updatedAt)
    }

    @Test
    fun `an equally recent entry does not overwrite`() = runTest {
        val kdf = CountingKdfProvider()
        val payload = ItemPayload.Login(id = "login-1", title = "GitHub", password = "same")
        val source = FakeVaultRepository()
        seed(source, payload, createdAt = 100L, updatedAt = 4_000L)
        val file = exporter(source, kdf).invoke("pass".toCharArray())

        val target = FakeVaultRepository()
        seed(target, payload, createdAt = 100L, updatedAt = 4_000L)

        val plan = importer(target, kdf).plan(file, "pass".toCharArray(), now = 9_000_000L)

        assertEquals(1, plan.skippedCount)
        assertEquals(0, plan.overwriteCount)
    }

    @Test
    fun `add-only mode inserts new entries and skips every overwrite`() = runTest {
        val kdf = CountingKdfProvider()
        val source = FakeVaultRepository()
        seed(
            source,
            ItemPayload.Login(id = "login-1", title = "GitHub", password = "from-file"),
            createdAt = 100L,
            updatedAt = 5_000L
        )
        seed(
            source,
            ItemPayload.SecureNote(id = "note-1", title = "New note", notes = "fresh"),
            createdAt = 200L,
            updatedAt = 6_000L
        )
        val file = exporter(source, kdf).invoke("pass".toCharArray())

        val local = ItemPayload.Login(id = "login-1", title = "GitHub", password = "local")
        val target = FakeVaultRepository()
        seed(target, local, createdAt = 50L, updatedAt = 1_000L)

        val importer = importer(target, kdf)
        val plan = importer.plan(file, "pass".toCharArray(), now = 9_000_000L)
        assertEquals(1, plan.insertCount)
        assertEquals(1, plan.overwriteCount)

        val result = importer.apply(plan, addOnly = true)

        assertEquals(1, result.inserted)
        assertEquals(0, result.overwritten)
        assertEquals(1, result.skipped)
        assertEquals(local, decrypt(target, "login-1"))
        assertEquals(1_000L, target.rows.getValue("login-1").item.updatedAt)
        assertEquals("New note", decrypt(target, "note-1").title)
    }

    @Test
    fun `a forged future updatedAt is clamped to now`() = runTest {
        val kdf = CountingKdfProvider()
        val now = 9_000_000L
        val source = FakeVaultRepository()
        seed(
            source,
            ItemPayload.Login(id = "login-1", title = "GitHub", password = "from-file"),
            createdAt = 100L,
            updatedAt = Long.MAX_VALUE
        )
        val file = exporter(source, kdf).invoke("pass".toCharArray())

        val target = FakeVaultRepository()
        val importer = importer(target, kdf)
        importer.apply(importer.plan(file, "pass".toCharArray(), now = now))

        assertEquals(now, target.rows.getValue("login-1").item.updatedAt)

        // The clamp is the whole point: a re-import of the same forged file no longer beats it.
        val second = importer.plan(file, "pass".toCharArray(), now = now)
        assertEquals(1, second.skippedCount)
    }

    @Test
    fun `a clamped future entry still beats an older local row`() = runTest {
        val kdf = CountingKdfProvider()
        val now = 9_000_000L
        val source = FakeVaultRepository()
        seed(
            source,
            ItemPayload.Login(id = "login-1", title = "GitHub", password = "from-file"),
            createdAt = 100L,
            updatedAt = now + 10_000_000L
        )
        val file = exporter(source, kdf).invoke("pass".toCharArray())

        val target = FakeVaultRepository()
        seed(
            target,
            ItemPayload.Login(id = "login-1", title = "GitHub", password = "local"),
            createdAt = 50L,
            updatedAt = now - 1
        )

        val importer = importer(target, kdf)
        val plan = importer.plan(file, "pass".toCharArray(), now = now)
        assertEquals(1, plan.overwriteCount)

        importer.apply(plan)

        assertEquals(now, target.rows.getValue("login-1").item.updatedAt)
        assertEquals("from-file", (decrypt(target, "login-1") as ItemPayload.Login).password)
    }
}
