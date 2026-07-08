package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import com.unshoo.pixelmusic.data.database.ArtistPlayCountRow
import timber.log.Timber
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.YTItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.pages.ExplorePage
import unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage
import unshoo.ianshulyadav.pixelmusic.innertube.pages.ChartsPage
import javax.inject.Inject
import kotlinx.coroutines.async
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.preferences.PlaylistPreferencesRepository
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.database.toSongs

data class ExploreUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isContinuationLoading: Boolean = false,
    val homePageSections: List<HomePage.Section> = emptyList(),
    val homePageContinuation: String? = null,
    val newReleaseAlbums: List<AlbumItem> = emptyList(),
    val chartsPage: ChartsPage? = null,
    val error: String? = null,
    val selectedFilter: String = "All",
    val recentMixes: List<Playlist> = emptyList(),
    val libraryPlaylists: List<Playlist> = emptyList(),
    val moodChips: List<unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage.Chip> = emptyList(),
    val explorePageSections: List<unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage.Section> = emptyList(),
    val activeMoodChip: unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage.Chip? = null,
    val localSongs: Map<String, Song> = emptyMap()
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val playbackStatsRepository: com.unshoo.pixelmusic.data.stats.PlaybackStatsRepository,
    private val userPreferencesRepository: com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val musicDao: com.unshoo.pixelmusic.data.database.MusicDao,
    private val listeningStatsTracker: ListeningStatsTracker,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private var stage2Job: kotlinx.coroutines.Job? = null
    private var stage3Job: kotlinx.coroutines.Job? = null

    private val explorePrefs by lazy { context.getSharedPreferences("explore_guest_cache", Context.MODE_PRIVATE) }

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                restoreFromCache()
            }
            loadDataInternal(forceRefresh = false)
        }
        viewModelScope.launch {
            playlistPreferencesRepository.userPlaylistsFlow.collect { playlists ->
                val mixes = playlists.filter {
                    (it.source == "LASTFM_MIX" || it.source == "AI" || it.isAiGenerated) &&
                            it.songIds.isNotEmpty() &&
                            !it.name.contains("deleted", ignoreCase = true)
                }.sortedByDescending { it.lastModified }
                val libPlaylists = playlists.filter { !it.isQueueGenerated && it.id != "_downloaded_" && it.source != "LASTFM_MIX" && it.source != "AI" && !it.isAiGenerated }
                    .sortedByDescending { it.lastModified }
                _uiState.update { it.copy(recentMixes = mixes, libraryPlaylists = libPlaylists) }
            }
        }
    }

    private val gson by lazy {
        com.google.gson.GsonBuilder()
            .registerTypeAdapter(YTItem::class.java, YTItemTypeAdapter())
            .create()
    }

    private val cacheFile by lazy {
        java.io.File(context.cacheDir, "explore_cache.json")
    }

    private fun restoreFromCache() {
        try {
            if (cacheFile.exists()) {
                val json = cacheFile.readText()
                val cache = gson.fromJson(json, ExploreCacheModel::class.java)
                _uiState.update {
                    it.copy(
                        isLoading = true, // still loading fresh data
                        homePageSections = cache.sections,
                        homePageContinuation = cache.continuation,
                        newReleaseAlbums = cache.albums,
                        chartsPage = cache.charts
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to restore explore data from cache")
        }
    }

    private fun persistToCache(state: ExploreUiState) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cache = ExploreCacheModel(
                    sections = state.homePageSections,
                    albums = state.newReleaseAlbums,
                    charts = state.chartsPage,
                    continuation = state.homePageContinuation,
                    timestamp = System.currentTimeMillis()
                )
                val json = gson.toJson(cache)
                cacheFile.writeText(json)
            } catch (e: Exception) {
                Timber.e(e, "Failed to persist explore data to cache")
            }
        }
    }

    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            loadDataInternal(forceRefresh)
        }
    }

    private suspend fun loadDataInternal(forceRefresh: Boolean) {
        stage2Job?.cancel()
        stage3Job?.cancel()

        if (forceRefresh) {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
        } else {
            // Only show loading spinner if we have no cached data at all
            val hasCachedData = _uiState.value.homePageSections.isNotEmpty() ||
                    _uiState.value.newReleaseAlbums.isNotEmpty() ||
                    _uiState.value.chartsPage != null
            _uiState.update { it.copy(isLoading = !hasCachedData, error = null) }
        }
        try {
            // 1. Get history and candidateArtistId immediately (fast database/prefs calls)
            // Prefer merged local + YT Music history (kept hot by ListeningStatsTracker).
            // Fall back to disk-only local history if the tracker has not initialized yet.
            val history = withContext(Dispatchers.IO) {
                listeningStatsTracker.refreshMergedYoutubeHistory()
                val merged = listeningStatsTracker.playbackHistory.value
                if (merged.isNotEmpty()) {
                    merged.take(30)
                } else {
                    playbackStatsRepository.loadPlaybackHistory(limit = 30)
                }
            }
            val candidateArtistId = withContext(Dispatchers.IO) {
                userPreferencesRepository.subscribedArtistIdsFlow.first().firstOrNull()
            }
            
            // Query database for library artists with valid channel IDs to personalize New Releases
            val dbArtists = withContext(Dispatchers.IO) {
                try {
                    musicDao.getAllArtistsListRaw()
                } catch (e: Exception) {
                    emptyList()
                }
            }
            val libraryArtistChannelIds = dbArtists
                .mapNotNull { it.channelId }
                .filter { it.isNotBlank() }
                .distinct()

            val userActivityQuery = if (history.isNotEmpty()) {
                val artistCounts = history.mapNotNull { it.artist }.groupingBy { it }.eachCount()
                artistCounts.maxByOrNull { it.value }?.key ?: "Bollywood"
            } else {
                "Bollywood"
            }

            val hasLogin = YouTube.hasLoginCookie()

            // --- STAGE 1: Fetch and display core Above-the-Fold Content (Instant) ---
            var home: HomePage? = null
            var explore: ExplorePage? = null
            var charts: ChartsPage? = null
            var newReleasesResult: List<AlbumItem>? = null

            coroutineScope {
                val homeDeferred = async(Dispatchers.IO) { runCatching { YouTube.home().getOrNull() }.getOrNull() }
                val chartsDeferred = async(Dispatchers.IO) { runCatching { YouTube.getChartsPage().getOrNull() }.getOrNull() }
                val newReleasesDeferred = async(Dispatchers.IO) {
                    if (!YouTube.hasLoginCookie()) runCatching { YouTube.newReleaseAlbums().getOrNull() }.getOrNull() else null
                }
                val exploreDeferred = async(Dispatchers.IO) { runCatching { YouTube.explore().getOrNull() }.getOrNull() }

                home = homeDeferred.await()
                charts = chartsDeferred.await()
                newReleasesResult = newReleasesDeferred.await()
                explore = exploreDeferred.await()

                 _uiState.update { currentState ->
                    val filterSections = { sections: List<unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage.Section> ->
                        sections.filter { section ->
                            val title = section.title.lowercase()
                            !title.contains("new music videos") && !title.contains("new albums & singles")
                        }
                    }
                    val filteredHomeSections = filterSections(home?.sections.orEmpty())
                    val filteredExploreSections = filterSections(explore?.sections.orEmpty())
                    
                    val mergedSections = (filteredHomeSections + filteredExploreSections + currentState.homePageSections).distinctBy { it.title }
                    val mergedChips = ((home?.chips.orEmpty()) + (explore?.chips.orEmpty()) + currentState.moodChips).distinctBy { it.title }
                    currentState.copy(
                        isLoading = false,
                        isRefreshing = false,
                        homePageSections = mergedSections,
                        homePageContinuation = home?.continuation ?: currentState.homePageContinuation,
                        explorePageSections = filteredExploreSections,
                        chartsPage = charts ?: currentState.chartsPage,
                        newReleaseAlbums = newReleasesResult ?: currentState.newReleaseAlbums,
                        moodChips = mergedChips
                    )
                }
            }

            if (home == null && explore == null && charts == null && newReleasesResult == null) {
                // Only show error if we also have no cached data
                val hasCachedData = _uiState.value.homePageSections.isNotEmpty() ||
                        _uiState.value.newReleaseAlbums.isNotEmpty() ||
                        _uiState.value.chartsPage != null
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = if (!hasCachedData) "Failed to fetch explore data from YouTube Music. Please check your connection." else null
                    )
                }
                return
            }


            // --- STAGE 2: Fetch and display Library & Recommendations in background ---
            stage2Job = viewModelScope.launch(Dispatchers.IO) {
                try {
                    coroutineScope {
                        val communityPlaylistsDeferred = async {
                            YouTube.search(
                                query = "$userActivityQuery playlist",
                                filter = YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
                            ).getOrNull()
                        }

                        // Liked and Cached local songs
                        val allLocalSongs = try {
                            musicDao.getAllSongsList()
                        } catch (e: Exception) {
                            emptyList()
                        }

                        val likedSongs = allLocalSongs.filter { it.isFavorite }
                        val cachedSongs = allLocalSongs.filter { entity ->
                            entity.filePath.isNotBlank() && java.io.File(entity.filePath).exists()
                        }

                        val likedSongItems = likedSongs.take(15).map { entity ->
                            SongItem(
                                id = entity.id.toString(),
                                title = entity.title,
                                artists = listOf(unshoo.ianshulyadav.pixelmusic.innertube.models.Artist(entity.artistName, null)),
                                album = if (entity.albumName.isNotBlank()) unshoo.ianshulyadav.pixelmusic.innertube.models.Album(entity.albumName, "") else null,
                                duration = (entity.duration / 1000).toInt(),
                                thumbnail = entity.albumArtUriString ?: "",
                                explicit = false,
                                endpoint = null
                            )
                        }

                        val cachedSongItems = cachedSongs.take(15).map { entity ->
                            SongItem(
                                id = entity.id.toString(),
                                title = entity.title,
                                artists = listOf(unshoo.ianshulyadav.pixelmusic.innertube.models.Artist(entity.artistName, null)),
                                album = if (entity.albumName.isNotBlank()) unshoo.ianshulyadav.pixelmusic.innertube.models.Album(entity.albumName, "") else null,
                                duration = (entity.duration / 1000).toInt(),
                                thumbnail = entity.albumArtUriString ?: "",
                                explicit = false,
                                endpoint = null
                            )
                        }

                        // You Might Like (Recommendations based on top 3 most-played artists in history)
                        val topArtists = history
                            .mapNotNull { it.artist }
                            .filter { it.isNotBlank() }
                            .groupingBy { it }
                            .eachCount()
                            .entries
                            .sortedByDescending { it.value }
                            .take(3)
                            .map { it.key }

                        val searchJobs = topArtists.map { artistName ->
                            async {
                                YouTube.search(query = artistName, filter = YouTube.SearchFilter.FILTER_SONG)
                                    .getOrNull()?.items?.filterIsInstance<SongItem>() ?: emptyList()
                            }
                        }
                        val searchResults = searchJobs.flatMap { it.await() }

                        val playedSongIds = (history.mapNotNull { it.songId } + allLocalSongs.map { it.id.toString() }).toSet()
                        val youMightLikeItems = searchResults
                            .distinctBy { it.id }
                            .filter { it.id !in playedSongIds }
                            .take(10)

                        // Recently Played and Most Played local history auto-shelves
                        val localSongsMap = allLocalSongs.associateBy { it.id.toString() }
                        val historyMap = history.associateBy { it.songId }

                        val recentSongItems = history.take(15).map { entry ->
                            val local = localSongsMap[entry.songId]
                            val title = local?.title ?: entry.title ?: "Unknown"
                            val artistName = local?.artistName ?: entry.artist ?: "Unknown Artist"
                            val thumb = local?.albumArtUriString ?: entry.thumbnail ?: ""
                            val dur = local?.duration?.div(1000)
                            SongItem(
                                id = entry.songId,
                                title = title,
                                artists = listOf(unshoo.ianshulyadav.pixelmusic.innertube.models.Artist(artistName, null)),
                                album = if (local?.albumName?.isNotBlank() == true) unshoo.ianshulyadav.pixelmusic.innertube.models.Album(local.albumName, "") else null,
                                duration = dur?.toInt(),
                                thumbnail = thumb,
                                explicit = false,
                                endpoint = null
                            )
                        }

                        val mostPlayedSongs = playbackStatsRepository.loadSongPlayCounts(limit = 15)
                        val mostPlayedSongItems = mostPlayedSongs.mapNotNull { entry ->
                            val local = localSongsMap[entry.songId]
                            val hist = historyMap[entry.songId]
                            val title = local?.title ?: hist?.title
                            val artistName = local?.artistName ?: hist?.artist ?: "Unknown Artist"
                            val thumb = local?.albumArtUriString ?: hist?.thumbnail ?: ""
                            val dur = local?.duration?.div(1000)
                            if (title != null) {
                                SongItem(
                                    id = entry.songId,
                                    title = title,
                                    artists = listOf(unshoo.ianshulyadav.pixelmusic.innertube.models.Artist(artistName, null)),
                                    album = if (local?.albumName?.isNotBlank() == true) unshoo.ianshulyadav.pixelmusic.innertube.models.Album(local.albumName, "") else null,
                                    duration = dur?.toInt(),
                                    thumbnail = thumb,
                                    explicit = false,
                                    endpoint = null
                                )
                            } else null
                        }

                        val communityPlaylistsResult = communityPlaylistsDeferred.await()
                        val communityPlaylists = communityPlaylistsResult?.items?.filterIsInstance<PlaylistItem>() ?: emptyList()

                        _uiState.update { currentState ->
                            val updatedSections = currentState.homePageSections.toMutableList()

                            if (communityPlaylists.isNotEmpty()) {
                                updatedSections.add(HomePage.Section(
                                    title = "Community Playlists",
                                    label = "Based on your activity for $userActivityQuery",
                                    thumbnail = null,
                                    endpoint = null,
                                    items = communityPlaylists
                                ))
                            }

                            if (likedSongItems.size >= 3) {
                                updatedSections.add(HomePage.Section(
                                    title = "Your Liked Songs",
                                    label = "Favorites from your library",
                                    thumbnail = likedSongItems.firstOrNull()?.thumbnail,
                                    endpoint = null,
                                    items = likedSongItems
                                ))
                            }

                            if (cachedSongItems.size >= 3) {
                                updatedSections.add(HomePage.Section(
                                    title = "Cached & Downloaded",
                                    label = "Offline playback ready",
                                    thumbnail = cachedSongItems.firstOrNull()?.thumbnail,
                                    endpoint = null,
                                    items = cachedSongItems
                                ))
                            }

                            if (youMightLikeItems.isNotEmpty()) {
                                updatedSections.add(HomePage.Section(
                                    title = "You Might Like",
                                    label = "Recommended for you",
                                    thumbnail = youMightLikeItems.firstOrNull()?.thumbnail,
                                    endpoint = null,
                                    items = youMightLikeItems
                                ))
                            }

                            if (recentSongItems.size >= 5) {
                                updatedSections.add(HomePage.Section(
                                    title = "Your Recently Played",
                                    label = "Recent history",
                                    thumbnail = recentSongItems.firstOrNull()?.thumbnail,
                                    endpoint = null,
                                    items = recentSongItems
                                ))
                            }

                            if (mostPlayedSongItems.size >= 5) {
                                updatedSections.add(HomePage.Section(
                                    title = "Your Most Played",
                                    label = "All-time top tracks",
                                    thumbnail = mostPlayedSongItems.firstOrNull()?.thumbnail,
                                    endpoint = null,
                                    items = mostPlayedSongItems
                                ))
                            }

                            val finalNewReleases = if (YouTube.hasLoginCookie()) {
                                val localArtistNames = (
                                    allLocalSongs.map { it.artistName.lowercase().trim() } +
                                    dbArtists.map { it.name.lowercase().trim() } +
                                    history.mapNotNull { it.artist?.lowercase()?.trim() }
                                ).filter { it.isNotBlank() }.toSet()
                                
                                val globalReleases = YouTube.newReleaseAlbums().getOrNull().orEmpty()
                                val globalFiltered = globalReleases.filter { album ->
                                    album.artists?.any { it.name.lowercase().trim() in localArtistNames } == true
                                }
                                
                                val enrichedReleases = mutableListOf<AlbumItem>()
                                enrichedReleases.addAll(globalFiltered)
                                
                                val topArtistNames = history
                                    .mapNotNull { it.artist }
                                    .filter { it.isNotBlank() }
                                    .groupingBy { it }
                                    .eachCount()
                                    .entries
                                    .sortedByDescending { it.value }
                                    .take(3)
                                    .map { it.key }
                                    
                                val searchJobs = topArtistNames.map { artistName ->
                                    async {
                                        YouTube.search(query = artistName, filter = YouTube.SearchFilter.FILTER_ALBUM)
                                            .getOrNull()?.items?.filterIsInstance<AlbumItem>() ?: emptyList()
                                    }
                                }
                                val searchResults = searchJobs.flatMap { it.await() }
                                
                                (enrichedReleases + searchResults)
                                    .distinctBy { it.browseId }
                                    .take(15)
                            } else {
                                null
                            }

                            val renderedLocalEntities = (
                                likedSongs.take(15) + 
                                cachedSongs.take(15) + 
                                history.take(15).mapNotNull { entry -> localSongsMap[entry.songId] } + 
                                mostPlayedSongs.mapNotNull { entry -> localSongsMap[entry.songId] }
                            ).distinctBy { it.id }

                            val renderedSongsMapped = renderedLocalEntities.toSongs()
                            val localSongsMapFiltered = renderedSongsMapped.associateBy { it.id }

                            currentState.copy(
                                homePageSections = updatedSections.distinctBy { it.title },
                                localSongs = localSongsMapFiltered,
                                newReleaseAlbums = finalNewReleases ?: currentState.newReleaseAlbums
                            )
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error loading Stage 2 Explore data")
                }
            }

            // --- STAGE 3: Persist final state to cache ---
            stage3Job = viewModelScope.launch(Dispatchers.IO) {
                try {
                    // Wait briefly for Stage 2 to settle, then persist
                    kotlinx.coroutines.delay(2000)
                    persistToCache(_uiState.value)
                } catch (e: Exception) {
                    Timber.e(e, "Error persisting Stage 3 Explore data")
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error loading Explore screen data")
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = e.localizedMessage ?: "Unknown error occurred"
                )
            }
        }
    }

    fun loadMore() {
        val currentState = _uiState.value
        val continuation = currentState.homePageContinuation
        if (currentState.isContinuationLoading) return

        if (continuation == null) {
            val hasLocalSections = currentState.homePageSections.any { it.title == "Recently Played (Local)" }
            if (hasLocalSections) return

            viewModelScope.launch {
                _uiState.update { it.copy(isContinuationLoading = true) }
                try {
                    val recentSongs = withContext(Dispatchers.IO) {
                        val merged = listeningStatsTracker.playbackHistory.value
                        if (merged.isNotEmpty()) merged.take(20)
                        else playbackStatsRepository.loadPlaybackHistory(limit = 20)
                    }
                    val dbArtists = withContext(Dispatchers.IO) {
                        try {
                            musicDao.getAllArtistsListRaw().sortedByDescending { it.trackCount }.take(15)
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }

                    val localSections = mutableListOf<HomePage.Section>()

                    if (recentSongs.isNotEmpty()) {
                        val songItems = recentSongs.map { entry ->
                            SongItem(
                                id = entry.songId,
                                title = entry.title ?: "",
                                artists = listOf(unshoo.ianshulyadav.pixelmusic.innertube.models.Artist(entry.artist ?: "Unknown Artist", null)),
                                album = null,
                                duration = null,
                                thumbnail = entry.thumbnail ?: "",
                                explicit = false,
                                endpoint = null
                            )
                        }
                        localSections.add(HomePage.Section(
                            title = "Recently Played (Local)",
                            label = "From your history",
                            thumbnail = null,
                            endpoint = null,
                            items = songItems
                        ))
                    }

                    if (dbArtists.isNotEmpty()) {
                        val artistItems = dbArtists.map { entity ->
                            ArtistItem(
                                id = entity.channelId ?: entity.id.toString(),
                                title = entity.name,
                                thumbnail = entity.customImageUri ?: entity.imageUrl,
                                channelId = entity.channelId,
                                shuffleEndpoint = null,
                                radioEndpoint = null
                            )
                        }
                        localSections.add(HomePage.Section(
                            title = "Top Library Artists",
                            label = "Based on your local library",
                            thumbnail = null,
                            endpoint = null,
                            items = artistItems
                        ))
                    }

                    val libraryPlaylists = currentState.libraryPlaylists
                    if (libraryPlaylists.isNotEmpty()) {
                        val playlistItems = libraryPlaylists.take(10).map { playlist ->
                            PlaylistItem(
                                id = playlist.id,
                                title = playlist.name,
                                author = unshoo.ianshulyadav.pixelmusic.innertube.models.Artist("You", null),
                                songCountText = "${playlist.songIds.size} songs",
                                thumbnail = playlist.coverImageUri,
                                playEndpoint = null,
                                shuffleEndpoint = null,
                                radioEndpoint = null,
                                isEditable = true
                            )
                        }
                        localSections.add(HomePage.Section(
                            title = "Your Custom Playlists",
                            label = "Created by you",
                            thumbnail = null,
                            endpoint = null,
                            items = playlistItems
                        ))
                    }

                    _uiState.update { state ->
                        state.copy(
                            isContinuationLoading = false,
                            homePageSections = state.homePageSections + localSections
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error loading local sections for Explore")
                    _uiState.update { it.copy(isContinuationLoading = false) }
                }
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isContinuationLoading = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    YouTube.home(continuation = continuation).getOrNull()
                }
                if (result != null) {
                    _uiState.update {
                        val filteredNewSections = result.sections.filter { section ->
                            val title = section.title.lowercase()
                            !title.contains("new music videos") && !title.contains("new albums & singles")
                        }
                        val newState = it.copy(
                            isContinuationLoading = false,
                            homePageSections = it.homePageSections + filteredNewSections,
                            homePageContinuation = result.continuation
                        )
                        persistToCache(newState)
                        newState
                    }
                } else {
                    _uiState.update { it.copy(isContinuationLoading = false) }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading more Explore screen sections")
                _uiState.update { it.copy(isContinuationLoading = false) }
            }
        }
    }

    fun setSelectedFilter(filter: String) {
        _uiState.update { it.copy(selectedFilter = filter, activeMoodChip = null) }
        if (filter == "All") {
            loadData(forceRefresh = false)
        }
    }

    fun selectMoodChip(chip: unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage.Chip?) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeMoodChip = chip, isLoading = true, error = null) }
            if (chip == null) {
                loadDataInternal(false)
            } else {
                withContext(Dispatchers.IO) {
                    val endpoint = chip.endpoint
                    if (endpoint != null) {
                        YouTube.explore(browseId = endpoint.browseId, params = endpoint.params).onSuccess { exp ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    explorePageSections = exp.sections
                                )
                            }
                        }.onFailure { e ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = "Failed to fetch mood feed: ${e.message}"
                                )
                            }
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
            }
        }
    }
}

@androidx.annotation.Keep
data class ExploreCacheModel(
    val sections: List<HomePage.Section>,
    val albums: List<AlbumItem>,
    val charts: ChartsPage?,
    val continuation: String?,
    val timestamp: Long
)

private class YTItemTypeAdapter : com.google.gson.JsonSerializer<YTItem>, com.google.gson.JsonDeserializer<YTItem> {
    override fun serialize(src: YTItem, typeOfSrc: java.lang.reflect.Type, context: com.google.gson.JsonSerializationContext): com.google.gson.JsonElement {
        val obj = context.serialize(src).asJsonObject
        obj.addProperty("type", src::class.java.simpleName)
        return obj
    }

    override fun deserialize(json: com.google.gson.JsonElement, typeOfT: java.lang.reflect.Type, context: com.google.gson.JsonDeserializationContext): YTItem {
        val obj = json.asJsonObject
        val type = obj.get("type").asString
        val clazz = when (type) {
            "SongItem" -> SongItem::class.java
            "AlbumItem" -> AlbumItem::class.java
            "PlaylistItem" -> PlaylistItem::class.java
            "ArtistItem" -> ArtistItem::class.java
            else -> throw com.google.gson.JsonParseException("Unknown type: $type")
        }
        return context.deserialize(obj, clazz)
    }
}
