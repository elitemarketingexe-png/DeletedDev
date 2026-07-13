package com.unshoo.pixelmusic.presentation.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.model.Album
import com.unshoo.pixelmusic.data.model.Artist
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.data.model.SearchFilterType
import com.unshoo.pixelmusic.data.model.SearchResultItem
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.preferences.SearchSource
import com.unshoo.pixelmusic.presentation.components.PlaylistCover
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.presentation.components.SmartImageListTargetSize
import com.unshoo.pixelmusic.presentation.components.rememberShimmerBrush
import com.unshoo.pixelmusic.presentation.components.subcomps.EnhancedSongListItem
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.navigation.navigateSafely
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.SearchDiscoveryViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.SearchDiscoveryScreenState
import com.unshoo.pixelmusic.presentation.viewmodel.SearchDiscoveryTab
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.utils.formatSongCount
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import timber.log.Timber
import unshoo.ianshulyadav.pixelmusic.innertube.YouTube
import unshoo.ianshulyadav.pixelmusic.innertube.models.BrowseEndpoint
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.YTItem
import java.util.concurrent.ConcurrentHashMap

private val SearchGroupOuterCorner = 24.dp
private val SearchGroupInnerCorner  = 6.dp
private val SearchHorizontalPad     = 12.dp
private val SearchRowMinHeight      = 64.dp
private val SearchRowSpacing        = 2.dp

private val MoodAndGenresButtonShape = RoundedCornerShape(24.dp)
private val MoodAndGenresCoverShape = RoundedCornerShape(16.dp)
private val MoodAndGenresCoverSize = 80.dp
private val MoodAndGenresArtworkRequestSize = 80.dp
private val MoodAndGenresButtonHeight = 100.dp
private val MoodAndGenresMinCellWidth = 180.dp

private val SuggestedSongGroupHorizontalPadding = 12.dp
private val SuggestedSongGroupVerticalPadding = 2.dp
private val SuggestedSongGroupItemSpacing = 2.dp
private val SuggestedSongGroupLargeCorner = 28.dp
private val SuggestedSongGroupSmallCorner = 6.dp

private val MoodPalette = listOf(
    0xFF6650A4L, 0xFF7D5260L, 0xFF006E2CL, 0xFF8B5000L,
    0xFF006874L, 0xFF984816L, 0xFF3D5A80L, 0xFF6B3A2AL,
    0xFF17494DL, 0xFF4A4458L, 0xFF005700L, 0xFF9C4300L,
)

private val moodAndGenresArtworkCache = ConcurrentHashMap<String, String>()

@Composable
private fun rememberMoodAndGenresArtworkUrl(endpoint: BrowseEndpoint?): String? {
    endpoint ?: return null
    val cacheKey = "${endpoint.browseId}:${endpoint.params.orEmpty()}"
    val cachedArtwork = moodAndGenresArtworkCache[cacheKey]
    val artworkUrl by produceState(initialValue = cachedArtwork, key1 = cacheKey) {
        if (!value.isNullOrBlank()) return@produceState
        val resolvedArtwork = withContext(Dispatchers.IO) {
            YouTube.browse(endpoint.browseId, endpoint.params).getOrNull()?.thumbnail
        }
        if (!resolvedArtwork.isNullOrBlank()) {
            moodAndGenresArtworkCache[cacheKey] = resolvedArtwork
            value = resolvedArtwork
        }
    }
    return artworkUrl
}

@Composable
private fun rememberMoodAndGenresArtworkModel(
    endpoint: BrowseEndpoint?,
    artworkUrl: String?,
): ImageRequest? {
    if (artworkUrl.isNullOrBlank()) return null
    val context = LocalContext.current
    val requestSizePx = with(LocalDensity.current) { MoodAndGenresArtworkRequestSize.roundToPx() }
    val cacheKey = remember(endpoint, artworkUrl) {
        endpoint?.let { "${it.browseId}:${it.params.orEmpty()}" } ?: artworkUrl
    }
    return remember(context, artworkUrl, cacheKey, requestSizePx) {
        ImageRequest.Builder(context)
            .data(artworkUrl)
            .memoryCacheKey("mood_and_genres:$cacheKey")
            .diskCacheKey("mood_and_genres:$cacheKey")
            .diskCachePolicy(CachePolicy.ENABLED)
            .size(requestSizePx)
            .build()
    }
}

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

private fun segmentedSuggestedSongShape(index: Int, count: Int): Shape {
    val large = SuggestedSongGroupLargeCorner
    val small = SuggestedSongGroupSmallCorner
    return when {
        count <= 1 -> RoundedCornerShape(large)
        index == 0 -> RoundedCornerShape(topStart = large, topEnd = large, bottomEnd = small, bottomStart = small)
        index == count - 1 -> RoundedCornerShape(topStart = small, topEnd = small, bottomEnd = large, bottomStart = large)
        else -> RoundedCornerShape(small)
    }
}

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
    val coroutineScope     = rememberCoroutineScope()
    val focusRequester     = remember { FocusRequester() }

    val playerUiState  by playerViewModel.playerUiState.collectAsStateWithLifecycle()
    val searchSource   by playerViewModel.searchSource.collectAsStateWithLifecycle()
    val playerStableState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()

    val searchResults    = playerUiState.searchResults
    val searchHistory    = playerUiState.searchHistory
    val isSearching      = playerUiState.isSearching
    val currentFilter    = playerUiState.selectedSearchFilter

    var queryTfv         by remember { mutableStateOf(TextFieldValue(playerViewModel.searchQuery)) }
    var suggestions      by remember { mutableStateOf<List<String>>(emptyList()) }
    val lazyListState    = rememberLazyListState()

    val query = queryTfv.text.trim()

    val discoveryViewModel: SearchDiscoveryViewModel = hiltViewModel()
    val discoveryState by discoveryViewModel.state.collectAsStateWithLifecycle()
    val selectedDiscoveryTab by discoveryViewModel.selectedTab.collectAsStateWithLifecycle()

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect { keyboardController?.hide() }
    }

    LaunchedEffect(query) {
        onSearchBarActiveChange(query.isNotEmpty())
    }

    LaunchedEffect(Unit) {
        playerViewModel.loadSearchHistory()
        delay(120L)
        runCatching { focusRequester.requestFocus() }
        keyboardController?.show()
    }

    LaunchedEffect(playerViewModel) {
        playerViewModel.searchNavDoubleTapEvents.collect {
            delay(40L)
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
        }
    }

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

    val filteredHistory = remember(query, searchHistory) {
        searchHistory.filter { it.query.startsWith(query, ignoreCase = true) }.take(3)
    }

    val showHistoryAndSuggestions = query.isNotEmpty() && searchResults.isEmpty() && !isSearching
    val statusBarTopInset = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { keyboardController?.hide() })
            },
    ) {
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

        Column(modifier = Modifier.fillMaxSize()) {
            // Polished Search Bar row exactly as it was positioned before 20 commits
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = statusBarTopInset + 12.dp, end = 24.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val searchBarInputFieldColors = SearchBarDefaults.inputFieldColors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )

                Box(
                    Modifier
                        .weight(1f)
                        .background(color = Color.Transparent)
                ) {
                    DockedSearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                modifier = Modifier.focusRequester(focusRequester),
                                query = queryTfv.text,
                                onQueryChange = {
                                    queryTfv = TextFieldValue(it)
                                    playerViewModel.updateSearchQuery(it)
                                    playerViewModel.performSearch(it)
                                },
                                onSearch = { q ->
                                    if (q.isNotBlank()) {
                                        playerViewModel.onSearchQuerySubmitted(q)
                                    }
                                    keyboardController?.hide()
                                },
                                expanded = false,
                                onExpandedChange = {},
                                placeholder = {
                                    val placeholderText = if (searchSource == SearchSource.LOCAL) {
                                        "Search Library..."
                                    } else {
                                        "Search YouTube Music..."
                                    }
                                    Text(
                                        placeholderText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                         IconButton(
                                             onClick = { playerViewModel.toggleSearchSource() },
                                             modifier = Modifier
                                                 .size(48.dp)
                                                 .clip(CircleShape)
                                         ) {
                                             if (searchSource == SearchSource.LOCAL) {
                                                 Icon(
                                                     painter = painterResource(id = R.drawable.rounded_library_music_24),
                                                     contentDescription = "Toggle Search Source",
                                                     tint = MaterialTheme.colorScheme.primary,
                                                     modifier = Modifier.size(24.dp)
                                                 )
                                             } else {
                                                 YouTubeSegmentIcon()
                                             }
                                         }
                                        if (queryTfv.text.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    queryTfv = TextFieldValue("")
                                                    playerViewModel.updateSearchQuery("")
                                                    playerViewModel.performSearch("")
                                                },
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                                    )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Clear",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                },
                                colors = searchBarInputFieldColors
                            )
                        },
                        expanded = false,
                        onExpandedChange = {},
                        modifier = Modifier
                            .clip(RoundedCornerShape(28.dp)),
                        colors = SearchBarDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            dividerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            inputFieldColors = searchBarInputFieldColors
                        ),
                        content = {}
                    )
                }

                FilledIconButton(
                    modifier = Modifier.padding(bottom = 2.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    onClick = { navController.navigateSafely(Screen.Settings.route) }
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_settings_24),
                        contentDescription = "Settings"
                    )
                }
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(SearchRowSpacing),
            ) {
                if (query.isBlank()) {
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

                    if (searchSource == SearchSource.ONLINE) {
                        item(key = "discovery_tabs", contentType = "tabs") {
                            PrimaryTabRow(
                                selectedTabIndex  = selectedDiscoveryTab.ordinal,
                                containerColor    = Color.Transparent,
                                modifier          = Modifier
                                    .fillMaxWidth()
                                    .padding(top = if (searchHistory.isEmpty()) 0.dp else 8.dp)
                                    .animateItem(),
                            ) {
                                SearchDiscoveryTab.entries.forEach { tab ->
                                    Tab(
                                        selected = selectedDiscoveryTab == tab,
                                        onClick  = { discoveryViewModel.selectTab(tab) },
                                        text     = {
                                            Text(
                                                text = when (tab) {
                                                    SearchDiscoveryTab.EXPLORE     -> "Explore"
                                                    SearchDiscoveryTab.SUGGESTIONS -> "Suggestions"
                                                },
                                                maxLines = 1,
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        when (val currState = discoveryState) {
                            is SearchDiscoveryScreenState.Loading -> {
                                item(key = "loading_discovery") {
                                    SearchSkeleton(modifier = Modifier.animateItem())
                                }
                            }
                            is SearchDiscoveryScreenState.Success -> {
                                val data = currState.data
                                if (selectedDiscoveryTab == SearchDiscoveryTab.EXPLORE) {
                                    item(key = "mood_explore_title", contentType = "section_header") {
                                        Text(
                                            text = "Mood & Genres",
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                                        )
                                    }
                                    item(key = "mood_genres_grid", contentType = "mood_grid") {
                                        SearchMoodAndGenresGrid(
                                            data = data,
                                            navController = navController,
                                            modifier = Modifier.fillMaxWidth().animateItem(),
                                        )
                                    }
                                } else {
                                    if (data.suggestedSongs.isNotEmpty()) {
                                        item(key = "suggested_songs_header") {
                                            Text(
                                                text = "Unique Songs",
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                                            )
                                        }
                                        item(key = "suggested_songs_list") {
                                            SuggestedSongsSection(
                                                songs = data.suggestedSongs,
                                                navController = navController,
                                                playerViewModel = playerViewModel,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }

                                    if (data.suggestedArtists.isNotEmpty()) {
                                        item(key = "suggested_artists_header") {
                                            Text(
                                                text = "Unique Artists",
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                                            )
                                        }
                                        item(key = "suggested_artists_list") {
                                            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                                                items(data.suggestedArtists) { artist ->
                                                    Box(modifier = Modifier.padding(4.dp)) {
                                                        ArtistCardItem(
                                                            artist = artist,
                                                            onClick = {
                                                                navController.navigateSafely(Screen.ArtistDetail.createRoute(artist.id))
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (data.trendingAlbums.isNotEmpty()) {
                                        item(key = "trending_albums_header") {
                                            Text(
                                                text = "Top Albums",
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                                            )
                                        }
                                        item(key = "trending_albums_list") {
                                            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                                                items(data.trendingAlbums) { album ->
                                                    Box(modifier = Modifier.padding(4.dp)) {
                                                        AlbumCarouselItem(
                                                            album = album,
                                                            onClick = {
                                                                navController.navigateSafely(Screen.AlbumDetail.createRoute(album.browseId))
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                item(key = "empty_discovery") {
                                    SearchEmptyState(query = "", modifier = Modifier.animateItem())
                                }
                            }
                        }
                    }
                } else {
                    if (showHistoryAndSuggestions) {
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
                    }

                    if (!showHistoryAndSuggestions) {
                        item(key = "filter_chips", contentType = "filter_chips") {
                            SearchFilterChipsRow(
                                currentFilter = currentFilter,
                                searchSource  = searchSource,
                                onFilterSelect = { playerViewModel.updateSearchFilter(it) },
                                modifier      = Modifier.animateItem(),
                            )
                        }

                        if (searchResults.isNotEmpty()) {
                            item(key = "results_header", contentType = "section_header") {
                                SearchSectionHeader("Top Results", modifier = Modifier.animateItem())
                            }
                        }

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
    }
}

@Composable
private fun SearchSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        trailing?.invoke()
    }
}

@Composable
fun SuggestionItem(
    query: String,
    isOnlineSuggestion: Boolean,
    onClick: () -> Unit,
    onFillTextField: () -> Unit,
    onDelete: () -> Unit = {},
    shape: Shape = MaterialTheme.shapes.large,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = if (isOnlineSuggestion) Icons.Rounded.Search else Icons.Rounded.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = query,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!isOnlineSuggestion) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onFillTextField) {
                Icon(
                    imageVector = Icons.Rounded.NorthWest,
                    contentDescription = "Fill",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

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

@Composable
private fun SearchMoodAndGenresGrid(
    data: com.unshoo.pixelmusic.search.SearchDiscoveryUiModel,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val columnCount = (maxWidth.value / MoodAndGenresMinCellWidth.value).toInt().coerceAtLeast(1)
        val rowCount = ((data.moodAndGenres.size + columnCount - 1) / columnCount).coerceAtLeast(1)

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = MoodAndGenresMinCellWidth),
            contentPadding = PaddingValues(6.dp),
            userScrollEnabled = false,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height((MoodAndGenresButtonHeight + 12.dp) * rowCount + 12.dp),
        ) {
            items(
                items = data.moodAndGenres,
                key = { item -> "${item.title}:${item.endpoint.browseId}:${item.endpoint.params}" },
                contentType = { "mood_genres_item" },
            ) { item ->
                val color = MoodPalette.getOrElse(data.moodAndGenres.indexOf(item) % MoodPalette.size) { 0xFF6650A4L }
                MoodAndGenresButton(
                    title = item.title,
                    stripeColor = color,
                    endpoint = item.endpoint,
                    onClick = {
                        val browseId = item.endpoint.browseId
                        val params = item.endpoint.params.orEmpty()
                        navController.navigateSafely("youtube_browse/$browseId?params=$params")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                )
            }
        }
    }
}

@Composable
fun MoodAndGenresButton(
    title: String,
    stripeColor: Long,
    endpoint: BrowseEndpoint? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val base = remember(stripeColor) { Color(stripeColor) }
    val artworkUrl = rememberMoodAndGenresArtworkUrl(endpoint)
    val artworkModel = rememberMoodAndGenresArtworkModel(endpoint = endpoint, artworkUrl = artworkUrl)

    val cardStart = remember(base, colorScheme.primaryContainer) {
        lerp(base, colorScheme.primaryContainer, 0.18f)
    }
    val cardEnd = remember(base, colorScheme.surfaceContainerHighest) {
        lerp(base, colorScheme.surfaceContainerHighest, 0.34f)
    }
    val coverStart = remember(base, colorScheme.surface) {
        lerp(base, colorScheme.surface, 0.28f)
    }
    val coverEnd = remember(base, colorScheme.scrim) {
        lerp(base, colorScheme.scrim, 0.2f)
    }

    val cardBrush = remember(cardStart, cardEnd) {
        Brush.linearGradient(colors = listOf(cardStart, cardEnd), start = Offset.Zero, end = Offset(900f, 650f))
    }
    val coverBrush = remember(coverStart, coverEnd) {
        Brush.linearGradient(colors = listOf(coverStart, coverEnd), start = Offset.Zero, end = Offset(360f, 360f))
    }
    val textScrimBrush = remember(colorScheme.scrim) {
        Brush.horizontalGradient(
            colors = listOf(colorScheme.scrim.copy(alpha = 0.38f), colorScheme.scrim.copy(alpha = 0.18f), Color.Transparent)
        )
    }

    Card(
        onClick = onClick,
        shape = MoodAndGenresButtonShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.height(MoodAndGenresButtonHeight),
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
                    .size(MoodAndGenresCoverSize)
                    .clip(MoodAndGenresCoverShape)
                    .background(coverBrush),
            ) {
                if (artworkModel != null) {
                    AsyncImage(
                        model = artworkModel,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(textScrimBrush),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                color = Color.White,
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
private fun SuggestedSongsSection(
    songs: List<SongItem>,
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    if (songs.isEmpty()) return
    val visibleSongs = remember(songs) { songs.take(6) }

    Column(
        verticalArrangement = Arrangement.spacedBy(SuggestedSongGroupItemSpacing),
        modifier = modifier.padding(
            horizontal = SuggestedSongGroupHorizontalPadding,
            vertical = SuggestedSongGroupVerticalPadding,
        ),
    ) {
        visibleSongs.forEachIndexed { index, song ->
            val songNative = song.toNativeSong()
            Card(
                shape = segmentedSuggestedSongShape(index = index, count = visibleSongs.size),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                onClick = {
                    playerViewModel.showAndPlaySong(
                        song = songNative,
                        contextSongs = visibleSongs.map { it.toNativeSong() },
                        queueName = "Suggestions"
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(32.dp)
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artists.joinToString { it.name },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = {
                            // Standard popup menu or action triggers
                        }
                    ) {
                        Icon(
                            painter = painterResource(com.unshoo.pixelmusic.R.drawable.rounded_more_vert_24),
                            contentDescription = "More"
                        )
                    }
                }
            }
        }
    }
}

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
                onMoreOptionsClick = {},
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

        is SearchResultItem.AlbumItem -> {
            SearchResultAlbumItem(
                album = item.album,
                onOpenClick = { navController.navigateSafely(Screen.AlbumDetail.createRoute(item.album.id)) },
                onPlayClick = { playerViewModel.playAlbum(item.album) }
            )
        }

        is SearchResultItem.ArtistItem -> {
            SearchResultArtistItem(
                artist = item.artist,
                onOpenClick = { navController.navigateSafely(Screen.ArtistDetail.createRoute(item.artist.id)) },
                onPlayClick = { playerViewModel.playArtist(item.artist) }
            )
        }

        is SearchResultItem.PlaylistItem -> {
            val playlistSongs by remember(item.playlist.songIds, playerViewModel) {
                playerViewModel.observeSongs(item.playlist.songIds)
            }.collectAsStateWithLifecycle(initialValue = emptyList())

            val coroutineScope = rememberCoroutineScope()
            SearchResultPlaylistItem(
                playlist = item.playlist,
                playlistSongs = playlistSongs,
                onOpenClick = { navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.playlist.id)) },
                onPlayClick = {
                    val playlistId = item.playlist.id
                    coroutineScope.launch {
                        if (playlistId.startsWith("PL") || playlistId.startsWith("VL") || playlistId.toLongOrNull() == null) {
                            val ytPlaylistResult = withContext(Dispatchers.IO) {
                                YouTube.playlist(playlistId)
                            }
                            if (ytPlaylistResult.isSuccess) {
                                val ytPlaylistPage = ytPlaylistResult.getOrThrow()
                                val firstPageSongs = ytPlaylistPage.songs.map { it.toNativeSong() }
                                if (firstPageSongs.isNotEmpty()) {
                                    playerViewModel.insertYoutubeSongs(firstPageSongs)
                                    playerViewModel.playSongs(firstPageSongs, firstPageSongs.first(), item.playlist.name)
                                }
                            }
                        } else {
                            if (playlistSongs.isNotEmpty()) {
                                playerViewModel.playSongs(playlistSongs, playlistSongs.first(), item.playlist.name)
                            }
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultAlbumItem(
    album: Album,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmartImage(
                model = album.albumArtUriString,
                contentDescription = "Album Art: ${album.title}",
                targetSize = SmartImageListTargetSize,
                modifier = Modifier
                    .size(56.dp)
                    .clip(itemShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = album.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.cd_play_album), modifier = Modifier.size(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultArtistItem(
    artist: Artist,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!artist.effectiveImageUrl.isNullOrBlank()) {
                SmartImage(
                    model = artist.effectiveImageUrl,
                    contentDescription = "Artist: ${artist.name}",
                    targetSize = SmartImageListTargetSize,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    painter = painterResource(id = com.unshoo.pixelmusic.R.drawable.rounded_artist_24),
                    contentDescription = "Artist",
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape)
                        .padding(12.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatSongCount(artist.songCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Artist", modifier = Modifier.size(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultPlaylistItem(
    playlist: Playlist,
    playlistSongs: List<Song>,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaylistCover(
                playlist = playlist,
                playlistSongs = playlistSongs,
                size = 56.dp
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatSongCount(playlist.songIds.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Playlist", modifier = Modifier.size(24.dp))
            }
        }
    }
}

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
                text  = if (query.isEmpty()) "No content available" else "No results for \"$query\"",
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

@Composable
private fun SearchSkeleton(modifier: Modifier = Modifier) {
    val shimmerBrush = rememberShimmerBrush()
    val itemShape = remember { RoundedCornerShape(24.dp) }
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        repeat(8) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(itemShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(shimmerBrush)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )
                }
            }
        }
    }
}
