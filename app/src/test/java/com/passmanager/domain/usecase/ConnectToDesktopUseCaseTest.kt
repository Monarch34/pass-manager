package com.passmanager.domain.usecase

import com.passmanager.domain.port.DesktopPairingPort
import com.passmanager.protocol.PROTOCOL_V1
import com.passmanager.protocol.PairingQrPayload
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectToDesktopUseCaseTest {

    private val session = mockk<DesktopPairingPort>(relaxed = true)
    private val useCase = ConnectToDesktopUseCase(session)

    private fun payload(v: Int = PROTOCOL_V1, token: String = "tok123") = PairingQrPayload(
        v = v, ip = "192.168.1.10", port = 8765,
        pub = "base64pubkey==", token = token
    )

    @Test
    fun `valid version connects successfully`() = runTest {
        coEvery { session.connectAndPair(any<PairingQrPayload>()) } returns Unit

        val result = useCase(payload(v = PROTOCOL_V1))

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { session.connectAndPair(any<PairingQrPayload>()) }
    }

    @Test
    fun `version too high returns failure`() = runTest {
        val result = useCase(payload(v = PROTOCOL_V1 + 1))

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { session.connectAndPair(any<PairingQrPayload>()) }
    }

    @Test
    fun `version too low returns failure`() = runTest {
        val result = useCase(payload(v = PROTOCOL_V1 - 1))

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { session.connectAndPair(any<PairingQrPayload>()) }
    }

    @Test
    fun `blank token returns failure`() = runTest {
        val result = useCase(payload(token = "   "))

        assertTrue(result.isFailure)
    }

    @Test
    fun `pairing exception calls abortPairing when session is still pairing`() = runTest {
        val pairingStateFlow = kotlinx.coroutines.flow.MutableStateFlow<com.passmanager.domain.model.PairingSessionState>(
            com.passmanager.domain.model.PairingSessionState.Pairing
        )
        every { session.pairingState } returns pairingStateFlow
        coEvery { session.connectAndPair(any<PairingQrPayload>()) } throws RuntimeException("Network error")

        val result = useCase(payload())

        assertTrue(result.isFailure)
        coVerify(exactly = 1) { session.abortPairing(any()) }
    }

    @Test
    fun `pairing exception skips abortPairing when session is not pairing`() = runTest {
        val pairingStateFlow = kotlinx.coroutines.flow.MutableStateFlow<com.passmanager.domain.model.PairingSessionState>(
            com.passmanager.domain.model.PairingSessionState.Idle
        )
        every { session.pairingState } returns pairingStateFlow
        coEvery { session.connectAndPair(any<PairingQrPayload>()) } throws RuntimeException("Network error")

        val result = useCase(payload())

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { session.abortPairing(any()) }
    }
}
