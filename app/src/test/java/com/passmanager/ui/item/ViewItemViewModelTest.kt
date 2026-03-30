package com.passmanager.ui.item

import androidx.lifecycle.SavedStateHandle
import com.passmanager.R
import com.passmanager.domain.port.AppSettingsPort
import com.passmanager.domain.usecase.DecryptItemUseCase
import com.passmanager.domain.usecase.DeleteVaultItemByIdUseCase
import com.passmanager.domain.usecase.ObserveVaultItemByIdUseCase
import com.passmanager.test.MainDispatcherRule
import com.passmanager.ui.common.UserMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewItemViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loadForItem sets not found when item missing`() = runTest {
        val observe = mockk<ObserveVaultItemByIdUseCase>()
        every { observe("x") } returns flowOf(null)

        val decrypt = mockk<DecryptItemUseCase>()
        val delete = mockk<DeleteVaultItemByIdUseCase>()
        val appSettings = mockk<AppSettingsPort>()
        every { appSettings.useGoogleFavicons } returns MutableStateFlow(false)

        val vm = ViewItemViewModel(SavedStateHandle(), decrypt, observe, delete, appSettings)
        vm.loadForItem("x")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals(UserMessage.Resource(R.string.item_not_found), vm.uiState.value.error)
    }

    @Test
    fun `delete invokes use case for loaded item id`() = runTest {
        val observe = mockk<ObserveVaultItemByIdUseCase>()
        every { observe(any()) } returns flowOf(null)

        val decrypt = mockk<DecryptItemUseCase>()
        val delete = mockk<DeleteVaultItemByIdUseCase>()
        coEvery { delete(any()) } returns Unit

        val appSettings = mockk<AppSettingsPort>()
        every { appSettings.useGoogleFavicons } returns MutableStateFlow(false)

        val vm = ViewItemViewModel(SavedStateHandle(), decrypt, observe, delete, appSettings)
        vm.loadForItem("id")
        advanceUntilIdle()
        vm.delete()
        advanceUntilIdle()

        coVerify(exactly = 1) { delete("id") }
    }

    @Test
    fun `togglePasswordVisible flips flag`() = runTest {
        val observe = mockk<ObserveVaultItemByIdUseCase>()
        every { observe(any()) } returns flowOf(null)
        val appSettings = mockk<AppSettingsPort>()
        every { appSettings.useGoogleFavicons } returns MutableStateFlow(false)

        val vm = ViewItemViewModel(SavedStateHandle(), mockk(), observe, mockk(), appSettings)
        vm.loadForItem("id")
        advanceUntilIdle()

        assertFalse(vm.uiState.value.passwordVisible)
        vm.togglePasswordVisible()
        assertTrue(vm.uiState.value.passwordVisible)
    }
}
