package com.passmanager.domain.usecase

import com.passmanager.crypto.model.EncryptedData
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.model.VaultItem
import com.passmanager.domain.port.DesktopPairingPort
import com.passmanager.domain.repository.VaultRepository
import com.passmanager.protocol.SecureResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import com.passmanager.domain.exception.DesktopRateLimitException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendPasswordToDesktopUseCaseTest {

    private val vaultRepository = mockk<VaultRepository>()
    private val decryptPasswordBytesUseCase = mockk<DecryptPasswordBytesUseCase>()
    private val session = mockk<DesktopPairingPort>(relaxed = true)

    private val useCase = SendPasswordToDesktopUseCase(
        vaultRepository, decryptPasswordBytesUseCase, session
    )

    private val fakeItem = VaultItem(
        id = "item-1",
        encryptedData = EncryptedData(ByteArray(32), ByteArray(12)),
        keyVersion = 1, createdAt = 0L, updatedAt = 0L,
        category = ItemCategory.LOGIN
    )

    @Test
    fun `successful send returns title and sends password response`() = runTest {
        every { session.canSendPassword() } returns true
        coEvery { vaultRepository.getById("item-1") } returns fakeItem
        coEvery { decryptPasswordBytesUseCase(fakeItem) } returns
            DecryptPasswordBytesUseCase.Result(
                passwordBytes = "secret".toByteArray(),
                title = "GitHub"
            )

        val title = useCase("item-1")

        assertEquals("GitHub", title)
        coVerify(exactly = 1) { session.sendSecure(any<SecureResponse.Password>()) }
        coVerify(exactly = 1) { session.recordPasswordSent("GitHub") }
    }

    @Test(expected = DesktopRateLimitException::class)
    fun `rate limited throws DesktopRateLimitException and sends rate limited response`() = runTest {
        every { session.canSendPassword() } returns false

        useCase("item-1")
    }

    @Test
    fun `rate limited sends RateLimited response to desktop`() = runTest {
        every { session.canSendPassword() } returns false

        try {
            useCase("item-1")
        } catch (_: DesktopRateLimitException) {}

        coVerify(exactly = 1) { session.sendSecure(any<SecureResponse.RateLimited>()) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `item not found throws IllegalArgumentException`() = runTest {
        every { session.canSendPassword() } returns true
        coEvery { vaultRepository.getById(any()) } returns null

        useCase("missing-id")
    }

    @Test
    fun `password bytes are zeroed after send`() = runTest {
        val passwordBytes = "secret".toByteArray()
        every { session.canSendPassword() } returns true
        coEvery { vaultRepository.getById("item-1") } returns fakeItem
        coEvery { decryptPasswordBytesUseCase(fakeItem) } returns
            DecryptPasswordBytesUseCase.Result(
                passwordBytes = passwordBytes,
                title = "GitHub"
            )

        useCase("item-1")

        // The original passwordBytes returned by the use case should be zeroed
        assertTrue(passwordBytes.all { it == 0.toByte() })
    }
}
