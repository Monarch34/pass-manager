package com.passmanager.domain.usecase

import com.passmanager.domain.port.AppSettingsPort
import com.passmanager.domain.port.ElapsedRealtimeSource
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Back-pressure on repeated failed passphrase unlocks.
 *
 * Argon2id already puts a hard floor of roughly a second on every guess, which makes online
 * guessing slow. This makes it slower still, and — more usefully — makes a long run of guesses
 * visibly expensive rather than quietly free.
 *
 * Two properties carry the security here:
 *
 * 1. **The counter is incremented, and the write awaited, before the derivation starts.** Bumping
 *    it afterwards would leave a one-second window per attempt in which every concurrent caller
 *    reads the same pre-increment count, and a burst of parallel attempts would cost one tick
 *    between them. [AppSettingsPort.recordFailedUnlockAttempt] does the increment inside
 *    DataStore's own transaction so two racing attempts cannot collapse into one.
 * 2. **The wait is measured from [ElapsedRealtimeSource], never the wall clock.** Wall time is
 *    user-settable; a lockout derived from it disappears the moment the device clock moves.
 *
 * `elapsedRealtime` restarts at zero on boot, so a reading behind the stored anchor can only mean
 * the device rebooted, and the penalty is treated as served. That is a deliberate concession, not
 * an oversight: rebooting to shave at most a minute off a wait, when every single guess still
 * costs a full Argon2 derivation, is a bad trade for an attacker and a fine escape hatch for a
 * user who has genuinely locked themselves out.
 */
class UnlockThrottle @Inject constructor(
    private val appSettings: AppSettingsPort,
    private val clock: ElapsedRealtimeSource
) {

    companion object {
        /** The first four failures are free; the fifth is the first to cost anything. */
        const val FREE_ATTEMPTS = 4

        /** Beyond this the wait stops growing — long enough to hurt, short enough to survive. */
        const val MAX_DELAY_SECONDS = 60L

        /** 5 → 2s, 6 → 4s, 7 → 8s, 8 → 16s, 9 → 32s, 10 and beyond → 60s. */
        fun delaySecondsFor(attempts: Int): Long {
            if (attempts <= FREE_ATTEMPTS) return 0L
            val exponent = attempts - FREE_ATTEMPTS
            // Guarded before the shift: 2^6 already exceeds the ceiling, and a large exponent
            // would otherwise overflow into a negative delay.
            if (exponent >= 6) return MAX_DELAY_SECONDS
            return minOf(1L shl exponent, MAX_DELAY_SECONDS)
        }
    }

    /** Milliseconds the caller must still wait, or 0 if an attempt is allowed right now. */
    suspend fun remainingLockoutMs(): Long {
        val attempts = appSettings.failedUnlockAttempts.first()
        val penaltyMs = delaySecondsFor(attempts) * 1000L
        if (penaltyMs == 0L) return 0L

        val anchor = appSettings.unlockLockoutAnchorMs.first()
        val now = clock.elapsedRealtimeMs()
        // Monotonic clock running behind the anchor: the device rebooted, so the anchor belongs to
        // an uptime that no longer exists and the penalty cannot be measured against it.
        if (now < anchor) return 0L

        return (anchor + penaltyMs - now).coerceAtLeast(0L)
    }

    /**
     * Books an attempt as failed and returns the new count. Called *before* the derivation, so an
     * attempt that is abandoned halfway still counts.
     */
    suspend fun registerAttempt(): Int =
        appSettings.recordFailedUnlockAttempt(clock.elapsedRealtimeMs())

    /** Any successful unlock wipes the penalty — the point was to slow down guessing. */
    suspend fun clear() {
        appSettings.clearFailedUnlockAttempts()
    }
}
