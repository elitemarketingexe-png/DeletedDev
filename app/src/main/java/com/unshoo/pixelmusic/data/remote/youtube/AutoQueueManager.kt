package com.unshoo.pixelmusic.data.remote.youtube

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.unshoo.pixelmusic.data.remote.youtube.UmihiHelper.printe
import com.unshoo.pixelmusic.data.remote.youtube.UmihiHelper.printd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue

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
import com.unshoo.pixelmusic.data.model.ArtistRef
import com.unshoo.pixelmusic.utils.MediaItemBuilder
import com.unshoo.pixelmusic.presentation.viewmodel.ConnectivityStateHolder

/**
 * AutoQueueManager — Radio Mode (ArchiveTune 2026 Engine)
 *
 * Maintains a constant upcoming playback queue of 40–50 songs.
 * Automatically appends related songs when the remaining count falls below 40.
 * Supports online related tracks (YouTube Music next) and offline hybrid local fallback.
 */
object AutoQueueManager {

    private const val TARGET_QUEUE_SIZE = 45 // Targets exactly 45 upcoming songs
    private const val MAX_HISTORY = 150

    private var fetchJob: Job? = null
    private var lastFetchedVideoId: String? = null
    private var continuationToken: String? = null
    private var currentWatchEndpoint: WatchEndpoint? = null
    private val addedVideoIds = mutableSetOf<String>()

    // Memory cache mapping local/offline song IDs to matched YouTube video IDs
    private val localToYoutubeIdMap = mutableMapOf<String, String>()
    
    private var scope: CoroutineScope? = null
    private var contextRef: Context? = null
    private var datastoreRepository: DatastoreRepository? = null
    private var playerRef: Player? = null
    private var musicDaoRef: MusicDao? = null
    private var engagementDaoRef: com.unshoo.pixelmusic.data.database.EngagementDao? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            checkAndRefillQueue()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                checkAndRefillQueue()
            }
        }
    }

    fun attach(
        player: Player,
        context: Context,
        datastoreRepo: DatastoreRepository,
        coroutineScope: CoroutineScope,
        musicDao: MusicDao,
        engagementDao: com.unshoo.pixelmusic.data.database.EngagementDao
    ) {
        scope = coroutineScope
        contextRef = context.applicationContext
        datastoreRepository = datastoreRepo
        playerRef = player
        musicDaoRef = musicDao
        engagementDaoRef = engagementDao
        player.addListener(playerListener)
        printd("AutoQueueManager attached")
    }

    fun updatePlayer(newPlayer: Player) {
        val oldPlayer = playerRef
        if (oldPlayer !== newPlayer) {
            oldPlayer?.removeListener(playerListener)
            playerRef = newPlayer
            newPlayer.addListener(playerListener)
            printd("AutoQueueManager player updated")
        }
    }

    fun detach(player: Player?) {
        player?.removeListener(playerListener)
        playerRef = null
        fetchJob?.cancel()
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
        addedVideoIds.clear()
        fetchJob?.cancel()
    }

    fun seed(endpoint: WatchEndpoint, continuation: String?, videoId: String) {
        lastFetchedVideoId = videoId
        continuationToken = continuation
        currentWatchEndpoint = endpoint
        addedVideoIds.clear()
        addedVideoIds.add(videoId)
    }


    private fun checkAndRefillQueue() {
        forceRefill(forceRefresh = false)
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
                    val currentIndex = player.currentMediaItemIndex
                    val totalCount = player.mediaItemCount
                    val remaining = totalCount - currentIndex - 1
                    val currentId = player.currentMediaItem?.mediaId
                    Triple(remaining, currentId, totalCount)
                }
            } ?: return@launch

            val (remaining, currentId, _) = playerState
            if (currentId == null) return@launch

            if (forceRefresh) {
                fetchJob?.cancel()
            } else {
                if (fetchJob?.isActive == true) return@launch
            }

            fetchJob = launch(Dispatchers.IO) {
                refillQueueLoop(currentId, forceRefresh)
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

    private fun isSameSong(id1: String, id2: String): Boolean {
        if (id1 == id2) return true
        val yt1 = extractYtId(id1)
        val yt2 = extractYtId(id2)
        if (yt1 != null && yt2 != null) {
            return yt1 == yt2
        }
        return false
    }

    private fun extractYtId(id: String): String? {
        if (id.startsWith("youtube_")) return id.substringAfter("youtube_")
        if (id.startsWith("youtube://")) return id.substringAfter("youtube://")
        val longVal = id.toLongOrNull()
        if (longVal != null) {
            return null // positive/negative database IDs match directly or through duplicate copies in avoid sets
        }
        return id // Raw 11-char YouTube video ID
    }

    private suspend fun getContextualFamiliarSongs(
        currentSong: SongEntity?,
        currentQueueIds: Set<String>,
        avoidIds: Set<String>
    ): List<Song> {
        val dao = musicDaoRef ?: return emptyList()
        val engagementDao = engagementDaoRef
        
        val favoriteSongs = try {
            dao.getFavoriteSongsList(emptyList(), false, 0).map { it.toSong() }
        } catch (e: Exception) {
            emptyList()
        }

        val playedMultipleTimesSongs = mutableListOf<Song>()
        if (engagementDao != null) {
            val engagements = try {
                engagementDao.getAllEngagements()
            } catch (e: Exception) {
                emptyList()
            }
            val idsPlayedMultiple = engagements.filter { it.playCount >= 2 }.map { it.songId }.toSet()
            for (idStr in idsPlayedMultiple) {
                val dbSong = getDbSongByIdString(idStr)
                if (dbSong != null) {
                    playedMultipleTimesSongs.add(dbSong)
                }
            }
        }

        val combined = (favoriteSongs + playedMultipleTimesSongs).distinctBy { it.id }

        val currentGenre = currentSong?.genre
        val currentArtist = currentSong?.artistName
        val currentArtistId = currentSong?.artistId

        val contextualMatches = combined.filter { song ->
            val songIdStr = song.id
            val isAlreadyInQueue = currentQueueIds.any { isSameSong(it, songIdStr) }
            val isAvoid = avoidIds.any { isSameSong(it, songIdStr) }
            val matchesGenre = currentGenre != null && song.genre != null && song.genre.equals(currentGenre, ignoreCase = true)
            val matchesArtist = (currentArtist != null && song.artist.equals(currentArtist, ignoreCase = true)) || 
                                (currentArtistId != null && currentArtistId != -1L && song.artistId == currentArtistId)
            
            !isAlreadyInQueue && !isAvoid && (matchesGenre || matchesArtist)
        }

        if (contextualMatches.size >= 4) {
            return contextualMatches.shuffled()
        }

        val nonContextualFiltered = combined.filter { song ->
            val songIdStr = song.id
            val isAlreadyInQueue = currentQueueIds.any { isSameSong(it, songIdStr) }
            val isAvoid = avoidIds.any { isSameSong(it, songIdStr) }
            !isAlreadyInQueue && !isAvoid
        }

        return (contextualMatches + nonContextualFiltered.shuffled().take(15)).distinctBy { it.id }
    }

    private suspend fun refillQueueLoop(currentId: String, forceRefresh: Boolean) {
        val player = playerRef ?: return
        val dao = musicDaoRef ?: return
        val context = contextRef ?: return
        val engagementDao = engagementDaoRef

        val entryPoint = try {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context,
                YoutubeHelperEntryPoint::class.java
            )
        } catch (e: Exception) {
            null
        }
        val connectivityStateHolder = entryPoint?.connectivityStateHolder()
        connectivityStateHolder?.initialize()

        // 1. Identify if current song is YouTube or local/offline
        val currentMediaItem = withContext(Dispatchers.Main) { player.currentMediaItem }
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
            val songId = currentId.toLongOrNull()
            if (songId != null) {
                val dbSong = dao.getSongByIdOnce(songId)
                if (dbSong?.contentUriString?.startsWith("youtube://") == true) {
                    rawVideoId = dbSong.contentUriString.removePrefix("youtube://")
                }
            }
        }
        val isLocal = rawVideoId == null
        val resolvedVideoId = rawVideoId ?: ""

        if (forceRefresh) {
            lastFetchedVideoId = if (isLocal) currentId else resolvedVideoId
            continuationToken = null
            currentWatchEndpoint = null
            addedVideoIds.clear()
            addedVideoIds.add(lastFetchedVideoId!!)
        } else {
            val activeId = if (isLocal) currentId else resolvedVideoId
            if (lastFetchedVideoId == null) {
                lastFetchedVideoId = activeId
                addedVideoIds.add(activeId)
            }
        }

        var loopCount = 0
        while (true) {
            val playerState = withContext(Dispatchers.Main) {
                if (playerRef == null) null
                else {
                    val currentIndex = player.currentMediaItemIndex
                    val totalCount = player.mediaItemCount
                    val remaining = totalCount - currentIndex - 1
                    Pair(remaining, totalCount)
                }
            } ?: break

            val (remaining, totalCount) = playerState
            if (remaining >= TARGET_QUEUE_SIZE) {
                printd("AutoQueueManager: Queue is full. Current remaining: $remaining (>= $TARGET_QUEUE_SIZE)")
                break
            }

            if (loopCount >= 8) {
                printd("AutoQueueManager: Max loop count reached. Breaking.")
                break
            }
            loopCount++

            printd("AutoQueueManager: Refilling queue. Remaining: $remaining, Target: $TARGET_QUEUE_SIZE, Loop: $loopCount")

            val currentQueueIds = withContext(Dispatchers.Main) {
                if (playerRef == null) emptySet()
                else (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId }.toSet()
            }

            val highlyRotatedIds = mutableSetOf<String>()
            val engagements = try {
                engagementDao?.getAllEngagements()
            } catch (e: Exception) {
                null
            }
            if (engagements != null) {
                for (eng in engagements) {
                    if (eng.playCount > 8) {
                        highlyRotatedIds.add(eng.songId)
                        val ytId = getYoutubeVideoId(eng.songId)
                        if (ytId != null) {
                            highlyRotatedIds.add(ytId)
                            highlyRotatedIds.add("youtube_$ytId")
                            highlyRotatedIds.add(getDatabaseIdForYoutubeId(ytId).toString())
                        }
                    }
                }
            }

            val recentlyPlayedSongs = mutableListOf<Song>()
            val recentlyPlayedIds = mutableSetOf<String>()
            val recents = try {
                engagementDao?.getRecentlyPlayedSongs(30)
            } catch (e: Exception) {
                null
            }
            if (recents != null) {
                for (eng in recents) {
                    recentlyPlayedIds.add(eng.songId)
                    val ytId = getYoutubeVideoId(eng.songId)
                    if (ytId != null) {
                        recentlyPlayedIds.add(ytId)
                        recentlyPlayedIds.add("youtube_$ytId")
                        recentlyPlayedIds.add(getDatabaseIdForYoutubeId(ytId).toString())
                    }
                    val song = getDbSongByIdString(eng.songId)
                    if (song != null) {
                        recentlyPlayedSongs.add(song)
                    }
                }
            }

            val settings = datastoreRepository?.settings?.first() ?: return
            val avoidIds = if (settings.avoidRepetitiveSongs) {
                highlyRotatedIds + recentlyPlayedIds
            } else {
                highlyRotatedIds
            }

            val songsToAdd = mutableListOf<Song>()

            // 1. Resolve current playing song information
            val currentSongLongId = currentId.toLongOrNull()
            val currentSongEntity = if (currentSongLongId != null) dao.getSongByIdOnce(currentSongLongId) else null
            
            val currentMediaItem = withContext(Dispatchers.Main) { player.currentMediaItem }
            val currentTitle = currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
            val currentArtist = currentMediaItem?.mediaMetadata?.artist?.toString().orEmpty()
            
            var resolvedGenre = currentSongEntity?.genre
            if (resolvedGenre.isNullOrBlank() && currentTitle.isNotBlank() && currentArtist.isNotBlank()) {
                val dbMatch = dao.getSongsByArtistName(currentArtist, 1).firstOrNull()
                if (dbMatch != null) {
                    resolvedGenre = dbMatch.genre
                }
            }

            // 2. Discover related tracks (first priority is online YouTube Music for online tracks, and local related for offline tracks)
            val isOnline = connectivityStateHolder?.isOnline?.value ?: true
            var discovered = emptyList<Song>()
            
            if (isOnline && !isLocal && resolvedVideoId.isNotBlank()) {
                val related = fetchOnlineRelated(resolvedVideoId)
                if (related.isNotEmpty()) {
                    saveRelatedSongsToDb(resolvedVideoId, related, player)
                    discovered = related
                } else {
                    discovered = fetchLocalRelated(currentId, currentQueueIds)
                }
            } else {
                discovered = fetchLocalRelated(currentId, currentQueueIds)
            }

            // 3. Extract same-artist and same-genre local pools
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

            // 4. Extract familiar contextual songs (favorites or playCount >= 2 matching genre/artist)
            val familiarSongs = getContextualFamiliarSongs(currentSongEntity, currentQueueIds, avoidIds)

            // 5. Interleave pools with strict capping (max 2 per artist)
            val finalSongsToAdd = mutableListOf<Song>()
            val addedArtists = mutableMapOf<String, Int>()

            // Helper to check artist limits to ensure diversity
            fun canAddSong(song: Song): Boolean {
                val songIdStr = song.id
                val isInQueue = currentQueueIds.any { isSameSong(it, songIdStr) }
                val isAvoid = avoidIds.any { isSameSong(it, songIdStr) }
                val isAlreadyAdded = finalSongsToAdd.any { isSameSong(it.id, songIdStr) }
                if (isInQueue || isAvoid || isAlreadyAdded) return false

                val artistKey = song.artist.lowercase().trim()
                val artistCount = addedArtists[artistKey] ?: 0
                return artistCount < 2 // Max 2 songs per artist in the added batch
            }

            // Separate same-genre into popular and discovery (playCount = 0)
            val discoveryCandidates = sameGenreSongs.filter { song ->
                val playCount = engagementDao?.getPlayCount(song.id) ?: 0
                playCount == 0 && !song.isFavorite
            }.filter { canAddSong(it) }.shuffled().toMutableList()

            val popularGenreCandidates = sameGenreSongs.filter { song ->
                val playCount = engagementDao?.getPlayCount(song.id) ?: 0
                playCount > 0 || song.isFavorite
            }.filter { canAddSong(it) }.shuffled().toMutableList()

            val sameArtistCandidates = sameArtistSongs.filter { canAddSong(it) }.shuffled().toMutableList()
            val relatedCandidates = discovered.filter { canAddSong(it) }.toMutableList()
            val familiarCandidates = familiarSongs.filter { canAddSong(it) }.toMutableList()

            var addedCount = 0
            val targetBatchSize = 12

            while (addedCount < targetBatchSize) {
                var addedInThisRound = false

                // 1. YouTube Related / Automix gets HIGHEST PRIORITY (first priority)
                // We add up to 2 related songs per round if available
                for (i in 0 until 2) {
                    if (relatedCandidates.isNotEmpty()) {
                        val s = relatedCandidates.removeAt(0)
                        if (canAddSong(s)) {
                            finalSongsToAdd.add(s)
                            addedArtists[s.artist.lowercase().trim()] = (addedArtists[s.artist.lowercase().trim()] ?: 0) + 1
                            addedCount++
                            addedInThisRound = true
                        }
                    }
                }

                if (addedCount >= targetBatchSize) break

                // 2. Same Artist Exploration
                if (sameArtistCandidates.isNotEmpty()) {
                    val s = sameArtistCandidates.removeAt(0)
                    if (canAddSong(s)) {
                        finalSongsToAdd.add(s)
                        addedArtists[s.artist.lowercase().trim()] = (addedArtists[s.artist.lowercase().trim()] ?: 0) + 1
                        addedCount++
                        addedInThisRound = true
                    }
                }

                if (addedCount >= targetBatchSize) break

                // 3. Same Genre Discovery (Never played before)
                if (discoveryCandidates.isNotEmpty()) {
                    val s = discoveryCandidates.removeAt(0)
                    if (canAddSong(s)) {
                        finalSongsToAdd.add(s)
                        addedArtists[s.artist.lowercase().trim()] = (addedArtists[s.artist.lowercase().trim()] ?: 0) + 1
                        addedCount++
                        addedInThisRound = true
                    }
                }

                if (addedCount >= targetBatchSize) break

                // 4. Familiar Contextual
                if (familiarCandidates.isNotEmpty()) {
                    val s = familiarCandidates.removeAt(0)
                    if (canAddSong(s)) {
                        finalSongsToAdd.add(s)
                        addedArtists[s.artist.lowercase().trim()] = (addedArtists[s.artist.lowercase().trim()] ?: 0) + 1
                        addedCount++
                        addedInThisRound = true
                    }
                }

                if (addedCount >= targetBatchSize) break

                // 5. Same Genre Popular Exploration
                if (popularGenreCandidates.isNotEmpty()) {
                    val s = popularGenreCandidates.removeAt(0)
                    if (canAddSong(s)) {
                        finalSongsToAdd.add(s)
                        addedArtists[s.artist.lowercase().trim()] = (addedArtists[s.artist.lowercase().trim()] ?: 0) + 1
                        addedCount++
                        addedInThisRound = true
                    }
                }

                if (!addedInThisRound) break
            }

            // Fallback: If we couldn't build at least 6 songs due to strict limits, relax constraints but STILL enforce avoidIds and queue checks!
            if (finalSongsToAdd.size < 6) {
                val remainingCandidates = (discovered + sameGenreSongs + familiarSongs).distinctBy { it.id }
                for (s in remainingCandidates) {
                    val songIdStr = s.id
                    val isInQueue = currentQueueIds.any { isSameSong(it, songIdStr) }
                    val isAlreadyAdded = finalSongsToAdd.any { isSameSong(it.id, songIdStr) }
                    val isAvoid = avoidIds.any { isSameSong(it, songIdStr) }
                    if (!isInQueue && !isAlreadyAdded && !isAvoid) {
                        finalSongsToAdd.add(s)
                        if (finalSongsToAdd.size >= 8) break
                    }
                }
            }

            if (finalSongsToAdd.isEmpty()) {
                printd("AutoQueueManager: No songs to add, breaking.")
                break
            }

            val mediaItems = finalSongsToAdd.map { MediaItemBuilder.build(it) }
            withContext(Dispatchers.Main) {
                player.addMediaItems(mediaItems)
            }
            printd("AutoQueueManager: Appended ${mediaItems.size} songs to queue")
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
                
                val addedVideoIdsLocal = addedVideoIds
                val filteredItems = nextResult.items
                    .filter { it.id !in addedVideoIdsLocal }

                if (filteredItems.isEmpty()) {
                    return@onSuccess
                }

                filteredItems.forEach { addedVideoIds.add(it.id) }
                if (addedVideoIds.size > MAX_HISTORY) {
                    val excess = addedVideoIds.size - MAX_HISTORY
                    val toRemove = addedVideoIds.take(excess)
                    addedVideoIds.removeAll(toRemove.toSet())
                }

                fetchedSongs = filteredItems.map { it.toNativeSong() }
            }.onFailure { e ->
                printe("AutoQueueManager: Failed to fetch related online: ${e.message}")
            }
            return fetchedSongs
        } catch (e: Exception) {
            printe("AutoQueueManager: Exception fetching online related songs: ${e.message}")
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

            // Get popular local song IDs (play count >= 2)
            val popularIds = mutableSetOf<String>()
            if (engagementDao != null) {
                try {
                    val engagements = engagementDao.getAllEngagements()
                    engagements.filter { it.playCount >= 2 }.forEach {
                        popularIds.add(it.songId)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }

            if (currentSong != null) {
                val relatedEntities = dao.getLocalRelatedSongs(
                    songId = currentSong.id,
                    artistId = currentSong.artistId,
                    albumId = currentSong.albumId,
                    genre = currentSong.genre,
                    limit = 25
                )
                
                // Prioritize favorites and popular ones, but keep everything so we have plenty of recommendations
                val sortedRelated = relatedEntities.sortedWith(
                    compareByDescending<SongEntity> { it.isFavorite }
                        .thenByDescending { popularIds.contains(it.id.toString()) }
                )
                
                filtered = sortedRelated.filter { 
                    it.id.toString() !in addedVideoIds && it.id.toString() !in currentQueueIds
                }
            }
            
            if (filtered.size < 10) {
                val allLocalSongs = dao.getAllSongsList()
                val extraLocal = allLocalSongs.filter {
                    it.id.toString() !in addedVideoIds && it.id.toString() !in currentQueueIds && (currentSong == null || it.id != currentSong.id)
                }.sortedWith(
                    compareByDescending<SongEntity> { it.isFavorite }
                        .thenByDescending { popularIds.contains(it.id.toString()) }
                ).shuffled().take(20)
                filtered = (filtered + extraLocal).distinctBy { it.id }
            }
            
            filtered.forEach { addedVideoIds.add(it.id.toString()) }
            if (addedVideoIds.size > MAX_HISTORY) {
                val excess = addedVideoIds.size - MAX_HISTORY
                val toRemove = addedVideoIds.take(excess)
                addedVideoIds.removeAll(toRemove.toSet())
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
                // Check if source song exists in DB, if not, insert it first!
                val exists = dao.getSongByIdOnce(sourceLongId) != null
                if (!exists) {
                    val (currentMediaItem, playerDuration) = withContext(Dispatchers.Main) {
                        Pair(player.currentMediaItem, player.duration)
                    }
                    if (currentMediaItem != null) {
                        val title = currentMediaItem.mediaMetadata.title?.toString() ?: ""
                        val artist = currentMediaItem.mediaMetadata.artist?.toString() ?: ""
                        val artistLongId = artist.hashCode().toLong()
                        val album = currentMediaItem.mediaMetadata.albumTitle?.toString() ?: "YouTube Music"
                        val albumLongId = album.hashCode().toLong()
                        
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
                            mimeType = "audio/webm",
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
                            mimeType = "audio/webm",
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

                // Strictly insert artist/album first due to Foreign Key constraints referenced in SongEntity
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
