package com.unshoo.pixelmusic.data.remote.youtube

import android.util.Log
import kotlinx.coroutines.*
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube

/**
 * Manages YouTube Music telemetry (playback pings + watchtime heartbeats) to keep
 * YouTube Music history in sync with what PixelMusic plays.
 *
 * Uses the innertube [YouTube] singleton directly — no separate auth abstraction needed.
 * All HTTP pings are delegated to [YouTube.sendTelemetryPing], which handles cookie,
 * SAPISIDHASH Authorization, and domain forcing (music.youtube.com) internally.
 */
class YouTubeTelemetryManager {

    private val TAG = "YouTubeTelemetry"
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var fetchJob: Job? = null

    // State
    @Volatile private var currentVideoId: String? = null
    @Volatile private var currentDurationMs: Long = 0
    @Volatile private var currentPositionMs: Long = 0
    @Volatile private var lastReportedTimeMs: Long = 0
    @Volatile private var isTelemetryEnabled: Boolean = true
    @Volatile private var isPlaying: Boolean = false

    // Tracking URLs (fetched from /player response for this session)
    @Volatile private var activePlaybackUrl: String? = null
    @Volatile private var activeWatchtimeUrl: String? = null

    // Client playback nonce (random 16-char string, YouTube session identifier)
    @Volatile private var cpn: String = ""
    @Volatile private var sessionStartTimeMs: Long = 0

    // BUGFIX (telemetry health monitoring): track consecutive ping failures for the CURRENT
    // song's session so a run of silent failures (e.g. a stale/expired cookie, a transient
    // network blip) doesn't quietly disable history sync for the rest of a long listening
    // session with no recovery attempt.
    @Volatile private var consecutiveFailureCount: Int = 0
    private companion object {
        const val TAG_HEALTH = "YouTubeTelemetryHealth"
        const val MAX_CONSECUTIVE_FAILURES = 3
    }

    /**
     * Records the outcome of a telemetry ping. After MAX_CONSECUTIVE_FAILURES in a row for the
     * same song, re-fetches fresh tracking URLs for it - the most likely reason a previously-
     * working session starts failing repeatedly is that the signed tracking URLs expired or the
     * session's cpn/state went stale, not that the network is down (a real outage would also
     * fail the recovery fetch, which is fine - it just tries again next failure).
     */
    private fun recordPingResult(success: Boolean, videoId: String) {
        if (success) {
            if (consecutiveFailureCount > 0) {
                Log.d(TAG_HEALTH, "Telemetry recovered for $videoId after $consecutiveFailureCount failure(s)")
            }
            consecutiveFailureCount = 0
            return
        }

        consecutiveFailureCount += 1
        Log.w(TAG_HEALTH, "Telemetry ping failed for $videoId (consecutive=$consecutiveFailureCount)")

        if (consecutiveFailureCount >= MAX_CONSECUTIVE_FAILURES && currentVideoId == videoId) {
            Log.w(TAG_HEALTH, "Recreating telemetry session for $videoId after $consecutiveFailureCount consecutive failures")
            consecutiveFailureCount = 0
            val durationMs = currentDurationMs
            // Force a fresh session: clear currentVideoId first so onSongChanged() doesn't
            // early-return on the "same video" check, then re-run the same fetch+start flow
            // used for a genuine song change, picking up new tracking URLs and a new cpn.
            currentVideoId = null
            onSongChanged(videoId, durationMs)
        }
    }

    fun onSongChanged(videoId: String, durationMs: Long) {
        // BUG 1 FIX: guard at very top, before any state mutation or stopTelemetry() call
        if (!isTelemetryEnabled) return
        if (currentVideoId == videoId) return

        stopTelemetry()

        currentVideoId = videoId
        currentDurationMs = durationMs
        lastReportedTimeMs = 0
        cpn = generateCpn()
        sessionStartTimeMs = System.currentTimeMillis()

        // BUG 7 FIX: single auth check via innertube, no double clientProvider() calls
        if (!YouTube.hasLoginCookie()) {
            Log.w(TAG, "No authenticated user session — skipping telemetry for $videoId")
            return
        }

        Log.d(TAG, "Song changed -> $videoId | cpn=$cpn | duration=${durationMs}ms")

        // Fetch signed playback/watchtime tracking URLs from InnerTube /player response
        fetchJob = coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                val signatureTimestamp = unshoo.ianshulyadav.pixelmusic.innertube.NewPipeUtils
                    .getSignatureTimestamp(videoId)
                    .getOrNull()

                YouTube.player(
                    videoId = videoId,
                    playlistId = null,
                    client = unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.WEB_REMIX,
                    signatureTimestamp = signatureTimestamp,
                    setLogin = true
                ).getOrNull()
            }.onSuccess { playerResult ->
                activePlaybackUrl = playerResult?.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                activeWatchtimeUrl = playerResult?.playbackTracking?.videostatsWatchtimeUrl?.baseUrl

                if (activePlaybackUrl == null && activeWatchtimeUrl == null) {
                    Log.w(TAG, "No tracking URLs returned for $videoId — history sync may fail")
                }

                Log.d(TAG, "Tracking URLs for $videoId: playback=$activePlaybackUrl watchtime=$activeWatchtimeUrl")

                val startUrl = activePlaybackUrl
                    ?: "https://music.youtube.com/api/stats/playback?ns=yt&el=detailpage&docid=$videoId&ver=2&c=WEB_REMIX&cver=1.20260531.05.00&cplayer=UNIPLAYER"
                reportPlaybackStart(startUrl, videoId)

                // BUGFIX (sync completion): PlayerViewModel and ListeningStatsTracker used to
                // each independently call YouTube.registerPlayback() (which mints a BotGuard
                // PoToken for accounts that require one) as a THIRD, uncoordinated writer. Both
                // of those call sites are now disabled so this manager is the single owner of
                // all remote YouTube telemetry - but that means it needs to keep doing this call
                // itself, or accounts that need a PoToken would silently lose history sync
                // entirely rather than just losing the redundant duplicate writes.
                activePlaybackUrl?.let { trackingUrl ->
                    runCatching {
                        YouTube.registerPlayback(
                            playlistId = null,
                            playbackTracking = trackingUrl,
                            videoId = videoId
                        )
                    }.onFailure { ex ->
                        Log.w(TAG, "registerPlayback failed for $videoId: ${ex.message}")
                    }
                }
            }.onFailure { ex ->
                Log.e(TAG, "Failed to fetch playerResult for $videoId: ${ex.message}", ex)
                // Fallback URL so at least a basic history ping fires
                val startUrl = "https://music.youtube.com/api/stats/playback?ns=yt&el=detailpage&docid=$videoId&ver=2&c=WEB_REMIX&cver=1.20260531.05.00&cplayer=UNIPLAYER"
                reportPlaybackStart(startUrl, videoId)
            }
        }
    }

    fun onPlaybackStateChanged(playing: Boolean) {
        isPlaying = playing
    }

    fun setTelemetryEnabled(enabled: Boolean) {
        isTelemetryEnabled = enabled
        if (!enabled) stopTelemetry()
    }

    fun updateProgress(positionMs: Long, durationMs: Long = 0) {
        // BUG 2 FIX: guard at very top, before seek-detection block fires a ping
        if (!isTelemetryEnabled) return

        if (kotlin.math.abs(positionMs - currentPositionMs) > 2000L) {
            val prevPos = lastReportedTimeMs / 1000
            val preSeekPos = currentPositionMs / 1000
            if (preSeekPos > prevPos) {
                sendWatchtimePing(prevPos, preSeekPos)
            }
            lastReportedTimeMs = positionMs
        }

        currentPositionMs = positionMs
        if (durationMs > 0 && currentDurationMs <= 0) {
            currentDurationMs = durationMs
        }

        // Periodic diagnostic log (~every 5 seconds)
        if (positionMs % 5000 < 250) {
            Log.d(TAG, "progress: playing=$isPlaying vid=$currentVideoId dur=${currentDurationMs}ms watchtimeUrl=${activeWatchtimeUrl != null}")
        }

        if (!isPlaying || currentVideoId == null || currentDurationMs <= 0) return

        // Wait for tracking URLs; fallback URL is used if fetchJob failed
        if (fetchJob?.isActive == true) return

        val positionSec = positionMs / 1000
        val lastReportedSec = lastReportedTimeMs / 1000

        // 1 second: immediate playback-start validation ping
        if (lastReportedSec == 0L && positionSec >= 1L) {
            sendWatchtimePing(0, positionSec)
            lastReportedTimeMs = positionMs
            return
        }

        // Every 30 seconds: standard YouTube heartbeat frequency
        if (positionSec - lastReportedSec >= 30L) {
            sendWatchtimePing(lastReportedSec, positionSec)
            lastReportedTimeMs = positionMs
            return
        }

        // 96% completion: final watch-completion registration
        val completionRatio = positionMs.toFloat() / currentDurationMs.toFloat()
        if (completionRatio >= 0.96f && (lastReportedTimeMs.toFloat() / currentDurationMs) < 0.96f) {
            sendWatchtimePing(lastReportedSec, positionSec)
            lastReportedTimeMs = positionMs
            return
        }
    }

    /**
     * Sends the initial playback ping via [YouTube.sendTelemetryPing], which handles
     * all auth headers (Cookie, SAPISIDHASH, X-Goog-Visitor-Id) internally.
     */
    /**
     * BUGFIX (task-specific timeout strategy): a single transient network blip shouldn't count
     * as a real failure toward the consecutive-failure/recovery counter in recordPingResult().
     * One retry after a short delay absorbs momentary hiccups; only a failure that survives the
     * retry gets recorded as a genuine failure.
     */
    private fun sendTelemetryPingWithRetry(url: String): Boolean {
        if (YouTube.sendTelemetryPing(url)) return true
        Thread.sleep(400L)
        return YouTube.sendTelemetryPing(url)
    }

    private fun reportPlaybackStart(playbackUrl: String, videoId: String) {
        val currentCpn = cpn
        val rtSec = (System.currentTimeMillis() - sessionStartTimeMs) / 1000

        coroutineScope.launch(Dispatchers.IO) {
            val separator = if (playbackUrl.contains("?")) "&" else "?"
            var fullUrl = "$playbackUrl${separator}cpn=$currentCpn&rt=$rtSec"
            if (!fullUrl.contains("ver=")) fullUrl += "&ver=2"
            if (!fullUrl.contains("c=")) fullUrl += "&c=WEB_REMIX&cver=1.20260531.05.00&cplayer=UNIPLAYER"
            val success = sendTelemetryPingWithRetry(fullUrl)
            recordPingResult(success, videoId)
        }
    }

    private fun sendWatchtimePing(
        st: Long,
        et: Long,
        isFinalPing: Boolean = false,
        capturedCpn: String? = null,
        capturedSessionStartTimeMs: Long? = null,
        capturedWatchtimeUrl: String? = null
    ) {
        val videoId = currentVideoId ?: return
        val lengthSec = currentDurationMs / 1000

        val currentCpn = capturedCpn ?: cpn
        val currentSessionStartMs = capturedSessionStartTimeMs ?: sessionStartTimeMs

        val baseWatchtimeUrl = capturedWatchtimeUrl ?: activeWatchtimeUrl
        val baseUrl = baseWatchtimeUrl
            ?: "https://music.youtube.com/api/stats/watchtime?ns=yt&el=detailpage&docid=$videoId"

        val rtSec = (System.currentTimeMillis() - currentSessionStartMs) / 1000
        val pingState = if (et >= lengthSec * 0.95) "ended" else if (isFinalPing) "paused" else "playing"
        val separator = if (baseUrl.contains("?")) "&" else "?"

        var fullUrl = "$baseUrl${separator}cpn=$currentCpn&state=$pingState&st=$st&et=$et&cmt=$et&rt=$rtSec&lact=1"
        if (!fullUrl.contains("len=") && !baseUrl.contains("&len")) fullUrl += "&len=$lengthSec"
        if (!fullUrl.contains("ver=")) fullUrl += "&ver=2"
        if (!fullUrl.contains("c=")) fullUrl += "&c=WEB_REMIX&cver=1.20260531.05.00&cplayer=UNIPLAYER"
        if (!fullUrl.contains("afmt=")) fullUrl += "&afmt=251&muted=0&volume=100"

        val success = sendTelemetryPingWithRetry(fullUrl)
        recordPingResult(success, videoId)
    }

    fun stopTelemetry() {
        val jobToCancel = fetchJob
        fetchJob = null

        val prevVideoId = currentVideoId
        val prevPos = lastReportedTimeMs / 1000
        val finalPos = currentPositionMs / 1000
        val prevDur = currentDurationMs / 1000

        // Capture all state before clearing
        val capturedCpn = cpn
        val capturedSessionStartTimeMs = sessionStartTimeMs
        val capturedWatchtimeUrl = activeWatchtimeUrl

        currentVideoId = null
        currentDurationMs = 0
        currentPositionMs = 0
        lastReportedTimeMs = 0
        activePlaybackUrl = null
        activeWatchtimeUrl = null

        coroutineScope.launch {
            try { jobToCancel?.cancelAndJoin() } catch (_: CancellationException) {}

            if (prevVideoId != null && prevDur > 0 && finalPos >= prevPos) {
                sendWatchtimePing(
                    st = prevPos,
                    et = finalPos,
                    isFinalPing = true,
                    capturedCpn = capturedCpn,
                    capturedSessionStartTimeMs = capturedSessionStartTimeMs,
                    capturedWatchtimeUrl = capturedWatchtimeUrl
                )
            }
        }
    }

    private fun generateCpn(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return (1..16).map { chars.random() }.joinToString("")
    }

    fun destroy() {
        try { coroutineScope.cancel() } catch (_: CancellationException) {}
        fetchJob?.cancel()
    }
}
