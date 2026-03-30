package com.passmanager.ui.desktop

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.passmanager.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Desktop pairing notifications (channel creation + password-sent alerts).
 * Keeps [DesktopLinkViewModel] focused on session orchestration.
 */
@Singleton
class DesktopPairingNotificationHelper @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    private val notificationCounter = AtomicInteger(0)

    fun ensureChannelCreated() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.desktop_pairing_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = appContext.getString(R.string.desktop_pairing_channel_desc)
        }
        nm.createNotificationChannel(channel)
    }

    fun notifyPasswordSent(itemTitle: String) {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(appContext.getString(R.string.desktop_link_title))
            .setContentText(
                appContext.getString(R.string.desktop_notification_password_sent, itemTitle)
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID_BASE + notificationCounter.getAndIncrement(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "desktop_pairing"
        private const val NOTIFICATION_ID_BASE = 39000
    }
}
