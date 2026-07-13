package com.unshoo.pixelmusic.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

// ─────────────────────────────────────────────────────────────────────────────
// Expressive Morphing Shape (kept for advanced / non-loading contexts only)
// ─────────────────────────────────────────────────────────────────────────────

class MorphingExpressiveShape(private val phase: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val baseRadius = (minOf(size.width, size.height) / 2f) * 0.82f
        val numPoints = 100
        val angleStep = (2 * PI / numPoints).toFloat()
        val rotationAngle = phase * (PI.toFloat() / 2f)
        for (i in 0 until numPoints) {
            val theta = i * angleStep
            val rFactor = getInterpolatedRadius(theta, phase)
            val r = baseRadius * rFactor
            val x = centerX + r * cos(theta + rotationAngle)
            val y = centerY + r * sin(theta + rotationAngle)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }

    private fun getInterpolatedRadius(theta: Float, phase: Float): Float {
        val state = phase.toInt() % 4
        val fraction = phase - phase.toInt()
        val val0 = when (state) {
            0 -> 1.0f - 0.08f * cos(4f * theta)
            1 -> 1.0f + 0.16f * cos(8f * theta)
            2 -> 1.0f + 0.16f * cos(5f * theta)
            else -> 1.0f - 0.10f * cos(10f * theta)
        }
        val nextState = (state + 1) % 4
        val val1 = when (nextState) {
            0 -> 1.0f - 0.08f * cos(4f * theta)
            1 -> 1.0f + 0.16f * cos(8f * theta)
            2 -> 1.0f + 0.16f * cos(5f * theta)
            else -> 1.0f - 0.10f * cos(10f * theta)
        }
        return val0 + (val1 - val0) * fraction
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared shimmer brush — call once per screen and pass down to avoid N
// independent InfiniteTransition instances inside a single LazyColumn.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim = transition.animateFloat(
        initialValue = -300f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translation"
    )
    val colors = MaterialTheme.colorScheme
    val baseColor = colors.onSurface.copy(alpha = 0.08f)
    val highlightColor = colors.onSurface.copy(alpha = 0.22f)
    return Brush.linearGradient(
        colors = listOf(baseColor, highlightColor, baseColor),
        start = Offset(translateAnim.value, 0f),
        end = Offset(translateAnim.value + 250f, 0f)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ShapeShiftingPlaceholder — retained for backward compat but NOT used in
// loading paths (it runs heavy geometry math every frame).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ShapeShiftingPlaceholder(
    modifier: Modifier = Modifier,
    shimmerBrush: Brush = rememberShimmerBrush()
) {
    val transition = rememberInfiniteTransition(label = "morphing_transition")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "morphing_phase"
    )
    val shape = remember(phase) { MorphingExpressiveShape(phase) }
    Box(
        modifier = modifier
            .clip(shape)
            .background(shimmerBrush)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// ShimmerBox — lightweight alias used across skeleton items
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ShimmerBox(modifier: Modifier, shimmerBrush: Brush) {
    Box(modifier = modifier.background(shimmerBrush))
}

// ─────────────────────────────────────────────────────────────────────────────
// ExploreSkeletonGrid
// Mirrors actual Explore layout exactly:
//   1. Filter chips row
//   2. Ad/support banner
//   3. Quick Picks — header + LazyRow of 120dp cards (matches SongCardItem/AlbumCarouselItem)
//   4. Your Library — header + LazyRow of 260×120dp horizontal cards
//   5. Mixed For You — heading + LazyRow of 120dp cards
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ExploreSkeletonGrid(
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues()
) {
    // Single shared brush: one InfiniteTransition for the whole screen
    val shimmerBrush = rememberShimmerBrush()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Category Filter Chips
        item(key = "skel_explore_chips") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chipWidths = listOf(50.dp, 90.dp, 75.dp, 80.dp, 70.dp)
                chipWidths.forEachIndexed { i, w ->
                    Box(
                        modifier = Modifier
                            .width(w)
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(shimmerBrush)
                    )
                }
            }
        }

        // 2. Ad / Support card banner
        item(key = "skel_explore_banner") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .padding(horizontal = 16.dp)
                    .clip(AbsoluteSmoothCornerShape(24.dp, 60))
                    .background(shimmerBrush)
            )
        }

        // 3. Quick Picks section — matches SongCardItem (140dp square + text below)
        item(key = "skel_explore_quick_picks") {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Section header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(shimmerBrush)
                    )
                    Box(
                        modifier = Modifier
                            .width(64.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(shimmerBrush)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                // Quick Picks row — 140dp cards matching actual SongCardItem/QuickPickPortraitCard
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(4) {
                        Column(modifier = Modifier.width(140.dp)) {
                            ShapeShiftingPlaceholder(
                                modifier = Modifier.size(140.dp),
                                shimmerBrush = shimmerBrush
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(13.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(shimmerBrush)
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(11.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(shimmerBrush)
                            )
                        }
                    }
                }
            }
        }

        // 4. Your Library section — matches LibraryPlaylistCard / LibraryAlbumCard (260×120dp)
        item(key = "skel_explore_library") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .height(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(shimmerBrush)
                    )
                    Box(
                        modifier = Modifier
                            .width(55.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    repeat(2) {
                        // 260×100 card with inner art (80dp) + text column
                        Row(
                            modifier = Modifier
                                .width(260.dp)
                                .height(100.dp)
                                .clip(AbsoluteSmoothCornerShape(24.dp, 80))
                                .background(shimmerBrush)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ShapeShiftingPlaceholder(
                                modifier = Modifier.size(80.dp),
                                shimmerBrush = shimmerBrush
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(
                                modifier = Modifier.fillMaxHeight(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .width(110.dp)
                                            .height(13.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(65.dp)
                                            .height(11.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .width(50.dp)
                                        .height(17.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                                )
                            }
                        }
                    }
                }
            }
        }

        // 5. Mixed For You / Suggestion section — 120dp cards matching AlbumCarouselItem
        item(key = "skel_explore_mixed") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .width(150.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(shimmerBrush)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    repeat(4) {
                        Column(modifier = Modifier.width(140.dp)) {
                            ShapeShiftingPlaceholder(
                                modifier = Modifier.size(140.dp),
                                shimmerBrush = shimmerBrush
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(13.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(shimmerBrush)
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.55f)
                                    .height(11.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(shimmerBrush)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SearchSkeletonList
// Mirrors actual search result rows exactly:
//   Each row: 56dp rounded-corner artwork | title line (60%) | subtitle (40%) | 24dp circle menu
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SearchSkeletonList(
    modifier: Modifier = Modifier
) {
    val shimmerBrush = rememberShimmerBrush()
    val itemShape = remember { RoundedCornerShape(24.dp) }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(top = 8.dp)
    ) {
        items(8) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(itemShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album art / thumbnail
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(shimmerBrush)
                )
                Spacer(modifier = Modifier.width(14.dp))
                // Title + subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .height(15.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.42f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                // More-options circle
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(shimmerBrush)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PlaylistSkeletonDetail
// Mirrors actual PlaylistDetailScreen:
//   - Full-width 280dp blurred hero image area
//   - Back button circle (top-left)
//   - Title + subtitle stubs below hero
//   - Play / Shuffle pill buttons
//   - Action chips row (+Add, Remove, Reorder, ⬇)
//   - Song list rows (48dp art + 2 text lines + menu dot)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PlaylistSkeletonDetail(
    modifier: Modifier = Modifier
) {
    val shimmerBrush = rememberShimmerBrush()
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Hero image (full-width, 280dp tall)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(shimmerBrush)
        ) {
            // Back button circle overlay (top-left)
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            )
        }

        // Content below hero
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Playlist title (large)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmerBrush)
            )
            Spacer(modifier = Modifier.height(8.dp))

            // "72 Songs • 0 min" subtitle
            Box(
                modifier = Modifier
                    .width(130.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush)
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Play All + Shuffle pill buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(shimmerBrush)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(shimmerBrush)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))

            // Action chips row (+Add, Remove, Reorder, ⬇)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(72.dp, 80.dp, 82.dp, 50.dp).forEach { w ->
                    Box(
                        modifier = Modifier
                            .width(w)
                            .height(34.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(shimmerBrush)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Song list placeholders matching EnhancedSongListItem
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(6) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShapeShiftingPlaceholder(
                        modifier = Modifier.size(50.dp),
                        shimmerBrush = shimmerBrush
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .height(15.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerBrush)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.42f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerBrush)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(shimmerBrush)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AlbumSkeletonDetail  (NEW)
// Mirrors actual AlbumDetailScreen:
//   - Full-width collapsing hero (300dp)
//   - Back button circle
//   - Album title + artist subtitle stubs
//   - Disc track rows: track number circle + title/artist + duration + menu
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AlbumSkeletonDetail(
    modifier: Modifier = Modifier
) {
    val shimmerBrush = rememberShimmerBrush()
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Full-width collapsing hero image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(shimmerBrush)
        ) {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            )
        }

        // Album metadata below hero
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(18.dp))
            // Album title
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(shimmerBrush)
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Artist subtitle
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(15.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerBrush)
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Play / Shuffle buttons (same as playlist skeleton)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(shimmerBrush)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(shimmerBrush)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Track list rows
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(7) { index ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Album art thumbnail
                    ShapeShiftingPlaceholder(
                        modifier = Modifier.size(50.dp),
                        shimmerBrush = shimmerBrush
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        // Title
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (index % 2 == 0) 0.7f else 0.55f)
                                .height(15.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerBrush)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // Artist
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (index % 2 == 0) 0.45f else 0.35f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerBrush)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    // Menu dot
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(shimmerBrush)
                    )
                }
            }
        }
    }
}
