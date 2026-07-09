package com.unshoo.pixelmusic.data.remote.youtube

import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import unshoo.ianshulyadav.pixelmusic.innertube.InnerTube
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient

class YouTubeTelemetryManager(
    private val httpClient: OkHttpClient,
    private val clientProvider: () -> PlaybackAuthStateProvider
) {
    private val TAG = "YouTubeTelemetry"
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var fetchJob: Job? = null
    
    // State
    @Volatile private var currentVideoId: String? = null
    @Volatile private var currentDurationMs: Long = 0
    @Volatile private var currentPositionMs: Long = 0
    @Volatile private var lastReportedTimeMs: Long = 0
    @Volatile private var isPlaying: Boolean = false
    
    // Tracking URLs
    @Volatile private var activePlaybackUrl: String? = null
    @Volatile private var activeWatchtimeUrl: String? = null
    
    // Heartbeat logic
    @Volatile private var cpn: String = "" // Client playback nonce (random 16 char string)
    @Volatile private var sessionStartTimeMs: Long = 0
    
    fun onSongChanged(videoId: String, durationMs: Long) {
        if (currentVideoId == videoId) return
        stopTelemetry()
        
        currentVideoId = videoId
        currentDurationMs = durationMs
        lastReportedTimeMs = 0
        cpn = generateCpn()
        sessionStartTimeMs = System.currentTimeMillis()
        
        Log.d(TAG, "Song changed to $videoId, fetching telemetry URLs with cpn=$cpn")

        val state = clientProvider().getPlaybackAuthState()
        val cookie = state.cookie
        if (cookie.isNullOrBlank()) {
            Log.w(TAG, "No authenticated user session found, skipping telemetry URL fetch.")
            return
        }

        // Fetch signed playback/watchtime tracking URLs from InnerTube /player response
        fetchJob = coroutineScope.launch(Dispatchers.IO) {
            runCatching {
                val signatureTimestamp = unshoo.ianshulyadav.pixelmusic.innertube.NewPipeUtils
                    .getSignatureTimestamp(videoId)
                    .getOrNull()
                
                unshoo.ianshulyadav.pixelmusic.innertube.YouTube.player(
                    videoId = videoId,
                    playlistId = null,
                    client = unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.WEB_REMIX,
                    signatureTimestamp = signatureTimestamp,
                    setLogin = true
                ).getOrNull()
            }.onSuccess { playerResult ->
                activePlaybackUrl = playerResult?.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                activeWatchtimeUrl = playerResult?.playbackTracking?.videostatsWatchtimeUrl?.baseUrl
                
                Log.d(TAG, "Successfully retrieved tracking URLs for $videoId:\n" +
                        "  playbackUrl: $activePlaybackUrl\n" +
                        "  watchtimeUrl: $activeWatchtimeUrl")
                        
                val startUrl = activePlaybackUrl ?: "https://music.youtube.com/api/stats/playback?ns=yt&el=detailpage&docid=$videoId&ver=2&c=WEB_REMIX&cver=1.20260531.05.00&cplayer=UNIPLAYER"
                reportPlaybackStart(startUrl)
            }.onFailure { exception ->
                Log.e(TAG, "Failed to retrieve playerResult from YouTubeMusic: ${exception.message}", exception)
                
                val startUrl = "https://music.youtube.com/api/stats/playback?ns=yt&el=detailpage&docid=$videoId&ver=2&c=WEB_REMIX&cver=1.20260531.05.00&cplayer=UNIPLAYER"
                reportPlaybackStart(startUrl)
            }
        }
    }
    
    fun onPlaybackStateChanged(playing: Boolean) {
        isPlaying = playing
    }
    
    fun updateProgress(positionMs: Long, durationMs: Long = 0) {
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
        
        // Log telemetry state periodically (approx every 5 seconds) to aid diagnostic monitoring
        if (positionMs % 5000 < 250) {
            Log.d(TAG, "updateProgress: isPlaying=$isPlaying, currentVideoId=$currentVideoId, currentDurationMs=$currentDurationMs, activeWatchtimeUrl=${activeWatchtimeUrl != null}")
        }

        if (!isPlaying || currentVideoId == null || currentDurationMs <= 0) return
        
        // Wait until tracking URLs are fetched before sending any watchtime pings.
        // If the fetchJob is completed but activeWatchtimeUrl is still null, proceed anyway using the authentic fallback URL.
        if (fetchJob?.isActive == true) return
        
        val positionSec = positionMs / 1000
        val lastReportedSec = lastReportedTimeMs / 1000
        
        // 1 Second: Immediate playback start validation
        if (lastReportedSec == 0L && positionSec >= 1L) {
            sendWatchtimePing(0, positionSec)
            lastReportedTimeMs = positionMs
            return
        }
        
        // Every 30 Seconds: Standard YouTube heartbeat frequency
        if (positionSec - lastReportedSec >= 30L) {
            sendWatchtimePing(lastReportedSec, positionSec)
            lastReportedTimeMs = positionMs
            return
        }
        
        // 96% Completion: Final watch completion registration
        val completionRatio = positionMs.toFloat() / currentDurationMs.toFloat()
        if (completionRatio >= 0.96f && (lastReportedTimeMs.toFloat() / currentDurationMs) < 0.96f) {
            sendWatchtimePing(lastReportedSec, positionSec)
            lastReportedTimeMs = positionMs
            return
        }
    }
    
    private fun reportPlaybackStart(playbackUrl: String) {
        val currentCpn = cpn
        val currentSessionStartTimeMs = sessionStartTimeMs
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val state = clientProvider().getPlaybackAuthState()
                val cookie = state.cookie ?: return@launch
                
                val separator = if (playbackUrl.contains("?")) "&" else "?"
                val rtSec = (System.currentTimeMillis() - currentSessionStartTimeMs) / 1000
                
                // Force music.youtube.com domain to ensure YouTube Music history registration
                val forcedDomainUrl = playbackUrl.replace("https://s.youtube.com", "https://music.youtube.com")
                var fullUrl = "$forcedDomainUrl${separator}cpn=$currentCpn&rt=$rtSec"
                
                if (!fullUrl.contains("ver=")) {
                    fullUrl += "&ver=2"
                }
                if (!fullUrl.contains("c=")) {
                    fullUrl += "&c=WEB_REMIX&cver=1.20260531.05.00&cplayer=UNIPLAYER"
                }
                
                val cookieMap = runCatching {
                    unshoo.ianshulyadav.pixelmusic.innertube.utils.parseCookieString(cookie)
                }.getOrDefault(emptyMap())
                
                val origin = "https://music.youtube.com"
                val requestBuilder = Request.Builder()
                    .url(fullUrl)
                    .get()
                    .header("Cookie", cookie)
                    .header("User-Agent", YouTubeClient.WEB_REMIX.userAgent)
                    .header("Origin", origin)
                    .header("Referer", "$origin/")
                    .header("X-Goog-Visitor-Id", state.visitorData.orEmpty())
                    .header("Accept", "*/*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                
                val sapisid = cookieMap["SAPISID"] ?: cookieMap["__Secure-3PAPISID"]
                if (!sapisid.isNullOrBlank()) {
                    val currentTime = System.currentTimeMillis() / 1000
                    val sapisidHash = unshoo.ianshulyadav.pixelmusic.innertube.utils.sha1(
                        "$currentTime $sapisid $origin"
                    )
                    requestBuilder.header("Authorization", "SAPISIDHASH ${currentTime}_$sapisidHash")
                }
                
                val response = httpClient.newCall(requestBuilder.build()).execute()
                Log.d(TAG, "Playback start telemetry ping successfully sent (status: ${response.code})")
                response.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send playback start telemetry ping: ${e.message}")
            }
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
        val currentSessionStartTimeMs = capturedSessionStartTimeMs ?: sessionStartTimeMs
        
        // Force music.youtube.com domain to ensure YouTube Music history registration
        val baseWatchtimeUrl = capturedWatchtimeUrl ?: activeWatchtimeUrl
        val forcedDomainUrl = baseWatchtimeUrl?.replace("https://s.youtube.com", "https://music.youtube.com")
        
        val baseUrl = forcedDomainUrl ?: "https://music.youtube.com/api/stats/watchtime?ns=yt&el=detailpage&docid=$videoId"
        
        val rtSec = (System.currentTimeMillis() - currentSessionStartTimeMs) / 1000
        val state = if (et >= lengthSec * 0.95) "ended" else if (isFinalPing) "paused" else "playing"
        val separator = if (baseUrl.contains("?")) "&" else "?"
        
        var fullUrl = "$baseUrl${separator}cpn=$currentCpn&state=$state&st=$st&et=$et&cmt=$et&rt=$rtSec&lact=1"
        if (!fullUrl.contains("len=") && !baseUrl.contains("&len")) {
            fullUrl += "&len=$lengthSec"
        }
        
        if (!fullUrl.contains("ver=")) {
            fullUrl += "&ver=2"
        }
        
        if (!fullUrl.contains("c=")) {
            fullUrl += "&c=WEB_REMIX&cver=1.20260531.05.00&cplayer=UNIPLAYER"
        }
        
        if (!fullUrl.contains("afmt=")) {
            fullUrl += "&afmt=251&muted=0&volume=100"
        }
        
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val state = clientProvider().getPlaybackAuthState()
                val cookie = state.cookie ?: return@launch
                
                val cookieMap = runCatching {
                    unshoo.ianshulyadav.pixelmusic.innertube.utils.parseCookieString(cookie)
                }.getOrDefault(emptyMap())
                
                val origin = "https://music.youtube.com"
                val requestBuilder = Request.Builder()
                    .url(fullUrl)
                    .get()
                    .header("Cookie", cookie)
                    .header("User-Agent", YouTubeClient.WEB_REMIX.userAgent)
                    .header("Origin", origin)
                    .header("Referer", "$origin/")
                    .header("X-Goog-Visitor-Id", state.visitorData.orEmpty())
                    .header("Accept", "*/*")
                    .header("Accept-Language", "en-US,en;q=0.9")
                
                val sapisid = cookieMap["SAPISID"] ?: cookieMap["__Secure-3PAPISID"]
                if (!sapisid.isNullOrBlank()) {
                    val currentTime = System.currentTimeMillis() / 1000
                    val sapisidHash = unshoo.ianshulyadav.pixelmusic.innertube.utils.sha1(
                        "$currentTime $sapisid $origin"
                    )
                    requestBuilder.header("Authorization", "SAPISIDHASH ${currentTime}_$sapisidHash")
                }
                
                val response = httpClient.newCall(requestBuilder.build()).execute()
                Log.d(TAG, "Watchtime telemetry ping sent [st=$st, et=$et, state=$state] (status: ${response.code})")
                response.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send watchtime telemetry ping: ${e.message}")
            }
        }
    }
    
    fun stopTelemetry() {
        val jobToCancel = fetchJob
        fetchJob = null
        
        val prevVideoId = currentVideoId
        val prevPos = lastReportedTimeMs / 1000
        val finalPos = currentPositionMs / 1000
        val prevDur = currentDurationMs / 1000
        
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
            try {
                jobToCancel?.cancelAndJoin()
            } catch (e: CancellationException) {
                // Expected
            }
            
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
        try {
            coroutineScope.cancel()
        } catch (e: CancellationException) {
            // Expected
        }
        fetchJob?.cancel()
    }
}

interface PlaybackAuthStateProvider {
    fun getPlaybackAuthState(): unshoo.ianshulyadav.pixelmusic.innertube.PlaybackAuthState
}
