package com.passmanager.data.time

import android.os.SystemClock
import com.passmanager.domain.port.ElapsedRealtimeSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemElapsedRealtimeSource @Inject constructor() : ElapsedRealtimeSource {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}
