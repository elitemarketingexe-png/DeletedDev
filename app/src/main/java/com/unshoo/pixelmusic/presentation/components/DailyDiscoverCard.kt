package com.unshoo.pixelmusic.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.navigation.navigateSafely
import com.unshoo.pixelmusic.presentation.screens.SectionHeader
import com.unshoo.pixelmusic.presentation.utils.rememberDominantCardColor
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage

/**
 * OPTIMIZED Material 3 Expressive Daily Discover Card.
 *
 * Fixes lag introduced by previous Daily Discover implementation:
 * - Partition logic called toNativeSong() for every SongItem on every recomposition (heavy allocation)
 *   Replaced with lightweight check using localSongs map + numeric ID heuristic
 * - Shuffled sections on every remember caused recomposition + reordering jank; now stable deterministic order
 * - HorizontalPager with dynamic card width is okay, but avoid nested scrollable inside LazyColumn causing issues
 * - Card's LazyRow items previously called toNativeSong fallback even when nativeSongs already available
 * - Using rememberDominantCardColor which is now optimized (disk cache file + semaphore)
 */

@Composable
fun ExpressiveDailyDiscoverCard(
    section: HomePage.Section,
    playerViewModel: PlayerViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
    localSongs: Map<String, Song> = emptyMap()
) {
    val colors = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()

    // Filter once, stable
    val songs = remember(section.items) {
        section.items.filterIsInstance<SongItem>()
    }
    // Convert to native only once; reuse localSongs map if present (DB hit already cached in VM)
    val nativeSongs = remember(songs, localSongs) {
        // Avoid mapping entire list if localSongs large? It's map lookup O(1)
        songs.map { item -> localSongs[item.id] ?: item.toNativeSong() }
    }

    val cardThumbnail = remember(section, songs) {
        section.thumbnail.takeIf { !it.isNullOrBlank() }
            ?: songs.firstOrNull()?.thumbnail
    }

    val animatedBackground = rememberDominantCardColor(
        imageUrl = cardThumbnail,
        baseColor = colors.surfaceContainerHigh,
        isDarkTheme = isDark,
        darkBlendFraction = 0.28f,
        lightBlendFraction = 0.42f
    )

    val outerShape = remember { AbsoluteSmoothCornerShape(28.dp, 60) }
    val songCardShape = remember { AbsoluteSmoothCornerShape(18.dp, 60) }

    Card(
        modifier = modifier.wrapContentHeight(),
        shape = outerShape,
        colors = CardDefaults.cardColors(containerColor = animatedBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Badge & Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = colors.onSecondaryContainer
                        )
                        Text(
                            text = "Daily Discover",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(
                        onClick = {
                            if (nativeSongs.isNotEmpty()) {
                                playerViewModel.playSongs(
                                    nativeSongs.shuffled(),
                                    nativeSongs.random(),
                                    section.title
                                )
                            }
                        },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = colors.surfaceContainerHighest,
                            contentColor = colors.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Radio ${section.title}",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    FilledIconButton(
                        onClick = {
                            if (nativeSongs.isNotEmpty()) {
                                playerViewModel.showAndPlaySong(
                                    song = nativeSongs.first(),
                                    contextSongs = nativeSongs,
                                    queueName = section.title
                                )
                            }
                        },
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play ${section.title}",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
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

            // Horizontal song cards - limited to first 8 for performance
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(songs.take(8)) { index, songItem ->
                    val nativeSong = nativeSongs.getOrNull(index) ?: return@itemsIndexed
                    Surface(
                        modifier = Modifier
                            .width(96.dp)
                            .clip(songCardShape)
                            .clickable {
                                playerViewModel.showAndPlaySong(
                                    song = nativeSong,
                                    contextSongs = nativeSongs,
                                    queueName = section.title
                                )
                            },
                        shape = songCardShape,
                        color = colors.surfaceContainerLowest,
                        tonalElevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(84.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                SmartImage(
                                    model = songItem.thumbnail,
                                    contentDescription = songItem.title,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(AbsoluteSmoothCornerShape(12.dp, 60)),
                                    contentScale = ContentScale.Crop
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = colors.primary,
                                    contentColor = colors.onPrimary,
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Play",
                                        modifier = Modifier
                                            .padding(3.dp)
                                            .fillMaxSize()
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = songItem.title,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = songItem.artists.joinToString { it.name },
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = colors.onSurfaceVariant,
                                    fontSize = 10.sp
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
fun DailyDiscoverSection(
    sections: List<HomePage.Section>,
    playerViewModel: PlayerViewModel,
    navController: NavController,
    localSongs: Map<String, Song> = emptyMap()
) {
    if (sections.isEmpty()) return

    // OPTIMIZED sorting: avoid heavy toNativeSong() in partition
    // Use light heuristic: if SongItem.id is numeric or exists in localSongs map => offline/local
    // Deterministic order: offline first, then online, preserving original order (no shuffle = no jank)
    val sortedSections = remember(sections, localSongs) {
        val offline = mutableListOf<HomePage.Section>()
        val online = mutableListOf<HomePage.Section>()
        for (sec in sections) {
            val songItems = sec.items.filterIsInstance<SongItem>().take(3)
            if (songItems.isEmpty()) {
                online.add(sec)
                continue
            }
            val hasLocal = songItems.any { item ->
                // Fast checks: numeric id (DB) or present in localSongs
                localSongs.containsKey(item.id) || item.id.toLongOrNull() != null
            }
            if (hasLocal) offline.add(sec) else online.add(sec)
        }
        // Deterministic, no shuffle to avoid recomposition thrashing
        offline + online
    }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val cardWidth = remember(screenWidth) { (screenWidth * 0.84f).coerceIn(280.dp, 340.dp) }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState { sortedSections.size }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionHeader(
            title = "Your Daily Discover",
            actionLabel = "See All",
            onActionClick = {
                navController.navigateSafely(Screen.PlaylistDetail.createRoute("daily_discover_all"))
            }
        )

        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            pageSize = androidx.compose.foundation.pager.PageSize.Fixed(cardWidth),
            pageSpacing = 12.dp,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            beyondViewportPageCount = 1, // only preload 1 page, not all
            modifier = Modifier.fillMaxWidth()
        ) { pageIndex ->
            val section = sortedSections.getOrNull(pageIndex) ?: return@HorizontalPager
            ExpressiveDailyDiscoverCard(
                section = section,
                playerViewModel = playerViewModel,
                navController = navController,
                localSongs = localSongs,
                modifier = Modifier.width(cardWidth)
            )
        }
    }
}
