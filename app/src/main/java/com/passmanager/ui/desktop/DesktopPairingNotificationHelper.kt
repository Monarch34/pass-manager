package com.passmanager.ui.desktop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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
            // Never let this channel expand its content over a secure lock screen.
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * Confirms that a password reached the paired desktop.
     *
     * The vault entry name is deliberately absent from the notification. System notifications are
     * mirrored to the lock screen, to paired watches and to every installed notification-listener
     * app, and VISIBILITY_PRIVATE only hides them when the user opted into hiding sensitive
     * content — which is not the default. Which accounts live in the vault is itself sensitive, and
     * the user just triggered this copy, so the entry name tells them nothing new. It still shows
     * in-app on the desktop link screen (`desktop_link_last_item`).
     *
     * [itemTitle] is kept so callers stay unchanged; it is intentionally not rendered.
     */
    @Suppress("UNUSED_PARAMETER")
    fun notifyPasswordSent(itemTitle: String) {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val title = appContext.getString(R.string.desktop_link_title)
        val text = appContext.getString(R.string.desktop_notification_password_sent_generic)
        // Both versions carry the same copy: the private one is already name-free, and stating the
        // public version explicitly keeps the lock screen safe if that text ever regains detail.
        val publicVersion = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setColor(ContextCompat.getColor(appContext, R.color.logo_teal_light))
            .setContentTitle(title)
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_shield)
            .setColor(ContextCompat.getColor(appContext, R.color.logo_teal_light))
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIFICATION_ID_BASE + notificationCounter.getAndIncrement(), notification)
    }

    companion object {
        private const val CHANNEL_ID = "desktop_pairing"
        private const val NOTIFICATION_ID_BASE = 39000
    }
}
