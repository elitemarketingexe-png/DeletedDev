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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
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
    }

    private val syncMutex = Mutex()
    @Volatile private var lastSuccessfulSyncAtMs: Long = 0L

    suspend fun syncNow(force: Boolean = false) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSuccessfulSyncAtMs < MIN_SYNC_INTERVAL_MS) {
            return@withContext
        }

        syncMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            if (!force && lockedNow - lastSuccessfulSyncAtMs < MIN_SYNC_INTERVAL_MS) {
                return@withLock
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
                syncListeningHistory()
            } catch (_: Exception) {
            }
            lastSuccessfulSyncAtMs = System.currentTimeMillis()
        }
    }

    private suspend fun syncSubscribedArtists() {
        val allArtistItems = mutableListOf<ArtistItem>()

        val firstPage = YouTube.library(BROWSE_SUBSCRIPTIONS).getOrNull() ?: return
        allArtistItems += firstPage.items.filterIsInstance<ArtistItem>()

        var pages = 0
        var continuation = firstPage.continuation
        while (continuation != null && pages < 5) {
            yield()
            val next = YouTube.libraryContinuation(continuation).getOrNull() ?: break
            allArtistItems += next.items.filterIsInstance<ArtistItem>()
            continuation = next.continuation
            pages++
            delay(30L)
        }

        if (allArtistItems.isEmpty()) return

        val entities = allArtistItems.mapNotNull { item ->
            val id = ytArtistId(item.title)
            ArtistEntity(
                id = ytArtistId(item.title),
                name = item.title,
                trackCount = 0,
                imageUrl = item.thumbnail,
                channelId = item.id
            )
        }
        musicDao.insertArtistsIgnoreConflicts(entities)
        val subscribedIds = entities.mapNotNull { it.channelId }.toSet() + entities.map { it.id.toString() }
        userPreferencesRepository.setSubscribedArtistIds(subscribedIds)
    }

    private suspend fun syncLikedAlbums() {
        val allAlbumItems = mutableListOf<AlbumItem>()

        val firstPage = YouTube.library(BROWSE_ALBUMS).getOrNull() ?: return
        allAlbumItems += firstPage.items.filterIsInstance<AlbumItem>()

        var pages = 0
        var continuation = firstPage.continuation
        while (continuation != null && pages < 5) {
            yield()
            val next = YouTube.libraryContinuation(continuation).getOrNull() ?: break
            allAlbumItems += next.items.filterIsInstance<AlbumItem>()
            continuation = next.continuation
            pages++
            delay(30L)
        }

        if (allAlbumItems.isEmpty()) return

        val entities = allAlbumItems.mapNotNull { item ->
            val id = ytAlbumId(item.title)
            val primaryArtistName = item.artists?.firstOrNull()?.name ?: "Unknown Artist"
            val primaryArtistId = ytArtistId(primaryArtistName)
            AlbumEntity(
                id = id,
                title = item.title,
                artistName = primaryArtistName,
                artistId = primaryArtistId,
                songCount = 10,
                dateAdded = System.currentTimeMillis(),
                year = item.year ?: 0,
                albumArtUriString = item.thumbnail
            )
        }
        musicDao.insertAlbumsIgnoreConflicts(entities)
    }

    suspend fun syncLikedSongs() = withContext(Dispatchers.IO) {
        val allSongItems = mutableListOf<SongItem>()
        val firstPage = YouTube.playlist(LIKED_SONGS_PLAYLIST).getOrNull() ?: return@withContext
        allSongItems += firstPage.songs

        var pages = 0
        var continuation = firstPage.songsContinuation
        while (continuation != null && pages < 5) {
            yield()
            val next = YouTube.playlistContinuation(continuation).getOrNull() ?: break
            allSongItems += next.songs
            continuation = next.continuation
            pages++
            delay(30L)
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
            favoriteEntities.chunked(500).forEach { chunk ->
                favoritesDao.insertAll(chunk)
                yield()
            }
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

        val now = System.currentTimeMillis()
        val events = nativeSongs.take(100).mapIndexedNotNull { index, song ->
            val songIdStr = song.youtubeId ?: return@mapIndexedNotNull null
            val unifiedId = ytSongId(songIdStr).toString()
            PlaybackStatsRepository.PlaybackEvent(
                songId = unifiedId,
                timestamp = now - (index * 60_000L),
                durationMs = song.duration.coerceAtLeast(0L),
                startTimestamp = (now - (index * 60_000L) - song.duration).coerceAtLeast(0L),
                endTimestamp = now - (index * 60_000L),
                title = song.title,
                artist = song.artist,
                thumbnail = song.albumArtUriString,
                genre = song.genre,
                album = song.album
            )
        }
        playbackStatsRepository.recordPlaybackBatch(events)
    }

    private fun ytSongId(youtubeId: String): Long =
        -(15_000_000_000_000L + youtubeId.hashCode().toLong().absoluteValue)

    private fun ytAlbumId(name: String): Long =
        -(16_000_000_000_000L + name.lowercase().hashCode().toLong().absoluteValue)

    private fun ytArtistId(name: String): Long =
        -(17_000_000_000_000L + name.lowercase().hashCode().toLong().absoluteValue)
}
