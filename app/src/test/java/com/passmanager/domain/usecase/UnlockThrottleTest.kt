package com.passmanager.domain.usecase

import com.passmanager.domain.port.AppSettingsPort
import com.passmanager.domain.port.ElapsedRealtimeSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class UnlockThrottleTest {

    private class FakeClock(var nowMs: Long = 0L) : ElapsedRealtimeSource {
        override fun elapsedRealtimeMs(): Long = nowMs
    }

    private fun settings(attempts: Int, anchorMs: Long): AppSettingsPort =
        mockk<AppSettingsPort>(relaxed = true).also {
            every { it.failedUnlockAttempts } returns flowOf(attempts)
            every { it.unlockLockoutAnchorMs } returns flowOf(anchorMs)
        }

    // ── The curve ────────────────────────────────────

    @Test
    fun `the first four failures are free`() {
        for (attempts in 0..4) {
            assertEquals("attempts=$attempts", 0L, UnlockThrottle.delaySecondsFor(attempts))
        }
    }

    @Test
    fun `the delay doubles from the fifth failure and stops at sixty seconds`() {
        assertEquals(2L, UnlockThrottle.delaySecondsFor(5))
        assertEquals(4L, UnlockThrottle.delaySecondsFor(6))
        assertEquals(8L, UnlockThrottle.delaySecondsFor(7))
        assertEquals(16L, UnlockThrottle.delaySecondsFor(8))
        assertEquals(32L, UnlockThrottle.delaySecondsFor(9))
        assertEquals(60L, UnlockThrottle.delaySecondsFor(10))
        assertEquals(60L, UnlockThrottle.delaySecondsFor(11))
    }

    @Test
    fun `an absurd failure count neither overflows nor goes negative`() {
        // 1L shl 64 wraps; the guard has to come before the shift, not after.
        assertEquals(60L, UnlockThrottle.delaySecondsFor(100))
        assertEquals(60L, UnlockThrottle.delaySecondsFor(Int.MAX_VALUE))
    }

    // ── Remaining time ───────────────────────────────

    @Test
    fun `no penalty below the threshold`() = runTest {
        val throttle = UnlockThrottle(settings(attempts = 4, anchorMs = 1_000), FakeClock(1_000))

        assertEquals(0L, throttle.remainingLockoutMs())
    }

    @Test
    fun `the wait counts down from the anchor`() = runTest {
        val clock = FakeClock(10_000)
        val throttle = UnlockThrottle(settings(attempts = 5, anchorMs = 10_000), clock)

        assertEquals(2_000L, throttle.remainingLockoutMs())

        clock.nowMs = 11_500
        assertEquals(500L, throttle.remainingLockoutMs())

        clock.nowMs = 12_000
        assertEquals(0L, throttle.remainingLockoutMs())

        clock.nowMs = 99_000
        assertEquals(0L, throttle.remainingLockoutMs())
    }

    @Test
    fun `moving the wall clock cannot shorten the wait`() = runTest {
        // The throttle never reads wall time; the only clock it has is monotonic. This test exists
        // to document the property — there is no wall clock to move here, which is the point.
        val clock = FakeClock(10_000)
        val throttle = UnlockThrottle(settings(attempts = 9, anchorMs = 10_000), clock)

        assertEquals(32_000L, throttle.remainingLockoutMs())

        // Uptime advances by a second; the remaining wait drops by exactly a second.
        clock.nowMs = 11_000
        assertEquals(31_000L, throttle.remainingLockoutMs())
    }

    @Test
    fun `a reboot clears the penalty`() = runTest {
        // elapsedRealtime restarts near zero on boot, so a reading behind the anchor can only mean
        // the device rebooted and the anchor belongs to an uptime that no longer exists.
        val clock = FakeClock(nowMs = 500)
        val throttle = UnlockThrottle(settings(attempts = 10, anchorMs = 5_000_000), clock)

        assertEquals(0L, throttle.remainingLockoutMs())
    }

    // ── Bookkeeping ──────────────────────────────────

    @Test
    fun `registering an attempt stamps the current monotonic time`() = runTest {
        val clock = FakeClock(7_777)
        val settings = mockk<AppSettingsPort>(relaxed = true)
        coEvery { settings.recordFailedUnlockAttempt(any()) } returns 5

        val count = UnlockThrottle(settings, clock).registerAttempt()

        assertEquals(5, count)
        io.mockk.coVerify(exactly = 1) { settings.recordFailedUnlockAttempt(7_777) }
    }

    @Test
    fun `clearing wipes the counter`() = runTest {
        val settings = mockk<AppSettingsPort>(relaxed = true)

        UnlockThrottle(settings, FakeClock()).clear()

        io.mockk.coVerify(exactly = 1) { settings.clearFailedUnlockAttempts() }
    }
}
