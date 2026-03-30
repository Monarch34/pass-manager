package com.passmanager.domain.usecase

import com.passmanager.domain.model.ItemCategory
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SeedDemoVaultItemsUseCaseTest {

    @Test
    fun `invoke saves six items per category`() = runTest {
        val save = mockk<SaveVaultItemUseCase>(relaxed = true)
        val useCase = SeedDemoVaultItemsUseCase(save)

        val count = useCase()

        val expected = ItemCategory.entries.size * SeedDemoVaultItemsUseCase.ITEMS_PER_CATEGORY
        assertEquals(expected, count)
        coVerify(exactly = expected) { save(any(), any()) }
    }
}
