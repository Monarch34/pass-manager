package com.passmanager.ui.item

import androidx.lifecycle.SavedStateHandle
import com.passmanager.domain.model.ItemCategory
import com.passmanager.domain.usecase.DecryptItemUseCase
import com.passmanager.domain.usecase.GetVaultItemByIdUseCase
import com.passmanager.domain.usecase.SaveVaultItemUseCase
import com.passmanager.domain.validation.AddEditItemSaveValidator
import com.passmanager.domain.validation.BankPasswordValidator
import com.passmanager.navigation.Screen
import com.passmanager.test.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditItemViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `login form can save when title and password set`() = runTest {
        val saveUseCase = mockk<SaveVaultItemUseCase>(relaxed = true)
        val bankValidator = BankPasswordValidator()
        val saveValidator = AddEditItemSaveValidator(bankValidator)

        val vm = AddEditItemViewModel(
            saveVaultItemUseCase = saveUseCase,
            decryptItemUseCase = mockk(),
            getVaultItemByIdUseCase = mockk(),
            bankPasswordValidator = bankValidator,
            addEditItemSaveValidator = saveValidator,
            savedStateHandle = SavedStateHandle()
        )

        vm.onTitleChange("My login")
        vm.onPasswordChange("secret")
        val state = vm.uiState.first { it.canSave }
        assertEquals(ItemCategory.LOGIN, state.category)
    }

    @Test
    fun `save invokes use case for new item`() = runTest {
        val saveUseCase = mockk<SaveVaultItemUseCase>(relaxed = true)
        coEvery { saveUseCase(any(), any()) } returns "new-id"

        val bankValidator = BankPasswordValidator()
        val saveValidator = AddEditItemSaveValidator(bankValidator)

        val vm = AddEditItemViewModel(
            saveVaultItemUseCase = saveUseCase,
            decryptItemUseCase = mockk(),
            getVaultItemByIdUseCase = mockk(),
            bankPasswordValidator = bankValidator,
            addEditItemSaveValidator = saveValidator,
            savedStateHandle = SavedStateHandle()
        )

        vm.onTitleChange("T")
        vm.onPasswordChange("secret")
        vm.uiState.first { it.canSave }
        vm.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { saveUseCase(any(), any()) }
        assertTrue(vm.uiState.first { it.isSaved }.isSaved)
    }

    @Test
    fun `nav placeholder new does not load vault and applies initial category`() = runTest {
        val getById = mockk<GetVaultItemByIdUseCase>(relaxed = true)
        val bankValidator = BankPasswordValidator()
        val saveValidator = AddEditItemSaveValidator(bankValidator)

        val vm = AddEditItemViewModel(
            saveVaultItemUseCase = mockk(relaxed = true),
            decryptItemUseCase = mockk(),
            getVaultItemByIdUseCase = getById,
            bankPasswordValidator = bankValidator,
            addEditItemSaveValidator = saveValidator,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "itemId" to Screen.AddEditItem.NEW_ITEM_ROUTE_ID,
                    "initialCategory" to "identity",
                ),
            ),
        )

        advanceUntilIdle()

        coVerify(exactly = 0) { getById(any()) }
        // WhileSubscribed: uiState.value stays at initial until something collects the flow.
        val state = vm.uiState.first { it.category == ItemCategory.IDENTITY }
        assertEquals(ItemCategory.IDENTITY, state.category)
    }
}
