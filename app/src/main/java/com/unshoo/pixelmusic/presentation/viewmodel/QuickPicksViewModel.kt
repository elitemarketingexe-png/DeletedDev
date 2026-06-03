package com.unshoo.pixelmusic.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import com.unshoo.pixelmusic.data.preferences.UserPreferencesRepository
import com.unshoo.pixelmusic.data.preferences.QuickPicks
import com.unshoo.pixelmusic.data.repository.MusicRepository
import kotlinx.coroutines.flow.first
import unshoo.ianshulyadav.pixelmusic.innertube.models.filterVideo
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage
import javax.inject.Inject

private const val PREFS_NAME = "quick_picks_cache"
private const val KEY_SONGS = "songs_json"
private const val KEY_CATEGORIES = "categories_json"
private const val KEY_CACHE_TIMESTAMP = "cache_timestamp"
// Cache is valid for 6 hours
private const val CACHE_MAX_AGE_MS = 6 * 60 * 60 * 1000L

@HiltViewModel
class QuickPicksViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicRepository: MusicRepository
) : ViewModel() {

    private val _quickPicks = MutableStateFlow<List<Song>>(emptyList())
    val quickPicks: StateFlow<List<Song>> = _quickPicks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(listOf("All"))
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    init {
        // Immediately populate from cache so the UI shows something on relaunch
        loadFromCache()
        viewModelScope.launch {
            userPreferencesRepository.discoverFlow.collect { discover ->
                if (_selectedCategory.value == "All") {
                    loadQuickPicks("All")
                }
            }
        }
    }

    fun setCategory(category: String) {
        if (_selectedCategory.value == category && !_isLoading.value) {
            return
        }
        _selectedCategory.value = category
        loadQuickPicks(category)
    }

    fun refresh() {
        loadQuickPicks(_selectedCategory.value)
    }

    // -------------------------------------------------------------------------
    // Cache helpers
    // -------------------------------------------------------------------------

    private fun loadFromCache() {
        try {
            val timestamp = prefs.getLong(KEY_CACHE_TIMESTAMP, 0L)
            // Stale cache is loaded immediately to allow instant playback, updated in bg later
            val songsJson = prefs.getString(KEY_SONGS, null) ?: return
            val categoriesJson = prefs.getString(KEY_CATEGORIES, null)

            val songsArray = JSONArray(songsJson)
            val songs = mutableListOf<Song>()
            for (i in 0 until songsArray.length()) {
                val obj = songsArray.getJSONObject(i)
                songs.add(songFromJson(obj))
            }
            if (songs.isNotEmpty()) {
                _quickPicks.value = songs
            }

            if (categoriesJson != null) {
                val catArray = JSONArray(categoriesJson)
                val cats = mutableListOf<String>()
                for (i in 0 until catArray.length()) cats.add(catArray.getString(i))
                if (cats.isNotEmpty()) _categories.value = cats
            }
        } catch (e: Exception) {
            Timber.tag("QuickPicks").w(e, "Failed to load cache")
        }
    }

    private fun saveToCache(songs: List<Song>, categories: List<String>) {
        try {
            val songsArray = JSONArray()
            songs.forEach { song -> songsArray.put(songToJson(song)) }
            val catArray = JSONArray()
            categories.forEach { catArray.put(it) }
            prefs.edit()
                .putString(KEY_SONGS, songsArray.toString())
                .putString(KEY_CATEGORIES, catArray.toString())
                .putLong(KEY_CACHE_TIMESTAMP, System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            Timber.tag("QuickPicks").w(e, "Failed to save cache")
        }
    }

    private fun songToJson(song: Song): JSONObject = JSONObject().apply {
        put("id", song.id)
        put("title", song.title)
        put("artist", song.artist)
        put("artistId", song.artistId)
        put("album", song.album)
        put("albumId", song.albumId)
        put("albumArtist", song.albumArtist ?: "")
        put("path", song.path)
        put("contentUriString", song.contentUriString)
        put("albumArtUriString", song.albumArtUriString ?: "")
        put("duration", song.duration)
        put("genre", song.genre ?: "")
        put("mimeType", song.mimeType ?: "")
        put("bitrate", song.bitrate ?: 0)
        put("sampleRate", song.sampleRate ?: 0)
        put("youtubeId", song.youtubeId ?: "")
        put("albumBrowseId", song.albumBrowseId ?: "")
    }

    private fun songFromJson(obj: JSONObject): Song = Song(
        id = obj.optString("id"),
        title = obj.optString("title"),
        artist = obj.optString("artist", ""),
        artistId = obj.optLong("artistId", 0L),
        album = obj.optString("album", ""),
        albumId = obj.optLong("albumId", 0L),
        albumArtist = obj.optString("albumArtist").takeIf { it.isNotBlank() },
        path = obj.optString("path", ""),
        contentUriString = obj.optString("contentUriString", ""),
        albumArtUriString = obj.optString("albumArtUriString").takeIf { it.isNotBlank() },
        duration = obj.optLong("duration", 0L),
        genre = obj.optString("genre").takeIf { it.isNotBlank() },
        mimeType = obj.optString("mimeType").takeIf { it.isNotBlank() },
        bitrate = obj.optInt("bitrate", 0),
        sampleRate = obj.optInt("sampleRate", 0),
        youtubeId = obj.optString("youtubeId").takeIf { it.isNotBlank() },
        albumBrowseId = obj.optString("albumBrowseId").takeIf { it.isNotBlank() }
    )

    // -------------------------------------------------------------------------
    // Network fetch
    // -------------------------------------------------------------------------

    private fun loadQuickPicks(category: String) {
        viewModelScope.launch {
            if (_quickPicks.value.isEmpty()) {
                _isLoading.value = true
            }
            try {
                if (category == "All") {
                    val discover = userPreferencesRepository.discoverFlow.first()
                    when (discover) {
                        QuickPicks.DONT_SHOW -> {
                            _quickPicks.value = emptyList()
                        }
                        QuickPicks.QUICK_PICKS -> {
                            val localPool = withContext(Dispatchers.IO) {
                                musicRepository.getQuickPicks(150).first()
                            }
                            if (localPool.isNotEmpty()) {
                                val songs = localPool.shuffled().take(20)
                                _quickPicks.value = songs
                                saveToCache(songs, _categories.value)
                            } else {
                                loadOnlineQuickPicks(category)
                            }
                        }
                        QuickPicks.LAST_LISTEN -> {
                            val lastPlayed = withContext(Dispatchers.IO) {
                                musicRepository.getLastPlayedSong()
                            }
                            val related = if (lastPlayed != null) {
                                withContext(Dispatchers.IO) {
                                    val id = lastPlayed.id.toLongOrNull()
                                    if (id != null) musicRepository.getRelatedSongs(id, 50) else emptyList()
                                }
                            } else {
                                emptyList()
                            }
                            if (related.isNotEmpty()) {
                                val songs = related.shuffled().take(20)
                                _quickPicks.value = songs
                                saveToCache(songs, _categories.value)
                            } else {
                                loadOnlineQuickPicks(category)
                            }
                        }
                    }
                } else {
                    loadOnlineQuickPicks(category)
                }
            } catch (e: Exception) {
                Timber.tag("QuickPicks").e(e, "Error loading quick picks")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadOnlineQuickPicks(category: String) {
        val songs = withContext(Dispatchers.IO) {
            fetchYoutubeSongs(category)
        }
        if (songs.isNotEmpty()) {
            val shuffledSongs = songs.shuffled()
            _quickPicks.value = shuffledSongs
            if (category == "All") {
                saveToCache(shuffledSongs, _categories.value)
            }
        }
    }

    private suspend fun fetchYoutubeSongs(category: String): List<Song> {
        val pureYtMusicOnly = userPreferencesRepository.pureYtMusicOnlyFlow.first()

        val accountSongsPool = mutableListOf<SongItem>()

        val defaultHome = YouTube.home().getOrNull() ?: return emptyList()

        // Update categories flow dynamically from home page chips
        val chipTitles = defaultHome.chips?.map { it.title } ?: emptyList()
        _categories.value = listOf("All") + chipTitles

        val targetHome = if (category != "All" && defaultHome.chips != null) {
            val matchingChip = defaultHome.chips.firstOrNull { it.title.equals(category, ignoreCase = true) }
            if (matchingChip != null && matchingChip.endpoint?.params != null) {
                YouTube.home(params = matchingChip.endpoint.params).getOrNull() ?: defaultHome
            } else {
                defaultHome
            }
        } else {
            defaultHome
        }

        // Filter sections to exclude recently played / listen again / library / history
        val initialSections = targetHome.sections.filter {
            !it.title.contains("listen again", ignoreCase = true) &&
            !it.title.contains("recently played", ignoreCase = true) &&
            !it.title.contains("history", ignoreCase = true) &&
            !it.title.contains("library", ignoreCase = true)
        }

        val preferredSection = initialSections.firstOrNull {
            it.title.contains("quick picks", ignoreCase = true) ||
            it.title.contains("quick", ignoreCase = true)
        }

        if (preferredSection != null) {
            accountSongsPool.addAll(preferredSection.items.filterIsInstance<SongItem>())
        } else {
            initialSections.forEach { section ->
                accountSongsPool.addAll(section.items.filterIsInstance<SongItem>())
            }
        }

        // Load continuation pages if we have less than 25 songs
        var continuation = targetHome.continuation
        var pages = 0
        while (continuation != null && accountSongsPool.distinctBy { it.id }.size < 25 && pages < 2) {
            val continuationHome = YouTube.home(continuation = continuation).getOrNull()
            if (continuationHome != null) {
                val nextSections = continuationHome.sections.filter {
                    !it.title.contains("listen again", ignoreCase = true) &&
                    !it.title.contains("recently played", ignoreCase = true) &&
                    !it.title.contains("history", ignoreCase = true) &&
                    !it.title.contains("library", ignoreCase = true)
                }

                nextSections.forEach { section ->
                    accountSongsPool.addAll(section.items.filterIsInstance<SongItem>())
                }
                continuation = continuationHome.continuation
                pages++
            } else {
                break
            }
        }

        val filteredSongs = accountSongsPool.filterVideo(pureYtMusicOnly)
        val uniqueSongs = filteredSongs.distinctBy { it.id }.take(25)
        return uniqueSongs.map { it.toNativeSong() }
    }
}
