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
import timber.log.Timber
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage
import javax.inject.Inject

@HiltViewModel
class QuickPicksViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _quickPicks = MutableStateFlow<List<Song>>(emptyList())
    val quickPicks: StateFlow<List<Song>> = _quickPicks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(listOf("All"))
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    init {
        loadQuickPicks("All")
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

    private fun loadQuickPicks(category: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _quickPicks.value = emptyList()
            try {
                val songs = withContext(Dispatchers.IO) {
                    fetchYoutubeSongs(category)
                }
                _quickPicks.value = songs
                Timber.tag("QuickPicks").d("Loaded ${songs.size} songs for category: $category")
            } catch (e: Exception) {
                Timber.tag("QuickPicks").e(e, "Error fetching quick picks for category: $category")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchYoutubeSongs(category: String): List<Song> {
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

        val accountSongsPool = mutableListOf<SongItem>()
        val quickPicksSection = targetHome.sections.firstOrNull {
            it.title.contains("quick picks", ignoreCase = true) ||
            it.title.contains("quick", ignoreCase = true)
        } ?: targetHome.sections.firstOrNull()

        if (quickPicksSection != null) {
            accountSongsPool.addAll(quickPicksSection.items.filterIsInstance<SongItem>())
        }

        // Load continuation pages if we have less than 50 songs
        var continuation = targetHome.continuation
        var pages = 0
        while (continuation != null && accountSongsPool.distinctBy { it.id }.size < 50 && pages < 3) {
            val continuationHome = YouTube.home(continuation = continuation).getOrNull()
            if (continuationHome != null) {
                val nextQuickPicks = continuationHome.sections.firstOrNull {
                    it.title.contains("quick picks", ignoreCase = true) ||
                    it.title.contains("quick", ignoreCase = true)
                } ?: continuationHome.sections.firstOrNull()
                
                if (nextQuickPicks != null) {
                    accountSongsPool.addAll(nextQuickPicks.items.filterIsInstance<SongItem>())
                }
                continuation = continuationHome.continuation
                pages++
            } else {
                break
            }
        }

        val uniqueSongs = accountSongsPool.distinctBy { it.id }
        return uniqueSongs.map { it.toNativeSong() }
    }
}

