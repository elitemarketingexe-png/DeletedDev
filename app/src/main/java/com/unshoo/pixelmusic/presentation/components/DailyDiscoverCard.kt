package com.unshoo.pixelmusic.presentation.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImagePainter
import coil.size.Size
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.presentation.components.subcomps.EnhancedSongListItem
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage

/**
 * OPTIMIZED Daily Discover Card — v3
 *
 * Key changes vs previous version:
 * - Removed rememberDominantCardColor (Palette bitmap extraction) entirely.
 *   Colors now derived purely from MaterialTheme based on card index — zero CPU, zero coroutines.
 * - Removed all Card/Surface elevation (shadowElevation = 0) — no RenderThread shadow passes.
 * - LazyRow song thumbnails show shimmer while image loads (SubcomposeAsyncImage via SmartImage onState).
 * - toNativeSong() is deferred per-item in the LazyRow, not pre-computed for all songs upfront.
 * - Card click opens DailyDiscoverSongListSheet (ModalBottomSheet) with full LazyColumn song list.
 * - Subtle border replaces shadow for card definition.
 */

/**
 * Returns the card background color for index [cardIndex] based purely on the theme.
 * Cycles through primary → secondary → tertiary container, blended 45% with surface.
 * Zero allocations, zero coroutines, zero CPU work.
 */
@Composable
private fun dailyDiscoverCardColor(cardIndex: Int, isDark: Boolean): Color {
    val colors = MaterialTheme.colorScheme
    val base = colors.surface
    val blendFraction = if (isDark) 0.38f else 0.45f
    return when (cardIndex % 3) {
        0 -> lerp(base, colors.primaryContainer, blendFraction)
        1 -> lerp(base, colors.secondaryContainer, blendFraction)
        else -> lerp(base, colors.tertiaryContainer, blendFraction)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveDailyDiscoverCard(
    section: HomePage.Section,
    playerViewModel: PlayerViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
    localSongs: Map<String, Song> = emptyMap(),
    cardIndex: Int = 0
) {
    val colors = MaterialTheme.colorScheme
    val isDark = isSystemInDarkTheme()

    // Only filter SongItems once — stable
    val songs = remember(section.items) {
        section.items.filterIsInstance<SongItem>()
    }

    // Pre-convert to native only for play controls (small, bounded list)
    // Per-item thumbnail cards convert on-demand below
    val nativeSongs = remember(songs, localSongs) {
        songs.take(20).map { item -> localSongs[item.id] ?: item.toNativeSong() }
    }

    // Theme-based color — zero CPU (pure math, synchronous)
    val cardColor = dailyDiscoverCardColor(cardIndex, isDark)

    val outerShape = remember { AbsoluteSmoothCornerShape(28.dp, 60) }
    val thumbShape = remember { AbsoluteSmoothCornerShape(12.dp, 60) }
    val innerSongCardShape = remember { AbsoluteSmoothCornerShape(18.dp, 60) }

    // Bottom sheet state
    var showSongList by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Card(
        modifier = modifier
            .wrapContentHeight()
            .clickable { showSongList = true },
        shape = outerShape,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        // NO elevation — removed shadows entirely
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = 0.8.dp,
            color = colors.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Header: Badge & Controls ──────────────────────────────────────
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
                            contentDescription = "Shuffle ${section.title}",
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

            // ── Title & Label ─────────────────────────────────────────────────
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
                // Tap hint
                Text(
                    text = "Tap to see all songs",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 1.dp)
                )
            }

            // ── Horizontal Song Thumbnail Strip ───────────────────────────────
            // BoxWithConstraints calculates thumbnail width so exactly 3 cards fit perfectly regardless of DPI
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                // Available width minus 2 internal gaps (8.dp * 2) divided by 3
                val itemWidth = (maxWidth - 16.dp) / 3

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Limit to 8 for the strip — full list shown in the bottom sheet
                    itemsIndexed(songs.take(8)) { index, songItem ->
                        // Convert on-demand per-item, NOT all upfront
                        val nativeSong = remember(songItem.id, localSongs) {
                            localSongs[songItem.id] ?: songItem.toNativeSong()
                        }

                        // Shimmer loading state tracking
                        var imageLoaded by remember(songItem.thumbnail) { mutableStateOf(false) }

                        Surface(
                            modifier = Modifier
                                .width(itemWidth)
                                .clip(innerSongCardShape)
                                .clickable {
                                    playerViewModel.showAndPlaySong(
                                        song = nativeSong,
                                        contextSongs = nativeSongs,
                                        queueName = section.title
                                    )
                                },
                            shape = innerSongCardShape,
                            color = colors.surfaceContainerLowest,
                            // No elevation on inner cards either
                            tonalElevation = 0.dp
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
                                // Shimmer placeholder shown while image loads
                                if (!imageLoaded) {
                                    ShimmerBox(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(thumbShape)
                                    )
                                }

                                // Image fades in once loaded
                                SmartImage(
                                    model = songItem.thumbnail,
                                    contentDescription = songItem.title,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(thumbShape),
                                    contentScale = ContentScale.Crop,
                                    targetSize = Size(96, 96),
                                    alpha = if (imageLoaded) 1f else 0f,
                                    onState = { state ->
                                        if (state is AsyncImagePainter.State.Success) {
                                            imageLoaded = true
                                        }
                                    }
                                )

                                // Play badge — fades in once image is loaded via alpha
                                Surface(
                                    shape = CircleShape,
                                    color = colors.primary,
                                    contentColor = colors.onPrimary,
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(22.dp)
                                        .graphicsLayer { alpha = if (imageLoaded) 1f else 0f }
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

    // ── Full Song List Bottom Sheet ───────────────────────────────────────────
    if (showSongList) {
        DailyDiscoverSongListSheet(
            section = section,
            nativeSongs = nativeSongs,
            playerViewModel = playerViewModel,
            sheetState = sheetState,
            onDismiss = { showSongList = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyDiscoverSongListSheet(
    section: HomePage.Section,
    nativeSongs: List<Song>,
    playerViewModel: PlayerViewModel,
    sheetState: androidx.compose.material3.SheetState,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val listState = rememberLazyListState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Sheet Header ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = colors.primary
                        )
                        Text(
                            text = "Daily Discover",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${nativeSongs.size} songs",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = colors.onSurfaceVariant
                    )
                }
            }

            // ── Play Controls Row ─────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable {
                            if (nativeSongs.isNotEmpty()) {
                                playerViewModel.showAndPlaySong(
                                    nativeSongs.first(),
                                    nativeSongs,
                                    section.title
                                )
                                onDismiss()
                            }
                        },
                    color = colors.primary,
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Play All",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.onPrimary
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable {
                            if (nativeSongs.isNotEmpty()) {
                                val shuffled = nativeSongs.shuffled()
                                playerViewModel.playSongs(shuffled, shuffled.first(), section.title)
                                onDismiss()
                            }
                        },
                    color = colors.secondaryContainer,
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            contentDescription = null,
                            tint = colors.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Shuffle",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = colors.onSecondaryContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // ── Song List — lazy, only renders visible rows ───────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = 32.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(
                    items = nativeSongs,
                    key = { _, song -> "dd_sheet_song_${song.id}" }
                ) { _, song ->
                    EnhancedSongListItem(
                        song = song,
                        isPlaying = false,
                        isCurrentSong = false,
                        onClick = {
                            playerViewModel.showAndPlaySong(
                                song = song,
                                contextSongs = nativeSongs,
                                queueName = section.title
                            )
                            onDismiss()
                        },
                        onMoreOptionsClick = {
                            playerViewModel.selectSongForInfo(song)
                        }
                    )
                }
            }
        }
    }
}
