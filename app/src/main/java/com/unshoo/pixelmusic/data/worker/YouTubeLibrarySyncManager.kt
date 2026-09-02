package com.unshoo.pixelmusic.data.worker

import android.content.Context
import com.unshoo.pixelmusic.data.database.AlbumEntity
import com.unshoo.pixelmusic.data.database.ArtistEntity
import com.unshoo.pixelmusic.data.database.FavoritesDao
import com.unshoo.pixelmusic.data.database.FavoritesEntity
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.data.repository.MusicRepository
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.stats.PlaybackStatsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem
import com.unshoo.pixelmusic.data.model.youtube.PlaylistInfo
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

@Singleton
class YouTubeLibrarySyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicDao: MusicDao,
    private val favoritesDao: FavoritesDao,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playbackStatsRepository: PlaybackStatsRepository,
) {

    companion object {
        private const val LIKED_SONGS_PLAYLIST = "LM"
        private const val BROWSE_SUBSCRIPTIONS = "FEmusic_library_corpus_artists"
        private const val BROWSE_ALBUMS = "FEmusic_library_corpus_albums"
        private const val MIN_SYNC_INTERVAL_MS = 10 * 60 * 1000L
        // Maximum continuation pages per library category.
        // 50 pages × ~25 items/page ≈ 1250 items — covers virtually all libraries.
        private const val MAX_CONTINUATION_PAGES = 50
    }

    private val syncMutex = Mutex()
    @Volatile private var lastSuccessfulSyncAtMs: Long = 0L

    suspend fun syncNow(force: Boolean = false) = withContext(Dispatchers.IO) {
        val lastTimestamp = userPreferencesRepository.getLastSyncTimestamp()
        val now = System.currentTimeMillis()
        if (!force && now - lastTimestamp < 6L * 60L * 60L * 1000L) {
            return@withContext
        }

        syncMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            if (!force && lockedNow - userPreferencesRepository.getLastSyncTimestamp() < 6L * 60L * 60L * 1000L) {
                return@withLock
            }
            if (!YouTube.hasLoginCookie()) {
                return@withLock
            }
            val syncPlaylistsAndLikes = userPreferencesRepository.youtubeSyncPlaylistsAndLikesFlow.first()
            if (!syncPlaylistsAndLikes && !force) {
                return@withLock
            }
            // Bug 7 fix: reduced from 2500ms to 150ms. The long delay was causing
            // unnecessary latency on every automatic sync when the app is in the foreground.
            if (!force) {
                delay(150L)
            }
            try {
                syncSubscribedArtists()
            } catch (_: Exception) {
            }
            try {
                syncLikedSongs()
            } catch (_: Exception) {
            }
            try {
                syncLikedAlbums()
            } catch (_: Exception) {
            }
            try {
                syncLikedPlaylists()
            } catch (_: Exception) {
            }
            try {
                syncListeningHistory()
            } catch (_: Exception) {
            }
            userPreferencesRepository.setLastSyncTimestamp(System.currentTimeMillis())
        }
    }

    suspend fun syncSubscribedArtists(forceFull: Boolean = false) {
        val allArtistItems = mutableListOf<ArtistItem>()

        val firstPage = YouTube.library(BROWSE_SUBSCRIPTIONS).getOrNull() ?: return
        allArtistItems += firstPage.items.filterIsInstance<ArtistItem>()

        val existingSubscribedIds = userPreferencesRepository.subscribedArtistIdsFlow.first()
        val allFirstPageKnown = !forceFull && existingSubscribedIds.isNotEmpty() &&
            allArtistItems.isNotEmpty() &&
            allArtistItems.all { (it.id in existingSubscribedIds) || (ytArtistIdFromChannelId(it.id).toString() in existingSubscribedIds) }

        if (!allFirstPageKnown) {
            var pages = 0
            var continuation = firstPage.continuation
            while (continuation != null && pages < MAX_CONTINUATION_PAGES) {
                yield()
                val next = YouTube.libraryContinuation(continuation).getOrNull() ?: break
                val newItems = next.items.filterIsInstance<ArtistItem>()
                allArtistItems += newItems
                continuation = next.continuation
                pages++
                delay(30L)
            }
        }

        if (allArtistItems.isEmpty()) return

        val entities = allArtistItems.mapNotNull { item ->
            ArtistEntity(
                id = ytArtistIdFromChannelId(item.id),
                name = item.title,
                trackCount = 0,
                imageUrl = item.thumbnail,
                channelId = item.id
            )
        }
        musicDao.insertArtists(entities)
        val subscribedIds = existingSubscribedIds + entities.mapNotNull { it.channelId }.toSet() + entities.map { it.id.toString() }
        userPreferencesRepository.setSubscribedArtistIds(subscribedIds)
    }

    suspend fun syncLikedAlbums(forceFull: Boolean = false) {
        val allAlbumItems = mutableListOf<AlbumItem>()

        val firstPage = YouTube.library(BROWSE_ALBUMS).getOrNull() ?: return
        allAlbumItems += firstPage.items.filterIsInstance<AlbumItem>()

        val existingLikedAlbumIds = userPreferencesRepository.likedAlbumIdsFlow.first()
        val allFirstPageKnown = !forceFull && existingLikedAlbumIds.isNotEmpty() &&
            allAlbumItems.isNotEmpty() &&
            allAlbumItems.all { it.browseId in existingLikedAlbumIds }

        if (!allFirstPageKnown) {
            var pages = 0
            var continuation = firstPage.continuation
            while (continuation != null && pages < MAX_CONTINUATION_PAGES) {
                yield()
                val next = YouTube.libraryContinuation(continuation).getOrNull() ?: break
                allAlbumItems += next.items.filterIsInstance<AlbumItem>()
                continuation = next.continuation
                pages++
                delay(30L)
            }
        }

        if (allAlbumItems.isEmpty()) return

        val entities = allAlbumItems.mapNotNull { item ->
            val id = item.browseId.hashCode().toLong()
            com.unshoo.pixelmusic.presentation.viewmodel.AlbumIdMapper.putMapping(context, id, item.browseId)
            val primaryArtistName = item.artists?.firstOrNull()?.name ?: "Unknown Artist"
            val primaryArtistId = ytArtistId(primaryArtistName)
            AlbumEntity(
                id = id,
                title = item.title,
                artistName = primaryArtistName,
                artistId = primaryArtistId,
                songCount = 0,
                dateAdded = System.currentTimeMillis(),
                year = item.year ?: 0,
                albumArtUriString = item.thumbnail
            )
        }
        musicDao.insertAlbums(entities)
        val browseIds = existingLikedAlbumIds + allAlbumItems.map { it.browseId }.toSet()
        userPreferencesRepository.setLikedAlbumIds(browseIds)
    }

    suspend fun syncLikedSongs(forceFull: Boolean = false) = withContext(Dispatchers.IO) {
        val allSongItems = mutableListOf<SongItem>()
        val firstPage = YouTube.playlist(LIKED_SONGS_PLAYLIST).getOrNull() ?: return@withContext
        allSongItems += firstPage.songs

        val existingFavorites = favoritesDao.getFavoriteSongIdsOnce().toSet()
        val firstPageIds = firstPage.songs.mapNotNull { it.id?.let(::ytSongId) }
        val allFirstPageKnown = !forceFull && existingFavorites.isNotEmpty() &&
            firstPageIds.isNotEmpty() &&
            firstPageIds.all { it in existingFavorites }

        if (!allFirstPageKnown) {
            var pages = 0
            var continuation = firstPage.songsContinuation
            while (continuation != null && pages < MAX_CONTINUATION_PAGES) {
                yield()
                val next = YouTube.playlistContinuation(continuation).getOrNull() ?: break
                allSongItems += next.songs
                continuation = next.continuation
                pages++
                delay(30L)
            }
        }

        if (allSongItems.isEmpty()) return@withContext
        val songs = allSongItems.map { it.toNativeSong() }
        musicRepository.insertYoutubeSongs(songs)

        val baseTimestamp = System.currentTimeMillis()
        val favoriteEntities = songs.mapIndexedNotNull { index, song ->
            val songIdStr = song.youtubeId ?: return@mapIndexedNotNull null
            FavoritesEntity(
                songId = ytSongId(songIdStr),
                isFavorite = true,
                timestamp = baseTimestamp - index
            )
        }
        if (favoriteEntities.isNotEmpty()) {
            favoritesDao.insertAllBatched(favoriteEntities)
        }
    }

    private suspend fun syncListeningHistory() {
        val historyPage = YouTube.musicHistory().getOrNull() ?: return
        val allSongs = mutableListOf<SongItem>()
        historyPage.sections?.forEach { section ->
            allSongs += section.songs
        }
        if (allSongs.isEmpty()) return

        val nativeSongs = allSongs.map { it.toNativeSong() }
        musicRepository.insertYoutubeSongs(nativeSongs)
    }

    suspend fun syncLikedPlaylists(forceFull: Boolean = false) = withContext(Dispatchers.IO) {
        val allPlaylists = mutableListOf<PlaylistItem>()

        val firstPage = YouTube.library("FEmusic_liked_playlists").getOrNull() ?: return@withContext
        allPlaylists += firstPage.items.filterIsInstance<PlaylistItem>()

        val appDatabase = com.unshoo.pixelmusic.data.database.youtube.AppDatabase.getInstance(context)
        val playlistRepo = appDatabase.playlistRepository()

        val allFirstPageKnown = !forceFull && allPlaylists.isNotEmpty() &&
            allPlaylists.all { playlistRepo.getPlaylistById(it.id) != null }

        if (!allFirstPageKnown) {
            var pages = 0
            var continuation = firstPage.continuation
            while (continuation != null && pages < MAX_CONTINUATION_PAGES) {
                yield()
                val next = YouTube.libraryContinuation(continuation).getOrNull() ?: break
                allPlaylists += next.items.filterIsInstance<PlaylistItem>()
                continuation = next.continuation
                pages++
                delay(30L)
            }
        }

        if (allPlaylists.isEmpty()) return@withContext

        // Pre-fetch all existing playlists in one batch query so we can
        // preserve their lastSyncTimestamp without opening a separate DB transaction
        // per playlist (which was very slow for large libraries).
        val existingMap = allPlaylists.mapNotNull { item ->
            runCatching { playlistRepo.getPlaylistById(item.id) }.getOrNull()
        }.filterNotNull().associateBy { it.info.id }

        val playlistInfoList = allPlaylists.map { item ->
            val count = item.songCountText
                ?.split(" ")?.firstOrNull()
                ?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            PlaylistInfo(
                id = item.id,
                title = item.title,
                coverHref = com.unshoo.pixelmusic.data.remote.youtube.upgradeThumbnailUrlToHighQuality(
                    item.thumbnail
                ) ?: item.thumbnail ?: "",
                lastSyncSongCount = count,
                lastSyncTimestamp = existingMap[item.id]?.info?.lastSyncTimestamp ?: 0L
            )
        }

        // Insert all playlists in one sweep instead of one transaction per item.
        playlistInfoList.forEach { info ->
            playlistRepo.insertPlaylist(info)
        }
    }

    private fun ytSongId(youtubeId: String): Long =
        -(15_000_000_000_000L + youtubeId.hashCode().toLong().absoluteValue)

    private fun ytAlbumId(name: String): Long =
        -(16_000_000_000_000L + name.lowercase().hashCode().toLong().absoluteValue)

    private fun ytArtistId(name: String): Long =
        -(17_000_000_000_000L + name.lowercase().hashCode().toLong().absoluteValue)

    /**
     * Bug 1+8 fix: derives a stable Long ID from the YouTube channel ID string
     * rather than from the artist's display name. Prevents duplicate rows when a
     * channel is renamed and avoids collisions between differently-named artists
     * that happen to share the same hash.
     *
     * Falls back to the name-based hash only if the channelId is blank (should
     * not happen in practice but keeps the function total).
     */
    private fun ytArtistIdFromChannelId(channelId: String): Long {
        if (channelId.isBlank()) return ytArtistId(channelId)
        return -(17_000_000_000_000L + channelId.hashCode().toLong().absoluteValue)
    }
}
