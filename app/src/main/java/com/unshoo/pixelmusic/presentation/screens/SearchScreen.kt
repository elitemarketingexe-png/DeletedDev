package com.unshoo.pixelmusic.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.unshoo.pixelmusic.data.model.Album
import com.unshoo.pixelmusic.data.model.Artist
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.model.SearchFilterType
import com.unshoo.pixelmusic.data.model.SearchHistoryItem
import com.unshoo.pixelmusic.data.model.SearchResultItem
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.preferences.SearchSource
import com.unshoo.pixelmusic.presentation.components.rememberShimmerBrush
import com.unshoo.pixelmusic.presentation.components.SearchSkeletonList
import com.unshoo.pixelmusic.presentation.components.subcomps.EnhancedSongListItem
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.navigation.navigateSafely
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import timber.log.Timber
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube

// ──────────────────────────────────────────────────────────────────────────────
// Constants (ArchiveTune spec)
// ──────────────────────────────────────────────────────────────────────────────
private val SearchGroupOuterCorner = 24.dp
private val SearchGroupInnerCorner  = 6.dp
private val SearchHorizontalPad     = 12.dp
private val SearchRowMinHeight      = 64.dp
private val SearchRowSpacing        = 2.dp
private val MoodCardHeight          = 100.dp
private val MoodCardShape           = RoundedCornerShape(24.dp)
private val MoodCoverShape          = RoundedCornerShape(16.dp)
private val MoodCoverSize           = 80.dp
private val MoodMinCellWidth        = 180.dp

// ──────────────────────────────────────────────────────────────────────────────
// Private data / enum
// ──────────────────────────────────────────────────────────────────────────────
private data class MoodAndGenresItem(
    val title: String,
    val stripeColor: Long,
    val browseId: String,
    val params: String?,
    val artworkUrl: String? = null,
)

private enum class DiscoveryTab { EXPLORE, SUGGESTIONS }

// Predefined palette for genre cards (following dynamic theming seed)
private val MoodPalette = listOf(
    0xFF6650A4L, 0xFF7D5260L, 0xFF006E2CL, 0xFF8B5000L,
    0xFF006874L, 0xFF984816L, 0xFF3D5A80L, 0xFF6B3A2AL,
    0xFF17494DL, 0xFF4A4458L, 0xFF005700L, 0xFF9C4300L,
)

// ──────────────────────────────────────────────────────────────────────────────
// Segmented corner shape helper (ArchiveTune)
// ──────────────────────────────────────────────────────────────────────────────
private fun segmentedShape(index: Int, count: Int): Shape = when {
    count <= 1   -> RoundedCornerShape(SearchGroupOuterCorner)
    index == 0   -> RoundedCornerShape(
        topStart = SearchGroupOuterCorner, topEnd = SearchGroupOuterCorner,
        bottomEnd = SearchGroupInnerCorner, bottomStart = SearchGroupInnerCorner,
    )
    index == count - 1 -> RoundedCornerShape(
        topStart = SearchGroupInnerCorner, topEnd = SearchGroupInnerCorner,
        bottomEnd = SearchGroupOuterCorner, bottomStart = SearchGroupOuterCorner,
    )
    else -> RoundedCornerShape(SearchGroupInnerCorner)
}

// ──────────────────────────────────────────────────────────────────────────────
// SearchScreen
// ──────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    onSearchBarActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic             = LocalHapticFeedback.current
    val coroutineScope     = rememberCoroutineScope()
    val focusRequester     = remember { FocusRequester() }

    // ── ViewModel state ──────────────────────────────────────────────────────
    val playerUiState  by playerViewModel.playerUiState.collectAsStateWithLifecycle()
    val searchSource   by playerViewModel.searchSource.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()

    val searchResults    = playerUiState.searchResults
    val searchHistory    = playerUiState.searchHistory
    val isSearching      = playerUiState.isSearching
    val currentFilter    = playerUiState.selectedSearchFilter

    val currentSong      = stablePlayerState.currentSong
    val isPlaying        = stablePlayerState.isPlaying

    // ── Local UI state ────────────────────────────────────────────────────────
    var queryTfv         by remember { mutableStateOf(TextFieldValue(playerViewModel.searchQuery)) }
    var suggestions      by remember { mutableStateOf<List<String>>(emptyList()) }
    var moodGenres       by remember { mutableStateOf<List<MoodAndGenresItem>>(emptyList()) }
    var selectedTab      by rememberSaveable { mutableIntStateOf(0) } // 0=Explore, 1=Suggestions
    val lazyListState    = rememberLazyListState()

    val query = queryTfv.text.trim()

    // ── Keyboard hide on scroll (ArchiveTune) ────────────────────────────────
    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect { keyboardController?.hide() }
    }

    // ── Notify AppNavigation of Search Bar active status ─────────────────────
    LaunchedEffect(query) {
        onSearchBarActiveChange(query.isNotEmpty())
    }

    // ── Auto-focus on screen entry ────────────────────────────────────────────
    LaunchedEffect(Unit) {
        playerViewModel.loadSearchHistory()
        delay(120L)
        runCatching { focusRequester.requestFocus() }
        keyboardController?.show()
    }

    // ── Re-focus on double-tap nav icon ──────────────────────────────────────
    LaunchedEffect(playerViewModel) {
        playerViewModel.searchNavDoubleTapEvents.collect {
            delay(40L)
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
        }
    }

    // ── Load YouTube suggestions while typing (ONLINE, 150 ms debounce) ──────
    LaunchedEffect(query, searchSource) {
        if (query.isBlank() || searchSource == SearchSource.LOCAL) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(150L)
        withContext(Dispatchers.IO) {
            try {
                val result = YouTube.searchSuggestions(query)
                suggestions = result.getOrNull()?.queries ?: emptyList()
            } catch (e: Exception) {
                Timber.w(e, "Suggestions fetch failed")
            }
        }
    }

    // ── Load Mood & Genres (ONLINE) — uses ExplorePage.chips ─────────────────
    LaunchedEffect(searchSource) {
        if (searchSource != SearchSource.ONLINE) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            try {
                YouTube.explore().getOrNull()?.let { page ->
                    val chipItems = page.chips?.mapIndexed { idx, chip ->
                        val browseId = chip.endpoint?.browseId ?: return@mapIndexed null
                        val color = MoodPalette.getOrElse(idx % MoodPalette.size) { 0xFF6650A4L }
                        MoodAndGenresItem(
                            title     = chip.title,
                            stripeColor = color,
                            browseId  = browseId,
                            params    = chip.endpoint.params,
                        )
                    }?.filterNotNull().orEmpty()

                    val items = if (chipItems.isNotEmpty()) {
                        chipItems
                    } else {
                        page.sections.mapIndexed { idx, section ->
                            val browseId = section.endpoint?.browseId ?: return@mapIndexed null
                            if (section.title.isBlank()) return@mapIndexed null
                            val color = MoodPalette.getOrElse(idx % MoodPalette.size) { 0xFF6650A4L }
                            MoodAndGenresItem(
                                title     = section.title,
                                stripeColor = color,
                                browseId  = browseId,
                                params    = section.endpoint?.params,
                                artworkUrl = section.thumbnail,
                            )
                        }.filterNotNull()
                    }
                    moodGenres = items
                }
            } catch (e: Exception) {
                Timber.w(e, "Mood & Genres fetch failed")
            }
        }
    }

    // Calculate filtered history outside of LazyColumn DSL to avoid @Composable constraint violations
    val filteredHistory = remember(query, searchHistory) {
        searchHistory.filter { it.query.startsWith(query, ignoreCase = true) }.take(3)
    }

    // ── Root container — tap-outside dismisses keyboard ───────────────────────
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { keyboardController?.hide() })
            },
    ) {
        // Gradient header background
        val gradientStart  = MaterialTheme.colorScheme.primaryContainer
        val gradientMiddle = MaterialTheme.colorScheme.secondaryContainer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
                .align(Alignment.TopCenter)
                .drawWithCache {
                    val brush = Brush.verticalGradient(
                        0f to gradientStart.copy(alpha = 0.28f),
                        0.45f to gradientMiddle.copy(alpha = 0.12f),
                        1f to Color.Transparent,
                    )
                    onDrawBehind { drawRect(brush) }
                },
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(SearchRowSpacing),
        ) {

            // ── Source Toggle (ABOVE search bar) ─────────────────────────────
            item(key = "source_toggle", contentType = "source_toggle") {
                SearchSourceToggle(
                    searchSource = searchSource,
                    onToggle = { playerViewModel.toggleSearchSource() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .animateItem(),
                )
            }

            // ── Search bar ────────────────────────────────────────────────────
            item(key = "search_bar", contentType = "search_bar") {
                SearchInputBar(
                    value          = queryTfv,
                    onValueChange  = { new ->
                        queryTfv = new
                        playerViewModel.updateSearchQuery(new.text)
                        playerViewModel.performSearch(new.text)
                    },
                    onSearch       = { q ->
                        keyboardController?.hide()
                        playerViewModel.onSearchQuerySubmitted(q)
                        playerViewModel.performSearch(q)
                    },
                    onClear        = {
                        queryTfv = TextFieldValue("")
                        playerViewModel.updateSearchQuery("")
                        playerViewModel.performSearch("")
                    },
                    onBack         = { navController.navigateUp() },
                    focusRequester = focusRequester,
                    searchSource   = searchSource,
                    modifier       = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .animateItem(),
                )
            }

            // ─────────────────────────────────────────────────────────────────
            // BLANK QUERY → History / Explore / Suggestions
            // ─────────────────────────────────────────────────────────────────
            if (query.isBlank()) {

                // History section
                if (searchHistory.isNotEmpty()) {
                    item(key = "history_header", contentType = "section_header") {
                        SearchSectionHeader(
                            title = "Recent Searches",
                            trailing = {
                                TextButton(onClick = { playerViewModel.clearSearchHistory() }) {
                                    Text(
                                        "Clear all",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }

                    itemsIndexed(
                        items = searchHistory,
                        key   = { _, h -> "hist_${h.query}" },
                        contentType = { _, _ -> "history_item" },
                    ) { idx, histItem ->
                        SuggestionItem(
                            query           = histItem.query,
                            isOnlineSuggestion = false,
                            onClick         = {
                                queryTfv = TextFieldValue(histItem.query)
                                playerViewModel.updateSearchQuery(histItem.query)
                                playerViewModel.onSearchQuerySubmitted(histItem.query)
                                playerViewModel.performSearch(histItem.query)
                                keyboardController?.hide()
                            },
                            onDelete        = { playerViewModel.deleteSearchHistoryItem(histItem.query) },
                            onFillTextField = {
                                queryTfv = TextFieldValue(histItem.query, androidx.compose.ui.text.TextRange(histItem.query.length))
                                playerViewModel.updateSearchQuery(histItem.query)
                            },
                            shape           = segmentedShape(idx, searchHistory.size),
                            modifier        = Modifier.animateItem(),
                        )
                    }
                }

                // Discovery tabs (ONLINE mode only)
                if (searchSource == SearchSource.ONLINE) {
                    item(key = "discovery_tabs", contentType = "tabs") {
                        PrimaryTabRow(
                            selectedTabIndex  = selectedTab,
                            containerColor    = Color.Transparent,
                            modifier          = Modifier
                                .fillMaxWidth()
                                .padding(top = if (searchHistory.isEmpty()) 0.dp else 8.dp)
                                .animateItem(),
                        ) {
                            DiscoveryTab.entries.forEachIndexed { i, tab ->
                                Tab(
                                    selected = selectedTab == i,
                                    onClick  = { selectedTab = i },
                                    text     = {
                                        Text(
                                            text = when (tab) {
                                                DiscoveryTab.EXPLORE     -> "Explore"
                                                DiscoveryTab.SUGGESTIONS -> "Suggestions"
                                            },
                                            maxLines = 1,
                                        )
                                    },
                                )
                            }
                        }
                    }

                    if (selectedTab == DiscoveryTab.EXPLORE.ordinal) {
                        // Mood & Genres grid
                        item(key = "mood_genres_title", contentType = "section_title") {
                            Text(
                                text  = "Mood & Genres",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .animateItem(),
                            )
                        }

                        item(key = "mood_genres_grid", contentType = "mood_grid") {
                            MoodAndGenresGrid(
                                items        = moodGenres,
                                onItemClick  = { item ->
                                    navController.navigateSafely(
                                        "youtube_browse/${item.browseId}?params=${item.params ?: ""}"
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                            )
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────────
            // NON-BLANK QUERY → Suggestions inline + Filter Chips + Results
            // ─────────────────────────────────────────────────────────────────
            else {

                if (filteredHistory.isNotEmpty()) {
                    item(key = "hist_inline_header", contentType = "section_header") {
                        SearchSectionHeader("History", modifier = Modifier.animateItem())
                    }
                    itemsIndexed(
                        items = filteredHistory,
                        key   = { _, h -> "hist_inline_${h.query}" },
                        contentType = { _, _ -> "history_item" },
                    ) { idx, h ->
                        SuggestionItem(
                            query              = h.query,
                            isOnlineSuggestion = false,
                            onClick            = {
                                queryTfv = TextFieldValue(h.query)
                                playerViewModel.updateSearchQuery(h.query)
                                playerViewModel.onSearchQuerySubmitted(h.query)
                                playerViewModel.performSearch(h.query)
                                keyboardController?.hide()
                            },
                            onDelete        = { playerViewModel.deleteSearchHistoryItem(h.query) },
                            onFillTextField = {
                                queryTfv = TextFieldValue(h.query, androidx.compose.ui.text.TextRange(h.query.length))
                                playerViewModel.updateSearchQuery(h.query)
                            },
                            shape    = segmentedShape(idx, filteredHistory.size),
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                if (suggestions.isNotEmpty()) {
                    item(key = "suggestions_header", contentType = "section_header") {
                        SearchSectionHeader("Suggestions", modifier = Modifier.animateItem())
                    }
                    itemsIndexed(
                        items = suggestions,
                        key   = { _, s -> "sug_$s" },
                        contentType = { _, _ -> "suggestion_item" },
                    ) { idx, sug ->
                        SuggestionItem(
                            query              = sug,
                            isOnlineSuggestion = true,
                            onClick            = {
                                queryTfv = TextFieldValue(sug)
                                playerViewModel.updateSearchQuery(sug)
                                playerViewModel.onSearchQuerySubmitted(sug)
                                playerViewModel.performSearch(sug)
                                keyboardController?.hide()
                            },
                            onFillTextField = {
                                queryTfv = TextFieldValue(sug, androidx.compose.ui.text.TextRange(sug.length))
                                playerViewModel.updateSearchQuery(sug)
                            },
                            shape    = segmentedShape(idx, suggestions.size),
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                // Filter Chips
                item(key = "filter_chips", contentType = "filter_chips") {
                    SearchFilterChipsRow(
                        currentFilter = currentFilter,
                        searchSource  = searchSource,
                        onFilterSelect = { playerViewModel.updateSearchFilter(it) },
                        modifier      = Modifier.animateItem(),
                    )
                }

                // Results header (only when results or suggestions exist)
                if (searchResults.isNotEmpty()) {
                    item(key = "results_header", contentType = "section_header") {
                        SearchSectionHeader("Top Results", modifier = Modifier.animateItem())
                    }
                }

                // Skeleton / Results / Empty
                if (isSearching && searchResults.isEmpty()) {
                    item(key = "skeleton", contentType = "skeleton") {
                        SearchSkeleton(modifier = Modifier.animateItem())
                    }
                } else if (!isSearching && searchResults.isEmpty() && query.isNotBlank()) {
                    item(key = "empty", contentType = "empty") {
                        SearchEmptyState(query = query, modifier = Modifier.animateItem())
                    }
                } else {
                    itemsIndexed(
                        items       = searchResults,
                        key         = { _, item ->
                            when (item) {
                                is SearchResultItem.SongItem -> "song_${item.song.id}"
                                is SearchResultItem.AlbumItem -> "album_${item.album.id}"
                                is SearchResultItem.ArtistItem -> "artist_${item.artist.id}"
                                is SearchResultItem.PlaylistItem -> "playlist_${item.playlist.id}"
                            }
                        },
                        contentType = { _, item -> item::class.simpleName },
                    ) { _, item ->
                        SearchResultRow(
                            item            = item,
                            playerViewModel = playerViewModel,
                            navController   = navController,
                            modifier        = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Source Toggle — SingleChoiceSegmentedButtonRow (ABOVE search bar)
// ──────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSourceToggle(
    searchSource : SearchSource,
    onToggle     : () -> Unit,
    modifier     : Modifier = Modifier,
) {
    val options = listOf(SearchSource.LOCAL, SearchSource.ONLINE)
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { idx, src ->
            SegmentedButton(
                selected = searchSource == src,
                onClick  = { if (searchSource != src) onToggle() },
                shape    = SegmentedButtonDefaults.itemShape(idx, options.size),
                icon     = {
                    when (src) {
                        SearchSource.LOCAL  -> Icon(Icons.Rounded.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        SearchSource.ONLINE -> YouTubeSegmentIcon()
                    }
                },
                label    = {
                    Text(
                        text = when (src) {
                            SearchSource.LOCAL  -> "Library"
                            SearchSource.ONLINE -> "YouTube"
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        }
    }
}

/** YouTube icon with dynamic-themed red background + white play triangle */
@Composable
private fun YouTubeSegmentIcon() {
    val bgColor = MaterialTheme.colorScheme.error
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor),
    ) {
        Icon(
            imageVector         = Icons.Rounded.PlayArrow,
            contentDescription  = null,
            tint                = Color.White,
            modifier            = Modifier.size(14.dp),
        )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Search Input Bar (replaces DockedSearchBar)
// ──────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInputBar(
    value          : TextFieldValue,
    onValueChange  : (TextFieldValue) -> Unit,
    onSearch       : (String) -> Unit,
    onClear        : () -> Unit,
    onBack         : () -> Unit,
    focusRequester : FocusRequester,
    searchSource   : SearchSource,
    modifier       : Modifier = Modifier,
) {
    val placeholder = when (searchSource) {
        SearchSource.LOCAL  -> "Search your library…"
        SearchSource.ONLINE -> "Search YouTube Music…"
    }

    Surface(
        shape  = AbsoluteSmoothCornerShape(cornerRadius = 28.dp, smoothnessAsPercent = 60),
        color  = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        modifier = modifier.height(56.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            androidx.compose.foundation.text.BasicTextField(
                value         = value,
                onValueChange = onValueChange,
                modifier      = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine    = true,
                textStyle     = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction      = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onSearch(value.text.trim()) }
                ),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.text.isEmpty()) {
                            Text(
                                text  = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            AnimatedVisibility(
                visible = value.text.isNotEmpty(),
                enter   = fadeIn() + scaleIn(),
                exit    = fadeOut(),
            ) {
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector        = Icons.Rounded.Cancel,
                        contentDescription = "Clear",
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Section Header
// ──────────────────────────────────────────────────────────────────────────────
@Composable
private fun SearchSectionHeader(
    title    : String,
    modifier : Modifier = Modifier,
    trailing : @Composable (() -> Unit)? = null,
) {
    Row(
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = SearchHorizontalPad + 4.dp, end = 4.dp, top = 16.dp, bottom = 6.dp),
    ) {
        Text(
            text  = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Suggestion / History Item  (ArchiveTune SuggestionItem)
// ──────────────────────────────────────────────────────────────────────────────
@Composable
fun SuggestionItem(
    query              : String,
    isOnlineSuggestion : Boolean,
    onClick            : () -> Unit,
    onFillTextField    : () -> Unit,
    onDelete           : () -> Unit = {},
    shape              : Shape = MaterialTheme.shapes.large,
    modifier           : Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape   = shape,
        color   = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SearchHorizontalPad),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SearchRowMinHeight)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color  = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape  = MaterialTheme.shapes.medium,
                    ),
            ) {
                Icon(
                    imageVector        = if (isOnlineSuggestion) Icons.Rounded.Search else Icons.Rounded.History,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            Text(
                text     = query,
                style    = MaterialTheme.typography.bodyLarge,
                color    = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            if (!isOnlineSuggestion) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector        = Icons.Rounded.Close,
                        contentDescription = "Remove from history",
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier           = Modifier.size(18.dp),
                    )
                }
            }

            IconButton(onClick = onFillTextField) {
                Icon(
                    imageVector        = Icons.Rounded.NorthWest,
                    contentDescription = "Fill search field",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier           = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Filter chips row
// ──────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFilterChipsRow(
    currentFilter  : SearchFilterType,
    searchSource   : SearchSource,
    onFilterSelect : (SearchFilterType) -> Unit,
    modifier       : Modifier = Modifier,
) {
    val allFilters = buildList {
        add(SearchFilterType.ALL)
        add(SearchFilterType.SONGS)
        add(SearchFilterType.ALBUMS)
        add(SearchFilterType.ARTISTS)
        add(SearchFilterType.PLAYLISTS)
        if (searchSource == SearchSource.ONLINE) add(SearchFilterType.VIDEOS)
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(allFilters.size) { idx ->
            val filter = allFilters[idx]
            val selected = currentFilter == filter
            FilterChip(
                selected = selected,
                onClick  = { onFilterSelect(filter) },
                leadingIcon = {
                    val icon = when (filter) {
                        SearchFilterType.ALL       -> Icons.Rounded.Search
                        SearchFilterType.SONGS     -> Icons.Rounded.MusicNote
                        SearchFilterType.ALBUMS    -> Icons.Rounded.Album
                        SearchFilterType.ARTISTS   -> Icons.Rounded.Person
                        SearchFilterType.PLAYLISTS -> Icons.Rounded.QueueMusic
                        SearchFilterType.VIDEOS    -> Icons.Rounded.VideoLibrary
                        else                       -> Icons.Rounded.Search
                    }
                    Icon(icon, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                },
                label = {
                    Text(
                        text = when (filter) {
                            SearchFilterType.ALL       -> "All"
                            SearchFilterType.SONGS     -> "Songs"
                            SearchFilterType.ALBUMS    -> "Albums"
                            SearchFilterType.ARTISTS   -> "Artists"
                            SearchFilterType.PLAYLISTS -> "Playlists"
                            SearchFilterType.VIDEOS    -> "Videos"
                            else                       -> filter.name
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor     = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = Color.Transparent,
                ),
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Mood & Genres Grid  (ArchiveTune MoodAndGenresButton)
// ──────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MoodAndGenresGrid(
    items       : List<MoodAndGenresItem>,
    onItemClick : (MoodAndGenresItem) -> Unit,
    modifier    : Modifier = Modifier,
) {
    if (items.isEmpty()) {
        BoxWithConstraints(modifier = modifier) {
            val columnCount = (maxWidth.value / MoodMinCellWidth.value).toInt().coerceAtLeast(2)
            val rowCount    = 3
            LazyVerticalGrid(
                columns         = GridCells.Adaptive(minSize = MoodMinCellWidth),
                contentPadding  = PaddingValues(6.dp),
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height((MoodCardHeight + 12.dp) * rowCount + 12.dp),
            ) {
                items(6) {
                    MoodCardShimmer(Modifier.padding(6.dp))
                }
            }
        }
        return
    }

    BoxWithConstraints(modifier = modifier) {
        val rowCount = ((items.size + 1) / 2).coerceAtLeast(1)
        LazyVerticalGrid(
            columns          = GridCells.Adaptive(minSize = MoodMinCellWidth),
            contentPadding   = PaddingValues(6.dp),
            userScrollEnabled = false,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height((MoodCardHeight + 12.dp) * rowCount + 12.dp),
        ) {
            items(
                items        = items,
                key          = { "${it.browseId}:${it.params}" },
                contentType  = { "mood_item" },
            ) { item ->
                MoodAndGenresCard(
                    item      = item,
                    onClick   = { onItemClick(item) },
                    modifier  = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                )
            }
        }
    }
}

@Composable
private fun MoodAndGenresCard(
    item     : MoodAndGenresItem,
    onClick  : () -> Unit,
    modifier : Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val base        = remember(item.stripeColor) { Color(item.stripeColor) }

    val cardStart  = remember(base) { lerp(base, colorScheme.primaryContainer,      0.18f) }
    val cardEnd    = remember(base) { lerp(base, colorScheme.surfaceContainerHighest, 0.34f) }
    val coverStart = remember(base) { lerp(base, colorScheme.surface,               0.28f) }
    val coverEnd   = remember(base) { lerp(base, colorScheme.scrim,                 0.20f) }

    val cardBrush = remember(cardStart, cardEnd) {
        Brush.linearGradient(listOf(cardStart, cardEnd), Offset.Zero, Offset(900f, 650f))
    }
    val coverBrush = remember(coverStart, coverEnd) {
        Brush.linearGradient(listOf(coverStart, coverEnd), Offset.Zero, Offset(360f, 360f))
    }
    val scrimBrush = remember(colorScheme.scrim) {
        Brush.horizontalGradient(listOf(
            colorScheme.scrim.copy(alpha = 0.38f),
            colorScheme.scrim.copy(alpha = 0.18f),
            Color.Transparent,
        ))
    }

    Card(
        onClick    = onClick,
        shape      = MoodCardShape,
        colors     = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation  = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier   = modifier.height(MoodCardHeight),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cardBrush),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 12.dp)
                    .size(MoodCoverSize)
                    .clip(MoodCoverShape)
                    .background(coverBrush),
            ) {
                if (!item.artworkUrl.isNullOrBlank()) {
                    AsyncImage(
                        model            = item.artworkUrl,
                        contentDescription = null,
                        contentScale     = ContentScale.Crop,
                        modifier         = Modifier.fillMaxSize(),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scrimBrush),
            )

            Text(
                text     = item.title,
                style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                color    = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, end = 92.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun MoodCardShimmer(modifier: Modifier = Modifier) {
    val shimmerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(MoodCardHeight)
            .clip(MoodCardShape)
            .background(shimmerColor),
    )
}

// ──────────────────────────────────────────────────────────────────────────────
// Search Result Row — dispatches to type-specific cards
// ──────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchResultRow(
    item            : SearchResultItem,
    playerViewModel : PlayerViewModel,
    navController   : NavHostController,
    modifier        : Modifier = Modifier,
) {
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val currentSong   = stablePlayerState.currentSong
    val isPlaying     = stablePlayerState.isPlaying

    when (item) {
        is SearchResultItem.SongItem -> {
            val song = item.song
            val isActive = currentSong?.id == song.id

            EnhancedSongListItem(
                song          = song,
                isCurrentSong = isActive,
                isPlaying     = isPlaying,
                onMoreOptionsClick = {
                    // Standard context menu logic is handled by PlayerViewModel
                },
                onClick       = {
                    if (isActive) {
                        playerViewModel.playPause()
                    } else {
                        playerViewModel.playSong(song)
                    }
                },
                modifier   = modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        is SearchResultItem.AlbumItem ->
            SearchAlbumCard(
                album       = item.album,
                navController = navController,
                modifier    = modifier,
            )

        is SearchResultItem.ArtistItem ->
            SearchArtistCard(
                artist      = item.artist,
                navController = navController,
                modifier    = modifier,
            )

        is SearchResultItem.PlaylistItem ->
            SearchPlaylistCard(
                playlist        = item.playlist,
                playerViewModel = playerViewModel,
                navController   = navController,
                modifier        = modifier,
            )
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Album Card
// ──────────────────────────────────────────────────────────────────────────────
@Composable
private fun SearchAlbumCard(
    album        : Album,
    navController: NavHostController,
    modifier     : Modifier = Modifier,
) {
    Card(
        onClick   = { navController.navigateSafely(Screen.AlbumDetail.createRoute(album.id)) },
        shape     = AbsoluteSmoothCornerShape(cornerRadius = 20.dp, smoothnessAsPercent = 60),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border    = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier  = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                AsyncImage(
                    model            = album.albumArtUriString,
                    contentDescription = album.title,
                    contentScale     = ContentScale.Crop,
                    modifier         = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = album.title,
                    style    = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color    = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (album.artist.isNotBlank()) {
                    Text(
                        text     = album.artist,
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (album.year > 0) {
                    Text(
                        text  = "Album · ${album.year}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                onClick = { navController.navigateSafely(Screen.AlbumDetail.createRoute(album.id)) },
                shape   = CircleShape,
                color   = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector        = Icons.Rounded.PlayArrow,
                    contentDescription = "Play album",
                    tint               = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier           = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Artist Card
// ──────────────────────────────────────────────────────────────────────────────
@Composable
private fun SearchArtistCard(
    artist       : Artist,
    navController: NavHostController,
    modifier     : Modifier = Modifier,
) {
    Card(
        onClick   = { navController.navigateSafely(Screen.ArtistDetail.createRoute(artist.id)) },
        shape     = AbsoluteSmoothCornerShape(cornerRadius = 20.dp, smoothnessAsPercent = 60),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border    = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier  = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                AsyncImage(
                    model            = artist.imageUrl,
                    contentDescription = artist.name,
                    contentScale     = ContentScale.Crop,
                    modifier         = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = artist.name,
                    style    = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color    = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text  = "Artist",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                onClick = { navController.navigateSafely(Screen.ArtistDetail.createRoute(artist.id)) },
                shape   = CircleShape,
                color   = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector        = Icons.Rounded.PlayArrow,
                    contentDescription = "View artist",
                    tint               = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier           = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Playlist Card
// ──────────────────────────────────────────────────────────────────────────────
@Composable
private fun SearchPlaylistCard(
    playlist        : Playlist,
    playerViewModel : PlayerViewModel,
    navController   : NavHostController,
    modifier        : Modifier = Modifier,
) {
    val isYoutube = playlist.source == "YOUTUBE"
    Card(
        onClick   = {
            if (isYoutube) {
                navController.navigateSafely("online_playlist/${playlist.id}")
            } else {
                navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
            }
        },
        shape     = AbsoluteSmoothCornerShape(cornerRadius = 20.dp, smoothnessAsPercent = 60),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border    = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        modifier  = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                AsyncImage(
                    model            = playlist.coverImageUri,
                    contentDescription = playlist.name,
                    contentScale     = ContentScale.Crop,
                    modifier         = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = playlist.name,
                    style    = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color    = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text  = if (isYoutube) "YouTube Playlist" else "Playlist · ${playlist.songIds.size} songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(8.dp))

            Surface(
                onClick = {
                    if (isYoutube) navController.navigateSafely("online_playlist/${playlist.id}")
                    else navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                },
                shape   = CircleShape,
                color   = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector        = Icons.Rounded.PlayArrow,
                    contentDescription = "Play playlist",
                    tint               = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier           = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                )
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Empty state with breathing pulse animation
// ──────────────────────────────────────────────────────────────────────────────
@Composable
private fun SearchEmptyState(query: String, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue  = 1.05f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(1200),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "pulse_scale",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector        = Icons.Rounded.SearchOff,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier           = Modifier
                    .size(72.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale },
            )
            Text(
                text  = "No results for \"$query\"",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text  = "Try a different search or switch to your library",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Skeleton loading
// ──────────────────────────────────────────────────────────────────────────────
@Composable
private fun SearchSkeleton(modifier: Modifier = Modifier) {
    val shimmerBrush = rememberShimmerBrush()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        SearchSkeletonList(modifier = Modifier)
    }
}
