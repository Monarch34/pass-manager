package com.passmanager.domain.usecase

import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.VaultItemHeader
import com.passmanager.domain.port.DesktopPairingPort
import com.passmanager.domain.repository.VaultRepository
import com.passmanager.protocol.SecureResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SendItemListToDesktopUseCaseTest {

    private val vaultRepository = mockk<VaultRepository>()
    private val decryptItemHeaderUseCase = mockk<DecryptItemHeaderUseCase>()
    private val session = mockk<DesktopPairingPort>(relaxed = true)

    private val useCase = SendItemListToDesktopUseCase(
        vaultRepository, decryptItemHeaderUseCase, session
    )

    private fun header(id: String, category: ItemCategory = ItemCategory.LOGIN) = VaultItemHeader(
        id = id,
        encryptedTitle = byteArrayOf(1),
        titleIv = byteArrayOf(2),
        encryptedAddress = null,
        addressIv = null,
        category = category,
        updatedAt = 0L
    )

    @Test
    fun `sends all items when all headers decrypt successfully`() = runTest {
        val h1 = header("id1")
        val h2 = header("id2", ItemCategory.CARD)
        coEvery { vaultRepository.getHeaders() } returns listOf(h1, h2)
        coEvery { decryptItemHeaderUseCase(h1) } returns DecryptItemHeaderUseCase.Result("GitHub", "github.com")
        coEvery { decryptItemHeaderUseCase(h2) } returns DecryptItemHeaderUseCase.Result("Visa", null)

        val responseSlot = slot<SecureResponse.Items>()
        coEvery { session.sendSecure(capture(responseSlot)) } returns Unit

        useCase()

        val response = responseSlot.captured
        assertEquals(2, response.items.size)
        assertEquals(0, response.failedCount)
        assertEquals("GitHub", response.items[0].title)
        assertEquals("github.com", response.items[0].url)
        assertEquals("Visa", response.items[1].title)
        assertEquals("card", response.items[1].category)
    }

    @Test
    fun `reports failed count when some headers fail to decrypt`() = runTest {
        val h1 = header("id1")
        val h2 = header("id2")
        val h3 = header("id3")
        coEvery { vaultRepository.getHeaders() } returns listOf(h1, h2, h3)
        coEvery { decryptItemHeaderUseCase(h1) } returns DecryptItemHeaderUseCase.Result("Good1", null)
        coEvery { decryptItemHeaderUseCase(h2) } throws RuntimeException("decrypt failed")
        coEvery { decryptItemHeaderUseCase(h3) } returns DecryptItemHeaderUseCase.Result("Good3", null)

        val responseSlot = slot<SecureResponse.Items>()
        coEvery { session.sendSecure(capture(responseSlot)) } returns Unit

        useCase()

        val response = responseSlot.captured
        assertEquals(2, response.items.size)
        assertEquals(1, response.failedCount)
    }

    @Test
    fun `sends empty list for empty vault`() = runTest {
        coEvery { vaultRepository.getHeaders() } returns emptyList()

        val responseSlot = slot<SecureResponse.Items>()
        coEvery { session.sendSecure(capture(responseSlot)) } returns Unit

        useCase()

        val response = responseSlot.captured
        assertEquals(0, response.items.size)
        assertEquals(0, response.failedCount)
    }

    @Test
    fun `item with null title is counted as failure not included in list`() = runTest {
        val h1 = header("id1")
        coEvery { vaultRepository.getHeaders() } returns listOf(h1)
        coEvery { decryptItemHeaderUseCase(h1) } returns DecryptItemHeaderUseCase.Result(null, null)

        val responseSlot = slot<SecureResponse.Items>()
        coEvery { session.sendSecure(capture(responseSlot)) } returns Unit

        useCase()

        val response = responseSlot.captured
        assertEquals(0, response.items.size)
        assertEquals(1, response.failedCount)
    }

    @Test
    fun `sendSecure is called exactly once`() = runTest {
        coEvery { vaultRepository.getHeaders() } returns emptyList()

        useCase()

        coVerify(exactly = 1) { session.sendSecure(any<SecureResponse.Items>()) }
    }
}
