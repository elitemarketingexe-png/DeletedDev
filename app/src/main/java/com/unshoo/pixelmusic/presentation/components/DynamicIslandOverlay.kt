package com.unshoo.pixelmusic.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import coil.size.Size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unshoo.pixelmusic.presentation.viewmodel.PlayerViewModel
import com.unshoo.pixelmusic.ui.theme.defaultSpatial
import kotlinx.coroutines.delay

/**
 * Dynamic Island — an iOS-style floating "island" pill for PixelMusic.
 *
 * A near-black pill anchored below the status bar / camera cutout that mirrors the
 * now-playing state:
 *
 *  - **Collapsed** (default): album artwork, scrolling title · artist, play/pause.
 *  - **Expanded** (tap): adds previous/next, a tap-to-seek progress bar and a close
 *    button; auto-collapses after a few seconds.
 *  - **Swipe up** anywhere on the pill dismisses it until the next track change.
 *  - Automatically hidden while the full player sheet is expanded.
 *
 * This is an in-app overlay (no `SYSTEM_ALERT_WINDOW` permission required). For a
 * system-level island that also appears in the status bar while the app is in the
 * background, combine it with Android 16 Live Updates (see `LiveUpdateUtil`).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DynamicIslandOverlay(
    playerViewModel: PlayerViewModel,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val isSheetVisible by playerViewModel.isSheetVisible.collectAsStateWithLifecycle()
    val song = stablePlayerState.currentSong

    var expanded by remember { mutableStateOf(false) }
    var dismissed by remember { mutableStateOf(false) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    // Fresh island on every track change: re-appear, collapse back to the pill.
    LaunchedEffect(song?.id) {
        dismissed = false
        expanded = false
    }

    // Auto-collapse shortly after expanding (iOS behaves the same way).
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(5_000)
            expanded = false
        }
    }

    // Lightweight in-pill position estimate (the app exposes no per-second position flow).
    var positionMs by remember(song?.id) { mutableLongStateOf(0L) }
    LaunchedEffect(song?.id, stablePlayerState.isPlaying) {
        positionMs = 0L
        while (stablePlayerState.isPlaying) {
            delay(1_000)
            positionMs += 1_000
        }
    }

    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 72.dp.toPx() }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val cutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val topInset = maxOf(statusBarTop, cutoutTop)

    val showIsland = enabled && song != null && !dismissed && !isSheetVisible

    AnimatedVisibility(
        visible = showIsland,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        val currentSong = song ?: return@AnimatedVisibility
        // M3 Expressive: island expansion is size motion → default spatial spring (overshoots subtly).
        val motionScheme = MaterialTheme.motionScheme
        val islandWidth by animateDpAsState(
            targetValue = if (expanded) 320.dp else 152.dp,
            animationSpec = motionScheme.defaultSpatial(),
            label = "islandWidth"
        )
        val islandHeight by animateDpAsState(
            targetValue = if (expanded) 82.dp else 38.dp,
            animationSpec = motionScheme.defaultSpatial(),
            label = "islandHeight"
        )

        Surface(
            modifier = Modifier
                .padding(top = topInset + 6.dp)
                .width(islandWidth)
                .height(islandHeight)
                .graphicsLayer { translationY = dragOffsetY }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta -> dragOffsetY += delta },
                    onDragStopped = {
                        if (dragOffsetY < -dismissThresholdPx) dismissed = true
                        dragOffsetY = 0f
                    }
                )
                .clickable { expanded = !expanded },
            color = Color(0xFF0A0A0A),
            contentColor = Color.White,
            shape = RoundedCornerShape(50),
            shadowElevation = 10.dp,
            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.16f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (expanded) 14.dp else 8.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Album artwork
                    val artSize = if (expanded) 40.dp else 26.dp
                    Surface(
                        color = Color.White.copy(alpha = 0.07f),
                        shape = RoundedCornerShape(if (expanded) 16.dp else 12.dp),
                        modifier = Modifier.size(artSize)
                    ) {
                        SmartImage(
                            model = currentSong.albumArtUriString,
                            contentDescription = null,
                            modifier = Modifier.size(artSize),
                            shape = RoundedCornerShape(if (expanded) 16.dp else 12.dp),
                            contentScale = ContentScale.Crop,
                            targetSize = SmartImageListTargetSize
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Title · artist
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSong.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        if (expanded) {
                            Text(
                                text = currentSong.displayArtist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.65f)
                            )
                        }
                    }

                    if (expanded) {
                        IslandIconButton(
                            onClick = { playerViewModel.previousSong() },
                            contentDescription = "Previous"
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }

                    IslandIconButton(
                        onClick = { playerViewModel.playPause() },
                        contentDescription = if (stablePlayerState.isPlaying) "Pause" else "Play"
                    ) {
                        Icon(
                            imageVector = if (stablePlayerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }

                    if (expanded) {
                        IslandIconButton(
                            onClick = { playerViewModel.nextSong() },
                            contentDescription = "Next"
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        IslandIconButton(
                            onClick = { dismissed = true },
                            contentDescription = "Close"
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                if (expanded) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val total = stablePlayerState.totalDuration
                    val fraction =
                        if (total > 0) (positionMs.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                        else 0f

                    // Thin progress track — tap anywhere on it to seek.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f))
                            .pointerInput(total) {
                                detectTapGestures { offset ->
                                    if (total > 0) {
                                        val widthPx = size.width.toFloat()
                                        if (widthPx > 0f) {
                                            playerViewModel.seekTo(
                                                ((offset.x / widthPx) * total).toLong()
                                            )
                                        }
                                    }
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(3.dp)
                                .background(Color.White)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IslandIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(30.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.White.copy(alpha = 0.09f),
            contentColor = Color.White
        )
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
