package com.unshoo.pixelmusic.data.remote.youtube

import android.util.Log
import kotlinx.coroutines.*
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient

/**
 * Manages YouTube Music telemetry (playback pings) to keep
 * YouTube Music history in sync with what PixelMusic plays.
 *
 * Refactored to use "Basic Event Notification" (one-shot ping)
 * instead of continuous heartbeats to avoid bot detection.
 */
class YouTubeTelemetryManager {

    private val TAG = "YouTubeTelemetry"
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile private var isTelemetryEnabled: Boolean = true

    /**
     * Triggered when a new song starts playing.
     * Sends a single 'start playback' ping to YouTube to register the song in history.
     */
    suspend fun onSongChanged(videoId: String, durationMs: Long, playbackUrl: String? = null) {
        if (!isTelemetryEnabled) return

        // Auth check: only send if user is logged in
        if (!YouTube.hasLoginCookie()) {
            Log.w(TAG, "No authenticated user session — skipping telemetry for $videoId")
            return
        }

        Log.d(TAG, "Song changed -> $videoId. Triggering history sync ping...")

        coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                // Use the provided playbackUrl if available, otherwise fallback to cache
                var finalUrl = playbackUrl ?: com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.playbackTrackingCache[videoId]

                if (finalUrl == null) {
                    Log.w(TAG, "No playback tracking URL provided or cached for $videoId — sync will fail")
                    return@runCatching
                }

                // Register playback (The "Metrolist" approach: single one-shot ping)
                YouTube.registerPlayback(
                    playbackTracking = finalUrl,
                    videoId = videoId
                )

                Log.d(TAG, "Successfully registered playback for $videoId")
            }.onFailure { ex ->
                Log.e(TAG, "Failed to sync history for $videoId: ${ex.message}")
            }
        }
    }

    fun onPlaybackStateChanged(playing: Boolean) {
        // No longer needed for basic sync
    }

    fun setTelemetryEnabled(enabled: Boolean) {
        isTelemetryEnabled = enabled
    }

    fun updateProgress(positionMs: Long, durationMs: Long = 0) {
        // Heartbeats removed to avoid bot detection.
        // History is now registered once at the start of the song.
    }

    fun stopTelemetry() {
        // Final pings removed.
    }

    fun destroy() {
        try { coroutineScope.cancel() } catch (_: CancellationException) {}
    }
}
