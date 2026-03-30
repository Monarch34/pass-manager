package com.passmanager.domain.usecase

import android.util.Log
import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.ItemPayload
import com.passmanager.domain.model.VaultItem
import com.passmanager.domain.model.VaultItemHeader
import com.passmanager.domain.port.VaultKeyProvider
import com.passmanager.domain.repository.VaultRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProcessVaultListHeadersUseCaseTest {

    private val vaultRepository = mockk<VaultRepository>(relaxed = true)
    private val decryptItemUseCase = mockk<DecryptItemUseCase>()
    private val decryptItemHeaderUseCase = mockk<DecryptItemHeaderUseCase>()
    private val cipher = mockk<AesGcmCipher>()
    private val vaultKeyProvider = mockk<VaultKeyProvider>()

    private val useCase = ProcessVaultListHeadersUseCase(
        vaultRepository, decryptItemUseCase, decryptItemHeaderUseCase, cipher, vaultKeyProvider
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any<Throwable>()) } returns 0
    }

    @After
    fun teardown() {
        unmockkStatic(Log::class)
    }

    private fun headerWithEncryptedTitle(id: String = "h1") = VaultItemHeader(
        id = id,
        encryptedTitle = byteArrayOf(1, 2, 3),
        titleIv = byteArrayOf(4, 5, 6),
        encryptedAddress = byteArrayOf(7, 8, 9),
        addressIv = byteArrayOf(10, 11, 12),
        category = ItemCategory.LOGIN,
        updatedAt = 1000L
    )

    private fun legacyHeader(id: String = "legacy1") = VaultItemHeader(
        id = id,
        encryptedTitle = null,
        titleIv = null,
        encryptedAddress = null,
        addressIv = null,
        category = ItemCategory.LOGIN,
        updatedAt = 2000L
    )

    @Test
    fun `empty list returns empty outcome`() = runTest {
        val result = useCase(emptyList())

        assertTrue(result.rows.isEmpty())
        assertFalse(result.hadDecryptFailure)
    }

    @Test
    fun `headers with encryptedTitle use fast path`() = runTest {
        val header = headerWithEncryptedTitle("h1")
        coEvery { decryptItemHeaderUseCase(header) } returns
            DecryptItemHeaderUseCase.Result("GitHub", "github.com")

        val result = useCase(listOf(header))

        assertEquals(1, result.rows.size)
        assertEquals("h1", result.rows[0].id)
        assertEquals("GitHub", result.rows[0].title)
        assertEquals("github.com", result.rows[0].address)
        assertFalse(result.hadDecryptFailure)
    }

    @Test
    fun `decrypt failure sets hadDecryptFailure true and excludes row`() = runTest {
        val goodHeader = headerWithEncryptedTitle("good")
        val badHeader = headerWithEncryptedTitle("bad")
        coEvery { decryptItemHeaderUseCase(goodHeader) } returns
            DecryptItemHeaderUseCase.Result("Good Item", "good.com")
        coEvery { decryptItemHeaderUseCase(badHeader) } throws RuntimeException("decrypt failed")

        val result = useCase(listOf(goodHeader, badHeader))

        assertTrue(result.hadDecryptFailure)
        assertEquals(1, result.rows.size)
        assertEquals("good", result.rows[0].id)
    }

    @Test
    fun `legacy header without encryptedTitle uses full blob fallback`() = runTest {
        val header = legacyHeader("leg1")
        val fakeItem = VaultItem(
            id = "leg1",
            encryptedData = EncryptedData(ByteArray(32), ByteArray(12)),
            keyVersion = 1, createdAt = 0L, updatedAt = 2000L,
            category = ItemCategory.LOGIN
        )
        val fakePayload = ItemPayload.Login(
            id = "leg1", title = "Legacy Login",
            address = "legacy.com", password = "pw"
        )

        coEvery { vaultRepository.getById("leg1") } returns fakeItem
        coEvery { decryptItemUseCase(fakeItem) } returns fakePayload
        every { vaultKeyProvider.requireUnlockedKey() } returns ByteArray(32)
        every { cipher.encrypt(any(), any()) } returns EncryptedData(ByteArray(16), ByteArray(12))

        val result = useCase(listOf(header))

        assertEquals(1, result.rows.size)
        assertEquals("Legacy Login", result.rows[0].title)
        assertEquals("legacy.com", result.rows[0].address)
        assertFalse(result.hadDecryptFailure)
    }

    @Test
    fun `missing vault row for legacy header sets hadDecryptFailure`() = runTest {
        val header = legacyHeader("missing")
        coEvery { vaultRepository.getById("missing") } returns null

        val result = useCase(listOf(header))

        assertTrue(result.hadDecryptFailure)
        assertTrue(result.rows.isEmpty())
    }
}
