package com.passmanager.ui.desktop

import android.app.NotificationManager
import android.content.Context
import com.passmanager.domain.model.LockState
import com.passmanager.domain.model.PairingSessionState
import com.passmanager.domain.port.LockStateProvider
import com.passmanager.domain.usecase.ConnectToDesktopUseCase
import com.passmanager.domain.usecase.SendItemListToDesktopUseCase
import com.passmanager.domain.usecase.SendPasswordToDesktopUseCase
import com.passmanager.security.DesktopPairingSession
import com.passmanager.test.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopLinkViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun verifying() = PairingSessionState.Verifying(
        code = "123456",
        desktopIp = "10.0.0.1"
    )

    private fun active(pw: Int = 0, title: String? = null) = PairingSessionState.Active(
        desktopIp = "10.0.0.1",
        passwordsSent = pw,
        lastItemTitle = title
    )

    @Test
    fun `sendItemList runs once when transitioning to Active`() = runTest {
        val sessionFlow = MutableStateFlow<PairingSessionState>(PairingSessionState.Idle)
        val pairingSession = mockk<DesktopPairingSession>(relaxed = true)
        every { pairingSession.state } returns sessionFlow

        val sendList = mockk<SendItemListToDesktopUseCase>(relaxed = true)
        coEvery { sendList() } returns Unit

        val appContext = mockk<Context>(relaxed = true)
        every { appContext.getSystemService(Context.NOTIFICATION_SERVICE) } returns mockk<NotificationManager>(relaxed = true)

        val lockState = mockk<LockStateProvider>()
        every { lockState.lockState } returns MutableStateFlow(LockState.Unlocked)

        val requestHandler = mockk<DesktopRequestHandler>(relaxed = true)

        DesktopLinkViewModel(
            appContext = appContext,
            pairingSession = pairingSession,
            connectToDesktopUseCase = mockk(relaxed = true),
            sendItemListToDesktopUseCase = sendList,
            lockStateProvider = lockState,
            requestHandler = requestHandler
        )

        sessionFlow.value = verifying()
        advanceUntilIdle()
        coVerify(exactly = 0) { sendList.invoke() }

        sessionFlow.value = active()
        advanceUntilIdle()
        coVerify(exactly = 1) { sendList.invoke() }

        sessionFlow.value = active(1, "x")
        advanceUntilIdle()
        coVerify(exactly = 1) { sendList.invoke() }
    }

    @Test
    fun `vault lock state is reflected in ui`() = runTest {
        val sessionFlow = MutableStateFlow<PairingSessionState>(PairingSessionState.Idle)
        val pairingSession = mockk<DesktopPairingSession>(relaxed = true)
        every { pairingSession.state } returns sessionFlow

        val lockFlow = MutableStateFlow<LockState>(LockState.Unlocked)
        val lockState = mockk<LockStateProvider>()
        every { lockState.lockState } returns lockFlow

        val appContext = mockk<Context>(relaxed = true)
        every { appContext.getSystemService(Context.NOTIFICATION_SERVICE) } returns mockk<NotificationManager>(relaxed = true)

        val vm = DesktopLinkViewModel(
            appContext = appContext,
            pairingSession = pairingSession,
            connectToDesktopUseCase = mockk(relaxed = true),
            sendItemListToDesktopUseCase = mockk(relaxed = true),
            lockStateProvider = lockState,
            requestHandler = mockk(relaxed = true)
        )

        assertEquals(true, vm.uiState.value.vaultUnlocked)
        lockFlow.value = LockState.ColdLocked
        advanceUntilIdle()
        assertEquals(false, vm.uiState.value.vaultUnlocked)
    }
}
