package com.unshoo.pixelmusic.presentation.viewmodel

import androidx.paging.filter
import com.unshoo.pixelmusic.data.model.Album
import com.unshoo.pixelmusic.data.model.Artist
import com.unshoo.pixelmusic.data.model.Genre
import com.unshoo.pixelmusic.data.model.MusicFolder
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.model.SortOption
import com.unshoo.pixelmusic.data.model.StorageFilter
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.repository.MusicRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val ENABLE_FOLDERS_STORAGE_FILTER = false

/**
 * Library state — wired to local Room DB (local + YouTube songs, albums, artists).
 * YouTube subscribed artists / liked songs are synced into the DB by YouTubeLibrarySyncManager
 * before this state holder observes them, so all paging flows pick them up automatically.
 */
@Singleton
class LibraryStateHolder @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository
) {

    // --- Non-paged state kept for compat with callers that still use them ---
    private val _allSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val allSongs = _allSongs.asStateFlow()

    private val _allSongsById = MutableStateFlow<Map<String, Song>>(emptyMap())
    val allSongsById = _allSongsById.asStateFlow()

    private val _albums = MutableStateFlow<ImmutableList<Album>>(persistentListOf())
    val albums = _albums.asStateFlow()

    private val _artists = MutableStateFlow<ImmutableList<Artist>>(persistentListOf())
    val artists = _artists.asStateFlow()

    private val _musicFolders = MutableStateFlow<ImmutableList<MusicFolder>>(persistentListOf())
    val musicFolders = _musicFolders.asStateFlow()

    private val _isLoadingLibrary = MutableStateFlow(false)
    val isLoadingLibrary = _isLoadingLibrary.asStateFlow()

    private val _isLoadingCategories = MutableStateFlow(false)
    val isLoadingCategories = _isLoadingCategories.asStateFlow()

    // --- Sort / filter options ---
    private val _currentSongSortOption = MutableStateFlow<SortOption>(SortOption.SongDefaultOrder)
    val currentSongSortOption = _currentSongSortOption.asStateFlow()

    private val _currentStorageFilter = MutableStateFlow(StorageFilter.ALL)
    val currentStorageFilter = _currentStorageFilter.asStateFlow()

    private val _currentAlbumSortOption = MutableStateFlow<SortOption>(SortOption.AlbumTitleAZ)
    val currentAlbumSortOption = _currentAlbumSortOption.asStateFlow()

    private val _currentArtistSortOption = MutableStateFlow<SortOption>(SortOption.ArtistNameAZ)
    val currentArtistSortOption = _currentArtistSortOption.asStateFlow()

    private val _currentFolderSortOption = MutableStateFlow<SortOption>(SortOption.FolderNameAZ)
    val currentFolderSortOption = _currentFolderSortOption.asStateFlow()

    private val _currentFavoriteSortOption = MutableStateFlow<SortOption>(SortOption.LikedSongDateLiked)
    val currentFavoriteSortOption = _currentFavoriteSortOption.asStateFlow()

    /**
     * Effective storage filter — when "hide local media" pref is on, force ONLINE so
     * only streaming/YouTube songs appear; otherwise use the user-selected filter.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val effectiveStorageFilter: Flow<StorageFilter> =
        combine(
            _currentStorageFilter,
            userPreferencesRepository.hideLocalMediaFlow
        ) { filter, hideLocal ->
            if (hideLocal) StorageFilter.ONLINE else filter
        }.distinctUntilChanged()

    // --- Paging flows wired to the local Room DB ---

    @OptIn(ExperimentalCoroutinesApi::class)
    val songsPagingFlow: Flow<androidx.paging.PagingData<Song>> =
        combine(_currentSongSortOption, effectiveStorageFilter) { sort, filter ->
            sort to filter
        }.distinctUntilChanged()
        .flatMapLatest { (sortOption, filter) ->
            musicRepository.getPaginatedSongs(sortOption, filter)
        }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    val albumsPagingFlow: Flow<androidx.paging.PagingData<Album>> =
        combine(
            _currentAlbumSortOption,
            effectiveStorageFilter,
            userPreferencesRepository.minTracksPerAlbumFlow
        ) { sort, filter, minTracks ->
            Triple(sort, filter, minTracks)
        }.distinctUntilChanged()
        .flatMapLatest { (sortOption, filter, minTracks) ->
            musicRepository.getPaginatedAlbums(sortOption, filter, minTracks)
        }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    val artistsPagingFlow: Flow<androidx.paging.PagingData<Artist>> =
        combine(
            _currentArtistSortOption,
            effectiveStorageFilter
        ) { sortOption, filter ->
            sortOption to filter
        }.distinctUntilChanged()
        .flatMapLatest { (sortOption, filter) ->
            musicRepository.getPaginatedArtists(sortOption, filter)
        }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    val favoritesPagingFlow: Flow<androidx.paging.PagingData<Song>> =
        combine(_currentFavoriteSortOption, effectiveStorageFilter) { sort, filter ->
            sort to filter
        }.distinctUntilChanged()
        .flatMapLatest { (sortOption, storageFilter) ->
            musicRepository.getPaginatedFavoriteSongs(sortOption, storageFilter)
        }.flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    val favoriteSongCountFlow: Flow<Int> = effectiveStorageFilter
        .distinctUntilChanged()
        .flatMapLatest { filter -> musicRepository.getFavoriteSongCountFlow(filter) }
        .flowOn(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    val genres: Flow<ImmutableList<Genre>> = musicRepository.getGenres()
        .map { it.toImmutableList() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    private var foldersJob: kotlinx.coroutines.Job? = null
    private var scope: CoroutineScope? = null

    fun initialize(scope: CoroutineScope) {
        this.scope = scope
        foldersJob?.cancel()
        foldersJob = scope.launch {
            combine(
                effectiveStorageFilter,
                _currentFolderSortOption
            ) { filter, sortOption ->
                filter to sortOption
            }.flatMapLatest { (filter, sortOption) ->
                musicRepository.getMusicFolders(StorageFilter.LOCAL)
                    .map { folders ->
                        sortFoldersList(folders, sortOption).toImmutableList()
                    }
            }.flowOn(Dispatchers.IO)
            .collect { sortedFolders ->
                _musicFolders.value = sortedFolders
            }
        }
    }

    fun onCleared() {
        foldersJob?.cancel()
        scope = null
    }

    // --- No-op data loaders (paging flows auto-refresh from DB; nothing to do here) ---
    fun startObservingLibraryData() {}
    fun loadAlbumsFromRepository() {}
    fun loadArtistsFromRepository() {}
    fun loadFoldersFromRepository() {}
    fun loadAlbumsIfNeeded() {}
    fun loadArtistsIfNeeded() {}

    // --- allSongs / allSongsById hydration ---
    // NOTE: this is NOT what backs the visible library list (that's songsPagingFlow, above,
    // and must stay on PagingSource). This is a separate, bounded (~library-size) background
    // cache that PlayerViewModel relies on for queue building (resolvePlaybackQueueFromSortedIds)
    // and id->title lookups. It used to be populated eagerly pre-Paging; when the Songs tab
    // moved to PagingData these loaders were stubbed to no-ops and nobody re-wired the cache,
    // so every full-queue playback silently fell back to chunked DB fetches instead of an O(1)
    // map lookup, and the id/title lookups silently missed. Restoring it here, off the paging
    // path, on a background dispatcher, coalescing concurrent callers into one load.
    private var songsHydrationJob: kotlinx.coroutines.Job? = null

    fun loadSongsFromRepository() {
        val activeScope = scope ?: return
        if (songsHydrationJob?.isActive == true) return
        songsHydrationJob = activeScope.launch(Dispatchers.IO) {
            runCatching { musicRepository.getAllSongsOnce() }
                .onSuccess { songs ->
                    _allSongsById.value = songs.associateBy { it.id }
                    _allSongs.value = songs.toImmutableList()
                }
                .onFailure { e ->
                    Timber.e(e, "LibraryStateHolder: failed to hydrate allSongs/allSongsById cache")
                }
        }
    }

    fun loadSongsIfNeeded() {
        if (_allSongs.value.isEmpty()) {
            loadSongsFromRepository()
        }
    }

    // --- Sort options ---
    fun sortSongs(sortOption: SortOption, persist: Boolean = true) {
        _currentSongSortOption.value = sortOption
    }

    fun sortAlbums(sortOption: SortOption, persist: Boolean = true) {
        _currentAlbumSortOption.value = sortOption
    }

    fun sortArtists(sortOption: SortOption, persist: Boolean = true) {
        _currentArtistSortOption.value = sortOption
    }

    fun sortFolders(sortOption: SortOption, persist: Boolean = true) {
        _currentFolderSortOption.value = sortOption
    }

    private fun sortFoldersList(folders: Iterable<MusicFolder>, sortOption: SortOption): List<MusicFolder> {
        return when (sortOption) {
            SortOption.FolderNameAZ -> folders.sortedWith(
                compareBy<MusicFolder> { it.name.lowercase() }
                    .thenBy { it.path }
            )
            SortOption.FolderNameZA -> folders.sortedWith(
                compareByDescending<MusicFolder> { it.name.lowercase() }
                    .thenBy { it.path }
            )
            SortOption.FolderSongCountAsc -> folders.sortedWith(
                compareBy<MusicFolder> { it.totalSongCount }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.path }
            )
            SortOption.FolderSongCountDesc -> folders.sortedWith(
                compareByDescending<MusicFolder> { it.totalSongCount }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.path }
            )
            SortOption.FolderSubdirCountAsc -> folders.sortedWith(
                compareBy<MusicFolder> { it.totalSubFolderCount }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.path }
            )
            SortOption.FolderSubdirCountDesc -> folders.sortedWith(
                compareByDescending<MusicFolder> { it.totalSubFolderCount }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.path }
            )
            else -> folders.toList()
        }
    }

    fun sortFavoriteSongs(sortOption: SortOption, persist: Boolean = true) {
        _currentFavoriteSortOption.value = sortOption
    }

    fun updateSong(updatedSong: Song) {
        _allSongs.update { currentList ->
            currentList.map { if (it.id == updatedSong.id) updatedSong else it }.toImmutableList()
        }
    }

    fun removeSong(songId: String) {
        _allSongs.update { it.filter { s -> s.id != songId }.toImmutableList() }
    }

    fun setStorageFilter(filter: StorageFilter) {
        _currentStorageFilter.value = filter
    }

    private var isTrimmed = false

    fun trimMemory(level: Int) {
        if (
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        ) {
            if (_allSongs.value.isNotEmpty()) {
                Timber.d("LibraryStateHolder: trimming memory, clearing in-memory collections")
                _allSongs.value = persistentListOf()
                _allSongsById.value = emptyMap()
                _albums.value = persistentListOf()
                _artists.value = persistentListOf()
                _musicFolders.value = persistentListOf()
                isTrimmed = true
            }
        }
    }

    fun restoreAfterTrimIfNeeded() {
        if (isTrimmed) {
            Timber.d("LibraryStateHolder: restoring after trim")
            isTrimmed = false
            loadSongsFromRepository()
            loadAlbumsFromRepository()
            loadArtistsFromRepository()
            loadFoldersFromRepository()
        }
    }
}
