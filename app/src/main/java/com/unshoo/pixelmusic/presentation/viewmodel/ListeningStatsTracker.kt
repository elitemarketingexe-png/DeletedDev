package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.Context
import android.os.SystemClock
import androidx.media3.common.C
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.unshoo.pixelmusic.data.DailyMixManager
import com.unshoo.pixelmusic.data.database.EngagementDao
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.remote.youtube.SongDownloadWorker
import com.unshoo.pixelmusic.data.stats.PlaybackStatsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Tracks listening statistics for songs.
 * Extracted from PlayerViewModel to reduce its size and improve modularity.
 *
 * Responsibilities:
 * - Track active listening sessions
 * - Record play statistics when session ends
 * - Handle voluntary vs automatic plays
 */
@Singleton
class ListeningStatsTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dailyMixManager: DailyMixManager,
    private val playbackStatsRepository: PlaybackStatsRepository,
    private val engagementDao: EngagementDao,
    private val musicDao: MusicDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private var currentSession: ActiveSession? = null
    private var pendingVoluntarySongId: String? = null
    @Volatile private var telemetryCpn: String? = null
    @Volatile private var telemetryLastReportedTimeMs: Long = 0L
    @Volatile private var telemetrySessionStartTimeMs: Long = 0L
    @Volatile private var isPlaybackStartReported = false
    @Volatile private var isWatchCompleted = false
    private var scope: CoroutineScope? = null
    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _playbackHistory = MutableStateFlow<List<PlaybackStatsRepository.PlaybackHistoryEntry>>(emptyList())
    val playbackHistory: StateFlow<List<PlaybackStatsRepository.PlaybackHistoryEntry>> = _playbackHistory.asStateFlow()

    /**
     * Must be called to set the coroutine scope for async operations.
     */
    fun initialize(coroutineScope: CoroutineScope) {
        val activeScope = scope
        if (activeScope == null || activeScope.coroutineContext[Job]?.isActive != true) {
            scope = coroutineScope
        }
        coroutineScope.launch(Dispatchers.IO) {
            _playbackHistory.value = playbackStatsRepository.loadPlaybackHistory(
                limit = MAX_INTERNAL_PLAYBACK_HISTORY_ITEMS
            )
            // Merge authenticated YouTube Music history on top of local history so
            // Recently Played shows local songs + YT Music synced history together.
            refreshMergedYoutubeHistory()
        }
    }

    /**
     * Pull FEmusic_history from YouTube Music (when logged in) and merge with local
     * playback history. Local entries win on id collisions so offline plays stay accurate.
     */
    @Volatile private var lastMergedHistoryAtMs = 0L
    @Volatile private var mergeInFlight = false

    fun refreshMergedYoutubeHistory() {
        val now = System.currentTimeMillis()
        // Debounce: Explore refresh + login + init can all fire; avoid spamming FEmusic_history.
        if (mergeInFlight) return
        if (now - lastMergedHistoryAtMs < 30_000L && _playbackHistory.value.isNotEmpty()) return
        val activeScope = scope ?: persistenceScope
        mergeInFlight = true
        activeScope.launch(Dispatchers.IO) {
            try {
            runCatching {
                val ytPage = unshoo.ianshulyadav.pixelmusic.innertube.YouTube.musicHistory().getOrNull()
                    ?: return@runCatching
                val now = System.currentTimeMillis()
                val ytEntries = ytPage.sections
                    .orEmpty()
                    .asSequence()
                    .flatMap { section -> section.songs.asSequence() }
                    .mapIndexedNotNull { index, songItem ->
                        val videoId = songItem.id.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
                        val artistName = songItem.artists
                            .map { it.name }
                            .filter { it.isNotBlank() }
                            .joinToString(", ")
                            .ifBlank { "Unknown Artist" }
                        PlaybackStatsRepository.PlaybackHistoryEntry(
                            songId = "youtube_$videoId",
                            // Decreasing timestamps preserve YT history order in our sorted list.
                            timestamp = now - index,
                            title = songItem.title,
                            artist = artistName,
                            thumbnail = songItem.thumbnail
                        )
                    }
                    .distinctBy { it.songId }
                    .toList()
                if (ytEntries.isEmpty()) return@runCatching

                _playbackHistory.update { local ->
                    val localIds = local.map { it.songId }.toHashSet()
                    // Prefer local first (more accurate timestamps), then append remote-only items.
                    val remoteOnly = ytEntries.filter { it.songId !in localIds }
                    (local + remoteOnly).take(MAX_INTERNAL_PLAYBACK_HISTORY_ITEMS)
                }
                Timber.d(
                    "Merged YT Music history: remote=%d total=%d",
                    ytEntries.size,
                    _playbackHistory.value.size
                )
                lastMergedHistoryAtMs = System.currentTimeMillis()
            }.onFailure {
                Timber.w(it, "Failed to merge YouTube Music listening history")
            }
            } finally {
                mergeInFlight = false
            }
        }
    }

    @Synchronized
    fun onVoluntarySelection(songId: String) {
        pendingVoluntarySongId = songId
    }

    fun onSongChanged(
        song: Song?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        onTrackChanged(
            songId = song?.id,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = song?.duration ?: 0L,
            isPlaying = isPlaying,
            title = song?.title,
            artist = song?.displayArtist,
            thumbnail = song?.albumArtUriString,
            genre = song?.genre,
            album = song?.album
        )
    }

    @Synchronized
    fun onTrackChanged(
        songId: String?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        onTrackChanged(
            songId = songId,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = 0L,
            isPlaying = isPlaying
        )
    }

    private suspend fun resolveYtId(songId: String): String? {
        return if (songId.startsWith("youtube_")) songId.removePrefix("youtube_")
        else {
            val numericId = songId.toLongOrNull()
            if (numericId != null && numericId < 0) {
                val entity = musicDao.getSongByIdOnce(numericId)
                if (entity?.contentUriString?.startsWith("youtube://") == true) entity.contentUriString.removePrefix("youtube://") else null
            } else null
        }
    }

    @Synchronized
    fun onTrackChanged(
        songId: String?,
        positionMs: Long,
        durationMs: Long,
        fallbackDurationMs: Long,
        isPlaying: Boolean,
        title: String? = null,
        artist: String? = null,
        thumbnail: String? = null,
        genre: String? = null,
        album: String? = null
    ) {
        finalizeCurrentSession()
        val safeSongId = songId?.takeIf { it.isNotBlank() }
        if (safeSongId == null) {
            return
        }

        val nowRealtime = SystemClock.elapsedRealtime()
        val nowEpoch = System.currentTimeMillis()
        val normalizedDuration = normalizeDuration(durationMs, fallbackDurationMs)

        currentSession = ActiveSession(
            songId = safeSongId,
            totalDurationMs = normalizedDuration,
            startedAtEpochMs = nowEpoch,
            lastKnownPositionMs = positionMs.coerceAtLeast(0L),
            accumulatedListeningMs = 0L,
            lastRealtimeMs = nowRealtime,
            lastUpdateEpochMs = nowEpoch,
            isPlaying = isPlaying,
            isVoluntary = pendingVoluntarySongId == safeSongId,
            title = title,
            artist = artist,
            thumbnail = thumbnail,
            genre = genre,
            album = album
        )

        // Instant local history head so Recently Played matches the current song immediately
        // (before the full session finalize / YT remote sync completes).
        if (!title.isNullOrBlank()) {
            val optimistic = PlaybackStatsRepository.PlaybackHistoryEntry(
                songId = safeSongId,
                timestamp = nowEpoch,
                title = title,
                artist = artist,
                thumbnail = thumbnail
            )
            _playbackHistory.update { current ->
                val withoutDup = current.filterNot { it.songId == safeSongId }
                (listOf(optimistic) + withoutDup).take(MAX_INTERNAL_PLAYBACK_HISTORY_ITEMS)
            }
        }

        persistenceScope.launch(Dispatchers.IO) {
            runCatching {
                val ytId = resolveYtId(safeSongId)
                if (ytId != null) {
                    val cpn = (1..16).map { "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".random() }.joinToString("")
                    telemetryCpn = cpn
                    telemetryLastReportedTimeMs = 0L
                    telemetrySessionStartTimeMs = System.currentTimeMillis()
                    isPlaybackStartReported = false
                    isWatchCompleted = false

                    // SpatialFlow parity: resolve tracking URLs quickly, then:
                    //  1) send stats/playback start ping (with auth headers via sendTelemetryPing)
                    //  2) call YouTube.registerPlayback when we have a real tracking base URL
                    // Both are needed for the currently playing song to appear in YT Music history.
                    var trackingUrl: String? =
                        com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.playbackTrackingCache[ytId]
                    var watchtimeUrl: String? =
                        com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.watchtimeTrackingCache[ytId]
                    if (trackingUrl == null) {
                        // Wait up to ~1.2s for stream resolve to populate tracking cache.
                        repeat(12) {
                            kotlinx.coroutines.delay(100L)
                            trackingUrl =
                                com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.playbackTrackingCache[ytId]
                            watchtimeUrl =
                                com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.watchtimeTrackingCache[ytId]
                            if (trackingUrl != null) return@repeat
                        }
                    }

                    // If still missing, fetch a lightweight WEB_REMIX player response just for tracking URLs
                    // (same fallback SpatialFlow / registerYoutubePlaybackHistory uses).
                    if (trackingUrl.isNullOrBlank()) {
                        runCatching {
                            val signatureTimestamp = unshoo.ianshulyadav.pixelmusic.innertube.NewPipeUtils
                                .getSignatureTimestamp(ytId)
                                .getOrNull()
                            val playerRes = unshoo.ianshulyadav.pixelmusic.innertube.YouTube.player(
                                videoId = ytId,
                                playlistId = null,
                                client = unshoo.ianshulyadav.pixelmusic.innertube.models.YouTubeClient.WEB_REMIX,
                                signatureTimestamp = signatureTimestamp,
                                setLogin = true
                            ).getOrNull()
                            trackingUrl = playerRes?.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                            watchtimeUrl = playerRes?.playbackTracking?.videostatsWatchtimeUrl?.baseUrl
                            if (!trackingUrl.isNullOrBlank()) {
                                com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper
                                    .playbackTrackingCache[ytId] = trackingUrl!!
                            }
                            if (!watchtimeUrl.isNullOrBlank()) {
                                com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper
                                    .watchtimeTrackingCache[ytId] = watchtimeUrl!!
                            }
                        }
                    }

                    val finalTrackingUrl = (trackingUrl ?: "")
                        .replace("https://s.youtube.com", "https://music.youtube.com")
                        .ifBlank {
                            "https://music.youtube.com/api/stats/playback?ns=yt&el=detailpage&docid=$ytId"
                        }

                    val separator = if (finalTrackingUrl.contains("?")) "&" else "?"
                    val rtSec = (System.currentTimeMillis() - telemetrySessionStartTimeMs) / 1000
                    val startUrl =
                        "${finalTrackingUrl}${separator}ver=2&c=WEB_REMIX&cver=1.20260531.05.00" +
                            "&cplayer=UNIPLAYER&cpn=$cpn&rt=$rtSec&docid=$ytId"

                    unshoo.ianshulyadav.pixelmusic.innertube.YouTube.sendTelemetryPing(startUrl)
                    isPlaybackStartReported = true

                    // Full registerPlayback path (InnerTube-authenticated) when we have a real tracking URL.
                    if (!trackingUrl.isNullOrBlank()) {
                        runCatching {
                            unshoo.ianshulyadav.pixelmusic.innertube.YouTube.registerPlayback(
                                playlistId = null,
                                playbackTracking = trackingUrl!!,
                                videoId = ytId
                            )
                        }.onSuccess {
                            timber.log.Timber.d("YT history registerPlayback OK for %s", ytId)
                        }.onFailure {
                            timber.log.Timber.w(it, "YT history registerPlayback failed for %s", ytId)
                        }
                    }

                    // Immediate first watchtime heartbeat at t≈1s (SpatialFlow does this at 1s).
                    kotlinx.coroutines.delay(1_000L)
                    if (telemetryCpn == cpn && currentSession?.songId == safeSongId) {
                        sendWatchtimePingInternal(
                            videoId = ytId,
                            st = 0,
                            et = 1,
                            cpn = cpn,
                            sessionStartTimeMs = telemetrySessionStartTimeMs,
                            isPaused = false
                        )
                        telemetryLastReportedTimeMs = 1_000L
                    }
                }
            }.onFailure {
                timber.log.Timber.w(it, "YT telemetry start failed")
            }
        }
        if (pendingVoluntarySongId == safeSongId) {
            pendingVoluntarySongId = null
        }
    }

    private suspend fun sendWatchtimePingInternal(
        videoId: String,
        st: Long,
        et: Long,
        cpn: String,
        sessionStartTimeMs: Long,
        isPaused: Boolean
    ) {
        runCatching {
            val cachedWatchUrl = com.unshoo.pixelmusic.data.remote.youtube.YoutubeHelper.watchtimeTrackingCache[videoId]?.replace("https://s.youtube.com", "https://music.youtube.com")
                ?: "https://music.youtube.com/api/stats/watchtime?ns=yt&el=detailpage&docid=$videoId"
            
            val currentSession = currentSession
            val lengthSec = if (currentSession != null && currentSession.totalDurationMs > 0) currentSession.totalDurationMs / 1000 else 0L
            val rtSec = (System.currentTimeMillis() - sessionStartTimeMs) / 1000
            val state = if (isPaused) "paused" else if (et >= lengthSec * 0.95 && lengthSec > 0) "ended" else "playing"
            val separator = if (cachedWatchUrl.contains("?")) "&" else "?"

            val fullUrl = "$cachedWatchUrl${separator}cpn=$cpn&state=$state&st=$st&et=$et&cmt=$et&rt=$rtSec&lact=1&len=$lengthSec&ver=2&c=WEB_REMIX&cver=1.20260531.05.00&cplayer=UNIPLAYER&afmt=251&muted=0&volume=100"
            unshoo.ianshulyadav.pixelmusic.innertube.YouTube.sendTelemetryPing(fullUrl)
        }
    }

    @Synchronized
    fun onPlayStateChanged(isPlaying: Boolean, positionMs: Long) {
        val session = currentSession ?: return
        val nowRealtime = SystemClock.elapsedRealtime()
        accumulateRealtimeListening(session, nowRealtime)
        session.isPlaying = isPlaying
        session.lastRealtimeMs = nowRealtime
        session.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
        session.lastUpdateEpochMs = System.currentTimeMillis()

        val songId = session.songId
        val cpn = telemetryCpn
        if (cpn != null) {
            val startTimeMs = telemetrySessionStartTimeMs
            val lastReportedSec = telemetryLastReportedTimeMs / 1000
            val positionSec = positionMs / 1000
            if (positionSec > lastReportedSec) {
                persistenceScope.launch(Dispatchers.IO) {
                    val ytId = resolveYtId(songId)
                    if (ytId != null) {
                        sendWatchtimePingInternal(ytId, lastReportedSec, positionSec, cpn, startTimeMs, !isPlaying)
                    }
                }
            }
            if (!isPlaying) {
                telemetryCpn = null
            }
        }
    }

    @Synchronized
    fun onProgress(positionMs: Long, isPlaying: Boolean) {
        val session = currentSession ?: return
        val nowRealtime = SystemClock.elapsedRealtime()
        accumulateRealtimeListening(session, nowRealtime)
        session.isPlaying = isPlaying
        session.lastRealtimeMs = nowRealtime
        session.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
        session.lastUpdateEpochMs = System.currentTimeMillis()

        val songId = session.songId
        if (isPlaying) {
            val durationMs = session.totalDurationMs
            val positionSec = positionMs / 1000
            val lastReportedSec = telemetryLastReportedTimeMs / 1000
            val cpn = telemetryCpn ?: return
            val startTimeMs = telemetrySessionStartTimeMs

            var pingSt: Long? = null
            var pingEt: Long? = null

            if (Math.abs(positionMs - telemetryLastReportedTimeMs) > 2000L) {
                val prevPos = lastReportedSec
                val preSeekPos = positionSec
                if (preSeekPos > prevPos) {
                    pingSt = prevPos
                    pingEt = preSeekPos
                }
                telemetryLastReportedTimeMs = positionMs
            } else if (lastReportedSec == 0L && positionSec >= 1L) {
                pingSt = 0L
                pingEt = positionSec
                telemetryLastReportedTimeMs = positionMs
            } else if (positionSec - lastReportedSec >= 30L) {
                pingSt = lastReportedSec
                pingEt = positionSec
                telemetryLastReportedTimeMs = positionMs
            } else if (durationMs > 0L && !isWatchCompleted) {
                val completionRatio = positionMs.toFloat() / durationMs.toFloat()
                if (completionRatio >= 0.96f) {
                    pingSt = lastReportedSec
                    pingEt = positionSec
                    telemetryLastReportedTimeMs = positionMs
                    isWatchCompleted = true
                }
            }

            if (pingSt != null && pingEt != null) {
                persistenceScope.launch(Dispatchers.IO) {
                    val ytId = resolveYtId(songId)
                    if (ytId != null) {
                        sendWatchtimePingInternal(ytId, pingSt, pingEt, cpn, startTimeMs, false)
                    }
                }
            }
        }
    }

    fun ensureSession(
        song: Song?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        ensureSession(
            songId = song?.id,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = song?.duration ?: 0L,
            isPlaying = isPlaying,
            title = song?.title,
            artist = song?.displayArtist,
            thumbnail = song?.albumArtUriString,
            genre = song?.genre,
            album = song?.album
        )
    }

    @Synchronized
    fun ensureSession(
        songId: String?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean
    ) {
        ensureSession(
            songId = songId,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = 0L,
            isPlaying = isPlaying
        )
    }

    @Synchronized
    fun ensureSession(
        songId: String?,
        positionMs: Long,
        durationMs: Long,
        fallbackDurationMs: Long,
        isPlaying: Boolean,
        title: String? = null,
        artist: String? = null,
        thumbnail: String? = null,
        genre: String? = null,
        album: String? = null
    ) {
        val safeSongId = songId?.takeIf { it.isNotBlank() }
        if (safeSongId == null) {
            finalizeCurrentSession()
            return
        }
        val existing = currentSession
        if (existing?.songId == safeSongId) {
            updateDuration(normalizeDuration(durationMs, fallbackDurationMs))
            val nowRealtime = SystemClock.elapsedRealtime()
            accumulateRealtimeListening(existing, nowRealtime)
            existing.isPlaying = isPlaying
            existing.lastRealtimeMs = nowRealtime
            existing.lastKnownPositionMs = positionMs.coerceAtLeast(0L)
            existing.lastUpdateEpochMs = System.currentTimeMillis()
            return
        }
        onTrackChanged(
            songId = safeSongId,
            positionMs = positionMs,
            durationMs = durationMs,
            fallbackDurationMs = fallbackDurationMs,
            isPlaying = isPlaying,
            title = title,
            artist = artist,
            thumbnail = thumbnail,
            genre = genre,
            album = album
        )
    }

    @Synchronized
    fun updateDuration(durationMs: Long) {
        val session = currentSession ?: return
        if (durationMs > 0 && durationMs != C.TIME_UNSET) {
            session.totalDurationMs = durationMs
        }
    }

    @Synchronized
    fun finalizeCurrentSession(forceSynchronousPersistence: Boolean = false) {
        val session = currentSession ?: return
        val nowRealtime = SystemClock.elapsedRealtime()
        val nowEpoch = System.currentTimeMillis()
        accumulateRealtimeListening(session, nowRealtime)
        val listened = session.accumulatedListeningMs.coerceAtLeast(0L)

        val songId = session.songId
        val cpn = telemetryCpn
        if (cpn != null) {
            val startTimeMs = telemetrySessionStartTimeMs
            val lastReportedSec = telemetryLastReportedTimeMs / 1000
            val positionSec = session.lastKnownPositionMs / 1000
            if (positionSec > lastReportedSec) {
                persistenceScope.launch(Dispatchers.IO) {
                    val ytId = resolveYtId(songId)
                    if (ytId != null) {
                        sendWatchtimePingInternal(ytId, lastReportedSec, positionSec, cpn, startTimeMs, true)
                    }
                }
            }
            telemetryCpn = null
        }
        if (listened >= MIN_SESSION_LISTEN_MS) {
            val rawEndTimestamp = when {
                session.isPlaying -> nowEpoch
                session.lastUpdateEpochMs > 0L -> session.lastUpdateEpochMs
                else -> session.startedAtEpochMs + listened
            }
            val timestamp = rawEndTimestamp
                .coerceAtLeast(session.startedAtEpochMs.coerceAtLeast(0L))
                .coerceAtMost(nowEpoch)
            val songId = session.songId
            val historyEntry = PlaybackStatsRepository.PlaybackHistoryEntry(
                songId = songId,
                timestamp = timestamp,
                title = session.title,
                artist = session.artist,
                thumbnail = session.thumbnail
            )
            _playbackHistory.update { current ->
                (listOf(historyEntry) + current).take(MAX_INTERNAL_PLAYBACK_HISTORY_ITEMS)
            }
            persistPlayback(
                songId = songId,
                listened = listened,
                timestamp = timestamp,
                forceSynchronous = forceSynchronousPersistence,
                title = session.title,
                artist = session.artist,
                thumbnail = session.thumbnail,
                genre = session.genre,
                album = session.album
            )
        } else if (listened >= 1000L) {
            // Log as negative feedback skip signal if song was started but skipped before 15 seconds.
            com.unshoo.pixelmusic.data.remote.youtube.AutoQueueManager.registerSkip(session.songId)
        }
        currentSession = null
        if (pendingVoluntarySongId == session.songId) {
            pendingVoluntarySongId = null
        }
    }

    @Synchronized
    fun onPlaybackStopped() {
        finalizeCurrentSession()
    }

    @Synchronized
    fun onCleared() {
        finalizeCurrentSession(forceSynchronousPersistence = true)
        scope = null
    }

    private fun persistPlayback(
        songId: String,
        listened: Long,
        timestamp: Long,
        forceSynchronous: Boolean,
        title: String? = null,
        artist: String? = null,
        thumbnail: String? = null,
        genre: String? = null,
        album: String? = null
    ) {
        if (forceSynchronous) {
            kotlinx.coroutines.runBlocking {
                runCatching {
                    persistPlaybackInternal(
                        songId = songId,
                        listened = listened,
                        timestamp = timestamp,
                        title = title,
                        artist = artist,
                        thumbnail = thumbnail,
                        genre = genre,
                        album = album
                    )
                }.onFailure { throwable ->
                    Timber.e(throwable, "Failed to persist listening session synchronously for song=%s", songId)
                }
            }
        } else {
            persistenceScope.launch {
                runCatching {
                    persistPlaybackInternal(
                        songId = songId,
                        listened = listened,
                        timestamp = timestamp,
                        title = title,
                        artist = artist,
                        thumbnail = thumbnail,
                        genre = genre,
                        album = album
                    )
                }.onFailure { throwable ->
                    Timber.e(throwable, "Failed to persist listening session for song=%s", songId)
                }
            }
        }
    }

    private suspend fun persistPlaybackInternal(
        songId: String,
        listened: Long,
        timestamp: Long,
        title: String? = null,
        artist: String? = null,
        thumbnail: String? = null,
        genre: String? = null,
        album: String? = null
    ) {
        dailyMixManager.recordPlay(
            songId = songId,
            songDurationMs = listened,
            timestamp = timestamp
        )
        playbackStatsRepository.recordPlayback(
            songId = songId,
            durationMs = listened,
            timestamp = timestamp,
            title = title,
            artist = artist,
            thumbnail = thumbnail,
            genre = genre,
            album = album
        )
        val ytId = if (songId.startsWith("youtube_")) {
            songId.removePrefix("youtube_")
        } else {
            val numericId = songId.toLongOrNull()
            if (numericId != null && numericId < 0) {
                val songEntity = musicDao.getSongByIdOnce(numericId)
                if (songEntity?.contentUriString?.startsWith("youtube://") == true) {
                    songEntity.contentUriString.removePrefix("youtube://")
                } else null
            } else null
        }
        if (ytId != null) {
            persistenceScope.launch(Dispatchers.IO) {
                runCatching {
                    val cpn = (1..16).map { "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".random() }.joinToString("")
                    val lengthSec = listened / 1000
                    val pingUrl = "https://music.youtube.com/api/stats/watchtime?ns=yt&el=detailpage&docid=$ytId&ver=2&c=WEB_REMIX&cver=1.20260531.05.00&cplayer=UNIPLAYER&cpn=$cpn&state=ended&st=0&et=$lengthSec&cmt=$lengthSec&rt=$lengthSec&lact=1&len=$lengthSec"
                    unshoo.ianshulyadav.pixelmusic.innertube.YouTube.sendTelemetryPing(pingUrl)
                }
            }
        }
        if (userPreferencesRepository.cacheMostPlayedSongsOfflineFlow.first()) {
            triggerAutoCacheIfNeeded(songId)
        }
    }

    /**
     * Checks whether a YouTube song has been played enough times to warrant
     * automatic offline caching. Silently enqueues [SongDownloadWorker] if:
     * - The song is sourced from YouTube (content URI starts with "youtube://")
     * - Play count has reached or exceeded [AUTO_CACHE_PLAY_COUNT_THRESHOLD]
     * - The song is not already cached locally (file_path is blank)
     */
    private suspend fun triggerAutoCacheIfNeeded(songId: String) {
        try {
            val playCount = engagementDao.getPlayCount(songId) ?: return
            if (playCount < AUTO_CACHE_PLAY_COUNT_THRESHOLD) return

            // Resolve the Room numeric ID to look up the song entity
            val numericId = songId.toLongOrNull() ?: run {
                if (songId.startsWith("youtube_")) {
                    val ytId = songId.removePrefix("youtube_")
                    -(15_000_000_000_000L + kotlin.math.abs(ytId.hashCode().toLong()))
                } else null
            } ?: return
            val songEntity = musicDao.getSongByIdOnce(numericId) ?: return

            // Only auto-cache YouTube-streamed songs that aren't already downloaded
            val contentUri = songEntity.contentUriString
            if (!contentUri.startsWith("youtube://")) return
            if (songEntity.filePath.isNotBlank()) return // Already cached

            val youtubeId = contentUri.removePrefix("youtube://")
            if (youtubeId.isBlank()) return

            val workName = "auto_cache_$youtubeId"
            val request = OneTimeWorkRequestBuilder<SongDownloadWorker>()
                .setInputData(workDataOf(SongDownloadWorker.SONG_KEY to youtubeId))
                .addTag("auto_cache")
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
            Timber.d("Auto-cache triggered for YouTube song $youtubeId (play count = $playCount)")
        } catch (e: Exception) {
            Timber.w(e, "Auto-cache check failed for song $songId")
        }
    }

    private fun accumulateRealtimeListening(session: ActiveSession, nowRealtime: Long) {
        if (!session.isPlaying) return
        val delta = (nowRealtime - session.lastRealtimeMs).coerceAtLeast(0L)
        if (delta > 0L) {
            session.accumulatedListeningMs += delta
        }
    }

    private fun normalizeDuration(durationMs: Long, fallbackDurationMs: Long): Long {
        return when {
            durationMs > 0 && durationMs != C.TIME_UNSET -> durationMs
            fallbackDurationMs > 0 && fallbackDurationMs != C.TIME_UNSET -> fallbackDurationMs
            else -> 0L
        }
    }

    companion object {
        private val MIN_SESSION_LISTEN_MS = 2_000L
        private const val MAX_INTERNAL_PLAYBACK_HISTORY_ITEMS = 500
        /** Number of plays before a YouTube song is auto-downloaded for offline use. */
        private const val AUTO_CACHE_PLAY_COUNT_THRESHOLD = 3
    }
}

/**
 * Represents an active listening session for a song.
 */
data class ActiveSession(
    val songId: String,
    var totalDurationMs: Long,
    val startedAtEpochMs: Long,
    var lastKnownPositionMs: Long,
    var accumulatedListeningMs: Long,
    var lastRealtimeMs: Long,
    var lastUpdateEpochMs: Long,
    var isPlaying: Boolean,
    val isVoluntary: Boolean,
    val title: String? = null,
    val artist: String? = null,
    val thumbnail: String? = null,
    val genre: String? = null,
    val album: String? = null
)
