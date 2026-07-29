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
import kotlinx.coroutines.ensureActive
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
    val isChartsLoading: Boolean = false,
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

    companion object {
        private const val STALE_CONTENT_MS = 2 * 60 * 60 * 1000L // 2 hours
        private const val STAGE2_DELAY_MS = 800L // increased from 500 to let first frame settle
    }

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

    /** Wall-clock time the content currently in [_uiState] was fetched (0 = unknown/never). */
    private var lastContentTimestamp: Long = 0L

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
                // Avoid reading huge cache (>500KB) - corrupted or stale
                if (cacheFile.length() > 512 * 1024) {
                    cacheFile.delete()
                    return
                }
                val json = cacheFile.readText()
                val cache = gson.fromJson(json, ExploreCacheModel::class.java)
                if (cache != null && (cache.sections.isNotEmpty() || cache.albums.isNotEmpty() || cache.charts != null)) {
                    // Check staleness: if older than 12h, don't use for UI but keep for fallback
                    val age = System.currentTimeMillis() - cache.timestamp
                    if (age < 12 * 60 * 60 * 1000L) {
                        _uiState.update {
                            it.copy(
                                isLoading = true,
                                homePageSections = cache.sections,
                                homePageContinuation = cache.continuation,
                                newReleaseAlbums = cache.albums,
                                chartsPage = cache.charts
                            )
                        }
                        lastContentTimestamp = cache.timestamp
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to restore explore data from cache")
            try { cacheFile.delete() } catch (_: Exception) {}
        }
    }

    private fun persistToCache(state: ExploreUiState) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cache = ExploreCacheModel(
                    sections = state.homePageSections.take(20), // cap to 20 sections to keep file small
                    albums = state.newReleaseAlbums.take(30),
                    charts = state.chartsPage,
                    continuation = state.homePageContinuation,
                    timestamp = System.currentTimeMillis()
                )
                val json = gson.toJson(cache)
                if (json.length < 400 * 1024) { // only persist if <400KB
                    cacheFile.writeText(json)
                }
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
            val hasCachedData = _uiState.value.homePageSections.isNotEmpty() ||
                    _uiState.value.newReleaseAlbums.isNotEmpty() ||
                    _uiState.value.chartsPage != null
            _uiState.update { it.copy(isLoading = !hasCachedData, error = null) }
        }
        try {
            // Fast path: history in single IO block
            val history = withContext(Dispatchers.IO) {
                listeningStatsTracker.refreshMergedYoutubeHistory()
                val merged = listeningStatsTracker.playbackHistory.value
                if (merged.isNotEmpty()) merged.take(20) else playbackStatsRepository.loadPlaybackHistory(limit = 20)
            }

            // --- STAGE 1: Core Above-the-Fold (Instant - YouTube Home only) ---
            val home = withContext(Dispatchers.IO) {
                runCatching { YouTube.home().getOrNull() }.getOrNull()
            }

            _uiState.update { currentState ->
                val filterSections = { sections: List<HomePage.Section> ->
                    sections.filter { section ->
                        val title = section.title.lowercase()
                        !title.contains("new music videos") &&
                                !title.contains("new albums & singles") &&
                                !title.contains("trending")
                    }
                }
                val filteredHomeSections = filterSections(home?.sections.orEmpty())

                currentState.copy(
                    isLoading = false,
                    isRefreshing = false,
                    homePageSections = (filteredHomeSections + currentState.homePageSections).distinctBy { it.title },
                    moodChips = (home?.chips.orEmpty() + currentState.moodChips).distinctBy { it.title }
                )
            }

            if (home == null && _uiState.value.homePageSections.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = "Failed to fetch explore data. Check connection."
                    )
                }
                return
            }
            lastContentTimestamp = System.currentTimeMillis()
            persistToCache(_uiState.value)

            // --- STAGE 2: Library & Recommendations (background, throttled) ---
            stage2Job = viewModelScope.launch(Dispatchers.IO) {
                // Let Stage1 render first frame
                kotlinx.coroutines.delay(STAGE2_DELAY_MS)
                try {
                    // Early exit if cancelled
                    ensureActive()

                    // PERF: Use indexed query only, no File.exists() heavy checks
                    val likedSongs = try {
                        musicDao.getFavoriteSongsListSimple(limit = 10)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    // OPTIMIZATION: No File.exists() - trusting DB file_path. Playback handles missing file.
                    val cachedSongs = try {
                        musicDao.getSongsWithLocalFileList(limit = 15)
                    } catch (e: Exception) {
                        emptyList()
                    }

                    val likedSongItems = likedSongs.take(10).map { entity ->
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
                    val cachedSongItems = cachedSongs.take(10).map { entity ->
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

                    ensureActive()

                    // Recently Played and Most Played - reuse history, no extra distinct artist query
                    val historyMap = history.associateBy { it.songId }
                    val mostPlayedSongs = playbackStatsRepository.loadSongPlayCounts(limit = 10)

                    val neededLocalSongIds = (history.map { it.songId } + mostPlayedSongs.map { it.songId })
                        .mapNotNull { it.toLongOrNull() }
                        .distinct()
                        .take(30) // cap to avoid loading too many rows
                    val localSongsMap = try {
                        if (neededLocalSongIds.isNotEmpty()) {
                            musicDao.getSongsByIdsListSimple(neededLocalSongIds).associateBy { it.id.toString() }
                        } else {
                            emptyMap()
                        }
                    } catch (e: Exception) {
                        emptyMap()
                    }

                    val recentSongItems = history.take(10).map { entry ->
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

                    ensureActive()

                    _uiState.update { currentState ->
                        val updatedSections = currentState.homePageSections.toMutableList()



                        val finalNewReleases = emptyList<AlbumItem>()

                        val renderedLocalEntities = (
                                likedSongs.take(10) +
                                        cachedSongs.take(10) +
                                        history.take(10).mapNotNull { entry -> localSongsMap[entry.songId] } +
                                        mostPlayedSongs.mapNotNull { entry -> localSongsMap[entry.songId] }
                                ).distinctBy { it.id }

                        val renderedSongsMapped = renderedLocalEntities.toSongs()
                        val localSongsMapFiltered = renderedSongsMapped.associateBy { it.id }

                        currentState.copy(
                            homePageSections = updatedSections.distinctBy { it.title },
                            localSongs = localSongsMapFiltered,
                            newReleaseAlbums = finalNewReleases
                        )
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.e(e, "Error loading Stage 2 Explore data")
                }
            }

            // --- STAGE 3: Persist final state ---
            stage3Job = viewModelScope.launch(Dispatchers.IO) {
                try {
                    kotlinx.coroutines.delay(3000)
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

    fun loadChartsIfNeeded() {
        if (_uiState.value.chartsPage != null || _uiState.value.isChartsLoading) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isChartsLoading = true) }
            val charts = runCatching { YouTube.getChartsPage().getOrNull() }.getOrNull()
            _uiState.update { it.copy(isChartsLoading = false, chartsPage = charts) }
        }
    }

    fun loadMore() {
        // Continuation loading removed for performance
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
