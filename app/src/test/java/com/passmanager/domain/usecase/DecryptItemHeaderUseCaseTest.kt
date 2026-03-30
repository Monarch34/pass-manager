package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.VaultItemHeader
import com.passmanager.domain.port.VaultKeyProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DecryptItemHeaderUseCaseTest {

    private val cipher = mockk<AesGcmCipher>()
    private val vaultKeyProvider = mockk<VaultKeyProvider>()

    private val useCase = DecryptItemHeaderUseCase(cipher, vaultKeyProvider)

    @Before
    fun setup() {
        every { vaultKeyProvider.requireUnlockedKey() } returns ByteArray(32)
    }

    private fun header(
        encTitle: ByteArray? = byteArrayOf(1, 2, 3),
        titleIv: ByteArray? = byteArrayOf(4, 5, 6),
        encAddr: ByteArray? = byteArrayOf(7, 8, 9),
        addrIv: ByteArray? = byteArrayOf(10, 11, 12)
    ) = VaultItemHeader(
        id = "h1",
        encryptedTitle = encTitle,
        titleIv = titleIv,
        encryptedAddress = encAddr,
        addressIv = addrIv,
        category = ItemCategory.LOGIN,
        updatedAt = 0L
    )

    @Test
    fun `decrypts both title and address when present`() = runTest {
        every { cipher.decrypt(EncryptedData(byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6)), any()) } returns
            "MyTitle".toByteArray()
        every { cipher.decrypt(EncryptedData(byteArrayOf(7, 8, 9), byteArrayOf(10, 11, 12)), any()) } returns
            "example.com".toByteArray()

        val result = useCase(header())

        assertEquals("MyTitle", result.title)
        assertEquals("example.com", result.address)
    }

    @Test
    fun `returns null address when encryptedAddress is null`() = runTest {
        every { cipher.decrypt(any(), any()) } returns "MyTitle".toByteArray()

        val result = useCase(header(encAddr = null, addrIv = null))

        assertEquals("MyTitle", result.title)
        assertNull(result.address)
    }

    @Test
    fun `returns null title when encryptedTitle is null`() = runTest {
        every { cipher.decrypt(any(), any()) } returns "example.com".toByteArray()

        val result = useCase(header(encTitle = null, titleIv = null))

        assertNull(result.title)
        assertEquals("example.com", result.address)
    }

    @Test
    fun `returns null when encryptedTitle present but titleIv is null`() = runTest {
        every { cipher.decrypt(any(), any()) } returns "example.com".toByteArray()

        val result = useCase(header(titleIv = null))

        assertNull(result.title)
    }

    @Test
    fun `returns null when encryptedAddress present but addressIv is null`() = runTest {
        every { cipher.decrypt(any(), any()) } returns "MyTitle".toByteArray()

        val result = useCase(header(addrIv = null))

        assertNull(result.address)
    }

    @Test
    fun `returns both null for legacy header with all null fields`() = runTest {
        val result = useCase(header(encTitle = null, titleIv = null, encAddr = null, addrIv = null))

        assertNull(result.title)
        assertNull(result.address)
    }
}
