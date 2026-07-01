@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)
package com.unshoo.pixelmusic.presentation.screens

import com.unshoo.pixelmusic.presentation.components.AdSupportCard

import androidx.compose.animation.AnimatedVisibility
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
import java.util.Calendar
import kotlin.math.absoluteValue

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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

@UnstableApi
@Composable
fun ExploreScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel,
    paddingValuesParent: PaddingValues,
    exploreViewModel: ExploreViewModel = hiltViewModel(),
    quickPicksViewModel: QuickPicksViewModel = hiltViewModel()
) {
    val uiState by exploreViewModel.uiState.collectAsStateWithLifecycle()
    val quickPicks by quickPicksViewModel.quickPicks.collectAsStateWithLifecycle()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val isPlaying by remember(stablePlayerState) { mutableStateOf(stablePlayerState.isPlaying) }
    val currentSongId = stablePlayerState.currentSong?.id
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
            isRefreshing = uiState.isRefreshing,
            onRefresh = {
                exploreViewModel.loadData(forceRefresh = true)
                quickPicksViewModel.refresh()
            },
            state = pullRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullRefreshState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundBrush)
            ) {
                if (uiState.isLoading && uiState.homePageSections.isEmpty() && uiState.newReleaseAlbums.isEmpty() && uiState.chartsPage == null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (uiState.error != null && uiState.homePageSections.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = uiState.error!!,
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
                    val homeSectionsRaw = if (uiState.selectedFilter == "All") {
                        if (uiState.activeMoodChip != null) {
                            uiState.explorePageSections
                        } else {
                            uiState.homePageSections.ifEmpty { uiState.explorePageSections }
                        }
                    } else {
                        uiState.homePageSections
                    }
                    val homeSectionsFiltered = remember(homeSectionsRaw) {
                        homeSectionsRaw.filter { section ->
                            val title = section.title.lowercase()
                            !title.contains("cover") && !title.contains("remix")
                        }
                    }
                    val cardShelfSections = remember(homeSectionsFiltered) {
                        homeSectionsFiltered.filter { section ->
                            val title = section.title.lowercase()
                            val isSug = title.contains("mix") || title.contains("listen again") || 
                                        title.contains("favorites") || title.contains("suggest") || 
                                        title.contains("recommend") || title.contains("radio") || 
                                        title.contains("similar") || title.contains("played")
                            val hasSongs = section.items.filterIsInstance<SongItem>().isNotEmpty()
                            isSug && hasSongs
                        }
                    }
                    val bottomPadding = if (currentSongId != null) MiniPlayerHeight else 0.dp
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding(),
                            bottom = paddingValuesParent.calculateBottomPadding() + 24.dp + bottomPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // 2. Category Filter Chips (All, Smart Mix, For You, New Releases, Charts, Recap)
                        item(key = "explore_category_filters") {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val categories = listOf("All", "Smart Mix", "For You", "New Releases", "Charts", "Recap")
                                    categories.forEach { category ->
                                        FilterChip(
                                            selected = uiState.selectedFilter == category,
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

                                // Mood / Genre Chips (When All or For You is active)
                                if ((uiState.selectedFilter == "All" || uiState.selectedFilter == "For You") && uiState.moodChips.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        uiState.moodChips.forEach { chip ->
                                            val isSelected = uiState.activeMoodChip == chip
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
                                                shape = RoundedCornerShape(12.dp),
                                                border = null
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 3. AI Smart Mix Studio Card (Hero CTA)
                        val showSmartMixCard = when (uiState.selectedFilter) {
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

                        if (uiState.chartsPage != null && uiState.chartsPage!!.sections.isNotEmpty()) {
                            uiState.chartsPage!!.sections.forEachIndexed { index, chartSection ->
                                item(key = "chart_${chartSection.title}_${index}_header") {
                                    SectionHeader(title = chartSection.title)
                                }

                                val songItems = chartSection.items.filterIsInstance<SongItem>()
                                if (songItems.isNotEmpty()) {
                                    val songListNative = songItems.map { it.toNativeSong() }
                                    items(songItems.size) { idx ->
                                        val songItem = songItems[idx]
                                        val songNative = songListNative[idx]
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
                                            items(chartSection.items) { item ->
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

                        val showSupportCard = uiState.selectedFilter == "All" && !com.unshoo.pixelmusic.data.ads.AdManager.hasRecentlySupported(context)
                        if (showSupportCard) {
                            item(key = "explore_ad_support_card") {
                                AdSupportCard(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                )
                            }
                        }

                        if ((uiState.selectedFilter == "All" || uiState.selectedFilter == "New Releases") &&
                            uiState.newReleaseAlbums.isNotEmpty()
                        ) {
                            item(key = "new_releases_header") {
                                SectionHeader(title = "New Releases")
                            }
                            item(key = "new_releases_carousel") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(uiState.newReleaseAlbums) { album ->
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

                        if ((uiState.selectedFilter == "All" || uiState.selectedFilter == "For You") &&
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
                                    displayMode = quickPicksDisplayMode
                                )
                            }
                        }

                        if ((uiState.selectedFilter == "All" || uiState.selectedFilter == "Smart Mix" || uiState.selectedFilter == "For You") &&
                            uiState.recentMixes.isNotEmpty()
                        ) {
                            item(key = "recent_mixes_header") {
                                SectionHeader(title = "Recent Mixes")
                            }
                            item(key = "recent_mixes_carousel") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(uiState.recentMixes) { playlist ->
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

                        if ((uiState.selectedFilter == "All" || uiState.selectedFilter == "For You") &&
                            uiState.libraryPlaylists.isNotEmpty()
                        ) {
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
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(uiState.libraryPlaylists) { playlist ->
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

                        if (uiState.selectedFilter == "All" || uiState.selectedFilter == "For You") {
                            var carouselRendered = false

                            homeSectionsFiltered.forEachIndexed { index, section ->
                                val isSpeed = section.title.contains("speed dial", ignoreCase = true) || 
                                              section.title.contains("quick picks", ignoreCase = true)
                                val isBento = section.title.contains("featured", ignoreCase = true) || 
                                              section.title.contains("mixed for you", ignoreCase = true) ||
                                              (index % 4 == 0 && section.items.size >= 5)
                                              
                                if (isBento && section.items.size >= 5) {
                                    item(key = "bento_${section.title}_$index") {
                                        LibrarySwipeableCarousel(section, navController, playerViewModel)
                                    }
                                } else if (isSpeed && section.items.isNotEmpty()) {
                                    item(key = "speed_${section.title}_$index") {
                                        SpeedDialSection(section, navController, playerViewModel)
                                    }
                                } else if (cardShelfSections.contains(section)) {
                                    if (!carouselRendered && cardShelfSections.isNotEmpty()) {
                                        item(key = "mixed_for_you_section") {
                                            MixedForYouSection(cardShelfSections, playerViewModel, navController)
                                        }
                                        carouselRendered = true
                                    }
                                } else {
                                    item(key = "home_section_${section.title}_${index}_header") {
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
                                    item(key = "home_section_${section.title}_${index}_carousel") {
                                        if (section.title.startsWith("Similar to", ignoreCase = true) || section.title.contains("Fans also like", ignoreCase = true)) {
                                            SimilarArtistsCarousel(
                                                artists = section.items.filterIsInstance<ArtistItem>(),
                                                navController = navController
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

                            if (uiState.homePageContinuation != null) {
                                item(key = "load_more_trigger") {
                                    LaunchedEffect(Unit) {
                                        exploreViewModel.loadMore()
                                    }
                                    if (uiState.isContinuationLoading) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
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
        items(items) { item ->
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
                        }
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
    val shape = remember { AbsoluteSmoothCornerShape(20.dp, 60) }
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        SmartImage(
            model = song.albumArtUriString,
            contentDescription = song.title,
            modifier = Modifier
                .size(140.dp)
                .clip(shape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = song.artist,
            style = MaterialTheme.typography.bodySmall,
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
    val infiniteTransition = rememberInfiniteTransition(label = "sparkle_animation")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkle_scale"
    )
    val rotation by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkle_rotation"
    )

    val colors = MaterialTheme.colorScheme
    // Exciting gradient background for the button
    val gradientBrush = remember(colors) {
        Brush.linearGradient(
            colors = listOf(
                colors.primary,
                colors.tertiary
            )
        )
    }

    Box(
        modifier = modifier
            .size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        // Exciting premium icon button with gradient and micro-animation
        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = rotation
                }
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
    val shape = remember { AbsoluteSmoothCornerShape(24.dp, 80) }
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
    val blendedBgColor = remember(dominantColor, cardBgColor) {
        dominantColor.copy(alpha = 0.14f).compositeOver(cardBgColor)
    }
    
    Card(
        modifier = Modifier
            .width(220.dp)
            .height(130.dp)
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
                        .size(106.dp)
                        .clip(AbsoluteSmoothCornerShape(16.dp, 80))
                ) {
                    PlaylistCover(
                        playlist = playlist,
                        playlistSongs = playlistSongs ?: emptyList(),
                        modifier = Modifier.fillMaxSize(),
                        size = 106.dp
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
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = "Smart Mix",
            style = MaterialTheme.typography.bodySmall,
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
    val containerColor = if (isScrolled) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    }
    val animatedContainerColor by animateColorAsState(
        targetValue = containerColor,
        animationSpec = tween(durationMillis = 250),
        label = "explore_topbar_color"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(animatedContainerColor)
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
    val shape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 24.dp,
            cornerRadiusTR = 24.dp,
            cornerRadiusBR = 24.dp,
            cornerRadiusBL = 24.dp,
            smoothnessAsPercentTL = 60,
            smoothnessAsPercentTR = 60,
            smoothnessAsPercentBR = 60,
            smoothnessAsPercentBL = 60
        )
    }

    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        SmartImage(
            model = album.thumbnail,
            contentDescription = album.title,
            modifier = Modifier
                .size(140.dp)
                .clip(shape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
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
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick)
    ) {
        SmartImage(
            model = artist.thumbnail,
            contentDescription = artist.title,
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = GoogleSansRounded
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Artist",
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )
    }
}

@Composable
fun PlaylistCardItem(
    playlist: PlaylistItem,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        SmartImage(
            model = playlist.thumbnail,
            contentDescription = playlist.title,
            modifier = Modifier
                .size(140.dp)
                .clip(shape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            text = playlist.author?.name ?: "",
            style = MaterialTheme.typography.bodySmall,
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
    navController: NavController
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(artists) { artist ->
            SimilarArtistCardItem(
                artist = artist,
                onClick = {
                    navController.navigateSafely(Screen.ArtistDetail.createRoute(artist.id))
                }
            )
        }
    }
}

@Composable
fun SimilarArtistCardItem(
    artist: ArtistItem,
    onClick: () -> Unit
) {
    val shape = remember { AbsoluteSmoothCornerShape(20.dp, 60) }
    val primaryColor = MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(
                        color = primaryColor.copy(alpha = 0.12f),
                        shape = CircleShape
                    )
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                SmartImage(
                    model = artist.thumbnail,
                    contentDescription = artist.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = artist.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSansRounded
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Similar Artist",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = primaryColor.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
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
    val items = section.items.take(8)
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        is SongItem -> playerViewModel.showAndPlaySong(
                            item.toNativeSong(),
                            items.filterIsInstance<SongItem>().map { it.toNativeSong() },
                            section.title
                        )
                        is AlbumItem -> navController.navigateSafely(Screen.AlbumDetail.createRoute(item.browseId))
                        is ArtistItem -> navController.navigateSafely(Screen.ArtistDetail.createRoute(item.id))
                        is PlaylistItem -> navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.id))
                    }
                }
            )
        }

        // Dot page indicator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(items.size) { page ->
                val isSelected = pagerState.currentPage == page
                val animatedWidth by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isSelected) 22.dp else 6.dp,
                    animationSpec = spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                    label = "indicator_width_$page"
                )
                val dotColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    animationSpec = tween(180),
                    label = "indicator_color_$page"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(animatedWidth)
                        .clip(CircleShape)
                        .background(dotColor)
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

    // Dynamic color: extract Palette dominant color from album art thumbnail.
    // Blended at low alpha so it tints the M3 surface without overpowering it.
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var tintColor by remember(thumbnail) { mutableStateOf(colorScheme.surfaceContainer) }
    LaunchedEffect(thumbnail) {
        if (!thumbnail.isNullOrBlank()) {
            runCatching {
                val loader = ImageLoader(context)
                val req = ImageRequest.Builder(context)
                    .data(thumbnail).allowHardware(false).size(96).build()
                val result = loader.execute(req)
                if (result is SuccessResult) {
                    val bmp = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bmp != null) {
                        Palette.from(bmp).generate { palette ->
                            val swatch = palette?.dominantSwatch
                                ?: palette?.vibrantSwatch
                                ?: palette?.mutedSwatch
                            if (swatch != null) {
                                tintColor = Color(swatch.rgb).copy(alpha = 0.36f)
                                    .compositeOver(colorScheme.surfaceContainer)
                            }
                        }
                    }
                }
            }
        }
    }

    val animatedBgColor by animateColorAsState(
        targetValue = tintColor,
        animationSpec = tween(450),
        label = "card_bg_${title}"
    )
    val cardShape = remember { AbsoluteSmoothCornerShape(28.dp, 60) }

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
                        .fillMaxHeight()
                        .width(190.dp)
                        .align(Alignment.CenterEnd),
                    contentScale = ContentScale.Crop
                )
                // Horizontal scrim so text is readable
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0.0f to animatedBgColor,
                                    0.25f to animatedBgColor,
                                    0.80f to Color.Transparent
                                )
                            )
                        )
                )
            }

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
                        shape = AbsoluteSmoothCornerShape(10.dp, 60),
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
private fun CollageTile(
    item: YTItem,
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape,
    badgeLabel: String?,
    isHero: Boolean,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    queueName: String
) {
    val title = when (item) {
        is SongItem -> item.title
        is AlbumItem -> item.title
        is ArtistItem -> item.title
        is PlaylistItem -> item.title
        else -> ""
    }
    val thumbnail = when (item) {
        is SongItem -> item.thumbnail
        is AlbumItem -> item.thumbnail
        is ArtistItem -> item.thumbnail
        is PlaylistItem -> item.thumbnail
        else -> ""
    }
    Surface(
        modifier = modifier.clip(shape).clickable {
            when (item) {
                is SongItem -> playerViewModel.showAndPlaySong(item.toNativeSong(), listOf(item.toNativeSong()), queueName)
                is AlbumItem -> navController.navigateSafely(Screen.AlbumDetail.createRoute(item.browseId))
                is ArtistItem -> navController.navigateSafely(Screen.ArtistDetail.createRoute(item.id))
                is PlaylistItem -> navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.id))
            }
        },
        shape = shape,
        tonalElevation = if (isHero) 6.dp else 2.dp,
        shadowElevation = if (isHero) 4.dp else 1.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SmartImage(model = thumbnail, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)), startY = if (isHero) 80f else 30f)))
            if (badgeLabel != null) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    shape = AbsoluteSmoothCornerShape(12.dp, 60),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
            Text(
                text = title,
                style = if (isHero) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = if (isHero) 3 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)
            )
        }
    }
}

@Composable
private fun SpeedDialSection(
    section: unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage.Section,
    navController: NavController,
    playerViewModel: PlayerViewModel
) {
    val rows = section.items.take(9).chunked(3)
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(title = section.title)
        rows.forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { item ->
                    val title = when (item) {
                        is SongItem -> item.title
                        is AlbumItem -> item.title
                        is ArtistItem -> item.title
                        is PlaylistItem -> item.title
                        else -> ""
                    }
                    val thumbnail = when (item) {
                        is SongItem -> item.thumbnail
                        is AlbumItem -> item.thumbnail
                        is ArtistItem -> item.thumbnail
                        is PlaylistItem -> item.thumbnail
                        else -> ""
                    }
                    Box(
                        modifier = Modifier.weight(1f).aspectRatio(1f).clip(AbsoluteSmoothCornerShape(18.dp, 60)).clickable {
                            when (item) {
                                is SongItem -> playerViewModel.showAndPlaySong(item.toNativeSong(), listOf(item.toNativeSong()), section.title)
                                is AlbumItem -> navController.navigateSafely(Screen.AlbumDetail.createRoute(item.browseId))
                                is ArtistItem -> navController.navigateSafely(Screen.ArtistDetail.createRoute(item.id))
                                is PlaylistItem -> navController.navigateSafely(Screen.PlaylistDetail.createRoute(item.id))
                            }
                        }
                    ) {
                        SmartImage(model = thumbnail, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))))
                        Text(text = title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.BottomStart).padding(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MixedForYouSection(
    sections: List<HomePage.Section>,
    playerViewModel: PlayerViewModel,
    navController: NavController
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
            items(sections) { section ->
                MixedForYouCard(
                    section = section,
                    playerViewModel = playerViewModel,
                    navController = navController
                )
            }
        }
    }
}

@Composable
fun MixedForYouCard(
    section: HomePage.Section,
    playerViewModel: PlayerViewModel,
    navController: NavController
) {
    val cardThumbnail = remember(section) {
        section.thumbnail.takeIf { !it.isNullOrBlank() }
            ?: section.items.filterIsInstance<SongItem>().firstOrNull()?.thumbnail
    }
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var tintColor by remember(cardThumbnail) { mutableStateOf(colors.surfaceContainerHigh) }
    
    LaunchedEffect(cardThumbnail) {
        if (!cardThumbnail.isNullOrBlank()) {
            runCatching {
                val loader = ImageLoader(context)
                val req = ImageRequest.Builder(context)
                    .data(cardThumbnail)
                    .allowHardware(false)
                    .size(96)
                    .build()
                val result = loader.execute(req)
                if (result is SuccessResult) {
                    val bmp = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bmp != null) {
                        Palette.from(bmp).generate { palette ->
                            val swatch = palette?.vibrantSwatch
                                ?: palette?.dominantSwatch
                                ?: palette?.mutedSwatch
                            if (swatch != null) {
                                tintColor = Color(swatch.rgb).copy(alpha = 0.28f)
                                    .compositeOver(colors.surfaceContainerHigh)
                            }
                        }
                    }
                }
            }
        }
    }

    val animatedBackground by animateColorAsState(
        targetValue = tintColor,
        animationSpec = tween(400),
        label = "mix_card_bg_${section.title}"
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

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
            }

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
}@Composable
fun SmartMixStudioHeroCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = remember { AbsoluteSmoothCornerShape(28.dp, 60) }
    val colors = MaterialTheme.colorScheme
    
    val gradientBrush = remember(colors) {
        Brush.horizontalGradient(
            colors = listOf(
                colors.tertiaryContainer,
                colors.primaryContainer,
                colors.secondaryContainer
            )
        )
    }

    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .border(1.5.dp, colors.tertiary.copy(alpha = 0.4f), shape),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(22.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(colors.surface.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = colors.tertiary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "SMART MIX STUDIO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.primary,
                            letterSpacing = 1.sp
                        )
                        Surface(
                            shape = CircleShape,
                            color = colors.tertiary,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = "AI 2.0",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                fontWeight = FontWeight.Bold,
                                color = colors.onTertiary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Generate Adaptive Playlists",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                    Text(
                        text = "Blend your local library with YouTube Music online streams seamlessly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
