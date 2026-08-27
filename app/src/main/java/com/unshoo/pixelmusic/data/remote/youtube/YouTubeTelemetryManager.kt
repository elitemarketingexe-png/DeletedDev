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
 *
 * This is the ONLY remote YouTube history writer in the app. ListeningStatsTracker keeps
 * local stats only (see SEND_REDUNDANT_REMOTE_YOUTUBE_TELEMETRY there).
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
    //
    // BUGFIX (history stops recording after a handful of songs): this counter used to be
    // process-global and was NEVER reset when the song changed, while every single track
    // transition contributed one guaranteed-to-fail ping (see stopTelemetry() below). Three
    // songs in, the counter tripped the "recreate session" path, which re-entered
    // onSongChanged() -> stopTelemetry() -> another bogus ping -> another failure, minting a
    // fresh cpn mid-song each time. YouTube sees a churn of half-open playback sessions for
    // the same docid and stops committing them to history, which is exactly the reported
    // "first few songs sync, then nothing does" behaviour. The counter is now per-session
    // (reset on every song change) and failures are attributed to the session that actually
    // produced them.
    @Volatile private var consecutiveFailureCount: Int = 0

    /**
     * Guards the recovery path in [recordPingResult] so it can never loop: at most
     * [MAX_SESSION_RECOVERIES] tracking-URL refreshes per song, and never while one is in
     * flight. Recovery now re-fetches tracking URLs *in place* — it does not mint a new cpn
     * and does not re-enter [onSongChanged], so the YouTube-side session stays continuous.
     */
    @Volatile private var sessionRecoveryCount: Int = 0
    @Volatile private var recoveryInFlight: Boolean = false

    private companion object {
        const val TAG_HEALTH = "YouTubeTelemetryHealth"
        const val MAX_CONSECUTIVE_FAILURES = 3
        const val MAX_SESSION_RECOVERIES = 2

        /**
         * Upper bound for the InnerTube /player round-trip that fetches tracking URLs.
         * updateProgress() suppresses heartbeats while [fetchJob] is active, so an unbounded
         * fetch (no timeout on the socket, a captive portal that never answers) would silence
         * every heartbeat for the rest of the track. Bounded here so the fallback ping URL is
         * always reached.
         */
        const val TRACKING_URL_FETCH_TIMEOUT_MS = 12_000L

        /** A tick gap larger than this is treated as a seek rather than normal progress. */
        const val SEEK_DETECT_THRESHOLD_MS = 4_000L
    }

    /**
     * Records the outcome of a telemetry ping. After MAX_CONSECUTIVE_FAILURES in a row for the
     * same song, re-fetches fresh tracking URLs for it — the most likely reason a previously-
     * working session starts failing repeatedly is that the signed tracking URLs expired, not
     * that the network is down (a real outage also fails the recovery fetch, which is fine —
     * it just tries again on the next failure, up to MAX_SESSION_RECOVERIES).
     */
    private fun recordPingResult(success: Boolean, videoId: String) {
        // Only the *live* session's pings may move the health counter. A late-arriving result
        // for a song that has already been replaced must not penalise its successor.
        if (currentVideoId != videoId) return

        if (success) {
            if (consecutiveFailureCount > 0) {
                Log.d(TAG_HEALTH, "Telemetry recovered for $videoId after $consecutiveFailureCount failure(s)")
            }
            consecutiveFailureCount = 0
            return
        }

        consecutiveFailureCount += 1
        Log.w(TAG_HEALTH, "Telemetry ping failed for $videoId (consecutive=$consecutiveFailureCount)")

        if (consecutiveFailureCount < MAX_CONSECUTIVE_FAILURES) return
        if (recoveryInFlight) return
        if (sessionRecoveryCount >= MAX_SESSION_RECOVERIES) {
            Log.w(TAG_HEALTH, "Giving up telemetry recovery for $videoId (already retried $sessionRecoveryCount time(s))")
            return
        }

        consecutiveFailureCount = 0
        sessionRecoveryCount += 1
        recoveryInFlight = true
        Log.w(TAG_HEALTH, "Refreshing tracking URLs for $videoId (recovery #$sessionRecoveryCount)")

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val refreshed = fetchTrackingUrls(videoId)
                // Only adopt the result if this is still the live song.
                if (currentVideoId == videoId && refreshed != null) {
                    activePlaybackUrl = refreshed.first ?: activePlaybackUrl
                    activeWatchtimeUrl = refreshed.second ?: activeWatchtimeUrl
                    Log.d(TAG_HEALTH, "Tracking URLs refreshed for $videoId")
                }
            } finally {
                recoveryInFlight = false
            }
        }
    }

    /**
     * Starts a telemetry session for [videoId].
     *
     * @return true when a session was actually started (so the caller may latch this videoId),
     *         false when telemetry is disabled or the user is not signed in — in which case the
     *         caller must NOT latch, so the session can still start once a cookie appears.
     */
    fun onSongChanged(videoId: String, durationMs: Long): Boolean {
        // BUG 1 FIX: guard at very top, before any state mutation or stopTelemetry() call
        if (!isTelemetryEnabled) return false
        if (currentVideoId == videoId) return true

        stopTelemetry()

        // BUG 7 FIX: single auth check via innertube, no double clientProvider() calls.
        // Checked BEFORE claiming currentVideoId so a sign-in that lands mid-track can still
        // open a session for the song that is already playing.
        if (!YouTube.hasLoginCookie()) {
            Log.w(TAG, "No authenticated user session — skipping telemetry for $videoId")
            return false
        }

        currentVideoId = videoId
        currentDurationMs = durationMs
        currentPositionMs = 0
        lastReportedTimeMs = 0
        cpn = generateCpn()
        sessionStartTimeMs = System.currentTimeMillis()
        consecutiveFailureCount = 0
        sessionRecoveryCount = 0
        recoveryInFlight = false

        Log.d(TAG, "Song changed -> $videoId | cpn=$cpn | duration=${durationMs}ms")

        // Fetch signed playback/watchtime tracking URLs from InnerTube /player response
        fetchJob = coroutineScope.launch(Dispatchers.IO) {
            val urls = fetchTrackingUrls(videoId)
            if (currentVideoId != videoId) return@launch

            activePlaybackUrl = urls?.first
            activeWatchtimeUrl = urls?.second

            if (activePlaybackUrl == null && activeWatchtimeUrl == null) {
                Log.w(TAG, "No tracking URLs returned for $videoId — falling back to a synthetic ping URL")
            } else {
                Log.d(TAG, "Tracking URLs for $videoId: playback=$activePlaybackUrl watchtime=$activeWatchtimeUrl")
            }

            val startUrl = activePlaybackUrl ?: fallbackPlaybackUrl(videoId)
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
        }
        return true
    }

    /**
     * Resolves (playbackUrl, watchtimeUrl) for [videoId]. Bounded by
     * [TRACKING_URL_FETCH_TIMEOUT_MS]; returns null when the lookup fails or times out so the
     * caller falls back to a synthetic stats URL rather than going silent.
     */
    private suspend fun fetchTrackingUrls(videoId: String): Pair<String?, String?>? {
        return runCatching {
            withTimeoutOrNull(TRACKING_URL_FETCH_TIMEOUT_MS) {
                val signatureTimestamp = unshoo.ianshulyadav.pixelmusic.innertube.NewPipeUtils
                    .getSignatureTimestamp(videoId)
                    .getOrNull()

                val authStateWithoutPoToken = YouTube.currentPlaybackAuthState()
                    .copy(webClientPoTokenEnabled = false)
                val playerResult = YouTube.player(
                    videoId = videoId,
                    playlistId = null,
                    client = unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.WEB_REMIX,
                    signatureTimestamp = signatureTimestamp,
                    setLogin = true,
                    authState = authStateWithoutPoToken
                ).getOrNull()

                playerResult?.playbackTracking?.videostatsPlaybackUrl?.baseUrl to
                    playerResult?.playbackTracking?.videostatsWatchtimeUrl?.baseUrl
            }
        }.onFailure { ex ->
            if (ex is CancellationException) throw ex
            Log.e(TAG, "Failed to fetch playerResult for $videoId: ${ex.message}", ex)
        }.getOrNull()
    }

    private fun fallbackPlaybackUrl(videoId: String) =
        "https://music.youtube.com/api/stats/playback?ns=yt&el=detailpage&docid=$videoId" +
            "&ver=2&c=WEB_REMIX&cver=1.20260531.05.00&cplayer=UNIPLAYER"

    fun onPlaybackStateChanged(playing: Boolean) {
        isPlaying = playing
    }

    fun setTelemetryEnabled(enabled: Boolean) {
        isTelemetryEnabled = enabled
        if (!enabled) stopTelemetry()
    }

    /** True when a live session exists for [videoId]. Used by the service-side watchdog. */
    fun hasActiveSessionFor(videoId: String): Boolean = currentVideoId == videoId

    fun updateProgress(positionMs: Long, durationMs: Long = 0) {
        // BUG 2 FIX: guard at very top, before seek-detection block fires a ping
        if (!isTelemetryEnabled) return

        val videoId = currentVideoId
        if (durationMs > 0 && currentDurationMs <= 0) {
            currentDurationMs = durationMs
        }

        // BUGFIX (lost watch time): the old threshold was 2000ms, which the 1s progress loop
        // routinely exceeds whenever the loop tick is delayed (doze, a busy main thread, a
        // gapless transition). Every false positive silently advanced lastReportedTimeMs
        // WITHOUT sending a ping, so the skipped span was never reported to YouTube. A real
        // user seek is far larger than a late tick, so 4s separates the two cleanly, and the
        // pre-seek span is always reported before the marker moves.
        val delta = kotlin.math.abs(positionMs - currentPositionMs)
        if (delta > SEEK_DETECT_THRESHOLD_MS && videoId != null) {
            val prevPos = lastReportedTimeMs / 1000
            val preSeekPos = currentPositionMs / 1000
            if (preSeekPos > prevPos) {
                sendWatchtimePing(prevPos, preSeekPos)
            }
            lastReportedTimeMs = positionMs
        }

        currentPositionMs = positionMs

        // Periodic diagnostic log (~every 5 seconds)
        if (positionMs % 5000 < 250) {
            Log.d(TAG, "progress: playing=$isPlaying vid=$videoId dur=${currentDurationMs}ms watchtimeUrl=${activeWatchtimeUrl != null}")
        }

        if (!isPlaying || videoId == null || currentDurationMs <= 0) return

        // Wait for tracking URLs; fallback URL is used if fetchJob failed.
        // Bounded by TRACKING_URL_FETCH_TIMEOUT_MS so this can never suppress heartbeats
        // for the whole track.
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
     * BUGFIX (task-specific timeout strategy): a single transient network blip shouldn't count
     * as a real failure toward the consecutive-failure/recovery counter in recordPingResult().
     * One retry after a short delay absorbs momentary hiccups; only a failure that survives the
     * retry gets recorded as a genuine failure.
     */
    private suspend fun sendTelemetryPingWithRetry(url: String): Boolean {
        if (YouTube.sendTelemetryPing(url)) return true
        kotlinx.coroutines.delay(400L)
        return YouTube.sendTelemetryPing(url)
    }

    /**
     * Sends the initial playback ping via [YouTube.sendTelemetryPing], which handles
     * all auth headers (Cookie, SAPISIDHASH, X-Goog-Visitor-Id) internally.
     */
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

    /**
     * BUGFIX (the single biggest cause of "history stops syncing"): this used to read
     * `currentVideoId` and `currentDurationMs` off the live fields. [stopTelemetry] clears both
     * *before* launching the final ping, so `currentVideoId ?: return` bailed out every single
     * time and the `state=ended` ping — the one ping YouTube actually needs to commit a play to
     * watch history — was NEVER sent. Worse, when a new song had already claimed the fields by
     * the time the coroutine ran, the "ended" ping for the previous track went out carrying the
     * next track's id and length.
     *
     * Every value the ping depends on is now passed in explicitly, so a ping is always
     * self-consistent and independent of whatever the live session is doing.
     */
    private fun sendWatchtimePing(
        st: Long,
        et: Long,
        isFinalPing: Boolean = false,
        pingVideoId: String? = null,
        pingDurationMs: Long? = null,
        capturedCpn: String? = null,
        capturedSessionStartTimeMs: Long? = null,
        capturedWatchtimeUrl: String? = null
    ) {
        val videoId = pingVideoId ?: currentVideoId ?: return
        val lengthSec = (pingDurationMs ?: currentDurationMs) / 1000

        val currentCpn = capturedCpn ?: cpn
        val currentSessionStartMs = capturedSessionStartTimeMs ?: sessionStartTimeMs

        val baseWatchtimeUrl = capturedWatchtimeUrl ?: activeWatchtimeUrl
        val baseUrl = baseWatchtimeUrl
            ?: "https://music.youtube.com/api/stats/watchtime?ns=yt&el=detailpage&docid=$videoId"

        val rtSec = (System.currentTimeMillis() - currentSessionStartMs) / 1000
        val pingState = when {
            lengthSec > 0 && et >= lengthSec * 0.95 -> "ended"
            isFinalPing -> "paused"
            else -> "playing"
        }
        val separator = if (baseUrl.contains("?")) "&" else "?"

        var fullUrl = "$baseUrl${separator}cpn=$currentCpn&state=$pingState&st=$st&et=$et&cmt=$et&rt=$rtSec&lact=1"
        if (!fullUrl.contains("len=") && !baseUrl.contains("&len")) fullUrl += "&len=$lengthSec"
        if (!fullUrl.contains("ver=")) fullUrl += "&ver=2"
        if (!fullUrl.contains("c=")) fullUrl += "&c=WEB_REMIX&cver=1.20260531.05.00&cplayer=UNIPLAYER"
        if (!fullUrl.contains("afmt=")) fullUrl += "&afmt=251&muted=0&volume=100"

        coroutineScope.launch(Dispatchers.IO) {
            val success = sendTelemetryPingWithRetry(fullUrl)
            recordPingResult(success, videoId)
        }
    }

    fun stopTelemetry() {
        val jobToCancel = fetchJob
        fetchJob = null

        val prevVideoId = currentVideoId
        val prevPos = lastReportedTimeMs / 1000
        val finalPos = currentPositionMs / 1000
        val prevDurationMs = currentDurationMs

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
        consecutiveFailureCount = 0
        sessionRecoveryCount = 0
        recoveryInFlight = false

        if (prevVideoId == null || prevDurationMs <= 0 || finalPos < prevPos) {
            jobToCancel?.cancel()
            return
        }

        coroutineScope.launch {
            try { jobToCancel?.cancelAndJoin() } catch (_: CancellationException) {}

            // Every field this ping needs is passed explicitly — see the note on
            // sendWatchtimePing(). Nothing here reads the live session, which by now
            // belongs to the *next* track.
            sendWatchtimePing(
                st = prevPos,
                et = finalPos,
                isFinalPing = true,
                pingVideoId = prevVideoId,
                pingDurationMs = prevDurationMs,
                capturedCpn = capturedCpn,
                capturedSessionStartTimeMs = capturedSessionStartTimeMs,
                capturedWatchtimeUrl = capturedWatchtimeUrl
            )
        }
    }

    private fun generateCpn(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return (1..16).map { chars.random() }.joinToString("")
    }

    fun destroy() {
        fetchJob?.cancel()
        fetchJob = null
        try { coroutineScope.cancel() } catch (_: CancellationException) {}
    }
}
