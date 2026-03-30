package com.passmanager.domain.usecase

import com.passmanager.crypto.cipher.AesGcmCipher
import com.passmanager.crypto.model.EncryptedData
import com.passmanager.crypto.model.KdfParams
import com.passmanager.domain.model.ItemPayload
import com.passmanager.domain.model.VaultMetadata
import com.passmanager.domain.port.VaultKeyProvider
import com.passmanager.domain.repository.MetadataRepository
import com.passmanager.domain.repository.VaultRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SaveVaultItemUseCaseTest {

    private val vaultRepository = mockk<VaultRepository>(relaxed = true)
    private val metadataRepository = mockk<MetadataRepository>()
    private val cipher = mockk<AesGcmCipher>()
    private val vaultKeyProvider = mockk<VaultKeyProvider>()

    private val useCase = SaveVaultItemUseCase(
        vaultRepository, metadataRepository, cipher, vaultKeyProvider
    )

    private val fakeEncryptedData = EncryptedData(ByteArray(32), ByteArray(12))

    private val fakeMetadata = VaultMetadata(
        currentKeyVersion = 1,
        wrappedVaultKey = fakeEncryptedData,
        kdfSalt = ByteArray(16),
        kdfParams = KdfParams(),
        biometricEnabled = false,
        biometricWrappedKey = null
    )

    @Before
    fun setup() {
        coEvery { metadataRepository.get() } returns fakeMetadata
        every { vaultKeyProvider.requireUnlockedKey() } returns ByteArray(32)
        every { cipher.encrypt(any(), any()) } returns fakeEncryptedData
    }

    @Test
    fun `new item calls insert and returns generated id`() = runTest {
        val payload = ItemPayload.Login(id = "", title = "GitHub", password = "secret")

        val itemId = useCase(payload, existingId = null)

        assertEquals(36, itemId.length) // UUID length
        coVerify(exactly = 1) { vaultRepository.insert(id = itemId, any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { vaultRepository.update(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `existing item calls update with same id`() = runTest {
        val existingId = "existing-id-123"
        val payload = ItemPayload.Login(id = existingId, title = "GitHub", password = "secret")

        val returnedId = useCase(payload, existingId = existingId)

        assertEquals(existingId, returnedId)
        coVerify(exactly = 1) { vaultRepository.update(id = existingId, any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { vaultRepository.insert(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `login with address results in three encrypt calls`() = runTest {
        val payload = ItemPayload.Login(
            id = "", title = "GitHub",
            address = "github.com", password = "secret"
        )

        useCase(payload, existingId = null)

        // payload + title + address = 3 encrypt calls
        coVerify(exactly = 3) { cipher.encrypt(any(), any()) }
    }

    @Test
    fun `secure note with empty notes results in two encrypt calls`() = runTest {
        val payload = ItemPayload.SecureNote(id = "", title = "My Note", notes = "")

        useCase(payload, existingId = null)

        // payload + title = 2 encrypt calls (no address because listSubtitle is empty)
        coVerify(exactly = 2) { cipher.encrypt(any(), any()) }
    }

    @Test
    fun `note with content results in three encrypt calls`() = runTest {
        val payload = ItemPayload.SecureNote(id = "", title = "My Note", notes = "Some note content")

        useCase(payload, existingId = null)

        // payload + title + notes-as-subtitle = 3 encrypt calls
        coVerify(exactly = 3) { cipher.encrypt(any(), any()) }
    }
}
