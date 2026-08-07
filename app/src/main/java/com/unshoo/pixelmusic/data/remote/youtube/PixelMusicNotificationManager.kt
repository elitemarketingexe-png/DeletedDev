package com.unshoo.pixelmusic.data.remote.youtube

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.unshoo.pixelmusic.data.model.youtube.Playlist
import com.unshoo.pixelmusic.data.model.youtube.Song
import kotlin.math.abs

object PixelMusicNotificationManager {
    private lateinit var notificationManager: NotificationManager
    private lateinit var pendingIntent: PendingIntent

    fun init(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannels.entries.forEach {
                val notificationChannel = NotificationChannel(
                    it.channelId,
                    it.channelName,
                    it.importance
                ).apply {
                    description = it.description
                    setShowBadge(false)
                }
                notificationManager.createNotificationChannel(notificationChannel)
            }
        } else {
            PixelMusicHelper.printe("Could not start notification channels because the Android version is too old")
        }
    }

    fun showPlaylistDownloadProgress(
        context: Context,
        playlist: Playlist,
        currentSong: Int,
        totalSongs: Int
    ) {
        if (!::notificationManager.isInitialized) {
            init(context)
        }

        val percent = if (totalSongs > 0) ((currentSong.toFloat() / totalSongs) * 100).toInt().coerceIn(0, 100) else 0
        val contentText = "$currentSong of $totalSongs songs ($percent%)"

        val builder = getBaseNotification(context, NotificationChannels.PLAYLIST_DOWNLOAD)
            .setContentTitle(playlist.info.title)
            .setContentText(contentText)
            .setSubText("Pixel Music Offline Download")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(totalSongs, currentSong, false)
            .setOngoing(true)
            .setGroup(NotificationChannels.PLAYLIST_DOWNLOAD.group)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        val notification = builder.build()

        // Android 16 Live Updates: render download progress as an island-style status-bar pill.
        com.unshoo.pixelmusic.data.service.LiveUpdateUtil.promote(notification)

        notificationManager.notify(getNotificationID(playlist.info.id), notification)
        updateGroupSummary(context)
    }

    fun showPlaylistDownloadSuccess(
        context: Context,
        playlist: Playlist,
    ) {
        if (!::notificationManager.isInitialized) {
            init(context)
        }

        val id = getNotificationID(playlist.info.id)
        notificationManager.cancel(id)

        val notification = getBaseNotification(context, NotificationChannels.PLAYLIST_DOWNLOAD)
            .setContentTitle(playlist.info.title)
            .setContentText("Playlist saved for offline listening")
            .setSubText("Download complete")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setGroup(NotificationChannels.PLAYLIST_DOWNLOAD.group)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(id, notification)
        updateGroupSummary(context)
    }

    fun showPlaylistDownloadFailure(
        context: Context,
        playlist: Playlist,
    ) {
        if (!::notificationManager.isInitialized) {
            init(context)
        }

        val id = getNotificationID(playlist.info.id)
        notificationManager.cancel(id)

        val notification = getBaseNotification(context, NotificationChannels.PLAYLIST_DOWNLOAD)
            .setContentTitle("Download issue")
            .setContentText("Failed to download ${playlist.info.title}")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setGroup(NotificationChannels.PLAYLIST_DOWNLOAD.group)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(id, notification)
        updateGroupSummary(context)
    }

    fun showPlaylistDownloadCanceled(
        context: Context,
        playlist: Playlist
    ) {
        cancelPlaylistDownloadNotification(context, playlist.info.id)
    }

    fun cancelPlaylistDownloadNotification(context: Context, playlistId: String) {
        if (!::notificationManager.isInitialized) {
            init(context)
        }
        try {
            val id = getNotificationID(playlistId)
            notificationManager.cancel(id)
            notificationManager.cancel(0)
        } catch (_: Exception) {}
    }

    private fun updateGroupSummary(context: Context) {
        val summaryNotification =
            getBaseNotification(context, NotificationChannels.PLAYLIST_DOWNLOAD)
                .setContentTitle("Pixel Music Downloads")
                .setContentText("Downloads summary")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setGroup(NotificationChannels.PLAYLIST_DOWNLOAD.group)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

        notificationManager.notify(0, summaryNotification)
    }

    fun showSongDownloadFailed(
        context: Context,
        song: Song,
    ) {
        if (!::notificationManager.isInitialized) {
            init(context)
        }

        val notification = getBaseNotification(context, NotificationChannels.SONG_DOWNLOAD)
            .setContentTitle("Download issue")
            .setContentText("Failed to download ${song.title} - ${song.artist}")
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .setGroup(NotificationChannels.SONG_DOWNLOAD.group)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(getNotificationID(song.youtubeId), notification)
    }

    fun showSongDownloadSuccess(
        context: Context,
        song: Song,
    ) {
        if (!::notificationManager.isInitialized) {
            init(context)
        }

        val notification = getBaseNotification(context, NotificationChannels.SONG_DOWNLOAD)
            .setContentTitle(song.title)
            .setContentText("Song saved for offline listening")
            .setSubText(song.artist)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setGroup(NotificationChannels.SONG_DOWNLOAD.group)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(getNotificationID(song.youtubeId), notification)
    }

    private fun getBaseNotification(
        context: Context,
        channel: NotificationChannels
    ): NotificationCompat.Builder {
        return NotificationCompat.Builder(
            context,
            channel.channelId
        )
            .setContentIntent(pendingIntent)
    }

    private fun getNotificationID(id: String): Int {
        return 1000 + abs(id.hashCode() and 0x7fffffff)
    }

    private enum class NotificationChannels(
        val channelId: String,
        val channelName: String,
        val description: String,
        val importance: Int,
        val group: String
    ) {
        PLAYLIST_DOWNLOAD(
            channelId = "playlist_progress",
            channelName = "Playlist Download Progress",
            description = "Shows live progress and completion notifications for playlist downloads",
            importance = NotificationManager.IMPORTANCE_LOW,
            group = "PLAYLIST_GROUP"
        ),

        SONG_DOWNLOAD(
            channelId = "song_alerts",
            channelName = "Song Download Alerts",
            description = "Notifies about individual song download issues during playlist downloads",
            importance = NotificationManager.IMPORTANCE_DEFAULT,
            group = "SONG_GROUP"
        );
    }
}
