package com.unshoo.pixelmusic.data.service

import android.app.Notification
import android.content.Context
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList

/**
 * Wraps Media3's default provider while keeping the notification as a regular
 * transport notification.
 *
 * Important: do NOT mark it FLAG_LOCAL_ONLY. That flag prevents the session/
 * notification from being surfaced reliably by external system/companion media
 * surfaces, which breaks current-playing detection in apps like YouTube Music.
 */
@UnstableApi
class LocalOnlyMediaNotificationProvider(
    private val context: Context,
    private val delegate: DefaultMediaNotificationProvider =
        DefaultMediaNotificationProvider.Builder(context).build(),
) : MediaNotification.Provider {

    fun setSmallIcon(iconResId: Int) {
        delegate.setSmallIcon(iconResId)
    }

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        callback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val wrappedCallback = object : MediaNotification.Provider.Callback {
            override fun onNotificationChanged(notification: MediaNotification) {
                notification.notification.category = Notification.CATEGORY_TRANSPORT
                callback.onNotificationChanged(notification)
            }
        }
        val mediaNotification = delegate.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            wrappedCallback
        )
        mediaNotification.notification.category = Notification.CATEGORY_TRANSPORT
        return mediaNotification
    }

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = delegate.handleCustomCommand(session, action, extras)

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
        delegate.getNotificationChannelInfo()
}

