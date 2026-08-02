package com.unshoo.pixelmusic.data.service

import android.app.Notification
import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
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
 * Adopted from the PixelMusic fork: the notification is marked FLAG_LOCAL_ONLY so
 * it is not bridged to Wear OS as a generic remote-media control, and the ongoing
 * flag is force-applied while music is actively playing (Android 14+ fix).
 *
 * While actively playing, the notification is also promoted as an Android 16
 * "Live Update" (see [LiveUpdateUtil]) so the system renders it as an
 * iOS-Dynamic-Island-style pill in the status bar.
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
                val isActivelyPlaying = mediaSession.player.playWhenReady &&
                    mediaSession.player.playbackState == Player.STATE_READY

                notification.notification.flags =
                    notification.notification.flags or Notification.FLAG_LOCAL_ONLY
                notification.notification.category = Notification.CATEGORY_TRANSPORT

                // Android 14+ fix: force the ongoing flag while music is actively playing.
                if (isActivelyPlaying) {
                    notification.notification.flags =
                        notification.notification.flags or Notification.FLAG_ONGOING_EVENT
                }

                // Android 16 Live Updates: island-style status-bar pill while playing.
                LiveUpdateUtil.promoteIf(notification.notification, isActivelyPlaying)

                callback.onNotificationChanged(notification)
            }
        }
        val mediaNotification = delegate.createNotification(
            mediaSession,
            customLayout,
            actionFactory,
            wrappedCallback
        )
        mediaNotification.notification.flags =
            mediaNotification.notification.flags or Notification.FLAG_LOCAL_ONLY
        mediaNotification.notification.category = Notification.CATEGORY_TRANSPORT

        val isActivelyPlaying = mediaSession.player.playWhenReady &&
            mediaSession.player.playbackState == Player.STATE_READY
        if (isActivelyPlaying) {
            mediaNotification.notification.flags =
                mediaNotification.notification.flags or Notification.FLAG_ONGOING_EVENT
        }
        LiveUpdateUtil.promoteIf(mediaNotification.notification, isActivelyPlaying)
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

