package com.passmanager.domain.port

/**
 * Milliseconds since boot, including deep sleep — `SystemClock.elapsedRealtime()`.
 *
 * The unlock throttle measures its penalty against this and never against the wall clock. Wall
 * time is user-settable, so a lockout derived from it evaporates the moment someone moves the
 * device clock forward. This one only moves forward, at one second per second, and nothing in
 * the settings UI can touch it.
 *
 * A port so tests can drive time directly; implemented by
 * [com.passmanager.data.time.SystemElapsedRealtimeSource].
 */
interface ElapsedRealtimeSource {
    fun elapsedRealtimeMs(): Long
}
