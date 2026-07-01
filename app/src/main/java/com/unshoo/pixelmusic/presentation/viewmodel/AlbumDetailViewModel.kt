package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.model.Album
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.repository.MusicRepository // Importar MusicRepository
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube as InnerTubeYouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.unshoo.pixelmusic.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.database.AlbumEntity
import com.unshoo.pixelmusic.data.database.toEntity
import kotlin.math.absoluteValue

data class AlbumDetailUiState(
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isLiked: Boolean = false
)

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()
    val isAlbumLiked: StateFlow<Boolean> = _uiState.map { it.isLiked }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        val albumIdString: String? = savedStateHandle.get("albumId")
        if (albumIdString != null) {
            val albumId = albumIdString.toLongOrNull()
            val mappedBrowseId = albumId?.let { SearchStateHolder.albumIdMap[it] }
            if (mappedBrowseId != null) {
                loadOnlineAlbumData(mappedBrowseId)
            } else if (albumId != null) {
                loadAlbumData(albumId)
            } else {
                loadOnlineAlbumData(albumIdString)
            }
            observeLikedState(albumIdString)
        } else {
            _uiState.update { it.copy(error = context.getString(R.string.album_id_not_found), isLoading = false) }
        }
    }

    private fun observeLikedState(albumId: String) {
        viewModelScope.launch {
            userPreferencesRepository.likedAlbumIdsFlow.collect { likedSet ->
                val isLikedNow = likedSet.contains(albumId)
                _uiState.update { it.copy(isLiked = isLikedNow) }
            }
        }
    }

    fun toggleAlbumLikeStatus(album: Album) {
        val albumIdString: String = savedStateHandle.get("albumId") ?: return
        viewModelScope.launch {
            val isCurrentlyLiked = _uiState.value.isLiked
            val newLikedState = !isCurrentlyLiked

            // 1. Update local preferences
            userPreferencesRepository.setLikedAlbum(albumIdString, newLikedState)

            // 2. Sync with YouTube if it is a YouTube album and logged in
            if (albumIdString.toLongOrNull() == null && InnerTubeYouTube.hasLoginCookie()) {
                withContext(Dispatchers.IO) {
                    InnerTubeYouTube.likePlaylist(albumIdString, newLikedState)
                }
            }

            // 3. Update local database cache
            withContext(Dispatchers.IO) {
                if (newLikedState) {
                    val albumEntity = album.toEntity(artistIdForAlbum = album.artist.lowercase().hashCode().toLong().absoluteValue)
                    musicRepository.insertAlbums(listOf(albumEntity))
                } else {
                    musicRepository.deleteAlbumById(album.id)
                }
            }
        }
    }

    private fun loadAlbumData(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val albumDetailsFlow = musicRepository.getAlbumById(id)
                val albumSongsFlow = musicRepository.getSongsForAlbum(id)

                combine(albumDetailsFlow, albumSongsFlow) { album, songs ->
                    if (album != null) {
                        AlbumDetailUiState(
                            album = album,
                            songs = songs.sortedWith(
                                compareBy<Song> { it.discNumber ?: 1 }
                                    .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                                    .thenBy { it.title.lowercase() }
                            ),
                            isLoading = false
                        )
                    } else {
                        AlbumDetailUiState(
                            error = context.getString(R.string.album_not_found),
                            isLoading = false
                        )
                    }
                }
                    .catch { e ->
                        emit(
                            AlbumDetailUiState(
                                error = context.getString(R.string.error_loading_album, e.localizedMessage ?: ""),
                                isLoading = false
                            )
                        )
                    }
                    .collect { newState ->
                        _uiState.value = newState
                    }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = context.getString(R.string.error_loading_album, e.localizedMessage ?: ""),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun update(songs: List<Song>) {
        _uiState.update {
            it.copy(
                isLoading = false,
                songs = songs
            )
        }
    }

    private fun loadOnlineAlbumData(browseId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val result = withContext(Dispatchers.IO) {
                    InnerTubeYouTube.album(browseId)
                }
                result.onSuccess { albumPage ->
                    val albumItem = albumPage.album
                    val albumModel = Album(
                        id = albumItem.browseId.hashCode().toLong(),
                        title = albumItem.title,
                        artist = albumItem.artists?.joinToString { it.name } ?: "",
                        year = albumItem.year ?: 0,
                        dateAdded = System.currentTimeMillis(),
                        albumArtUriString = albumItem.thumbnail,
                        songCount = albumPage.songs.size,
                        albumArtist = albumItem.artists?.joinToString { it.name }
                    )
                    val songsModels = albumPage.songs.map { it.toNativeSong() }
                    
                    // Cache the online songs in local DB
                    musicRepository.insertYoutubeSongs(songsModels)

                    _uiState.value = AlbumDetailUiState(
                        album = albumModel,
                        songs = songsModels,
                        isLoading = false,
                        error = null
                    )
                }.onFailure { e ->
                    _uiState.update { it.copy(error = e.localizedMessage ?: "Failed to load online album", isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.localizedMessage, isLoading = false) }
            }
        }
    }
}
