package com.unshoo.pixelmusic.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
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
import androidx.compose.foundation.lazy.items
import androidx.navigation.NavController
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.presentation.navigation.Screen
import com.unshoo.pixelmusic.presentation.navigation.navigateSafely
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.data.remote.youtube.toNativeSong
import com.unshoo.pixelmusic.presentation.utils.rememberDominantCardColor
import com.unshoo.pixelmusic.presentation.screens.SectionHeader
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import unshoo.ianshulyadav.pixelmusic.innertube.models.SongItem
import unshoo.ianshulyadav.pixelmusic.innertube.pages.HomePage

/**
 * Material 3 Expressive Daily Discover & Mixed For You Card.
 * Designed according to Google's Material 3 Expressive guidelines for Android 17 (2026).
 * Features dynamic artwork palette extraction, layered squircle surfaces, crisp M3 typography,
 * and quick-action track triggers.
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
        section.items.filterIsInstance<SongItem>().take(3)
    }
    val nativeSongs = remember(songs, localSongs) {
        songs.map { localSongs[it.id] ?: it.toNativeSong() }
    }

    val cardThumbnail = remember(section, songs) {
        section.thumbnail.takeIf { !it.isNullOrBlank() }
            ?: songs.firstOrNull()?.thumbnail
    }

    val thumbnails = remember(section.items) {
        section.items.filterIsInstance<SongItem>()
            .mapNotNull { it.thumbnail }
            .distinct()
            .take(3)
    }

    val animatedBackground = rememberDominantCardColor(
        imageUrl = cardThumbnail,
        baseColor = colors.surfaceContainerHigh,
        isDarkTheme = isDark,
        darkBlendFraction = 0.32f,
        lightBlendFraction = 0.50f
    )

    val outerShape = remember { AbsoluteSmoothCornerShape(28.dp, 60) }
    val innerShape = remember { AbsoluteSmoothCornerShape(16.dp, 60) }

    Card(
        modifier = modifier
            .width(330.dp)
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
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Badge & Play Button Row
            Row(
                modifier = Modifier.fillMaxWidth(),
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

                // Play Mix Button
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
                    modifier = Modifier.size(42.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play ${section.title}",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Mix Title & Subtitle
            Column {
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

            // Layered Artwork Stack & Track Preview Box
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Layered Collage Thumbnails
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (thumbnails.size >= 2) {
                        // Background stacked art
                        SmartImage(
                            model = thumbnails.getOrNull(1),
                            contentDescription = null,
                            modifier = Modifier
                                .size(78.dp)
                                .align(Alignment.TopEnd)
                                .clip(AbsoluteSmoothCornerShape(16.dp, 60))
                                .shadow(2.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                    // Hero foreground art
                    SmartImage(
                        model = cardThumbnail,
                        contentDescription = section.title,
                        modifier = Modifier
                            .size(86.dp)
                            .align(Alignment.BottomStart)
                            .clip(AbsoluteSmoothCornerShape(20.dp, 60))
                            .shadow(4.dp),
                        contentScale = ContentScale.Crop
                    )
                }

                // Top 3 Tracks List Box (Tonal Surface Container Lowest)
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = innerShape,
                    color = colors.surfaceContainerLowest,
                    tonalElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        songs.forEachIndexed { index, songItem ->
                            val nativeSong = nativeSongs.getOrNull(index) ?: songItem.toNativeSong()
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
                                    .padding(vertical = 4.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = colors.primary
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

        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(sections) { section ->
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
