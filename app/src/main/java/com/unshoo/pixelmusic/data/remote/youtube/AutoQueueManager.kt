package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.unshoo.pixelmusic.data.remote.youtube.PixelMusicHelper.printe
import com.unshoo.pixelmusic.data.remote.youtube.PixelMusicHelper.printd
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.absoluteValue
import java.util.concurrent.ConcurrentHashMap

import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.database.RelatedSongMap
import com.unshoo.pixelmusic.data.database.SongEntity
import com.unshoo.pixelmusic.data.database.toSong
import com.unshoo.pixelmusic.data.database.AlbumEntity
import com.unshoo.pixelmusic.data.database.ArtistEntity
import com.unshoo.pixelmusic.data.database.SongArtistCrossRef
import com.unshoo.pixelmusic.data.database.SourceType
import com.unshoo.pixelmusic.data.database.SongEngagementEntity
import com.unshoo.pixelmusic.data.model.ArtistRef
import com.unshoo.pixelmusic.utils.MediaItemBuilder
import com.unshoo.pixelmusic.presentation.viewmodel.ConnectivityStateHolder

object AutoQueueManager {
    @Volatile private var targetQueueSize: Int = 45
    private const val MAX_HISTORY = 60
    private const val DECAY_LAMBDA = 1.15e-9

    private var fetchJob: Job? = null
    private var debounceJob: Job? = null
    @Volatile private var fetchStartTimeMs: Long = 0L

    fun setTargetQueueSize(size: Int) {
        targetQueueSize = size.coerceIn(5, 100)
    }

    // Guards the "is a refill already running, and if not, start one" decision in
    // forceRefill() so it's a single atomic operation instead of a racy
    // check-then-act on fetchJob.
    private val refillGate = Mutex()

    @Volatile private var pendingRefillAfterCurrent = false
    @Volatile private var lastFetchedVideoId: String? = null
    @Volatile private var continuationToken: String? = null
    @Volatile private var currentWatchEndpoint: WatchEndpoint? = null
    private val addedVideoIds = mutableSetOf<String>()

    // High-performance thread-safe cache mapping local/offline song IDs to matched YouTube video IDs
    private val localToYoutubeIdMap = ConcurrentHashMap<String, String>()

    enum class Mood { CHILL, UPBEAT, DEFAULT }
    private val sessionPlayHistory = mutableListOf<String>()
    private val CHILL_GENRES = setOf("classical", "lofi", "acoustic", "ambient", "jazz", "piano", "chill", "blues", "slow")
    private val UPBEAT_GENRES = setOf("rock", "metal", "dance", "electronic", "edm", "pop", "workout", "rap", "hip hop", "house", "techno", "party")
    
    private var scope: CoroutineScope? = null
    private var contextRef: Context? = null
    private var datastoreRepository: DatastoreRepository? = null
    private var playerRef: Player? = null
    private var musicDaoRef: MusicDao? = null
    private var engagementDaoRef: com.unshoo.pixelmusic.data.database.EngagementDao? = null
    // Called after items are added to the player so DualPlayerEngine can refresh its internal queue snapshot immediately.
    private var onQueueItemsAddedCallback: (() -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.let { item ->
                scope?.launch(Dispatchers.IO) {
                    val genre = try {
                        val metadataGenre = item.mediaMetadata.genre?.toString()
                        if (!metadataGenre.isNullOrBlank()) {
                            metadataGenre
                        } else {
                            val songId = item.mediaId
                            val dao = musicDaoRef
                            val longId = songId.toLongOrNull()
                            if (longId != null) {
                                dao?.getSongByIdOnce(longId)?.genre
                            } else if (songId.startsWith("youtube_")) {
                                val videoId = songId.substringAfter("youtube_")
                                val dbId = getDatabaseIdForYoutubeId(videoId)
                                dao?.getSongByIdOnce(dbId)?.genre
                            } else {
                                null
                            }
                        }
                    } catch (e: Exception) {
                        null
                    }
                    
                    if (!genre.isNullOrBlank()) {
                        synchronized(sessionPlayHistory) {
                            sessionPlayHistory.add(genre)
                            if (sessionPlayHistory.size > 5) {
                                sessionPlayHistory.removeAt(0)
                            }
                        }
                        printd("AutoQueueManager: Active session play history: $sessionPlayHistory (Mood = ${getActiveSessionMood()})")
                    }
                }
            }
            
            // Check remaining queue depth on track transition.
            // Top the queue back up to targetQueueSize whenever the upcoming count has dropped below the target.
            // If the user deliberately jumped / seeked to a new song, force-refresh the seed so radio recommendations
            // adapt directly to the newly playing track.
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                scheduleAdaptiveRefill(forceRefresh = true)
            } else {
                val player = playerRef
                if (player != null) {
                    val remaining = computeRemainingUpcoming(player)
                    if (remaining < targetQueueSize) {
                        val adaptiveDelay = computeAdaptiveDebounceMs(player)
                        scheduleAdaptiveRefill(adaptiveDelay, forceRefresh = false)
                    }
                } else {
                    scheduleAdaptiveRefill(forceRefresh = false)
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> {
                    scheduleAdaptiveRefill(forceRefresh = false)
                }
                Player.STATE_READY -> {
                    // Playback stream established. If queue is critically low (<= 1 item),
                    // trigger an immediate adaptive top-up now that audio is playing smoothly.
                    val player = playerRef
                    if (player != null) {
                        val remaining = computeRemainingUpcoming(player)
                        if (remaining <= 1) {
                            val adaptiveDelay = computeAdaptiveDebounceMs(player)
                            scheduleAdaptiveRefill(adaptiveDelay, forceRefresh = false)
                        }
                    }
                }
            }
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return
            val player = playerRef
            val adaptiveDelay = computeAdaptiveDebounceMs(player)
            scheduleAdaptiveRefill(adaptiveDelay, forceRefresh = false)
        }
    }

    fun attach(
        player: Player,
        context: Context,
        datastoreRepo: DatastoreRepository,
        coroutineScope: CoroutineScope,
        musicDao: MusicDao,
        engagementDao: com.unshoo.pixelmusic.data.database.EngagementDao,
        onQueueItemsAdded: (() -> Unit)? = null
    ) {
        playerRef?.removeListener(playerListener)
        player.removeListener(playerListener) // Ensure idempotent attachment
        scope = coroutineScope
        contextRef = context.applicationContext
        datastoreRepository = datastoreRepo
        playerRef = player
        musicDaoRef = musicDao
        engagementDaoRef = engagementDao
        onQueueItemsAddedCallback = onQueueItemsAdded
        player.addListener(playerListener)
        printd("AutoQueueManager attached")
    }

    fun updatePlayer(newPlayer: Player) {
        val oldPlayer = playerRef
        if (oldPlayer !== newPlayer) {
            oldPlayer?.removeListener(playerListener)
            newPlayer.removeListener(playerListener)
            playerRef = newPlayer
            newPlayer.addListener(playerListener)
            printd("AutoQueueManager player updated")
        }
    }

    fun detach(player: Player?) {
        player?.removeListener(playerListener)
        playerRef?.removeListener(playerListener)
        playerRef = null
        reset()
        scope = null
        contextRef = null
        datastoreRepository = null
        musicDaoRef = null
        engagementDaoRef = null
    }

    fun reset() {
        lastFetchedVideoId = null
        continuationToken = null
        currentWatchEndpoint = null
        pendingRefillAfterCurrent = false
        synchronized(addedVideoIds) {
            addedVideoIds.clear()
        }
        synchronized(sessionPlayHistory) {
            sessionPlayHistory.clear()
        }
        synchronized(this) {
            debounceJob?.cancel()
            debounceJob = null
        }
        fetchJob?.cancel()
        fetchJob = null
        fetchStartTimeMs = 0L
    }

    /**
     * Called when auto-queue is disabled (toggle ON→OFF). Cancels any in-flight refill
     * and safely trims out auto-added songs while strictly preserving user-initiated queue items.
     */
    fun disableAndTrimQueue() {
        val autoAddedSnapshot = synchronized(addedVideoIds) { addedVideoIds.toSet() }
        reset()
        val currentScope = scope ?: return
        currentScope.launch(Dispatchers.Main) {
            val player = playerRef ?: return@launch
            val totalCount = player.mediaItemCount
            val currentIndex = player.currentMediaItemIndex
            if (totalCount <= 1) return@launch

            // Remove backwards so indices stay stable. Only remove tracks that were auto-added by AutoQueueManager.
            for (i in totalCount - 1 downTo 0) {
                if (i == currentIndex) continue
                val item = player.getMediaItemAt(i)
                val mediaId = item.mediaId
                val cleanId = normalizeSongId(mediaId)
                val wasAutoAdded = autoAddedSnapshot.any { isSameSong(it, cleanId) }
                if (wasAutoAdded) {
                    player.removeMediaItem(i)
                }
            }
        }
    }

    /**
     * Called when auto-queue is re-enabled (toggle OFF→ON).
     * Resets tracking state and re-seeds from the currently playing song, appending fresh
     * related songs on top of whatever is already queued.
     */
    fun resetAndReseedFromCurrentSong() {
        reset()
        val player = playerRef ?: return
        val currentScope = scope ?: return
        currentScope.launch(Dispatchers.IO) {
            val settings = datastoreRepository?.settings?.first() ?: return@launch
            if (!settings.autoQueueEnabled) return@launch
            val (currentId, videoId, currentTitle, currentArtist) = withContext(Dispatchers.Main) {
                val item = player.currentMediaItem ?: return@withContext null
                val mediaId = item.mediaId
                val playbackUri = item.localConfiguration?.uri?.toString()
                val metaUri = item.mediaMetadata.extras?.getString("com.unshoo.pixelmusic.external.CONTENT_URI")
                val contentUri = metaUri ?: playbackUri
                val vid = when {
                    mediaId.startsWith("youtube_") -> mediaId.substringAfter("youtube_")
                    contentUri?.startsWith("youtube://") == true -> contentUri.removePrefix("youtube://")
                    else -> null
                }
                val title = item.mediaMetadata.title?.toString().orEmpty()
                val artist = item.mediaMetadata.artist?.toString().orEmpty()
                listOf(mediaId, vid, title, artist)
            } ?: return@launch

            val cleanCurrentId = currentId as String
            var resolvedVideoId = videoId
            val songTitle = currentTitle as String
            val songArtist = currentArtist as String

            // Resolve YouTube ID via local DB or cache if not already found
            if (resolvedVideoId == null) {
                resolvedVideoId = localToYoutubeIdMap[cleanCurrentId]
            }
            if (resolvedVideoId == null) {
                val longId = cleanCurrentId.toLongOrNull()
                if (longId != null) {
                    val dbSong = musicDaoRef?.getSongByIdOnce(longId)
                    if (dbSong?.contentUriString?.startsWith("youtube://") == true) {
                        resolvedVideoId = dbSong.contentUriString.removePrefix("youtube://")
                    }
                }
            }

            // Fallback YouTube search for local / unmapped songs when online
            if (resolvedVideoId == null && isDeviceOnline() && songTitle.isNotBlank() && songArtist.isNotBlank()) {
                resolvedVideoId = tryResolveOnlineVideoId(songTitle, songArtist)?.also {
                    localToYoutubeIdMap[cleanCurrentId] = it
                }
            }

            val seedId = resolvedVideoId ?: cleanCurrentId
            synchronized(addedVideoIds) { addedVideoIds.add(seedId) }
            lastFetchedVideoId = seedId

            if (resolvedVideoId != null) {
                val endpoint = WatchEndpoint(videoId = resolvedVideoId, playlistId = "RDAMVM$resolvedVideoId")
                currentWatchEndpoint = endpoint
                continuationToken = null
            }

            // Trigger deferred refill with dynamic adaptive debounce
            scheduleAdaptiveRefill(delayMs = computeAdaptiveDebounceMsAsync(playerRef), forceRefresh = false)
        }
    }

    fun seed(endpoint: WatchEndpoint, continuation: String?, videoId: String) {
        lastFetchedVideoId = videoId
        continuationToken = continuation
        currentWatchEndpoint = endpoint
        synchronized(addedVideoIds) {
            addedVideoIds.clear()
            addedVideoIds.add(videoId)
        }
    }

    fun registerSkip(songId: String) {
        val cleanId = extractYtId(songId) ?: songId
        val ctx = contextRef ?: return
        try {
            val sharedPrefs = ctx.getSharedPreferences("auto_queue_skips", Context.MODE_PRIVATE)
            val currentCount = sharedPrefs.getInt(cleanId + "_skip_count", 0)
            val now = System.currentTimeMillis()
            sharedPrefs.edit()
                .putLong(cleanId + "_last_skip_time", now)
                .putInt(cleanId + "_skip_count", currentCount + 1)
                .apply()
            printd("AutoQueueManager: Registered skip for $cleanId (count = ${currentCount + 1})")
        } catch (e: Exception) {
            printe("AutoQueueManager: Error saving skip to SharedPreferences: ${e.message}")
        }
    }

    private suspend fun getActiveSkippedSongIds(engagementsMap: Map<String, Int>? = null): Set<String> {
        val ctx = contextRef ?: return emptySet()
        val activeIds = mutableSetOf<String>()
        try {
            val sharedPrefs = ctx.getSharedPreferences("auto_queue_skips", Context.MODE_PRIVATE)
            val allEntries = sharedPrefs.all
            val now = System.currentTimeMillis()
            val FOUR_HOURS_MS = 4 * 60 * 60 * 1000L
            val editor = sharedPrefs.edit()
            var modified = false

            val skippedSongs = allEntries.keys
                .filter { it.endsWith("_last_skip_time") }
                .map { it.removeSuffix("_last_skip_time") }

            for (songId in skippedSongs) {
                val lastSkipTime = sharedPrefs.getLong(songId + "_last_skip_time", 0L)
                if (now - lastSkipTime < FOUR_HOURS_MS) {
                    val skipCount = sharedPrefs.getInt(songId + "_skip_count", 0)

                    val dbSongId = if (songId.toLongOrNull() == null && !songId.startsWith("youtube_")) {
                        getDatabaseIdForYoutubeId(songId).toString()
                    } else {
                        songId
                    }

                    // Use preloaded engagementsMap when available to eliminate N+1 DB queries
                    val playCount = if (engagementsMap != null) {
                        val p1 = engagementsMap[songId] ?: 0
                        val p2 = engagementsMap[dbSongId] ?: 0
                        kotlin.math.max(p1, p2)
                    } else {
                        try {
                            val p1 = engagementDaoRef?.getPlayCount(songId) ?: 0
                            val p2 = engagementDaoRef?.getPlayCount(dbSongId) ?: 0
                            kotlin.math.max(p1, p2)
                        } catch (e: Exception) {
                            0
                        }
                    }

                    if (playCount > 3 && skipCount < 2) {
                        printd("AutoQueueManager: Skip bypass triggered for favorite $songId (plays = $playCount, skips = $skipCount)")
                    } else {
                        activeIds.add(songId)
                    }
                } else {
                    editor.remove(songId + "_last_skip_time")
                    editor.remove(songId + "_skip_count")
                    modified = true
                }
            }
            if (modified) {
                editor.apply()
            }
        } catch (e: Exception) {
            printe("AutoQueueManager: Error reading skips from SharedPreferences: ${e.message}")
        }
        return activeIds
    }

    private fun getActiveSessionMood(): Mood {
        val history = synchronized(sessionPlayHistory) {
            sessionPlayHistory.toList()
        }
        if (history.isEmpty()) return Mood.DEFAULT
        var chillCount = 0
        var upbeatCount = 0
        for (genre in history) {
            val norm = genre.lowercase().trim()
            if (CHILL_GENRES.any { norm.contains(it) }) chillCount++
            else if (UPBEAT_GENRES.any { norm.contains(it) }) upbeatCount++
        }
        return when {
            chillCount > upbeatCount && chillCount >= 2 -> Mood.CHILL
            upbeatCount > chillCount && upbeatCount >= 2 -> Mood.UPBEAT
            else -> Mood.DEFAULT
        }
    }

    private fun isDeviceOnline(): Boolean {
        val ctx = contextRef ?: return true
        return try {
            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                ctx,
                YoutubeHelperEntryPoint::class.java
            )
            entryPoint.connectivityStateHolder().isOnline.value
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Dynamically computes the debounce delay (200ms – 2500ms) based on queue urgency and player state.
     * Uses in-memory connectivity state without synchronous Main-thread Binder IPC.
     */
    fun computeAdaptiveDebounceMs(
        remaining: Int,
        playbackState: Int = Player.STATE_IDLE,
        isPlaying: Boolean = false
    ): Long {
        val baseDelay = when (remaining.coerceAtLeast(0)) {
            0 -> 200L
            1 -> 400L
            2 -> 400L
            3, 4 -> 400L
            else -> 1000L
        }

        var networkModifier = 0L
        if (!isDeviceOnline()) {
            return 300L // Local DB query is instantaneous; no network contention
        }

        var playerModifier = 0L
        if (playbackState == Player.STATE_BUFFERING) {
            playerModifier = 800L // Give stream chunks top network priority
        } else if (playbackState == Player.STATE_READY && isPlaying) {
            playerModifier = -150L
        }

        return (baseDelay + networkModifier + playerModifier).coerceIn(200L, 2500L)
    }

    /**
     * Accurate count of upcoming items from the current playback position respecting active shuffle order.
     * Must be called on the Main thread.
     */
    private fun computeRemainingUpcoming(player: Player): Int {
        val timeline = player.currentTimeline
        val timelineCount = timeline.windowCount
        if (timelineCount == 0) return 0
        val startIndex = player.currentMediaItemIndex
        if (startIndex == androidx.media3.common.C.INDEX_UNSET) return 0
        var remaining = 0
        var windowIndex = timeline.getNextWindowIndex(
            startIndex,
            Player.REPEAT_MODE_OFF,
            player.shuffleModeEnabled
        )
        while (windowIndex != androidx.media3.common.C.INDEX_UNSET && remaining < timelineCount) {
            remaining++
            windowIndex = timeline.getNextWindowIndex(
                windowIndex,
                Player.REPEAT_MODE_OFF,
                player.shuffleModeEnabled
            )
        }
        return remaining
    }

    fun computeAdaptiveDebounceMs(player: Player?): Long {
        if (player == null) return computeAdaptiveDebounceMs(0)
        val remaining = computeRemainingUpcoming(player)
        return computeAdaptiveDebounceMs(remaining, player.playbackState, player.isPlaying)
    }

    suspend fun computeAdaptiveDebounceMsAsync(player: Player?): Long {
        return withContext(Dispatchers.Main) {
            computeAdaptiveDebounceMs(player)
        }
    }

    fun scheduleAdaptiveRefill(delayMs: Long? = null, forceRefresh: Boolean = false) {
        val currentScope = scope ?: return
        synchronized(this) {
            val isStuck = fetchJob?.isActive == true && (System.currentTimeMillis() - fetchStartTimeMs > 25_000L)
            if (!forceRefresh && fetchJob?.isActive == true && !isStuck) {
                pendingRefillAfterCurrent = true
                return
            }
            // Cancel previous in-flight debounce timer to prevent multiple parallel refill jobs
            debounceJob?.cancel()
            debounceJob = currentScope.launch(Dispatchers.IO) {
                val actualDelay = delayMs ?: computeAdaptiveDebounceMsAsync(playerRef)
                if (actualDelay > 0L) {
                    kotlinx.coroutines.delay(actualDelay)
                }
                forceRefill(forceRefresh = forceRefresh)
            }
        }
    }

    fun scheduleRefill(delayMs: Long = 0L, forceRefresh: Boolean = false) {
        scheduleAdaptiveRefill(delayMs = if (delayMs > 0L) delayMs else null, forceRefresh = forceRefresh)
    }

    fun forceRefill(forceRefresh: Boolean) {
        val currentScope = scope ?: return
        val player = playerRef ?: return

        currentScope.launch(Dispatchers.IO) {
            val settings = datastoreRepository?.settings?.first() ?: return@launch
            if (!settings.autoQueueEnabled) return@launch

            val playerState = withContext(Dispatchers.Main) {
                if (playerRef == null) null
                else {
                    val remaining = computeRemainingUpcoming(player)
                    val currentId = player.currentMediaItem?.mediaId
                    val uriScheme = player.currentMediaItem?.localConfiguration?.uri?.scheme
                    val isLocalOrFile = uriScheme == "file" || uriScheme == "content"
                    listOf(remaining, currentId, player.mediaItemCount, isLocalOrFile)
                }
            } ?: return@launch

            val remaining = playerState[0] as Int
            val currentId = playerState[1] as? String
            val isLocalOrFile = playerState[3] as Boolean
            if (currentId == null) return@launch

            refillGate.withLock {
                val isStuck = fetchJob?.isActive == true && (System.currentTimeMillis() - fetchStartTimeMs > 25_000L)
                if (forceRefresh || isStuck) {
                    fetchJob?.cancel()
                    fetchJob = null
                    if (forceRefresh) {
                        val currentClean = normalizeSongId(currentId)
                        val isSameAsSeeded = lastFetchedVideoId?.let { isSameSong(it, currentClean) } == true
                        synchronized(addedVideoIds) {
                            addedVideoIds.retainAll { isSameSong(it, currentClean) }
                            addedVideoIds.add(currentClean)
                        }
                        // Only clear endpoint and continuation if not explicitly pre-seeded for this current track
                        if (!isSameAsSeeded || currentWatchEndpoint == null) {
                            continuationToken = null
                            currentWatchEndpoint = null
                        }
                    }
                } else {
                    if (fetchJob?.isActive == true) {
                        pendingRefillAfterCurrent = true
                        return@withLock
                    }
                }

                fetchStartTimeMs = System.currentTimeMillis()
                fetchJob = launch(Dispatchers.IO) {
                    try {
                        withTimeoutOrNull(20_000L) {
                            refillQueueLoopWithFollowUp(currentId, forceRefresh)
                        }
                    } finally {
                        fetchStartTimeMs = 0L
                        fetchJob = null
                    }
                }
            }
        }
    }

    private suspend fun tryResolveOnlineVideoId(title: String, artist: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val query = "$title $artist".trim()
                if (query.isBlank()) return@withContext null
                val searchResult = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                val songItem = searchResult?.items?.firstOrNull { it is unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem } as? unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
                songItem?.id
            } catch (e: Exception) {
                null
            }
        }
    }

    private suspend fun getYoutubeVideoId(songId: String): String? {
        if (songId.startsWith("youtube_")) {
            return songId.substringAfter("youtube_")
        }
        if (songId.startsWith("youtube://")) {
            return songId.substringAfter("youtube://")
        }
        val cached = localToYoutubeIdMap[songId]
        if (cached != null) return cached

        val longId = songId.toLongOrNull()
        if (longId != null && longId < 0) {
            val songEntity = musicDaoRef?.getSongByIdOnce(longId)
            if (songEntity?.contentUriString?.startsWith("youtube://") == true) {
                val vidId = songEntity.contentUriString.removePrefix("youtube://")
                localToYoutubeIdMap[songId] = vidId
                return vidId
            }
        }
        return null
    }

    private suspend fun getDbSongByIdString(idStr: String): Song? {
        val dao = musicDaoRef ?: return null
        val longId = idStr.toLongOrNull()
        if (longId != null) {
            return dao.getSongByIdOnce(longId)?.toSong()
        }
        if (idStr.startsWith("youtube_")) {
            val videoId = idStr.substringAfter("youtube_")
            val dbId = getDatabaseIdForYoutubeId(videoId)
            return dao.getSongByIdOnce(dbId)?.toSong()
        }
        return null
    }

    private val commonWords = setOf(
        "the", "and", "you", "for", "with", "from", "this", "that", "feat", "ft",
        "remix", "version", "original", "mix", "audio", "video", "official", "music",
        "song", "lyric", "lyrics", "acoustic", "live", "cover", "remastered", "remaster"
    )

    private fun getTitleSimilarityScore(title1: String, title2: String): Double {
        val t1 = title1.lowercase().replace(Regex("[^a-zA-Z0-9 ]"), " ")
        val t2 = title2.lowercase().replace(Regex("[^a-zA-Z0-9 ]"), " ")
        val words1 = t1.split(" ").filter { it.length > 2 && it !in commonWords }
        val words2 = t2.split(" ").filter { it.length > 2 && it !in commonWords }
        if (words1.isEmpty() || words2.isEmpty()) return 0.0
        val intersection = words1.toSet().intersect(words2.toSet())
        return intersection.size * 3.0
    }

    private fun getSongSimilarityScore(
        s1Title: String, s1Artist: String, s1Genre: String?,
        s2Title: String, s2Artist: String, s2Genre: String?
    ): Double {
        var score = 0.0
        val a1 = s1Artist.lowercase().trim()
        val a2 = s2Artist.lowercase().trim()
        if (a1 == a2) {
            score += 10.0
        } else if (a1.contains(a2) || a2.contains(a1)) {
            score += 6.0
        }
        
        score += getTitleSimilarityScore(s1Title, s2Title)
        
        val g1 = s1Genre?.lowercase()?.trim().orEmpty()
        val g2 = s2Genre?.lowercase()?.trim().orEmpty()
        if (g1.isNotEmpty() && g2.isNotEmpty() && 
            g1 != "youtube" && g1 != "youtube music" && 
            g2 != "youtube" && g2 != "youtube music") {
            if (g1 == g2) {
                score += 4.0
            } else if (g1.contains(g2) || g2.contains(g1)) {
                score += 2.0
            }
        }
        
        return score
    }

    private fun isSameSong(id1: String, id2: String): Boolean {
        if (id1 == id2) return true
        val clean1 = normalizeSongId(id1)
        val clean2 = normalizeSongId(id2)
        if (clean1 == clean2) return true

        val long1 = clean1.toLongOrNull()
        val long2 = clean2.toLongOrNull()

        // 1. YouTube DB mapping comparison
        if (long1 != null && long1 < 0) {
            val raw2 = clean2.removePrefix("youtube_").removePrefix("youtube://")
            if (raw2.length == 11 && getDatabaseIdForYoutubeId(raw2) == long1) {
                return true
            }
        }
        if (long2 != null && long2 < 0) {
            val raw1 = clean1.removePrefix("youtube_").removePrefix("youtube://")
            if (raw1.length == 11 && getDatabaseIdForYoutubeId(raw1) == long2) {
                return true
            }
        }

        // 2. Resolve cached YouTube IDs if mapped (lock-free via ConcurrentHashMap)
        val ytId1 = localToYoutubeIdMap[id1] ?: localToYoutubeIdMap[clean1]
        val ytId2 = localToYoutubeIdMap[id2] ?: localToYoutubeIdMap[clean2]
        if (ytId1 != null && ytId2 != null && ytId1 == ytId2) return true
        
        val raw1 = clean1.removePrefix("youtube_").removePrefix("youtube://")
        val raw2 = clean2.removePrefix("youtube_").removePrefix("youtube://")
        if (raw1.length == 11 && raw1 == ytId2) return true
        if (raw2.length == 11 && raw2 == ytId1) return true

        return false
    }

    private fun normalizeSongId(id: String): String {
        return when {
            id.startsWith("youtube_") -> id.substringAfter("youtube_")
            id.startsWith("youtube://") -> id.substringAfter("youtube://")
            else -> id
        }
    }

    private suspend fun addToAddedVideoIds(songId: String) {
        val cleanId = getYoutubeVideoId(songId) ?: songId
        synchronized(addedVideoIds) {
            addedVideoIds.add(cleanId)
            if (addedVideoIds.size > MAX_HISTORY) {
                val excess = addedVideoIds.size - MAX_HISTORY
                val toRemove = addedVideoIds.take(excess)
                addedVideoIds.removeAll(toRemove.toSet())
            }
        }
    }

    private fun extractYtId(id: String): String? {
        if (id.startsWith("youtube_")) return id.substringAfter("youtube_")
        if (id.startsWith("youtube://")) return id.substringAfter("youtube://")
        val longVal = id.toLongOrNull()
        if (longVal != null) {
            return null
        }
        return id
    }

    private suspend fun getContextualFamiliarSongs(
        currentSong: SongEntity?,
        currentQueueIds: Set<String>,
        avoidIds: Set<String>,
        engagements: List<SongEngagementEntity>? = null
    ): List<Song> {
        val dao = musicDaoRef ?: return emptyList()
        val engagementDao = engagementDaoRef

        val favoriteSongs = try {
            dao.getFavoriteSongsList(emptyList(), false, 0).map { it.toSong() }
        } catch (e: Exception) {
            emptyList()
        }

        val allEngs = engagements ?: try {
            engagementDao?.getAllEngagements() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }

        val playedMultipleTimesSongs = mutableListOf<Song>()
        val engagementsMap = allEngs.associateBy { it.songId }
        val idsPlayedMultiple = allEngs.filter { it.playCount >= 2 }.map { it.songId }.toSet()

        val multipleLongIds = idsPlayedMultiple.mapNotNull { idStr ->
            idStr.toLongOrNull() ?: if (idStr.startsWith("youtube_")) {
                getDatabaseIdForYoutubeId(idStr.substringAfter("youtube_"))
            } else null
        }
        if (multipleLongIds.isNotEmpty()) {
            val batchSongs = dao.getSongsByIdsListSimple(multipleLongIds).map { it.toSong() }
            playedMultipleTimesSongs.addAll(batchSongs)
        }

        val combined = (favoriteSongs + playedMultipleTimesSongs).distinctBy { it.id }

        val now = System.currentTimeMillis()
        fun calculateAffinityScore(song: Song): Double {
            val songIdStr = song.id
            val rawId = extractYtId(songIdStr) ?: songIdStr
            val eng = engagementsMap[songIdStr] ?: engagementsMap[rawId]
            var score = 0.0
            if (song.isFavorite) score += 5.0
            if (eng != null) {
                val timeDiffMs = (now - eng.lastPlayedTimestamp).coerceAtLeast(0L)
                val decay = kotlin.math.exp(-DECAY_LAMBDA * timeDiffMs)
                score += eng.playCount * decay
            }
            return score
        }

        val sortedCandidates = combined.sortedByDescending { calculateAffinityScore(it) }

        val currentGenre = currentSong?.genre
        val currentArtist = currentSong?.artistName
        val currentArtistId = currentSong?.artistId

        val contextualMatches = sortedCandidates.filter { song ->
            val songIdStr = song.id
            val isAlreadyInQueue = currentQueueIds.any { isSameSong(it, songIdStr) }
            val isAvoid = avoidIds.any { isSameSong(it, songIdStr) }

            val matchesGenre = currentGenre != null && song.genre != null && 
                               song.genre.equals(currentGenre, ignoreCase = true) && 
                               !song.genre.equals("YouTube", ignoreCase = true) &&
                               !song.genre.equals("YouTube Music", ignoreCase = true)
            val matchesArtist = (currentArtist != null && song.artist.equals(currentArtist, ignoreCase = true)) || 
                                (currentArtistId != null && currentArtistId != -1L && song.artistId == currentArtistId)

            !isAlreadyInQueue && !isAvoid && (matchesGenre || matchesArtist)
        }

        return contextualMatches.take(15)
    }

    suspend fun buildMixQueue(seedSong: Song, onlineRelated: List<Song>): List<Song> {
        if (onlineRelated.isNotEmpty()) {
            return (listOf(seedSong) + onlineRelated).distinctBy { it.youtubeId ?: it.id }
        }
        val dao = musicDaoRef ?: return (listOf(seedSong) + onlineRelated).distinctBy { it.id }
        val engagementDao = engagementDaoRef

        val dislikedSongIds = try { dao.getDislikedSongIds().toSet() } catch (_: Exception) { emptySet() }
        val dislikedYoutubeIds = try { dao.getDislikedYoutubeIds().toSet() } catch (_: Exception) { emptySet() }

        val engagements = try {
            engagementDao?.getAllEngagements() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val engagementsMap = engagements.associate { it.songId to it.playCount }
        val highlyRotatedIds = engagements.filter { it.playCount > 40 }.map { it.songId }.toSet()

        val recents = try {
            engagementDao?.getRecentlyPlayedSongs(100) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val recentlyPlayedIds = recents.map { it.songId }.toSet()

        val settings = datastoreRepository?.settings?.first() ?: return (listOf(seedSong) + onlineRelated).distinctBy { it.id }
        val activeSkips = getActiveSkippedSongIds(engagementsMap)
        val avoidIds = if (settings.avoidRepetitiveSongs) {
            highlyRotatedIds + recentlyPlayedIds + activeSkips
        } else {
            highlyRotatedIds + activeSkips
        }

        val avoidLongIds = avoidIds.mapNotNull { id ->
            id.toLongOrNull() ?: getDatabaseIdForYoutubeId(normalizeSongId(id))
        }
        val avoidSongs = if (avoidLongIds.isNotEmpty()) {
            dao.getSongsByIdsListSimple(avoidLongIds)
        } else {
            emptyList()
        }
        val avoidKeys = avoidSongs.mapNotNull { s ->
            val title = s.title.lowercase().trim()
            val artist = s.artistName.lowercase().trim()
            if (title.isNotEmpty() && artist.isNotEmpty()) "$title|$artist" else null
        }.toSet()

        val seedTitleKey = seedSong.title.lowercase().trim()
        val seedArtistKey = seedSong.artist.lowercase().trim()
        val currentQueueKeys = if (seedTitleKey.isNotEmpty() && seedArtistKey.isNotEmpty()) {
            setOf("$seedTitleKey|$seedArtistKey")
        } else {
            emptySet()
        }
        val currentQueueIds = setOf(seedSong.id)

        val seedLongId = seedSong.id.toLongOrNull()
        val currentSongEntity = if (seedLongId != null) dao.getSongByIdOnce(seedLongId) else null
        
        var resolvedGenre = currentSongEntity?.genre ?: seedSong.genre
        if (resolvedGenre.isNullOrBlank() && seedSong.title.isNotBlank() && seedSong.artist.isNotBlank()) {
            val dbMatch = dao.getSongsByArtistName(seedSong.artist, 1).firstOrNull()
            if (dbMatch != null) {
                resolvedGenre = dbMatch.genre
            }
        }

        val discovered = if (onlineRelated.isNotEmpty()) {
            onlineRelated
        } else {
            fetchLocalRelated(seedSong.id, currentQueueIds)
        }

        val sameArtistSongs = if (currentSongEntity?.artistName != null || seedSong.artist.isNotBlank()) {
            val artistToQuery = currentSongEntity?.artistName ?: seedSong.artist
            dao.getSongsByArtistName(artistToQuery, 20).map { it.toSong() }
        } else {
            emptyList()
        }

        val sameGenreSongs = if (!resolvedGenre.isNullOrBlank() && !resolvedGenre.equals("YouTube", ignoreCase = true)) {
            dao.getSongsByGenre(resolvedGenre, seedLongId ?: 0L, 50).map { it.toSong() }
        } else {
            emptyList()
        }

        val familiarSongs = getContextualFamiliarSongs(currentSongEntity, currentQueueIds, avoidIds, engagements)

        val finalSongsToAdd = mutableListOf<Song>()
        finalSongsToAdd.add(seedSong)

        val addedArtists = mutableMapOf<String, Int>()
        addedArtists[seedSong.artist.lowercase().trim()] = 1

        val addedKeys = mutableSetOf<String>()
        if (seedTitleKey.isNotEmpty() && seedArtistKey.isNotEmpty()) {
            addedKeys.add("$seedTitleKey|$seedArtistKey")
        }

        val activeMood = getActiveSessionMood()
        fun canAddSong(song: Song): Boolean {
            val songIdStr = song.id
            val ytId = song.youtubeId
            if (dislikedSongIds.contains(songIdStr.toLongOrNull() ?: 0L) || (ytId != null && dislikedYoutubeIds.contains(ytId))) {
                return false
            }
            val isInQueue = currentQueueIds.any { isSameSong(it, songIdStr) }
            val isAvoid = avoidIds.any { isSameSong(it, songIdStr) }
            val isAlreadyAdded = finalSongsToAdd.any { isSameSong(it.id, songIdStr) }
            if (isInQueue || isAvoid || isAlreadyAdded) return false

            val cleanTitle = song.title.lowercase().trim()
            val cleanArtist = song.artist.lowercase().trim()
            if (cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty()) {
                val key = "$cleanTitle|$cleanArtist"
                if (currentQueueKeys.contains(key) || avoidKeys.contains(key) || addedKeys.contains(key)) {
                    return false
                }
            }

            val songGenre = song.genre?.lowercase()?.trim().orEmpty()
            if (activeMood == Mood.CHILL) {
                if (UPBEAT_GENRES.any { songGenre.contains(it) }) return false
            } else if (activeMood == Mood.UPBEAT) {
                if (CHILL_GENRES.any { songGenre.contains(it) }) return false
            }

            val artistKey = song.artist.lowercase().trim()
            val artistCount = addedArtists[artistKey] ?: 0
            return artistCount < 2
        }

        val discoveryCandidates = sameGenreSongs.filter { song ->
            (engagementsMap[song.id] ?: 0) == 0 && !song.isFavorite
        }.filter { canAddSong(it) }.shuffled().toMutableList()

        val popularGenreCandidates = sameGenreSongs.filter { song ->
            (engagementsMap[song.id] ?: 0) > 0 || song.isFavorite
        }.filter { canAddSong(it) }.shuffled().toMutableList()

        val sameArtistCandidates = sameArtistSongs.filter { canAddSong(it) }.shuffled().toMutableList()
        val relatedCandidates = discovered.filter { canAddSong(it) }.toMutableList()
        val familiarCandidates = familiarSongs.filter { canAddSong(it) }.toMutableList()

        var addedCount = 1
        val targetBatchSize = 50

        while (addedCount < targetBatchSize) {
            var addedInThisRound = false

            for (i in 0 until 2) {
                if (relatedCandidates.isNotEmpty()) {
                    val s = relatedCandidates.removeAt(0)
                    if (canAddSong(s)) {
                        finalSongsToAdd.add(s)
                        addedArtists[s.artist.lowercase().trim()] = (addedArtists[s.artist.lowercase().trim()] ?: 0) + 1
                        val cleanTitle = s.title.lowercase().trim()
                        val cleanArtist = s.artist.lowercase().trim()
                        if (cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty()) {
                            addedKeys.add("$cleanTitle|$cleanArtist")
                        }
                        addedCount++
                        addedInThisRound = true
                    }
                }
            }

            if (addedCount >= targetBatchSize) break

            for (i in 0 until 2) {
                if (discoveryCandidates.isNotEmpty()) {
                    val s = discoveryCandidates.removeAt(0)
                    if (canAddSong(s)) {
                        finalSongsToAdd.add(s)
                        addedArtists[s.artist.lowercase().trim()] = (addedArtists[s.artist.lowercase().trim()] ?: 0) + 1
                        val cleanTitle = s.title.lowercase().trim()
                        val cleanArtist = s.artist.lowercase().trim()
                        if (cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty()) {
                            addedKeys.add("$cleanTitle|$cleanArtist")
                        }
                        addedCount++
                        addedInThisRound = true
                    }
                }
            }

            if (addedCount >= targetBatchSize) break

            if (sameArtistCandidates.isNotEmpty()) {
                val s = sameArtistCandidates.removeAt(0)
                if (canAddSong(s)) {
                    finalSongsToAdd.add(s)
                    addedArtists[s.artist.lowercase().trim()] = (addedArtists[s.artist.lowercase().trim()] ?: 0) + 1
                    val cleanTitle = s.title.lowercase().trim()
                    val cleanArtist = s.artist.lowercase().trim()
                    if (cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty()) {
                        addedKeys.add("$cleanTitle|$cleanArtist")
                    }
                    addedCount++
                    addedInThisRound = true
                }
            }

            if (addedCount >= targetBatchSize) break

            if (popularGenreCandidates.isNotEmpty()) {
                val s = popularGenreCandidates.removeAt(0)
                if (canAddSong(s)) {
                    finalSongsToAdd.add(s)
                    addedArtists[s.artist.lowercase().trim()] = (addedArtists[s.artist.lowercase().trim()] ?: 0) + 1
                    val cleanTitle = s.title.lowercase().trim()
                    val cleanArtist = s.artist.lowercase().trim()
                    if (cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty()) {
                        addedKeys.add("$cleanTitle|$cleanArtist")
                    }
                    addedCount++
                    addedInThisRound = true
                }
            }

            if (addedCount >= targetBatchSize) break

            if (familiarCandidates.isNotEmpty()) {
                val s = familiarCandidates.removeAt(0)
                if (canAddSong(s)) {
                    finalSongsToAdd.add(s)
                    addedArtists[s.artist.lowercase().trim()] = (addedArtists[s.artist.lowercase().trim()] ?: 0) + 1
                    val cleanTitle = s.title.lowercase().trim()
                    val cleanArtist = s.artist.lowercase().trim()
                    if (cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty()) {
                        addedKeys.add("$cleanTitle|$cleanArtist")
                    }
                    addedCount++
                    addedInThisRound = true
                }
            }

            if (!addedInThisRound) break
        }

        if (finalSongsToAdd.size < targetBatchSize) {
            val remainingCandidates = (discovered + sameGenreSongs + familiarSongs).distinctBy { it.id }
            for (s in remainingCandidates) {
                val songIdStr = s.id
                val isInQueue = currentQueueIds.any { isSameSong(it, songIdStr) }
                val isAlreadyAdded = finalSongsToAdd.any { isSameSong(it.id, songIdStr) }
                val isAvoid = avoidIds.any { isSameSong(it, songIdStr) }
                if (!isInQueue && !isAlreadyAdded && !isAvoid) {
                    val cleanTitle = s.title.lowercase().trim()
                    val cleanArtist = s.artist.lowercase().trim()
                    val isDuplicateTitleArtist = cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty() && 
                        (currentQueueKeys.contains("$cleanTitle|$cleanArtist") || avoidKeys.contains("$cleanTitle|$cleanArtist") || addedKeys.contains("$cleanTitle|$cleanArtist"))
                    
                    if (!isDuplicateTitleArtist) {
                        finalSongsToAdd.add(s)
                        if (cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty()) {
                            addedKeys.add("$cleanTitle|$cleanArtist")
                        }
                        if (finalSongsToAdd.size >= targetBatchSize) break
                    }
                }
            }
        }

        return finalSongsToAdd
    }

    /**
     * Runs [refillQueueLoop] and, if another refill request arrived while this one was running,
     * schedules a follow-up pass asynchronously so track-change refills are never dropped.
     */
    private suspend fun refillQueueLoopWithFollowUp(currentId: String, forceRefresh: Boolean) {
        try {
            refillQueueLoop(currentId, forceRefresh)
        } finally {
            val shouldFollowUp = pendingRefillAfterCurrent
            pendingRefillAfterCurrent = false
            if (shouldFollowUp) {
                scope?.launch(Dispatchers.IO) {
                    forceRefill(forceRefresh = false)
                }
            }
        }
    }

    private suspend fun refillQueueLoop(currentId: String, forceRefresh: Boolean) {
        val player = playerRef ?: return
        val dao = musicDaoRef ?: return
        val engagementDao = engagementDaoRef

        val dislikedSongIds = try { dao.getDislikedSongIds().toSet() } catch (_: Exception) { emptySet() }
        val dislikedYoutubeIds = try { dao.getDislikedYoutubeIds().toSet() } catch (_: Exception) { emptySet() }

        // 1. Identify if current song is YouTube or local/offline, and resolve video ID
        val currentMediaItem = withContext(Dispatchers.Main) { player.currentMediaItem }
        val currentTitle = currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
        val currentArtist = currentMediaItem?.mediaMetadata?.artist?.toString().orEmpty()
        val playbackUriStr = currentMediaItem?.localConfiguration?.uri?.toString()
        val metadataUriStr = currentMediaItem?.mediaMetadata?.extras?.getString("com.unshoo.pixelmusic.external.CONTENT_URI")
        val contentUriStr = metadataUriStr ?: playbackUriStr

        var rawVideoId: String? = if (currentId.startsWith("youtube_")) {
            currentId.substringAfter("youtube_")
        } else if (contentUriStr?.startsWith("youtube://") == true) {
            contentUriStr.removePrefix("youtube://")
        } else {
            null
        }

        if (rawVideoId == null) {
            rawVideoId = localToYoutubeIdMap[currentId]
        }

        if (rawVideoId == null) {
            val songId = currentId.toLongOrNull()
            if (songId != null) {
                val dbSong = dao.getSongByIdOnce(songId)
                if (dbSong?.contentUriString?.startsWith("youtube://") == true) {
                    rawVideoId = dbSong.contentUriString.removePrefix("youtube://")
                    localToYoutubeIdMap[currentId] = rawVideoId
                }
            }
        }

        val isOnline = isDeviceOnline()

        // Online search fallback for local/playlist songs without a known YouTube video ID
        if (rawVideoId == null && isOnline && currentTitle.isNotBlank() && currentArtist.isNotBlank()) {
            rawVideoId = tryResolveOnlineVideoId(currentTitle, currentArtist)?.also {
                localToYoutubeIdMap[currentId] = it
            }
        }

        val isLocal = rawVideoId == null
        val resolvedVideoId = rawVideoId ?: ""
        val activeId = if (isLocal) currentId else resolvedVideoId

        if (forceRefresh) {
            lastFetchedVideoId = activeId
            if (currentWatchEndpoint == null && !isLocal && resolvedVideoId.isNotBlank()) {
                currentWatchEndpoint = WatchEndpoint(videoId = resolvedVideoId, playlistId = "RDAMVM$resolvedVideoId")
            }
            synchronized(addedVideoIds) {
                addedVideoIds.add(activeId)
            }
        } else {
            if (lastFetchedVideoId == null) {
                lastFetchedVideoId = activeId
                if (currentWatchEndpoint == null && !isLocal && resolvedVideoId.isNotBlank()) {
                    currentWatchEndpoint = WatchEndpoint(videoId = resolvedVideoId, playlistId = "RDAMVM$resolvedVideoId")
                }
                synchronized(addedVideoIds) {
                    addedVideoIds.add(activeId)
                }
            }
        }

        // Hoist expensive static database queries OUTSIDE the loop to prevent SQLite connection pool thrashing
        val settings = datastoreRepository?.settings?.first() ?: return
        val engagements = try {
            engagementDao?.getAllEngagements() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val engagementsMap = engagements.associate { it.songId to it.playCount }
        val highlyRotatedIds = engagements.filter { it.playCount > 40 }.map { it.songId }.toSet()

        val recents = try {
            engagementDao?.getRecentlyPlayedSongs(100) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        val recentlyPlayedIds = recents.map { it.songId }.toSet()

        val activeSkips = getActiveSkippedSongIds(engagementsMap)
        val avoidIds = if (settings.avoidRepetitiveSongs) {
            highlyRotatedIds + recentlyPlayedIds + activeSkips
        } else {
            highlyRotatedIds + activeSkips
        }

        val avoidLongIds = avoidIds.mapNotNull { id ->
            id.toLongOrNull() ?: getDatabaseIdForYoutubeId(normalizeSongId(id))
        }
        val avoidSongs = if (avoidLongIds.isNotEmpty()) {
            dao.getSongsByIdsListSimple(avoidLongIds)
        } else {
            emptyList()
        }
        val avoidKeys = avoidSongs.mapNotNull { s ->
            val title = s.title.lowercase().trim()
            val artist = s.artistName.lowercase().trim()
            if (title.isNotEmpty() && artist.isNotEmpty()) "$title|$artist" else null
        }.toSet()

        // Resolve current playing song information once
        val currentSongLongId = currentId.toLongOrNull()
        val currentSongEntity = if (currentSongLongId != null) dao.getSongByIdOnce(currentSongLongId) else null
        
        var resolvedGenre = currentSongEntity?.genre
        if (resolvedGenre.isNullOrBlank() && currentTitle.isNotBlank() && currentArtist.isNotBlank()) {
            val dbMatch = dao.getSongsByArtistName(currentArtist, 1).firstOrNull()
            if (dbMatch != null) {
                resolvedGenre = dbMatch.genre
            }
        }

        val sameArtistSongs = if (currentSongEntity?.artistName != null || currentArtist.isNotBlank()) {
            val artistToQuery = currentSongEntity?.artistName ?: currentArtist
            dao.getSongsByArtistName(artistToQuery, 20).map { it.toSong() }
        } else {
            emptyList()
        }

        val sameGenreSongs = if (!resolvedGenre.isNullOrBlank() && !resolvedGenre.equals("YouTube", ignoreCase = true)) {
            dao.getSongsByGenre(resolvedGenre, currentSongLongId ?: 0L, 50).map { it.toSong() }
        } else {
            emptyList()
        }

        var loopCount = 0
        var emptyFetchCount = 0

        while (true) {
            val playerState = withContext(Dispatchers.Main) {
                if (playerRef == null) null
                else {
                    val remaining = computeRemainingUpcoming(player)
                    Pair(remaining, player.mediaItemCount)
                }
            } ?: break

            val (remaining, totalCount) = playerState
            if (remaining >= targetQueueSize) {
                printd("AutoQueueManager: Queue is full. Current remaining: $remaining (>= $targetQueueSize)")
                break
            }

            if (loopCount >= 15 || emptyFetchCount >= 3) {
                printd("AutoQueueManager: Breaking — loopCount=$loopCount, emptyFetchCount=$emptyFetchCount")
                break
            }
            loopCount++

            printd("AutoQueueManager: Refilling queue. Remaining: $remaining, Target: $targetQueueSize, Loop: $loopCount")

            val currentQueueIds = withContext(Dispatchers.Main) {
                if (playerRef == null) emptySet()
                else (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId }.toSet()
            }

            val currentQueueKeys = withContext(Dispatchers.Main) {
                if (playerRef == null) emptySet()
                else (0 until player.mediaItemCount).mapNotNull { index ->
                    val item = player.getMediaItemAt(index)
                    val title = item.mediaMetadata.title?.toString()?.lowercase()?.trim() ?: ""
                    val artist = item.mediaMetadata.artist?.toString()?.lowercase()?.trim() ?: ""
                    if (title.isNotEmpty() && artist.isNotEmpty()) "$title|$artist" else null
                }.toSet()
            }

            // Discover related tracks (first priority is online YouTube Music for online tracks, and local related for offline tracks)
            var discovered = emptyList<Song>()
            
            if (isOnline && !isLocal && resolvedVideoId.isNotBlank()) {
                val related = fetchOnlineRelated(resolvedVideoId)
                if (related.isNotEmpty()) {
                    saveRelatedSongsToDb(resolvedVideoId, related, player)
                    val needed = (targetQueueSize - remaining).coerceAtLeast(1)
                    val batchKeys = mutableSetOf<String>()
                    val batchIds = mutableSetOf<String>()
                    val filteredRelated = related.filter { song ->
                        val songIdStr = song.id
                        val isInQueue = currentQueueIds.any { isSameSong(it, songIdStr) }
                        val isAvoid = avoidIds.any { isSameSong(it, songIdStr) }
                        val alreadyTracked = synchronized(addedVideoIds) {
                            addedVideoIds.any { isSameSong(it, songIdStr) }
                        }
                        val cleanTitle = song.title.lowercase().trim()
                        val cleanArtist = song.artist.lowercase().trim()
                        val key = if (cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty()) "$cleanTitle|$cleanArtist" else null
                        val isDuplicateTitleArtist = key != null &&
                            (currentQueueKeys.contains(key) || avoidKeys.contains(key) || batchKeys.contains(key))
                        val isDuplicateInBatch = batchIds.any { isSameSong(it, songIdStr) }

                        val canAdd = !isInQueue && !isAvoid && !alreadyTracked && !isDuplicateTitleArtist && !isDuplicateInBatch
                        if (canAdd) {
                            batchIds.add(songIdStr)
                            if (key != null) batchKeys.add(key)
                        }
                        canAdd
                    }.take(needed)
                    discovered = filteredRelated
                    if (filteredRelated.isNotEmpty()) {
                        val mediaItems = filteredRelated.map { MediaItemBuilder.build(it) }
                        withContext(Dispatchers.Main) {
                            player.addMediaItems(mediaItems)
                            onQueueItemsAddedCallback?.invoke()
                        }
                        for (song in filteredRelated) {
                            addToAddedVideoIds(song.id)
                        }
                        printd("AutoQueueManager: Appended ${mediaItems.size} online mix radio songs directly.")
                        emptyFetchCount = 0
                        continue
                    }
                    emptyFetchCount++
                    if (emptyFetchCount >= 3) {
                        printd("AutoQueueManager: 3 consecutive duplicate pages — resetting pagination for next refill")
                        continuationToken = null
                        currentWatchEndpoint = null
                    }
                    continue
                } else {
                    discovered = fetchLocalRelated(currentId, currentQueueIds)
                }
            } else {
                discovered = fetchLocalRelated(currentId, currentQueueIds)
            }

            // Extract familiar contextual songs using in-memory engagements
            val familiarSongs = getContextualFamiliarSongs(currentSongEntity, currentQueueIds, avoidIds, engagements)

            val finalSongsToAdd = mutableListOf<Song>()
            val addedArtists = mutableMapOf<String, Int>()
            val addedKeys = mutableSetOf<String>()

            val activeMood = getActiveSessionMood()
            fun canAddSong(song: Song): Boolean {
                val songIdStr = song.id
                val ytId = song.youtubeId
                if (dislikedSongIds.contains(songIdStr.toLongOrNull() ?: 0L) || (ytId != null && dislikedYoutubeIds.contains(ytId))) {
                    return false
                }
                val isInQueue = currentQueueIds.any { isSameSong(it, songIdStr) }
                val isAvoid = avoidIds.any { isSameSong(it, songIdStr) }
                val isAlreadyAdded = finalSongsToAdd.any { isSameSong(it.id, songIdStr) }
                val isAlreadyInAddedVideoIds = synchronized(addedVideoIds) {
                    addedVideoIds.any { isSameSong(it, songIdStr) }
                }
                if (isInQueue || isAvoid || isAlreadyAdded || isAlreadyInAddedVideoIds) return false

                val cleanTitle = song.title.lowercase().trim()
                val cleanArtist = song.artist.lowercase().trim()
                if (cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty()) {
                    val key = "$cleanTitle|$cleanArtist"
                    if (currentQueueKeys.contains(key) || avoidKeys.contains(key) || addedKeys.contains(key)) {
                        return false
                    }
                }

                val songGenre = song.genre?.lowercase()?.trim().orEmpty()
                if (activeMood == Mood.CHILL) {
                    if (UPBEAT_GENRES.any { songGenre.contains(it) }) return false
                } else if (activeMood == Mood.UPBEAT) {
                    if (CHILL_GENRES.any { songGenre.contains(it) }) return false
                }

                val artistKey = song.artist.lowercase().trim()
                val artistCount = addedArtists[artistKey] ?: 0
                return artistCount < 2
            }

            val discoveryCandidates = sameGenreSongs.filter { song ->
                (engagementsMap[song.id] ?: 0) == 0 && !song.isFavorite
            }.filter { canAddSong(it) }.shuffled().toMutableList()

            val popularGenreCandidates = sameGenreSongs.filter { song ->
                (engagementsMap[song.id] ?: 0) > 0 || song.isFavorite
            }.filter { canAddSong(it) }.shuffled().toMutableList()

            val sameArtistCandidates = sameArtistSongs.filter { canAddSong(it) }.shuffled().toMutableList()
            val relatedCandidates = discovered.filter { canAddSong(it) }.toMutableList()
            val familiarCandidates = familiarSongs.filter { canAddSong(it) }.toMutableList()

            var addedCount = 0
            val targetBatchSize = 12

            while (addedCount < targetBatchSize) {
                var addedInThisRound = false

                for (i in 0 until 2) {
                    if (relatedCandidates.isNotEmpty()) {
                        val s = relatedCandidates.removeAt(0)
                        if (canAddSong(s)) {
                            finalSongsToAdd.add(s)
                            addToAddedVideoIds(s.id)
                            addedArtists[s.artist.lowercase().trim()] = (addedArtists[s.artist.lowercase().trim()] ?: 0) + 1
                            val cleanTitle = s.title.lowercase().trim()
                            val cleanArtist = s.artist.lowercase().trim()
                            if (cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty()) {
                                addedKeys.add("$cleanTitle|$cleanArtist")
                            }
                            addedCount++
                            addedInThisRound = true
                        }
                    }
                }

                if (addedCount >= targetBatchSize) break

                for (i in 0 until 2) {
                    if (discoveryCandidates.isNotEmpty()) {
                        val s = discoveryCandidates.removeAt(0)
                        if (canAddSong(s)) {
                            finalSongsToAdd.add(s)
                            addToAddedVideoIds(s.id)
                            addedArtists[s.artist.lowercase().trim()] = (addedArtists[s.artist.lowercase().trim()] ?: 0) + 1
                            val cleanTitle = s.title.lowercase().trim()
                            val cleanArtist = s.artist.lowercase().trim()
                            if (cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty()) {
                                addedKeys.add("$cleanTitle|$cleanArtist")
                            }
                            addedCount++
                            addedInThisRound = true
                        }
                    }
                }

                if (addedCount >= targetBatchSize) break

                if (sameArtistCandidates.isNotEmpty()) {
                    val s = sameArtistCandidates.removeAt(0)
                    if (canAddSong(s)) {
                        finalSongsToAdd.add(s)
                        addToAddedVideoIds(s.id)
                        addedArtists[s.artist.lowercase().trim()] = (addedArtists[s.artist.lowercase().trim()] ?: 0) + 1
                        val cleanTitle = s.title.lowercase().trim()
                        val cleanArtist = s.artist.lowercase().trim()
                        if (cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty()) {
                            addedKeys.add("$cleanTitle|$cleanArtist")
                        }
                        addedCount++
                        addedInThisRound = true
                    }
                }

                if (addedCount >= targetBatchSize) break

                if (popularGenreCandidates.isNotEmpty()) {
                    val s = popularGenreCandidates.removeAt(0)
                    if (canAddSong(s)) {
                        finalSongsToAdd.add(s)
                        addToAddedVideoIds(s.id)
                        addedArtists[s.artist.lowercase().trim()] = (addedArtists[s.artist.lowercase().trim()] ?: 0) + 1
                        val cleanTitle = s.title.lowercase().trim()
                        val cleanArtist = s.artist.lowercase().trim()
                        if (cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty()) {
                            addedKeys.add("$cleanTitle|$cleanArtist")
                        }
                        addedCount++
                        addedInThisRound = true
                    }
                }

                if (addedCount >= targetBatchSize) break

                if (familiarCandidates.isNotEmpty()) {
                    val s = familiarCandidates.removeAt(0)
                    if (canAddSong(s)) {
                        finalSongsToAdd.add(s)
                        addToAddedVideoIds(s.id)
                        addedArtists[s.artist.lowercase().trim()] = (addedArtists[s.artist.lowercase().trim()] ?: 0) + 1
                        val cleanTitle = s.title.lowercase().trim()
                        val cleanArtist = s.artist.lowercase().trim()
                        if (cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty()) {
                            addedKeys.add("$cleanTitle|$cleanArtist")
                        }
                        addedCount++
                        addedInThisRound = true
                    }
                }

                if (!addedInThisRound) break
            }

            if (finalSongsToAdd.size < 6) {
                val remainingCandidates = (discovered + sameGenreSongs + familiarSongs).distinctBy { it.id }
                for (s in remainingCandidates) {
                    val songIdStr = s.id
                    val isInQueue = currentQueueIds.any { isSameSong(it, songIdStr) }
                    val isAlreadyAdded = finalSongsToAdd.any { isSameSong(it.id, songIdStr) }
                    val isAvoid = avoidIds.any { isSameSong(it, songIdStr) }
                    val isAlreadyInAddedVideoIds = synchronized(addedVideoIds) {
                        addedVideoIds.any { isSameSong(it, songIdStr) }
                    }
                    if (!isInQueue && !isAlreadyAdded && !isAvoid && !isAlreadyInAddedVideoIds) {
                        val cleanTitle = s.title.lowercase().trim()
                        val cleanArtist = s.artist.lowercase().trim()
                        val isDuplicateTitleArtist = cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty() && 
                            (currentQueueKeys.contains("$cleanTitle|$cleanArtist") || avoidKeys.contains("$cleanTitle|$cleanArtist") || addedKeys.contains("$cleanTitle|$cleanArtist"))
                        
                        if (!isDuplicateTitleArtist) {
                            finalSongsToAdd.add(s)
                            addToAddedVideoIds(s.id)
                            if (cleanTitle.isNotEmpty() && cleanArtist.isNotEmpty()) {
                                addedKeys.add("$cleanTitle|$cleanArtist")
                            }
                            if (finalSongsToAdd.size >= 8) break
                        }
                    }
                }
            }

            if (finalSongsToAdd.isEmpty()) {
                printd("AutoQueueManager: No songs to add this loop — emptyFetchCount=$emptyFetchCount")
                emptyFetchCount++
                if (emptyFetchCount >= 3) {
                    continuationToken = null
                    currentWatchEndpoint = null
                }
                continue
            }
            emptyFetchCount = 0

            val mediaItems = finalSongsToAdd.map { MediaItemBuilder.build(it) }
            withContext(Dispatchers.Main) {
                player.addMediaItems(mediaItems)
                onQueueItemsAddedCallback?.invoke()
            }
            printd("AutoQueueManager: Appended ${mediaItems.size} songs to queue")
        }
    }

    private suspend fun fetchOnlineRelated(videoId: String): List<Song> {
        try {
            val endpoint = currentWatchEndpoint ?: WatchEndpoint(videoId = videoId, playlistId = "RDAMVM$videoId")
            val result = YouTube.next(endpoint = endpoint, continuation = continuationToken, followAutomixPreview = true)
            
            var fetchedSongs = emptyList<Song>()
            result.onSuccess { nextResult ->
                continuationToken = nextResult.continuation
                currentWatchEndpoint = nextResult.endpoint
                
                val addedVideoIdsLocal = synchronized(addedVideoIds) {
                    addedVideoIds.toSet()
                }
                val filteredItems = nextResult.items
                    .filter { it.id !in addedVideoIdsLocal }

                if (filteredItems.isEmpty()) {
                    if (nextResult.continuation == null) {
                        printd("AutoQueueManager: Continuation exhausted and all items filtered — resetting addedVideoIds for fresh fetch")
                        val retainedSet = synchronized(addedVideoIds) {
                            addedVideoIds.filter { isSameSong(it, videoId) }.toMutableSet()
                        }
                        retainedSet.add(videoId)
                        synchronized(addedVideoIds) {
                            addedVideoIds.clear()
                            addedVideoIds.addAll(retainedSet)
                        }
                        continuationToken = null
                        currentWatchEndpoint = WatchEndpoint(videoId = videoId, playlistId = "RDAMVM$videoId")
                    } else {
                        printd("AutoQueueManager: All fetched items already added, will try next continuation")
                    }
                    return@onSuccess
                }

                fetchedSongs = filteredItems.map { it.toNativeSong() }
            }.onFailure { e ->
                printe("AutoQueueManager: Failed to fetch related online: ${e.message}")
                continuationToken = null
            }
            return fetchedSongs
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            printe("AutoQueueManager: Exception fetching online related songs: ${e.message}")
            continuationToken = null
            return emptyList()
        }
    }

    private suspend fun fetchLocalRelated(songIdStr: String, currentQueueIds: Set<String>): List<Song> {
        val dao = musicDaoRef ?: return emptyList()
        val engagementDao = engagementDaoRef
        try {
            val songId = songIdStr.toLongOrNull()
            val currentSong = if (songId != null) dao.getSongByIdOnce(songId) else null
            
            var filtered = emptyList<SongEntity>()

            val engagementsMap = if (engagementDao != null) {
                try {
                    engagementDao.getAllEngagements().associateBy { it.songId }
                } catch (e: Exception) {
                    emptyMap()
                }
            } else {
                emptyMap()
            }
            val now = System.currentTimeMillis()

            if (currentSong != null) {
                val relatedEntities = dao.getLocalRelatedSongs(
                    songId = currentSong.id,
                    artistId = currentSong.artistId,
                    albumId = currentSong.albumId,
                    genre = currentSong.genre,
                    limit = 60
                )

                val mappedRelatedIds = try {
                    dao.getRelatedSongs(currentSong.id, 100).map { it.id }.toSet()
                } catch (e: Exception) {
                    emptySet()
                }

                fun calculateRelevanceAndDecayScore(entity: SongEntity): Double {
                    var relevance = getSongSimilarityScore(
                        currentSong.title, currentSong.artistName, currentSong.genre,
                        entity.title, entity.artistName, entity.genre
                    )
                    if (entity.albumId == currentSong.albumId) relevance += 5.0
                    if (mappedRelatedIds.contains(entity.id)) relevance += 20.0

                    val songIdStr = entity.id.toString()
                    val rawId = extractYtId(songIdStr) ?: songIdStr
                    val eng = engagementsMap[songIdStr] ?: engagementsMap[rawId]
                    
                    var popularityScore = 0.0
                    if (entity.isFavorite) popularityScore += 2.0
                    if (eng != null) {
                        val timeDiffMs = (now - eng.lastPlayedTimestamp).coerceAtLeast(0L)
                        val decay = kotlin.math.exp(-DECAY_LAMBDA * timeDiffMs)
                        popularityScore += (eng.playCount.coerceAtMost(10) * 0.5) * decay
                    }
                    return relevance + popularityScore
                }

                val sortedRelated = relatedEntities.sortedByDescending { calculateRelevanceAndDecayScore(it) }

                filtered = sortedRelated.filter { entity ->
                    val entityIdStr = entity.id.toString()
                    val isInQueue = currentQueueIds.any { isSameSong(it, entityIdStr) }
                    val isAlreadyAdded = synchronized(addedVideoIds) {
                        addedVideoIds.any { isSameSong(it, entityIdStr) }
                    }
                    !isInQueue && !isAlreadyAdded
                }
            }
            
            if (filtered.size < 12 && currentSong != null) {
                val artistSongs = dao.getSongsByArtistName(currentSong.artistName, 30)
                val genreSongs = if (!currentSong.genre.isNullOrBlank() && !currentSong.genre.equals("YouTube", ignoreCase = true)) {
                    dao.getSongsByGenre(currentSong.genre, currentSong.id, 30)
                } else {
                    emptyList()
                }
                
                val allLocalSongs = dao.getAllSongsList()
                val fallbackCandidates = (artistSongs + genreSongs + allLocalSongs).distinctBy { it.id }
                
                fun calculateFallbackScore(entity: SongEntity): Double {
                    var relevance = getSongSimilarityScore(
                        currentSong.title, currentSong.artistName, currentSong.genre,
                        entity.title, entity.artistName, entity.genre
                    )
                    
                    val songIdStr = entity.id.toString()
                    val rawId = extractYtId(songIdStr) ?: songIdStr
                    val eng = engagementsMap[songIdStr] ?: engagementsMap[rawId]
                    
                    var popularityScore = 0.0
                    if (entity.isFavorite) popularityScore += 2.0
                    if (eng != null) {
                        val timeDiffMs = (now - eng.lastPlayedTimestamp).coerceAtLeast(0L)
                        val decay = kotlin.math.exp(-DECAY_LAMBDA * timeDiffMs)
                        popularityScore += (eng.playCount.coerceAtMost(10) * 0.5) * decay
                    }
                    return relevance + popularityScore
                }
                
                val extraLocal = fallbackCandidates.filter { entity ->
                    val entityIdStr = entity.id.toString()
                    val isInQueue = currentQueueIds.any { isSameSong(it, entityIdStr) }
                    val isAlreadyAdded = synchronized(addedVideoIds) {
                        addedVideoIds.any { isSameSong(it, entityIdStr) }
                    }
                    val isCurrent = entity.id == currentSong.id
                    !isInQueue && !isAlreadyAdded && !isCurrent
                }.sortedByDescending { calculateFallbackScore(it) }
                 .take(30)
                 
                filtered = (filtered + extraLocal).distinctBy { it.id }
            }
            
            return filtered.map { it.toSong() }
        } catch (e: Exception) {
            printe("AutoQueueManager: Exception fetching local related songs: ${e.message}")
            return emptyList()
        }
    }

    private suspend fun saveRelatedSongsToDb(sourceVideoId: String, relatedSongs: List<Song>, player: Player) {
        val dao = musicDaoRef ?: return

        try {
            val sourceLongId = getDatabaseIdForYoutubeId(sourceVideoId)
            
            val songEntities = mutableListOf<SongEntity>()
            val albumEntities = mutableListOf<AlbumEntity>()
            val artistEntities = mutableListOf<ArtistEntity>()
            val crossRefs = mutableListOf<SongArtistCrossRef>()
            val relatedMaps = mutableListOf<RelatedSongMap>()

            withContext(Dispatchers.IO) {
                val exists = dao.getSongByIdOnce(sourceLongId) != null
                if (!exists) {
                    val (currentMediaItem, playerDuration) = withContext(Dispatchers.Main) {
                        Pair(player.currentMediaItem, player.duration)
                    }
                    if (currentMediaItem != null) {
                        val title = currentMediaItem.mediaMetadata.title?.toString() ?: ""
                        val artist = currentMediaItem.mediaMetadata.artist?.toString() ?: ""
                        // Standardized negative ID offsets matching Mappers.kt to avoid collision with positive local MediaStore IDs
                        val artistLongId = -(17_000_000_000_000L + kotlin.math.abs(artist.lowercase().hashCode().toLong()))
                        val album = currentMediaItem.mediaMetadata.albumTitle?.toString() ?: "YouTube Music"
                        val albumLongId = -(16_000_000_000_000L + kotlin.math.abs(album.lowercase().hashCode().toLong()))
                        
                        val sourceArtist = ArtistEntity(id = artistLongId, name = artist, trackCount = 1, imageUrl = null)
                        val sourceAlbum = AlbumEntity(
                            id = albumLongId,
                            title = album,
                            artistName = artist,
                            artistId = artistLongId,
                            albumArtUriString = upgradeThumbnailUrlToHighQuality(currentMediaItem.mediaMetadata.artworkUri?.toString()),
                            songCount = 1,
                            dateAdded = System.currentTimeMillis(),
                            year = 0,
                            albumArtist = artist
                        )
                        val sourceSong = SongEntity(
                            id = sourceLongId,
                            title = title,
                            artistName = artist,
                            artistId = artistLongId,
                            albumArtist = artist,
                            albumName = album,
                            albumId = albumLongId,
                            contentUriString = "youtube://$sourceVideoId",
                            albumArtUriString = upgradeThumbnailUrlToHighQuality(currentMediaItem.mediaMetadata.artworkUri?.toString()),
                            duration = playerDuration.coerceAtLeast(0L),
                            genre = "YouTube",
                            filePath = "",
                            parentDirectoryPath = "/Cloud/YouTube",
                            isFavorite = false,
                            lyrics = null,
                            trackNumber = 0,
                            discNumber = null,
                            year = 0,
                            dateAdded = System.currentTimeMillis(),
                            mimeType = "audio/opus",
                            bitrate = 128,
                            sampleRate = 44100,
                            sourceType = SourceType.YOUTUBE
                        )
                        val sourceCrossRef = SongArtistCrossRef(songId = sourceLongId, artistId = artistLongId, isPrimary = true)
                        
                        dao.insertArtists(listOf(sourceArtist))
                        dao.insertAlbums(listOf(sourceAlbum))
                        dao.insertSongs(listOf(sourceSong))
                        dao.insertSongArtistCrossRefs(listOf(sourceCrossRef))
                    }
                }

                relatedSongs.forEach { song ->
                    val songVideoId = song.youtubeId ?: song.id.substringAfter("youtube_")
                    val songLongId = getDatabaseIdForYoutubeId(songVideoId)
                    val artistLongId = song.artistId
                    val albumLongId = song.albumId

                    artistEntities.add(
                        ArtistEntity(
                            id = artistLongId,
                            name = song.artist,
                            trackCount = 1,
                            imageUrl = null
                        )
                    )

                    albumEntities.add(
                        AlbumEntity(
                            id = albumLongId,
                            title = song.album,
                            artistName = song.artist,
                            artistId = artistLongId,
                            albumArtUriString = upgradeThumbnailUrlToHighQuality(song.albumArtUriString),
                            songCount = 1,
                            dateAdded = System.currentTimeMillis(),
                            year = 0,
                            albumArtist = song.artist
                        )
                    )

                    songEntities.add(
                        SongEntity(
                            id = songLongId,
                            title = song.title,
                            artistName = song.artist,
                            artistId = artistLongId,
                            albumArtist = song.artist,
                            albumName = song.album,
                            albumId = albumLongId,
                            contentUriString = song.contentUriString,
                            albumArtUriString = upgradeThumbnailUrlToHighQuality(song.albumArtUriString),
                            duration = song.duration,
                            genre = song.genre,
                            filePath = "",
                            parentDirectoryPath = "/Cloud/YouTube",
                            isFavorite = false,
                            lyrics = null,
                            trackNumber = 0,
                            discNumber = null,
                            year = 0,
                            dateAdded = System.currentTimeMillis(),
                            mimeType = "audio/opus",
                            bitrate = 128,
                            sampleRate = 44100,
                            sourceType = SourceType.YOUTUBE
                        )
                    )

                    crossRefs.add(
                        SongArtistCrossRef(
                            songId = songLongId,
                            artistId = artistLongId,
                            isPrimary = true
                        )
                    )

                    relatedMaps.add(
                        RelatedSongMap(
                            songId = sourceLongId,
                            relatedSongId = songLongId
                        )
                    )
                }

                dao.insertArtists(artistEntities.distinctBy { it.id })
                dao.insertAlbums(albumEntities.distinctBy { it.id })
                dao.insertSongs(songEntities.distinctBy { it.id })
                dao.insertSongArtistCrossRefs(crossRefs.distinct())
                dao.insertRelatedSongMaps(relatedMaps.distinct())
            }
        } catch (e: Exception) {
            printe("AutoQueueManager: Error saving related songs to DB: ${e.message}")
        }
    }

    private fun getDatabaseIdForYoutubeId(youtubeId: String): Long {
        val YOUTUBE_SONG_ID_OFFSET = 15_000_000_000_000L
        return -(YOUTUBE_SONG_ID_OFFSET + youtubeId.hashCode().toLong().absoluteValue)
    }
}