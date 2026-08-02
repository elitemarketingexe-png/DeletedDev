@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)
package com.unshoo.pixelmusic.presentation.screens

import androidx.compose.material3.IconButton
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.graphics.Shape
import androidx.compose.animation.core.spring
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import java.util.Calendar
import androidx.compose.runtime.snapshotFlow

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.unshoo.pixelmusic.presentation.components.QuickPicksSection
import com.unshoo.pixelmusic.presentation.viewmodel.QuickPicksViewModel
import com.unshoo.pixelmusic.data.ads.AdManager
import com.unshoo.pixelmusic.presentation.components.AdSupportCard
import com.unshoo.pixelmusic.R
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.presentation.utils.rememberDominantCardColor
import com.unshoo.pixelmusic.presentation.components.subcomps.EnhancedSongListItem
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.navigation.navigateSafely
import com.unshoo.pixelmusic.presentation.navigation.navigateSafelyReplacing
import com.unshoo.pixelmusic.presentation.viewmodel.ExploreUiState
import com.unshoo.pixelmusic.presentation.viewmodel.ExploreViewModel
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.unshoo.pixelmusic.data.model.Playlist
import com.unshoo.pixelmusic.presentation.components.PlaylistCover
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.rounded.AutoAwesome
import unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.ArtistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.PlaylistItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.models.YTItem
import unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.compositeOver
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import unshoo.ianshulyadav.pixelmusic.innertube.pages.ChartsPage
import androidx.compose.runtime.snapshotFlow

private val ExpressiveSmallShape = AbsoluteSmoothCornerShape(16.dp, 70)
private val ExpressiveMediumShape = AbsoluteSmoothCornerShape(20.dp, 70)
private val ExpressiveLargeShape = AbsoluteSmoothCornerShape(26.dp, 70)

private fun isBentoSection(title: String, itemSize: Int): Boolean {
    val t = title.lowercase()
    return itemSize >= 4 && (
        t.contains("featured") ||
        t.contains("supermix") ||
        t.contains("curated") ||
        t.contains("mixed for you") ||
        t.contains("new releases") ||
        t.contains("forgotten favorites") ||
        t.contains("forgotten")
    )
}

private data class ExploreFilterSlice(
    val selectedFilter: String = "All",
    val activeMoodChip: unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage.Chip? = null
)

private data class ExploreContentSlice(
    val homePageSections: List<HomePage.Section> = emptyList(),
    val explorePageSections: List<HomePage.Section> = emptyList(),
    val newReleaseAlbums: List<AlbumItem> = emptyList(),
    val chartsPage: ChartsPage? = null,
    val recentMixes: List<Playlist> = emptyList(),
    val libraryPlaylists: List<Playlist> = emptyList()
)

@UnstableApi
@Composable
fun ExploreScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    paddingValuesParent: PaddingValues,
    exploreViewModel: ExploreViewModel = hiltViewModel(),
    quickPicksViewModel: QuickPicksViewModel = hiltViewModel()
) {
    val filterSlice by remember(exploreViewModel) {
        exploreViewModel.uiState
            .map { ExploreFilterSlice(it.selectedFilter, it.activeMoodChip) }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = ExploreFilterSlice())

    val contentSlice by remember(exploreViewModel) {
        exploreViewModel.uiState
            .map { 
                ExploreContentSlice(
                    it.homePageSections,
                    it.explorePageSections,
                    it.newReleaseAlbums,
                    it.chartsPage,
                    it.recentMixes,
                    it.libraryPlaylists
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = ExploreContentSlice())

    val moodChips by remember(exploreViewModel) {
        exploreViewModel.uiState.map { it.moodChips }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val isLoading by remember(exploreViewModel) {
        exploreViewModel.uiState.map { it.isLoading }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = true)

    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var isManualRefreshing by remember { mutableStateOf(false) }
    val isRefreshingViewModel by remember(exploreViewModel) {
        exploreViewModel.uiState.map { it.isRefreshing }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val isRefreshing = isRefreshingViewModel || isManualRefreshing

    val error by remember(exploreViewModel) {
        exploreViewModel.uiState.map { it.error }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)

    val homePageContinuation by remember(exploreViewModel) {
        exploreViewModel.uiState.map { it.homePageContinuation }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)

    val isContinuationLoading by remember(exploreViewModel) {
        exploreViewModel.uiState.map { it.isContinuationLoading }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    val localSongs by remember(exploreViewModel) {
        exploreViewModel.uiState.map { it.localSongs }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = emptyMap())

    val quickPicks by quickPicksViewModel.quickPicks.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val isPlaying by remember { derivedStateOf { stablePlayerState.isPlaying } }
    val currentSongId by remember { derivedStateOf { stablePlayerState.currentSong?.id } }
    val quickPicksDisplayMode by playerViewModel.quickPicksDisplayMode.collectAsStateWithLifecycle()
    val pullRefreshState = rememberPullToRefreshState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val listState = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val scrollThresholdPx = remember(density) { with(density) { 16.dp.toPx() } }
    val isScrolled = remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > scrollThresholdPx }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    
    val backgroundBrush = remember(surfaceColor, primaryColor, tertiaryColor) {
        Brush.verticalGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.18f),
                tertiaryColor.copy(alpha = 0.08f),
                surfaceColor.copy(alpha = 0.85f),
                surfaceColor
            ),
            endY = 1400f
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            ExploreTopBar(
                onSettingsClick = {
                    navController.navigateSafely(Screen.Settings.route)
                },
                onCreateClick = {
                    navController.navigateSafely(Screen.SmartMix.route)
                },
                isScrolled = isScrolled.value
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isManualRefreshing = true
                    exploreViewModel.loadData(forceRefresh = true)
                    quickPicksViewModel.refresh(force = true)
                    kotlinx.coroutines.delay(1000)
                    isManualRefreshing = false
                }
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = innerPadding.calculateTopPadding() + 8.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundBrush)
            ) {
                if (isLoading && contentSlice.homePageSections.isEmpty() && contentSlice.newReleaseAlbums.isEmpty() && contentSlice.chartsPage == null) {
                    com.unshoo.pixelmusic.presentation.components.ExploreSkeletonGrid(
                        paddingValues = PaddingValues(
                            top = innerPadding.calculateTopPadding(),
                            bottom = paddingValuesParent.calculateBottomPadding() + 24.dp + (if (currentSongId != null) com.unshoo.pixelmusic.presentation.components.MiniPlayerHeight else 0.dp)
                        )
                    )
                } else if (error != null && contentSlice.homePageSections.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = error!!,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Button(
                            onClick = { exploreViewModel.loadData() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Retry")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                } else {
                    val homeSectionsRaw = if (filterSlice.selectedFilter == "All") {
                        if (filterSlice.activeMoodChip != null) {
                            contentSlice.explorePageSections
                        } else {
                            contentSlice.homePageSections.ifEmpty { contentSlice.explorePageSections }
                        }
                    } else {
                        contentSlice.homePageSections
                    }
                    val (cardShelfSections, fromYourLibraryAlbums, remainingSections) = remember(homeSectionsRaw) {
                        val cardShelf = mutableListOf<HomePage.Section>()
                        var libraryAlbums = emptyList<AlbumItem>()
                        val remaining = mutableListOf<HomePage.Section>()

                        for (section in homeSectionsRaw) {
                            val title = section.title.lowercase()
                            if (title.contains("from your library")) {
                                libraryAlbums = section.items.filterIsInstance<AlbumItem>()
                                continue
                            }
                            val isBento = isBentoSection(section.title, section.items.size)
                            val isLocalDuplicate = title.contains("local")
                            val isSug = !isBento && !isLocalDuplicate &&
                                        (title.contains("mix") || title.contains("listen again") || 
                                        title.contains("favorites") || title.contains("suggest") || 
                                        title.contains("recommend") || title.contains("radio") || 
                                        title.contains("played") ||
                                        title.contains("liked") || title.contains("cached") ||
                                        title.contains("you might like") || title.contains("recently") ||
                                        title.contains("most"))
                            val hasSongs = section.items.any { it is SongItem }
                            if (isSug && hasSongs) {
                                cardShelf.add(section)
                            } else {
                                remaining.add(section)
                            }
                        }
                        Triple(cardShelf, libraryAlbums, remaining)
                    }
                    val bottomPadding = if (currentSongId != null) MiniPlayerHeight else 0.dp
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding(),
                            bottom = paddingValuesParent.calculateBottomPadding() + 24.dp + bottomPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 2. Category Filter Chips (All, Smart Mix, For You, Charts, Recap)
                        item(key = "explore_category_filters", contentType = "CategoryFilters") {
                            var showMoodRow by remember { mutableStateOf(false) }
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val categories = listOf("All", "Smart Mix", "For You", "New Releases", "Charts", "Recap")
                                        categories.forEach { category ->
                                            FilterChip(
                                                selected = filterSlice.selectedFilter == category,
                                                onClick = { exploreViewModel.setSelectedFilter(category) },
                                                label = { Text(category) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    labelColor = MaterialTheme.colorScheme.onSurface
                                                ),
                                                shape = RoundedCornerShape(16.dp),
                                                border = null
                                            )
                                        }
                                    }
                                    
                                    // Mood chip dropdown arrow
                                    if ((filterSlice.selectedFilter == "All" || filterSlice.selectedFilter == "For You") && moodChips.isNotEmpty()) {
                                        androidx.compose.material3.IconButton(
                                            onClick = { showMoodRow = !showMoodRow },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (showMoodRow) androidx.compose.material.icons.Icons.Rounded.KeyboardArrowUp else androidx.compose.material.icons.Icons.Rounded.KeyboardArrowDown,
                                                contentDescription = "Toggle genres",
                                                tint = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }

                                // Mood / Genre Chips (When All or For You is active and arrow clicked)
                                if (showMoodRow && (filterSlice.selectedFilter == "All" || filterSlice.selectedFilter == "For You") && moodChips.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        moodChips.forEach { chip ->
                                            val isSelected = filterSlice.activeMoodChip == chip
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    val newChip = if (isSelected) null else chip
                                                    exploreViewModel.selectMoodChip(newChip)
                                                },
                                                label = { Text(chip.title) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                shape = RoundedCornerShape(16.dp),
                                                border = null
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (filterSlice.selectedFilter == "Recap") {
                            item(key = "explore_recap_view") {
                                RecapScreen(
                                    navController = navController,
                                    playerViewModel = playerViewModel
                                )
                            }
                        }

                        // 3. AI Smart Mix Studio Card (Hero CTA)
                        val showSmartMixCard = when (filterSlice.selectedFilter) {
                            "Smart Mix" -> true
                            "All" -> AdManager.hasRecentlySupported(context)
                            else -> false
                        }
                        if (showSmartMixCard) {
                            item(key = "smart_mix_studio_card") {
                                SmartMixStudioHeroCard(
                                    onClick = { navController.navigateSafely(Screen.SmartMix.route) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                )
                            }
                        }

                        if ((filterSlice.selectedFilter == "All" || filterSlice.selectedFilter == "Charts") &&
                            contentSlice.chartsPage != null && contentSlice.chartsPage!!.sections.isNotEmpty()) {
                            contentSlice.chartsPage!!.sections.forEachIndexed { index, chartSection ->
                                item(key = "chart_${chartSection.title}_${index}_header") {
                                    SectionHeader(title = chartSection.title)
                                }

                                val songItems = chartSection.items.filterIsInstance<SongItem>()
                                if (songItems.isNotEmpty()) {
                                    val songListNative = songItems.map { it.toNativeSong() }
                                    items(
                                        items = songListNative,
                                        key = { song -> "chart_${chartSection.title}_${index}_song_${song.id}" }
                                    ) { songNative ->
                                        EnhancedSongListItem(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            song = songNative,
                                            isPlaying = isPlaying && currentSongId == songNative.id,
                                            isCurrentSong = currentSongId == songNative.id,
                                            onClick = {
                                                playerViewModel.showAndPlaySong(
                                                    songNative,
                                                    songListNative,
                                                    chartSection.title
                                                )
                                            },
                                            onMoreOptionsClick = {
                                                playerViewModel.selectSongForInfo(songNative)
                                            }
                                        )
                                    }
                                } else {
                                    item(key = "chart_${chartSection.title}_${index}_list") {
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 16.dp),
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            items(items = chartSection.items, key = { item ->
                                                when (item) {
                                                    is SongItem -> "chart_song_${item.id}"
                                                    is AlbumItem -> "chart_album_${item.browseId}"
                                                    is ArtistItem -> "chart_artist_${item.id}"
                                                    is PlaylistItem -> "chart_playlist_${item.id}"
                                                    else -> "chart_item_${item.hashCode()}"
                                                }
                                            }) { item ->
                                                when (item) {
                                                    is AlbumItem -> {
                                                        AlbumCarouselItem(
                                                            album = item,
                                                            onClick = {
                                                                navController.navigateSafely(Screen.AlbumDetail.createRoute(item.browseId))
                                                            }
                                                        )
                                                    }
                                                    is ArtistItem -> {
                                                        ArtistCardItem(
                                                            artist = item,
                                                            onClick = {
                                                                navController.navigateSafely(Screen.ArtistDetail.createRoute(item.id))
                                                            }
                                                        )
                                                    }
                                                    is PlaylistItem -> {
                                                        PlaylistCardItem(
                                                            playlist = item,
                                                            onClick = {
                                                                navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.id))
                                                            }
                                                        )
                                                    }
                                                    else -> {}
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val showSupportCard = filterSlice.selectedFilter == "All" && 
                            !com.unshoo.pixelmusic.data.ads.AdManager.hasRecentlySupported(context) &&
                            !com.unshoo.pixelmusic.data.ads.AdManager.isSupportCardDismissed(context)
                        if (showSupportCard) {
                            item(key = "explore_ad_support_card") {
                                AdSupportCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                )
                            }
                        }



                        if ((filterSlice.selectedFilter == "All" || filterSlice.selectedFilter == "For You") &&
                            quickPicks.isNotEmpty()
                        ) {
                            item(key = "quick_picks_section") {
                                QuickPicksSection(
                                    songs = quickPicks,
                                    onSongClick = { song ->
                                        playerViewModel.showAndPlaySong(song, quickPicks, "Quick Picks")
                                    },
                                    onSeeAllClick = {
                                        navController.navigateSafely(Screen.QuickPicksAll.route)
                                    },
                                    currentSongId = currentSongId,
                                    displayMode = quickPicksDisplayMode,
                                    cardSize = 140.dp
                                )
                            }
                        }

                        if ((filterSlice.selectedFilter == "All" || filterSlice.selectedFilter == "Smart Mix" || filterSlice.selectedFilter == "For You") &&
                            contentSlice.recentMixes.isNotEmpty()
                        ) {
                            item(key = "recent_mixes_header") {
                                SectionHeader(title = "Recent Mixes")
                            }
                            item(key = "recent_mixes_carousel") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(items = contentSlice.recentMixes, key = { playlist -> "recent_mix_${playlist.id}" }) { playlist ->
                                        RecentMixCardItem(
                                            playlist = playlist,
                                            playerViewModel = playerViewModel,
                                            onClick = {
                                                navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        val hasLibraryContent = contentSlice.libraryPlaylists.isNotEmpty() || fromYourLibraryAlbums.isNotEmpty()
                        if ((filterSlice.selectedFilter == "All" || filterSlice.selectedFilter == "For You") && hasLibraryContent) {
                            item(key = "your_library_header") {
                                SectionHeader(
                                    title = "Your Library",
                                    onActionClick = {
                                        navController.navigateSafely(Screen.Library.route)
                                    },
                                    actionLabel = "See All"
                                )
                            }
                            item(key = "your_library_carousel") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    items(items = fromYourLibraryAlbums, key = { album -> "library_album_${album.browseId}" }) { album ->
                                        LibraryAlbumCard(
                                            album = album,
                                            onClick = {
                                                navController.navigateSafely(Screen.AlbumDetail.createRoute(album.browseId))
                                            }
                                        )
                                    }
                                    items(items = contentSlice.libraryPlaylists, key = { playlist -> "library_playlist_${playlist.id}" }) { playlist ->
                                        LibraryPlaylistCard(
                                            playlist = playlist,
                                            playerViewModel = playerViewModel,
                                            onClick = {
                                                navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // New Releases section
                        if (filterSlice.selectedFilter == "New Releases" && contentSlice.newReleaseAlbums.isNotEmpty()) {
                            item(key = "new_releases_full_header") {
                                SectionHeader(title = "All New Releases & Singles")
                            }
                            val albums = contentSlice.newReleaseAlbums
                            val chunkedAlbums = albums.chunked(2)
                            items(items = chunkedAlbums, key = { chunk -> "nr_row_${chunk.first().browseId}" }) { rowAlbums ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    rowAlbums.forEach { album ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            AlbumCarouselItem(
                                                album = album,
                                                onClick = {
                                                    navController.navigateSafely(Screen.AlbumDetail.createRoute(album.browseId))
                                                }
                                            )
                                        }
                                    }
                                    if (rowAlbums.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        if ((filterSlice.selectedFilter == "All" || filterSlice.selectedFilter == "For You") &&
                            contentSlice.newReleaseAlbums.isNotEmpty()
                        ) {
                            item(key = "new_releases_header") {
                                SectionHeader(
                                    title = "New Releases",
                                    onActionClick = {
                                        exploreViewModel.setSelectedFilter("New Releases")
                                    }
                                )
                            }
                            item(key = "new_releases_carousel") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(items = contentSlice.newReleaseAlbums, key = { album -> "new_release_${album.browseId}" }) { album ->
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

                        // Smart Mix Horizontal List for Smart Mix Filter
                        if (filterSlice.selectedFilter == "Smart Mix" && contentSlice.recentMixes.isNotEmpty()) {
                            item(key = "smart_mix_header") {
                                SectionHeader(title = "Your Smart Mixes")
                            }
                            item(key = "smart_mix_carousel") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(items = contentSlice.recentMixes, key = { playlist -> "smart_mix_${playlist.id}" }) { playlist ->
                                        RecentMixCardItem(
                                            playlist = playlist,
                                            playerViewModel = playerViewModel,
                                            onClick = {
                                                navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (filterSlice.selectedFilter == "All" || filterSlice.selectedFilter == "For You") {

                            remainingSections.forEachIndexed { index, section ->
                                val titleLower = section.title.lowercase()
                                if (titleLower.contains("local") || 
                                    titleLower.contains("trending") || 
                                    titleLower.contains("long listens")
                                ) return@forEachIndexed

                                val isShelf = (section.title.contains("recently played", ignoreCase = true) ||
                                              section.title.contains("most played", ignoreCase = true) ||
                                              section.title.contains("heavy rotation", ignoreCase = true) ||
                                              section.title.contains("liked", ignoreCase = true) ||
                                              section.title.contains("cached", ignoreCase = true) ||
                                              section.title.contains("you might like", ignoreCase = true) ||
                                              section.title.contains("for you", ignoreCase = true) ||
                                              section.title.contains("mix", ignoreCase = true) ||
                                              section.title.contains("listen again", ignoreCase = true) ||
                                              section.title.contains("favorites", ignoreCase = true) ||
                                              section.title.contains("suggest", ignoreCase = true) ||
                                              section.title.contains("recommend", ignoreCase = true) ||
                                              section.title.contains("radio", ignoreCase = true) ||
                                              section.title.contains("recently", ignoreCase = true)) &&
                                              section.items.any { it is SongItem }

                                val isSimilar = !isShelf && 
                                                (section.title.startsWith("Similar to", ignoreCase = true) || 
                                                 section.title.contains("Fans also like", ignoreCase = true) ||
                                                 section.title.contains("Similar", ignoreCase = true))

                                val isBento = !isShelf && !isSimilar && 
                                              isBentoSection(section.title, section.items.size)

                                if (isShelf && section.items.isNotEmpty()) {
                                    item(
                                        key = "shelf_${section.title}_$index",
                                        contentType = "mixed_for_you_card"
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 20.dp, vertical = 4.dp)
                                        ) {
                                            MixedForYouCard(
                                                section = section,
                                                playerViewModel = playerViewModel,
                                                navController = navController
                                            )
                                        }
                                    }
                                } else if (isBento) {
                                    item(
                                        key = "bento_${section.title}_$index",
                                        contentType = "swipeable_carousel"
                                    ) {
                                        LibrarySwipeableCarousel(section, navController, playerViewModel)
                                    }
                                } else {
                                    item(
                                        key = "home_section_${section.title}_${index}_header",
                                        contentType = "section_header"
                                    ) {
                                        val isSectionQuickPicks = section.title.contains("quick picks", ignoreCase = true)
                                        val quickPicksSongs = remember(section.items) {
                                            section.items.filterIsInstance<SongItem>().map { it.toNativeSong() }
                                        }
                                        SectionHeader(
                                            title = section.title,
                                            onActionClick = if (isSectionQuickPicks && quickPicksSongs.isNotEmpty()) {
                                                {
                                                    playerViewModel.playSongs(
                                                        quickPicksSongs,
                                                        quickPicksSongs.first(),
                                                        section.title
                                                    )
                                                }
                                            } else null,
                                            actionLabel = if (isSectionQuickPicks && quickPicksSongs.isNotEmpty()) "Play All" else null
                                        )
                                    }
                                    item(
                                        key = "home_section_${section.title}_${index}_carousel",
                                        contentType = "yt_item_carousel"
                                    ) {
                                        if (isSimilar) {
                                            SimilarArtistsCarousel(
                                                artists = section.items.filterIsInstance<ArtistItem>(),
                                                navController = navController,
                                                playerViewModel = playerViewModel
                                            )
                                        } else {
                                            YTItemCarousel(
                                                items = section.items,
                                                navController = navController,
                                                playerViewModel = playerViewModel,
                                                sectionTitle = section.title
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YTItemCarousel(
    items: List<YTItem>,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    sectionTitle: String
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(items = items, key = { it.id }) { item ->
            when (item) {
                is SongItem -> {
                    val songNative = item.toNativeSong()
                    SongCardItem(
                        song = songNative,
                        onClick = {
                            playerViewModel.showAndPlaySong(
                                song = songNative,
                                contextSongs = items.filterIsInstance<SongItem>().map { it.toNativeSong() },
                                queueName = sectionTitle
                            )
                        }
                    )
                }
                is AlbumItem -> {
                    AlbumCarouselItem(
                        album = item,
                        onClick = {
                            navController.navigateSafely(Screen.AlbumDetail.createRoute(item.browseId))
                        }
                    )
                }
                is PlaylistItem -> {
                    PlaylistCardItem(
                        playlist = item,
                        onClick = {
                            navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.id))
                        }
                    )
                }
                is ArtistItem -> {
                    ArtistCardItem(
                        artist = item,
                        onClick = {
                            navController.navigateSafely(Screen.ArtistDetail.createRoute(item.id))
                        },
                        playerViewModel = playerViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun SongCardItem(
    song: Song,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(124.dp)
            .clickable(onClick = onClick)
    ) {
        SmartImage(
            model = song.albumArtUriString,
            contentDescription = song.title,
            modifier = Modifier
                .size(124.dp)
                .clip(ExpressiveMediumShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = GoogleSansRounded
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun AnimatedSparklesIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // OPTIMIZED: Removed rememberInfiniteTransition (2 continuous animations running in top bar)
    // Previous version had scale + rotation infinite loops constantly invalidating composition.
    // Now static gradient button with simple click, zero background work.
    val colors = MaterialTheme.colorScheme
    val gradientBrush = remember(colors) {
        Brush.linearGradient(
            colors = listOf(
                colors.primary,
                colors.tertiary
            )
        )
    }

    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(gradientBrush)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = "Smart Mix",
                modifier = Modifier.size(20.dp),
                tint = colors.onPrimary
            )
        }
    }
}

@Composable
fun LibraryPlaylistCard(
    playlist: Playlist,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit
) {
    val previewSongIds = remember(playlist.songIds) {
        playlist.songIds.take(4)
    }
    var playlistSongs by remember(previewSongIds) {
        mutableStateOf<List<Song>?>(if (previewSongIds.isEmpty()) emptyList() else null)
    }
    LaunchedEffect(previewSongIds) {
        if (previewSongIds.isNotEmpty()) {
            playlistSongs = playerViewModel.getSongs(previewSongIds)
        }
    }

    val dominantColor = playlist.coverColorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.secondaryContainer
    val cardBgColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val isDarkTheme = isSystemInDarkTheme()
    val blendedBgColor = remember(dominantColor, cardBgColor, isDarkTheme) {
        val blendFraction = if (isDarkTheme) 0.18f else 0.35f
        androidx.compose.ui.graphics.lerp(cardBgColor, dominantColor, blendFraction)
    }
    
    Card(
        modifier = Modifier
            .width(260.dp)
            .height(120.dp)
            .clip(ExpressiveMediumShape)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = blendedBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                dominantColor.copy(alpha = 0.22f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(ExpressiveSmallShape)
                ) {
                    PlaylistCover(
                        playlist = playlist,
                        playlistSongs = playlistSongs ?: emptyList(),
                        modifier = Modifier.fillMaxSize(),
                        size = 96.dp
                    )
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(28.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                            .clickable {
                                playlistSongs?.let { songs ->
                                    if (songs.isNotEmpty()) {
                                        playerViewModel.playSongs(songs, songs.first(), playlist.name)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.2).sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        val countText = if (playlist.displaySongCount != null) {
                            "${playlist.displaySongCount} songs"
                        } else {
                            "${playlist.songIds.size} songs"
                        }
                        Text(
                            text = countText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }

                    val sourceLabel = if (playlist.source == "YOUTUBE") "YouTube" else "Library"
                    val badgeBg = if (playlist.source == "YOUTUBE") {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    }
                    val badgeText = if (playlist.source == "YOUTUBE") {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }

                    Box(
                        modifier = Modifier
                            .background(badgeBg, shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = badgeText
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryAlbumCard(
    album: AlbumItem,
    onClick: () -> Unit
) {
    val shape = remember { AbsoluteSmoothCornerShape(24.dp, 80) }
    val coverShape = remember { AbsoluteSmoothCornerShape(14.dp, 80) }
    val colors = MaterialTheme.colorScheme
    val isDarkTheme = isSystemInDarkTheme()

    val blendedBgColor = rememberDominantCardColor(
        imageUrl = album.thumbnail,
        baseColor = colors.surfaceContainerHigh,
        isDarkTheme = isDarkTheme,
        darkBlendFraction = 0.18f,
        lightBlendFraction = 0.35f
    )

    Card(
        modifier = Modifier
            .width(260.dp)
            .height(120.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = blendedBgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                blendedBgColor.copy(alpha = 0.3f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmartImage(
                    model = album.thumbnail,
                    contentDescription = album.title,
                    modifier = Modifier
                        .size(96.dp)
                        .clip(coverShape),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = (-0.2).sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = colors.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = album.artists?.joinToString { it.name } ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(colors.secondaryContainer.copy(alpha = 0.6f), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Album",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecentMixCardItem(
    playlist: Playlist,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit
) {
    val shape = remember { AbsoluteSmoothCornerShape(20.dp, 60) }
    val previewSongIds = remember(playlist.songIds) {
        playlist.songIds.take(4)
    }
    var playlistSongs by remember(previewSongIds) {
        mutableStateOf<List<Song>?>(if (previewSongIds.isEmpty()) emptyList() else null)
    }
    LaunchedEffect(previewSongIds) {
        if (previewSongIds.isNotEmpty()) {
            playlistSongs = playerViewModel.getSongs(previewSongIds)
        }
    }

    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        PlaylistCover(
            playlist = playlist,
            playlistSongs = playlistSongs ?: emptyList(),
            modifier = Modifier
                .size(140.dp)
                .clip(shape),
            size = 140.dp
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSansRounded
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = "Smart Mix",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun ExploreTopBar(
    onSettingsClick: () -> Unit,
    onCreateClick: () -> Unit,
    isScrolled: Boolean = false,
) {
    val baseContainerColor = MaterialTheme.colorScheme.primaryContainer
    val surfaceColor = MaterialTheme.colorScheme.surface
    val solidTintedColor = remember(baseContainerColor, surfaceColor) {
        Color(
            red = (baseContainerColor.red * 0.45f) + (surfaceColor.red * 0.55f),
            green = (baseContainerColor.green * 0.45f) + (surfaceColor.green * 0.55f),
            blue = (baseContainerColor.blue * 0.45f) + (surfaceColor.blue * 0.55f),
            alpha = 1f
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
        color = solidTintedColor,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 24.dp, top = 12.dp, end = 20.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Explore",
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 40.sp,
                letterSpacing = 1.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedSparklesIconButton(onClick = onCreateClick)

                FilledIconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_settings_24),
                        contentDescription = stringResource(R.string.settings_top_bar_title),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}


@Composable
fun SectionHeader(
    title: String,
    onActionClick: (() -> Unit)? = null,
    actionLabel: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = GoogleSansRounded
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        if (onActionClick != null && actionLabel != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onActionClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun AlbumCarouselItem(
    album: AlbumItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.size(136.dp)) {
            SmartImage(
                model = album.thumbnail,
                contentDescription = album.title,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(AbsoluteSmoothCornerShape(22.dp, 80)),
                contentScale = ContentScale.Crop
            )
            val isSingle = album.releaseType == unshoo.ianshulyadav.pixelmusic.innertube.models.AlbumReleaseType.SINGLE
            Surface(
                shape = ExpressiveSmallShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            ) {
                Text(
                    text = if (isSingle) "Single" else "Album",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSansRounded
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = album.artists?.joinToString { it.name } ?: "",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun ArtistCardItem(
    artist: ArtistItem,
    onClick: () -> Unit,
    playerViewModel: PlayerViewModel? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val artistId = artist.id
    val artistName = artist.title
    val artistThumbnail = artist.thumbnail

    val playEndpoint = remember(artist) {
        artist.shuffleEndpoint 
            ?: artist.radioEndpoint
            ?: unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint(
                playlistId = "RDAMVM$artistId",
                videoId = null
            )
    }

    val radioEndpoint = remember(artist) {
        artist.radioEndpoint
            ?: artist.shuffleEndpoint
            ?: unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint(
                playlistId = "RDAMVM$artistId",
                videoId = null
            )
    }

    val bodyLargeStyle = MaterialTheme.typography.bodyMedium
    val nameTextStyle = remember(bodyLargeStyle, colorScheme.onSurface) {
        bodyLargeStyle.copy(
            fontWeight = FontWeight.Bold,
            fontFamily = GoogleSansRounded
        )
    }

    Card(
        modifier = Modifier
            .width(136.dp)
            .height(204.dp),
        shape = ExpressiveLargeShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainerLow),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Circular artist photo avatar
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (!artistThumbnail.isNullOrBlank()) {
                    SmartImage(
                        model = artistThumbnail,
                        contentDescription = artistName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.rounded_music_note_24),
                        contentDescription = artistName,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Artist details
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = artistName,
                    style = nameTextStyle,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Artist",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Clean action buttons row (Play + Radio) matching M3 Expressive style
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        playerViewModel?.playRadio(
                            endpoint = playEndpoint,
                            title = "${artistName} Mix",
                            artistName = artistName
                        )
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .background(colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play Artist",
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = {
                        playerViewModel?.playRadio(
                            endpoint = radioEndpoint,
                            title = "${artistName} Radio",
                            artistName = artistName
                        )
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .background(colorScheme.secondaryContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Radio,
                        contentDescription = "Artist Radio",
                        tint = colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistBentoCard(
    playlist: PlaylistItem,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = isSystemInDarkTheme()
    val baseSurface = colorScheme.surfaceContainerHigh

    val animatedBgColor = rememberDominantCardColor(
        imageUrl = playlist.thumbnail,
        baseColor = baseSurface,
        isDarkTheme = isDarkTheme,
        darkBlendFraction = 0.28f,
        lightBlendFraction = 0.45f
    )

    val playlistTitle = playlist.title
    val authorName = playlist.author?.name ?: "YouTube Music"
    val songCountText = playlist.songCountText ?: ""

    // Extract endpoint for play & radio action buttons
    val playEndpoint = remember(playlist) {
        playlist.playEndpoint 
            ?: playlist.shuffleEndpoint 
            ?: unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint(
                playlistId = playlist.id,
                videoId = null
            )
    }

    val radioEndpoint = remember(playlist) {
        playlist.radioEndpoint 
            ?: playlist.shuffleEndpoint 
            ?: unshoo.ianshulyadav.pixelmusic.innertube.models.WatchEndpoint(
                playlistId = "RDAMPL${playlist.id}",
                videoId = null
            )
    }

    Card(
        modifier = modifier
            .width(280.dp)
            .wrapContentHeight(),
        shape = AbsoluteSmoothCornerShape(28.dp, 80),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = animatedBgColor),
        onClick = {
            navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Large Cover Thumbnail + Title, Author, Song Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(AbsoluteSmoothCornerShape(20.dp, 80))
                        .background(colorScheme.surfaceContainerHighest)
                ) {
                    if (!playlist.thumbnail.isNullOrBlank()) {
                        SmartImage(
                            model = playlist.thumbnail,
                            contentDescription = playlistTitle,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = playlistTitle,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = GoogleSansRounded,
                            letterSpacing = (-0.3).sp
                        ),
                        color = colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = authorName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (songCountText.isNotBlank()) {
                        Text(
                            text = songCountText,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Bottom Circular Action Buttons (Play, Radio/Mix, Bookmark/Save) matching reference M3 layout
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play Button
                IconButton(
                    onClick = {
                        playerViewModel.playRadio(
                            endpoint = playEndpoint,
                            title = playlistTitle,
                            artistName = authorName
                        )
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play Playlist",
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Radio Button
                IconButton(
                    onClick = {
                        playerViewModel.playRadio(
                            endpoint = radioEndpoint,
                            title = "${playlistTitle} Radio",
                            artistName = authorName
                        )
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(colorScheme.surfaceContainerHighest.copy(alpha = 0.8f), CircleShape)
                        .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Radio,
                        contentDescription = "Playlist Radio",
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Bookmark / Detail Button
                IconButton(
                    onClick = {
                        navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlist.id))
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .background(colorScheme.surfaceContainerHighest.copy(alpha = 0.8f), CircleShape)
                        .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BookmarkBorder,
                        contentDescription = "Save Playlist",
                        tint = colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistCardItem(
    playlist: PlaylistItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .clickable(onClick = onClick)
    ) {
        SmartImage(
            model = playlist.thumbnail,
            contentDescription = playlist.title,
            modifier = Modifier
                .size(136.dp)
                .clip(ExpressiveMediumShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSansRounded
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = playlist.author?.name ?: "",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun SimilarArtistsCarousel(
    artists: List<ArtistItem>,
    navController: NavController,
    playerViewModel: PlayerViewModel
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(items = artists, key = { it.id }) { artist ->
            ArtistCardItem(
                artist = artist,
                onClick = {
                    navController.navigateSafely(Screen.ArtistDetail.createRoute(artist.id))
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibrarySwipeableCarousel(
    section: HomePage.Section,
    navController: NavController,
    playerViewModel: PlayerViewModel
) {
    // OPTIMIZED: Removed auto-scroll while(true) loop that ran continuous animateScrollToPage every 4.5s.
    // That loop kept a coroutine active even when Composable not visible, causing jank.
    // Now static pager, user-driven only, with precomputed native songs to avoid repeated toNativeSong()
    val items = remember(section.items) { section.items.take(6) } // reduced from 8 to 6 for perf
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()
    // Precompute native songs once to avoid mapping inside click lambda repeatedly
    val songItemsNative = remember(items) {
        items.filterIsInstance<SongItem>().map { it.toNativeSong() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(title = section.title)

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            pageSpacing = 12.dp,
            beyondViewportPageCount = 1
        ) { page ->
            val item = items[page]
            LibraryCarouselCard(
                item = item,
                onClick = {
                    when (item) {
                        is SongItem -> {
                            val native = songItemsNative.firstOrNull { it.id == item.id } ?: item.toNativeSong()
                            playerViewModel.showAndPlaySong(
                                native,
                                songItemsNative,
                                section.title
                            )
                        }
                        is AlbumItem -> navController.navigateSafely(Screen.AlbumDetail.createRoute(item.browseId))
                        is ArtistItem -> navController.navigateSafely(Screen.ArtistDetail.createRoute(item.id))
                        is PlaylistItem -> navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.id))
                    }
                }
            )
        }

        // Dot indicator - static width, no animateDpAsState per dot (reduces recomposition)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(items.size) { page ->
                val isSelected = pagerState.currentPage == page
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(if (isSelected) 20.dp else 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                        .clickable { scope.launch { pagerState.animateScrollToPage(page) } }
                )
            }
        }
    }
}

@Composable
private fun LibraryCarouselCard(
    item: YTItem,
    onClick: () -> Unit
) {
    val title = when (item) {
        is SongItem -> item.title
        is AlbumItem -> item.title
        is ArtistItem -> item.title
        is PlaylistItem -> item.title
        else -> ""
    }
    val subtitle = when (item) {
        is SongItem -> item.artists.joinToString { it.name }
        is AlbumItem -> item.artists?.joinToString { it.name } ?: ""
        is ArtistItem -> "Artist"
        is PlaylistItem -> item.songCountText ?: ""
        else -> ""
    }
    val thumbnail: String? = when (item) {
        is SongItem -> item.thumbnail
        is AlbumItem -> item.thumbnail
        is ArtistItem -> item.thumbnail
        is PlaylistItem -> item.thumbnail
        else -> null
    }
    val badgeLabel = when (item) {
        is PlaylistItem -> if (item.shuffleEndpoint != null) "MIX" else null
        is AlbumItem -> "ALBUM"
        else -> null
    }

    // Dynamic color: extract dominant color with in-memory LRU cache and background IO dispatch.
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = isSystemInDarkTheme()
    val animatedBgColor = rememberDominantCardColor(
        imageUrl = thumbnail,
        baseColor = colorScheme.surfaceContainer,
        isDarkTheme = isDarkTheme,
        darkBlendFraction = 0.35f,
        lightBlendFraction = 0.52f
    )
    val cardShape = remember { AbsoluteSmoothCornerShape(28.dp, 60) }
    val badgeShape = remember { AbsoluteSmoothCornerShape(10.dp, 60) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp)
            .clickable(onClick = onClick),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = animatedBgColor
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Thumbnail — right side, fading into the card
            if (!thumbnail.isNullOrBlank()) {
                SmartImage(
                    model = thumbnail,
                    contentDescription = title,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .fillMaxWidth(0.55f),
                    contentScale = ContentScale.Crop
                )
            }

            // Left side gradient overlay to ensure text contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                animatedBgColor,
                                animatedBgColor,
                                animatedBgColor.copy(alpha = 0.85f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Text content — left side
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(0.68f)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.Center
            ) {
                if (badgeLabel != null) {
                    Surface(
                        shape = badgeShape,
                        color = colorScheme.primaryContainer.copy(alpha = 0.88f)
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp,
                            color = colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(5.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
}
    }
}

@Composable
fun SmartMixStudioHeroCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val secondaryContainer = MaterialTheme.colorScheme.secondaryContainer
    val surfaceContainerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val heroGradient = remember(primaryContainer, secondaryContainer, surfaceContainerHigh) {
        Brush.horizontalGradient(
            colors = listOf(
                primaryContainer.copy(alpha = 0.75f),
                secondaryContainer.copy(alpha = 0.85f),
                surfaceContainerHigh
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(AbsoluteSmoothCornerShape(28.dp, 80))
            .background(heroGradient)
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Text(
                        text = "STUDIO",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Smart Mix Studio",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = GoogleSansRounded
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Craft personalized mixes with AI & Last.fm",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "Create Mix",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}


@Composable
fun MixedForYouSection(
    sections: List<HomePage.Section>,
    playerViewModel: PlayerViewModel,
    navController: NavController,
    localSongs: Map<String, Song> = emptyMap()
) {
    if (sections.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { sections.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "Mixed For You",
            actionLabel = "See All",
            onActionClick = {
                navController.navigateSafely(Screen.PlaylistDetail.createRoute("mixed_for_you_all"))
            }
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = sections,
                key = { index, section -> "mixed_for_you_${section.title}_$index" }
            ) { _, section ->
                MixedForYouCard(
                    section = section,
                    playerViewModel = playerViewModel,
                    navController = navController,
                    localSongs = localSongs
                )
            }
        }
    }
}

@Composable
fun MixedForYouCard(
    section: HomePage.Section,
    playerViewModel: PlayerViewModel,
    navController: NavController,
    localSongs: Map<String, Song> = emptyMap()
) {
    val cardThumbnail = remember(section) {
        section.thumbnail.takeIf { !it.isNullOrBlank() }
            ?: section.items.filterIsInstance<SongItem>().firstOrNull()?.thumbnail
    }
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val isDarkTheme = isSystemInDarkTheme()
    val animatedBackground = rememberDominantCardColor(
        imageUrl = cardThumbnail,
        baseColor = colors.surfaceContainerHigh,
        isDarkTheme = isDarkTheme,
        darkBlendFraction = 0.28f,
        lightBlendFraction = 0.45f
    )

    val shape = remember { AbsoluteSmoothCornerShape(24.dp, 60) }

    Card(
        modifier = Modifier
            .width(320.dp)
            .wrapContentHeight(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = animatedBackground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!section.label.isNullOrBlank()) {
                    Text(
                        text = section.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!cardThumbnail.isNullOrBlank()) {
                    SmartImage(
                        model = cardThumbnail,
                        contentDescription = section.title,
                        modifier = Modifier
                            .size(90.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                val songs = section.items.filterIsInstance<SongItem>().take(3)
                if (songs.isNotEmpty()) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val nativeSongs = remember(songs, localSongs) { songs.map { localSongs[it.id] ?: it.toNativeSong() } }
                        songs.forEachIndexed { index, songItem ->
                            val nativeSong = nativeSongs[index]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        playerViewModel.showAndPlaySong(
                                            song = nativeSong,
                                            contextSongs = nativeSongs,
                                            queueName = section.title
                                        )
                                    }
                                    .padding(vertical = 2.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.onSurfaceVariant
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = songItem.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = colors.onSurface
                                    )
                                    Text(
                                        text = songItem.artists.joinToString { it.name },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Fallback for sections containing non-song items (e.g. Albums, Playlists)
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = section.items.take(4), key = { item -> item.id }) { item ->
                            val itemThumb = when (item) {
                                is AlbumItem -> item.thumbnail
                                is PlaylistItem -> item.thumbnail
                                is ArtistItem -> item.thumbnail
                                else -> null
                            }
                            val itemTitle = when (item) {
                                is AlbumItem -> item.title
                                is PlaylistItem -> item.title
                                is ArtistItem -> item.title
                                else -> ""
                            }
                            if (!itemThumb.isNullOrBlank()) {
                                SmartImage(
                                    model = itemThumb,
                                    contentDescription = itemTitle,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            when (item) {
                                                is AlbumItem -> navController.navigateSafely(Screen.AlbumDetail.createRoute(item.browseId))
                                                is PlaylistItem -> navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.id))
                                                is ArtistItem -> navController.navigateSafely(Screen.ArtistDetail.createRoute(item.id))
                                                else -> {}
                                            }
                                        },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val songs = section.items.filterIsInstance<SongItem>()
                val nativeSongs = remember(songs, localSongs) { songs.map { localSongs[it.id] ?: it.toNativeSong() } }
                if (nativeSongs.isNotEmpty()) {
                    Button(
                        onClick = {
                            playerViewModel.showAndPlaySong(
                                song = nativeSongs.first(),
                                contextSongs = nativeSongs,
                                queueName = section.title
                            )
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        ),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play", style = MaterialTheme.typography.labelLarge)
                    }

                    FilledTonalButton(
                        onClick = {
                            playerViewModel.playSongs(
                                nativeSongs.shuffled(),
                                nativeSongs.random(),
                                section.title
                            )
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = colors.secondaryContainer,
                            contentColor = colors.onSecondaryContainer
                        ),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Radio",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Radio", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun MusicCardShelf(
    section: HomePage.Section,
    playerViewModel: PlayerViewModel,
    navController: NavController
) {
    val shape = remember { AbsoluteSmoothCornerShape(24.dp, 60) }
    val colors = MaterialTheme.colorScheme

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row: Title & Subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    if (!section.label.isNullOrBlank()) {
                        Text(
                            text = section.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }
            }

            // Body: Large Thumbnail and List of Songs side-by-side or stacked on smaller screens
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!section.thumbnail.isNullOrBlank()) {
                    SmartImage(
                        model = section.thumbnail,
                        contentDescription = section.title,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                // List of items (songs)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val songs = section.items.filterIsInstance<SongItem>().take(3)
                    val nativeSongs = remember(songs) { songs.map { it.toNativeSong() } }
                    songs.forEachIndexed { index, songItem ->
                        val nativeSong = nativeSongs[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    playerViewModel.showAndPlaySong(
                                        song = nativeSong,
                                        contextSongs = nativeSongs,
                                        queueName = section.title
                                    )
                                }
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.onSurfaceVariant
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = songItem.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = songItem.artists.joinToString { it.name },
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Buttons: Play and Radio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val songs = section.items.filterIsInstance<SongItem>()
                val nativeSongs = remember(songs) { songs.map { it.toNativeSong() } }
                if (nativeSongs.isNotEmpty()) {
                    Button(
                        onClick = {
                            playerViewModel.showAndPlaySong(
                                song = nativeSongs.first(),
                                contextSongs = nativeSongs,
                                queueName = section.title
                            )
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Play")
                    }

                    FilledTonalButton(
                        onClick = {
                            playerViewModel.playSongs(
                                nativeSongs.shuffled(),
                                nativeSongs.random(),
                                section.title
                            )
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = colors.secondaryContainer,
                            contentColor = colors.onSecondaryContainer
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Radio"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Radio")
                    }
                }
            }
        }
    }
}


