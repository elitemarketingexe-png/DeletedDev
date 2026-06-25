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
import androidx.compose.animation.core.tween
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

    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundBrush = remember(surfaceColor, primaryColor) {
        Brush.verticalGradient(
            colors = listOf(
                primaryColor.copy(alpha = 0.15f),
                surfaceColor.copy(alpha = 0.6f),
                surfaceColor
            ),
            endY = 1000f
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
                }
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
                    val homeSectionsFiltered = uiState.homePageSections
                    val bottomPadding = if (currentSongId != null) MiniPlayerHeight else 0.dp
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = innerPadding.calculateTopPadding(),
                            bottom = paddingValuesParent.calculateBottomPadding() + 24.dp + bottomPadding
                        ),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        item(key = "explore_filters") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                        }


                        if (uiState.selectedFilter == "All" || uiState.selectedFilter == "Smart Mix") {
                            item(key = "smart_mix_category") {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .clickable { navController.navigateSafely(Screen.SmartMix.route) },
                                    shape = AbsoluteSmoothCornerShape(24.dp, 60),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(20.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Smart Mix Studio",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                            Text(
                                                text = "Generate AI adaptive playlists matching your mood and tempo",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
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

                        if (uiState.selectedFilter == "All") {
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

                        if (uiState.selectedFilter == "All" || uiState.selectedFilter == "For You") {
                            homeSectionsFiltered.forEachIndexed { index, section ->
                                val isBento = section.title.contains("mix", ignoreCase = true) && section.items.size >= 5
                                val isSpeed = section.title.contains("speed dial", ignoreCase = true) || section.title.contains("quick picks", ignoreCase = true)
                                if (isBento) {
                                    item(key = "bento_${section.title}_$index") {
                                        BentoGridSection(section, navController, playerViewModel)
                                    }
                                } else if (isSpeed && section.items.isNotEmpty()) {
                                    item(key = "speed_${section.title}_$index") {
                                        SpeedDialSection(section, navController, playerViewModel)
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
    onCreateClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
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

@Composable
private fun WelcomeGreetingBanner(userName: String?) {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
    val name = userName?.takeIf { it.isNotBlank() } ?: "Music Lover"
    Text(
        text = "$greeting, $name",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun BentoGridSection(
    section: unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage.Section,
    navController: NavController,
    playerViewModel: PlayerViewModel
) {
    val items = section.items
    if (items.size < 5) return
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionHeader(title = section.title)
        Row(modifier = Modifier.height(310.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CollageTile(
                item = items[0],
                modifier = Modifier.weight(1.35f).fillMaxHeight(),
                shape = AbsoluteSmoothCornerShape(
                    cornerRadiusTL = 52.dp,
                    smoothnessAsPercentTR = 60,
                    cornerRadiusTR = 16.dp,
                    smoothnessAsPercentTL = 60,
                    cornerRadiusBL = 16.dp,
                    smoothnessAsPercentBR = 60,
                    cornerRadiusBR = 52.dp,
                    smoothnessAsPercentBL = 60
                ),
                badgeLabel = "FEATURED MIX",
                isHero = true,
                navController = navController,
                playerViewModel = playerViewModel,
                queueName = section.title
            )
            Column(modifier = Modifier.weight(0.75f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CollageTile(
                    item = items[1],
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    shape = AbsoluteSmoothCornerShape(32.dp, 60),
                    badgeLabel = null,
                    isHero = false,
                    navController = navController,
                    playerViewModel = playerViewModel,
                    queueName = section.title
                )
                CollageTile(
                    item = items[2],
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTL = 14.dp,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusTR = 40.dp,
                        smoothnessAsPercentTL = 60,
                        cornerRadiusBL = 40.dp,
                        smoothnessAsPercentBR = 60,
                        cornerRadiusBR = 14.dp,
                        smoothnessAsPercentBL = 60
                    ),
                    badgeLabel = null,
                    isHero = false,
                    navController = navController,
                    playerViewModel = playerViewModel,
                    queueName = section.title
                )
            }
        }
        Row(modifier = Modifier.height(154.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CollageTile(
                item = items[3],
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = AbsoluteSmoothCornerShape(
                    cornerRadiusTL = 36.dp,
                    smoothnessAsPercentTR = 60,
                    cornerRadiusTR = 14.dp,
                    smoothnessAsPercentTL = 60,
                    cornerRadiusBL = 14.dp,
                    smoothnessAsPercentBR = 60,
                    cornerRadiusBR = 36.dp,
                    smoothnessAsPercentBL = 60
                ),
                badgeLabel = null,
                isHero = false,
                navController = navController,
                playerViewModel = playerViewModel,
                queueName = section.title
            )
            CollageTile(
                item = items[4],
                modifier = Modifier.weight(1.2f).fillMaxHeight(),
                shape = AbsoluteSmoothCornerShape(44.dp, 60),
                badgeLabel = "HIT MIX",
                isHero = false,
                navController = navController,
                playerViewModel = playerViewModel,
                queueName = section.title
            )
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
