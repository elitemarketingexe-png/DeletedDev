package com.unshoo.pixelmusic.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import com.unshoo.pixelmusic.data.database.MusicDao
import com.unshoo.pixelmusic.data.stats.PlaybackStatsRepository
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint
import unshoo.ianshulyadav.pixelmusic.innertube.pages.ChartsPage
import unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage
import javax.inject.Inject
import javax.inject.Singleton

data class SearchDiscoveryData(
    val moodAndGenres: List<unshoo.ianshulyadav.pixelmusic.innertube.pages.MoodAndGenres.Item>,
    val newReleaseAlbums: List<AlbumItem>,
    val chartSections: List<ChartsPage.ChartSection>,
    val suggestedSongs: List<SongItem>,
    val searchedAlbums: List<AlbumItem>,
    val suggestedArtists: List<ArtistItem>,
)

@Singleton
class SearchDiscoveryRepository @Inject constructor(
    private val musicDao: MusicDao,
    private val playbackStatsRepository: PlaybackStatsRepository,
) {
    suspend fun loadDiscovery(): Result<SearchDiscoveryData> =
        withContext(Dispatchers.IO) {
            try {
                coroutineScope {
                    val explorePageDeferred = async { YouTube.explore().getOrThrow() }
                    val chartsPageDeferred = async { YouTube.getChartsPage().getOrThrow() }
                    val suggestedSongsDeferred = async { loadSuggestedSongs() }
                    val searchedAlbumsDeferred = async {
                        searchItems<AlbumItem>(
                            query = TopAlbumsQuery,
                            filter = YouTube.SearchFilter.FILTER_ALBUM,
                        )
                    }
                    val suggestedArtistsDeferred = async { loadSuggestedArtists() }

                    val explorePage = explorePageDeferred.await()
                    val chartsPage = chartsPageDeferred.await()

                    Result.success(
                        SearchDiscoveryData(
                            moodAndGenres = explorePage.moodAndGenres,
                            newReleaseAlbums = explorePage.newReleaseAlbums,
                            chartSections = chartsPage.sections,
                            suggestedSongs = suggestedSongsDeferred.await(),
                            searchedAlbums = searchedAlbumsDeferred.await(),
                            suggestedArtists = suggestedArtistsDeferred.await(),
                        )
                    )
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                Result.failure(throwable)
            }
        }

    private suspend inline fun <reified T> searchItems(
        query: String,
        filter: YouTube.SearchFilter,
    ): List<T> =
        try {
            YouTube
                .search(
                    query = query,
                    filter = filter,
                ).getOrThrow()
                .items
                .filterIsInstance<T>()
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            emptyList()
        }

    private suspend fun loadSuggestedSongs(): List<SongItem> =
        coroutineScope {
            // Retrieve most played songs from stats history
            var seedSongIds = playbackStatsRepository
                .loadSongPlayCounts(limit = MaxHistoryLookupItems)
                // Filter out local songs if needed (non-YouTube songs usually have long IDs or numeric paths)
                .filterNot { it.songId.startsWith("/") || it.songId.toLongOrNull() != null }
                .take(MaxSuggestionSeedItems)
                .map { it.songId }
            
            if (seedSongIds.isEmpty()) {
                try {
                    val trendingResult = YouTube.search(query = "trending hits", filter = YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    seedSongIds = trendingResult?.items?.filterIsInstance<SongItem>()?.take(MaxSuggestionSeedItems)?.map { it.id }.orEmpty()
                } catch (_: Exception) {}
            }
            
            val seedSongIdsSet = seedSongIds.toSet()

            seedSongIds
                .map { songId ->
                    async {
                        loadRelatedSongs(songId)
                            .ifEmpty { searchRelatedSongs(songId) }
                    }
                }.awaitAll()
                .flatten()
                .filterNot { song -> song.id in seedSongIdsSet }
                .distinctBy { song -> song.id }
                .take(MaxSuggestedItems)
        }

    private suspend fun loadRelatedSongs(songId: String): List<SongItem> =
        try {
            val nextResult = YouTube.next(WatchEndpoint(videoId = songId)).getOrThrow()
            val relatedSongs = nextResult
                .relatedEndpoint
                ?.let { endpoint -> YouTube.related(endpoint).getOrNull()?.songs }
                .orEmpty()
            (relatedSongs + nextResult.items).distinctBy { item -> item.id }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            emptyList()
        }

    private suspend fun searchRelatedSongs(songId: String): List<SongItem> =
        try {
            // Find song details in DB to query related songs
            val song = musicDao.getSongByIdOnce(songId.hashCode().toLong())
            if (song != null) {
                searchItems(
                    query = buildString {
                        append(song.title)
                        val artist = song.artistsJson?.let {
                            try {
                                val array = org.json.JSONArray(it)
                                if (array.length() > 0) array.getJSONObject(0).optString("name", "") else ""
                            } catch (_: Exception) { "" }
                        } ?: ""
                        if (artist.isNotBlank()) {
                            append(' ')
                            append(artist)
                        }
                    },
                    filter = YouTube.SearchFilter.FILTER_SONG,
                )
            } else {
                emptyList()
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            emptyList()
        }

    private suspend fun loadSuggestedArtists(): List<ArtistItem> =
        coroutineScope {
            var seedArtists = musicDao
                .getArtistsByPlayCount()
                .filter { it.channelId.isNotBlank() }
                .take(MaxSuggestionSeedItems)

            if (seedArtists.isEmpty()) {
                try {
                    val trendingArtistsResult = YouTube.search(query = "popular artists", filter = YouTube.SearchFilter.FILTER_ARTIST).getOrNull()
                    val fallbackItems = trendingArtistsResult?.items?.filterIsInstance<ArtistItem>().orEmpty()
                    seedArtists = fallbackItems.map { artistItem ->
                        com.unshoo.pixelmusic.data.database.ArtistPlayCountRow(
                            artistItem.id,
                            0L,
                            0
                        )
                    }
                } catch (_: Exception) {}
            }

            val seedArtistIds = seedArtists.mapTo(HashSet()) { it.channelId }

            seedArtists
                .map { artist ->
                    async {
                        loadRelatedArtists(artist.channelId)
                            .ifEmpty { searchRelatedArtists(artist.channelId) }
                    }
                }.awaitAll()
                .flatten()
                .filterNot { artist -> artist.id in seedArtistIds }
                .distinctBy { artist -> artist.id }
                .take(MaxSuggestedItems)
        }

    private suspend fun loadRelatedArtists(artistChannelId: String): List<ArtistItem> =
        try {
            YouTube
                .artist(artistChannelId)
                .getOrThrow()
                .sections
                .flatMap { section -> section.items }
                .filterIsInstance<ArtistItem>()
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            emptyList()
        }

    private suspend fun searchRelatedArtists(artistChannelId: String): List<ArtistItem> =
        try {
            val artist = musicDao.getArtistsByPlayCount().find { it.channelId == artistChannelId }
            if (artist != null) {
                // Find matching artist name by channel_id from database
                val artistEntity = musicDao.getArtistByChannelId(artistChannelId)
                if (artistEntity != null) {
                    searchItems(
                        query = artistEntity.name,
                        filter = YouTube.SearchFilter.FILTER_ARTIST,
                    )
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            emptyList()
        }

    private companion object {
        const val MaxHistoryLookupItems = 36
        const val MaxSuggestionSeedItems = 6
        const val MaxSuggestedItems = 12
        const val TopAlbumsQuery = "top albums"
    }
}
