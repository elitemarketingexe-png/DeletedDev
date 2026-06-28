package com.unshoo.pixelmusic.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.pages.ExplorePage
import unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage
import unshoo.ianshulyadav.pixelmusic.innertube.pages.ChartsPage
import javax.inject.Inject

import kotlinx.coroutines.async
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem

data class ExploreUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isContinuationLoading: Boolean = false,
    val homePageSections: List<HomePage.Section> = emptyList(),
    val homePageContinuation: String? = null,
    val newReleaseAlbums: List<AlbumItem> = emptyList(),
    val chartsPage: ChartsPage? = null,
    val error: String? = null,
    val selectedFilter: String = "All"
)

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val playbackStatsRepository: com.unshoo.pixelmusic.data.stats.PlaybackStatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (forceRefresh) {
                _uiState.update { it.copy(isRefreshing = true, error = null) }
            } else {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }
            try {
                // Fetch Explore sections parallelly
                val homeDeferred = async(Dispatchers.IO) { YouTube.home().getOrNull() }
                val exploreDeferred = async(Dispatchers.IO) { YouTube.explore().getOrNull() }
                val chartsDeferred = async(Dispatchers.IO) { YouTube.getChartsPage().getOrNull() }
                val historyDeferred = async(Dispatchers.IO) { playbackStatsRepository.loadPlaybackHistory(limit = 15) }

                val home = homeDeferred.await()
                val explore = exploreDeferred.await()
                val charts = chartsDeferred.await()
                val history = historyDeferred.await()

                if (home == null && explore == null && charts == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = "Failed to fetch explore data from YouTube Music. Please check your connection."
                        )
                    }
                } else {
                    val userActivityQuery = if (history.isNotEmpty()) {
                        val artistCounts = history.mapNotNull { it.artist }.groupingBy { it }.eachCount()
                        artistCounts.maxByOrNull { it.value }?.key ?: "Bollywood"
                    } else {
                        "Bollywood"
                    }

                    // Load community playlists for user's favorite artist
                    val communityPlaylistsResult = withContext(Dispatchers.IO) {
                        YouTube.search(
                            query = "$userActivityQuery playlist",
                            filter = YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
                        ).getOrNull()
                    }

                    val communityPlaylists = communityPlaylistsResult?.items?.filterIsInstance<PlaylistItem>() ?: emptyList()

                    val rawSections = home?.sections ?: emptyList()
                    var replacedTrending = false
                    var updatedSections = rawSections.map { section ->
                        if (section.title.contains("trending", ignoreCase = true) && communityPlaylists.isNotEmpty() && !replacedTrending) {
                            replacedTrending = true
                            HomePage.Section(
                                title = "Trending community playlists",
                                label = "Based on your activity for $userActivityQuery",
                                thumbnail = null,
                                endpoint = null,
                                items = communityPlaylists
                            )
                        } else {
                            section
                        }
                    }

                    if (communityPlaylists.isNotEmpty() && !updatedSections.any { it.title.contains("trending", ignoreCase = true) }) {
                        updatedSections = updatedSections + HomePage.Section(
                            title = "Trending community playlists",
                            label = "Based on your activity for $userActivityQuery",
                            thumbnail = null,
                            endpoint = null,
                            items = communityPlaylists
                        )
                    }

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            homePageSections = updatedSections,
                            homePageContinuation = home?.continuation,
                            newReleaseAlbums = explore?.newReleaseAlbums ?: emptyList(),
                            chartsPage = charts
                        )
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
    }

    fun loadMore() {
        val currentState = _uiState.value
        val continuation = currentState.homePageContinuation
        if (currentState.isContinuationLoading || continuation == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isContinuationLoading = true) }
            try {
                val result = withContext(Dispatchers.IO) {
                    YouTube.home(continuation = continuation).getOrNull()
                }
                if (result != null) {
                    _uiState.update {
                        it.copy(
                            isContinuationLoading = false,
                            homePageSections = it.homePageSections + result.sections,
                            homePageContinuation = result.continuation
                        )
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
        _uiState.update { it.copy(selectedFilter = filter) }
    }
}
