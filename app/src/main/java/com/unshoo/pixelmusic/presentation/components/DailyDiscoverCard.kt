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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
 * Material 3 Expressive Daily Discover & Mixed For You Card.
 * Designed according to Google's Material 3 Expressive guidelines for Android 17 (2026).
 * Features dynamic artwork palette extraction, layered squircle surfaces, crisp M3 typography,
 * and horizontal scrollable song list cards.
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

    val songs = remember(section.items) {
        section.items.filterIsInstance<SongItem>()
    }
    val nativeSongs = remember(songs, localSongs) {
        songs.map { localSongs[it.id] ?: it.toNativeSong() }
    }

    val cardThumbnail = remember(section, songs) {
        section.thumbnail.takeIf { !it.isNullOrBlank() }
            ?: songs.firstOrNull()?.thumbnail
    }

    val animatedBackground = rememberDominantCardColor(
        imageUrl = cardThumbnail,
        baseColor = colors.surfaceContainerHigh,
        isDarkTheme = isDark,
        darkBlendFraction = 0.32f,
        lightBlendFraction = 0.50f
    )

    val outerShape = remember { AbsoluteSmoothCornerShape(28.dp, 60) }
    val songCardShape = remember { AbsoluteSmoothCornerShape(18.dp, 60) }

    Card(
        modifier = modifier
            .width(360.dp)
            .wrapContentHeight(),
        shape = outerShape,
        colors = CardDefaults.cardColors(
            containerColor = animatedBackground
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Badge & Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Radio Button
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
                        modifier = Modifier.size(38.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = colors.surfaceContainerHighest,
                            contentColor = colors.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "Radio ${section.title}",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Play Button
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
                        modifier = Modifier.size(38.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play ${section.title}",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Mix Title & Subtitle
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!section.label.isNullOrBlank()) {
                    Text(
                        text = section.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Horizontal Scrollable Song Cards List
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(songs) { index, songItem ->
                    val nativeSong = nativeSongs.getOrNull(index) ?: songItem.toNativeSong()
                    Surface(
                        modifier = Modifier
                            .width(140.dp)
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
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.BottomEnd
                            ) {
                                SmartImage(
                                    model = songItem.thumbnail,
                                    contentDescription = songItem.title,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(AbsoluteSmoothCornerShape(14.dp, 60)),
                                    contentScale = ContentScale.Crop
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = colors.primary,
                                    contentColor = colors.onPrimary,
                                    modifier = Modifier
                                        .padding(6.dp)
                                        .size(26.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Play",
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .fillMaxSize()
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = songItem.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = colors.onSurface
                                )
                                Text(
                                    text = songItem.artists.joinToString { it.name },
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = colors.onSurfaceVariant
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SectionHeader(
            title = "Your Daily Discover",
            actionLabel = "See All",
            onActionClick = {
                navController.navigateSafely(Screen.PlaylistDetail.createRoute("daily_discover_all"))
            }
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(sections.size) { index ->
                val section = sections[index]
                ExpressiveDailyDiscoverCard(
                    section = section,
                    playerViewModel = playerViewModel,
                    navController = navController,
                    localSongs = localSongs
                )
            }
        }
    }
}
