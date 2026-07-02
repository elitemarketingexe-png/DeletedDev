package com.unshoo.pixelmusic.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.unshoo.pixelmusic.data.model.Song
import com.unshoo.pixelmusic.presentation.components.SmartImage
import com.unshoo.pixelmusic.data.preferences.QuickPicksDisplayMode
import kotlin.math.absoluteValue

private val QuickPicksPillHeight = 56.dp
private val QuickPicksPillSpacing = 8.dp
private const val QuickPicksPillsPerColumn = 3
private const val QuickPicksLimit = 48
private val QuickPicksPillArtSize = 36.dp
private val QuickPicksWidthSteps = listOf(148.dp, 166.dp, 184.dp, 202.dp, 220.dp)

private data class QuickPicksPillCell(val song: Song, val width: Dp)
private data class QuickPicksPillRow(val pills: List<QuickPicksPillCell>, val contentWidth: Dp)

@Composable
fun QuickPicksSection(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onSeeAllClick: (() -> Unit)? = null,
    currentSongId: String? = null,
    displayMode: QuickPicksDisplayMode = QuickPicksDisplayMode.LIST,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return
    val visible = remember(songs) {
        val count = (songs.size / 3) * 3
        songs.take(count.coerceAtMost(QuickPicksLimit))
    }
    val rows = remember(visible) { buildQuickPickRows(visible) }
    val scrollState = rememberScrollState()
    val actualRowsCount = rows.size
    val sectionHeight = if (actualRowsCount > 0) {
        QuickPicksPillHeight * actualRowsCount + QuickPicksPillSpacing * (actualRowsCount - 1)
    } else 0.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Quick Picks",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp)
            )
            if (onSeeAllClick != null) {
                FilledIconButton(
                    modifier = Modifier
                        .height(40.dp)
                        .width(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.secondary
                    ),
                    onClick = onSeeAllClick
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = "See all quick picks",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        
        if (displayMode == QuickPicksDisplayMode.CARD) {
            val pagerState = rememberPagerState(pageCount = { songs.take(20).size })
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 48.dp),
                pageSpacing = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                val song = songs[page]
                
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                val absOffset = pageOffset.absoluteValue
                
                val scale = 0.88f + (1f - 0.88f) * (1f - absOffset.coerceIn(0f, 1f))
                val alpha = 0.55f + (1f - 0.55f) * (1f - absOffset.coerceIn(0f, 1f))
                val tiltY = (pageOffset * -10f).coerceIn(-10f, 10f)
                
                QuickPickCarouselCard(
                    song = song,
                    isPlaying = song.id == currentSongId,
                    onClick = { onSongClick(song) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                            rotationY = tiltY
                            cameraDistance = 8f * density
                        }
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sectionHeight)
                    .horizontalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(QuickPicksPillSpacing)
            ) {
                rows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(QuickPicksPillSpacing),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        row.pills.forEach { cell ->
                            QuickPickPill(
                                song = cell.song,
                                width = cell.width,
                                isPlaying = cell.song.id == currentSongId,
                                onClick = { onSongClick(cell.song) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqualizerAnimation(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 4
) {
    val transition = rememberInfiniteTransition(label = "equalizer")
    
    val heights = (0 until barCount).map { index ->
        val duration = when(index) {
            0 -> 600
            1 -> 800
            2 -> 500
            3 -> 700
            else -> 600
        }
        val delay = index * 150
        transition.animateFloat(
            initialValue = 0.15f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = duration, delayMillis = delay, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
    }
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { heightVal ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(heightVal.value)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun QuickPickCarouselCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val targetBg = if (isPlaying) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.95f)
    }
    val bgColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = 220),
        label = "QuickPickBg"
    )
    
    val targetBorder = if (isPlaying) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }

    val transition = rememberInfiniteTransition(label = "pulse")
    val borderGlowAlpha by if (isPlaying) {
        transition.animateFloat(
            initialValue = 0.4f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "borderGlow"
        )
    } else {
        remember { mutableFloatStateOf(1f) }
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .height(112.dp)
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = if (isPlaying) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = borderGlowAlpha))
        } else {
            targetBorder
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPlaying) 6.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                SmartImage(
                    model = song.albumArtUriString,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        EqualizerAnimation(
                            modifier = Modifier
                                .width(24.dp)
                                .height(20.dp),
                            color = Color.White,
                            barCount = 4
                        )
                    }
                }
            }
            
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isPlaying) {
                            Text(
                                text = "NOW PLAYING",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        } else {
                            Text(
                                text = "QUICK PICK",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(2.dp))
                    
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (isPlaying) Modifier.basicMarquee() else Modifier
                    )
                    
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (isPlaying) {
                    Text(
                        text = "Playing • Tap to pause",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "Tap to play",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPickPill(
    song: Song,
    width: Dp,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val targetBg = if (isPlaying) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceContainerHigh
    val bgColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = tween(durationMillis = 220),
        label = "QuickPickBg"
    )
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(width)
            .height(QuickPicksPillHeight),
        shape = RoundedCornerShape(QuickPicksPillHeight / 2),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val artUri = song.albumArtUriString
            SmartImage(
                model = artUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                shape = CircleShape,
                modifier = Modifier.size(QuickPicksPillArtSize)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun buildQuickPickRows(songs: List<Song>): List<QuickPicksPillRow> {
    val groups = songs.chunked(QuickPicksPillsPerColumn).take(QuickPicksLimit / QuickPicksPillsPerColumn)
    val columns = groups.mapIndexed { colIndex, group ->
        val widthStep = QuickPicksWidthSteps[colIndex % QuickPicksWidthSteps.size]
        group.map { QuickPicksPillCell(it, widthStep) }
    }
    // Transpose columns -> rows
    val rows = mutableListOf<QuickPicksPillRow>()
    for (rowIdx in 0 until QuickPicksPillsPerColumn) {
        val pills = columns.mapNotNull { col -> col.getOrNull(rowIdx) }
        if (pills.isEmpty()) continue
        val totalWidth = pills.sumOf { it.width.value.toDouble() }.dp +
                QuickPicksPillSpacing * (pills.size - 1)
        rows.add(QuickPicksPillRow(pills, totalWidth))
    }
    return rows
}
