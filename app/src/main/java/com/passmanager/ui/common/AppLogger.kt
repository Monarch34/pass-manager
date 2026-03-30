package com.passmanager.ui.common

import android.util.Log
import com.passmanager.BuildConfig

/**
 * Centralized logging for the UI layer: verbose in debug, quiet in release.
 */
object AppLogger {
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
    }
}
