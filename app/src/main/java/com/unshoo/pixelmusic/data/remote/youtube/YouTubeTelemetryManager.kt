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
                val sanitizedVideoId = videoId.removePrefix("youtube_")
                
                // 1. Try provided URL or cache immediately
                var finalUrl = playbackUrl ?: com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.playbackTrackingCache[sanitizedVideoId]

                // 2. If null, wait for the playback resolution flow to populate the cache (up to 3 seconds)
                if (finalUrl == null) {
                    Log.d(TAG, "Tracking URL not found for $sanitizedVideoId, waiting for playback resolve...")
                    for (i in 1..15) {
                        kotlinx.coroutines.delay(200L)
                        finalUrl = com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.playbackTrackingCache[sanitizedVideoId]
                        if (finalUrl != null) {
                            Log.d(TAG, "Tracking URL found in cache after ${i * 200}ms")
                            break
                        }
                    }
                }

                // 3. Final Fallback: if still null, do one single lightweight probe to get the URL
                if (finalUrl == null) {
                    Log.d(TAG, "Tracking URL still null for $sanitizedVideoId, performing one-shot probe...")
                    val signatureTimestamp = unshoo.ianshulyadav.pixelmusic.innertube.NewPipeUtils.getSignatureTimestamp(sanitizedVideoId).getOrNull()
                    val playerRes = YouTube.player(
                        videoId = sanitizedVideoId,
                        playlistId = null,
                        client = unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.WEB_REMIX,
                        signatureTimestamp = signatureTimestamp,
                        setLogin = true
                    ).getOrNull()
                    finalUrl = playerRes?.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                        ?: playerRes?.playbackTracking?.videostatsWatchtimeUrl?.baseUrl
                    
                    // Update cache for future use
                    finalUrl?.let { com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.playbackTrackingCache[sanitizedVideoId] = it }
                }

                if (finalUrl == null) {
                    Log.w(TAG, "No playback tracking URL found for $sanitizedVideoId after wait and probe — sync will fail")
                    return@runCatching
                }

                // Register playback (The "Metrolist" approach: single one-shot ping)
                YouTube.registerPlayback(
                    playbackTracking = finalUrl,
                    videoId = sanitizedVideoId
                )

                Log.d(TAG, "Successfully registered playback for $sanitizedVideoId")
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
