package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.VaultItem
import com.passmanager.domain.port.VaultKeyProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the byte-level JSON parser in [DecryptPasswordBytesUseCase].
 * The cipher mock returns raw JSON bytes, exercising the parser's field extraction logic.
 */
class DecryptPasswordBytesUseCaseTest {

    private val cipher = mockk<AesGcmCipher>()
    private val vaultKeyProvider = mockk<VaultKeyProvider>()

    private val useCase = DecryptPasswordBytesUseCase(cipher, vaultKeyProvider)

    private fun fakeItem() = VaultItem(
        id = "item-1",
        encryptedData = EncryptedData(ByteArray(32), ByteArray(12)),
        keyVersion = 1,
        createdAt = 0L,
        updatedAt = 0L,
        category = ItemCategory.LOGIN
    )

    private fun setupMocks(jsonPayload: String) {
        every { vaultKeyProvider.requireUnlockedKey() } returns ByteArray(32)
        every { cipher.decrypt(any(), any()) } returns jsonPayload.toByteArray(Charsets.UTF_8)
    }

    @Test
    fun `extracts simple password and title`() = runTest {
        setupMocks("""{"title":"GitHub","username":"user","password":"secret"}""")

        val result = useCase(fakeItem())

        assertEquals("GitHub", result.title)
        assertTrue(result.passwordBytes.contentEquals("secret".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `handles escaped double quote in password`() = runTest {
        setupMocks("""{"title":"T","password":"pass\"word"}""")

        val result = useCase(fakeItem())

        val expected = "pass\"word".toByteArray(Charsets.UTF_8)
        assertTrue(result.passwordBytes.contentEquals(expected))
    }

    @Test
    fun `handles escaped backslash in password`() = runTest {
        setupMocks("""{"title":"T","password":"p\\w"}""")

        val result = useCase(fakeItem())

        val expected = "p\\w".toByteArray(Charsets.UTF_8)
        assertTrue(result.passwordBytes.contentEquals(expected))
    }

    @Test
    fun `handles newline escape in password`() = runTest {
        setupMocks("""{"title":"T","password":"line1\nline2"}""")

        val result = useCase(fakeItem())

        val expected = "line1\nline2".toByteArray(Charsets.UTF_8)
        assertTrue(result.passwordBytes.contentEquals(expected))
    }

    @Test
    fun `handles unicode escape sequence in password`() = runTest {
        // \u00e9 = é (U+00E9), UTF-8: 0xC3 0xA9
        setupMocks("""{"title":"T","password":"caf\u00e9"}""")

        val result = useCase(fakeItem())

        val expected = "café".toByteArray(Charsets.UTF_8)
        assertTrue(result.passwordBytes.contentEquals(expected))
    }

    @Test(expected = IllegalStateException::class)
    fun `throws when password field missing`() = runTest {
        setupMocks("""{"title":"Note","notes":"some text"}""")

        useCase(fakeItem())
    }

    @Test
    fun `returns Unknown title when title field missing`() = runTest {
        setupMocks("""{"password":"secret"}""")

        val result = useCase(fakeItem())

        assertEquals("Unknown", result.title)
        assertTrue(result.passwordBytes.contentEquals("secret".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `handles whitespace around colon in JSON`() = runTest {
        setupMocks("""{"title" : "T","password" : "pw"}""")

        val result = useCase(fakeItem())

        assertEquals("T", result.title)
        assertTrue(result.passwordBytes.contentEquals("pw".toByteArray(Charsets.UTF_8)))
    }
}
