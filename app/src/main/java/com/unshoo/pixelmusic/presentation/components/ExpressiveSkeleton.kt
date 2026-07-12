package com.unshoo.pixelmusic.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

class MorphingExpressiveShape(private val phase: Float) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val baseRadius = minOf(size.width, size.height) / 2f
        
        val numPoints = 100
        val angleStep = (2 * PI / numPoints).toFloat()
        val rotationAngle = phase * (PI.toFloat() / 2f)
        
        for (i in 0 until numPoints) {
            val theta = i * angleStep
            val rFactor = getInterpolatedRadius(theta, phase)
            val r = baseRadius * rFactor
            
            val x = centerX + r * cos(theta + rotationAngle)
            val y = centerY + r * sin(theta + rotationAngle)
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        return Outline.Generic(path)
    }

    private fun getInterpolatedRadius(theta: Float, phase: Float): Float {
        val state = phase.toInt() % 4
        val fraction = phase - phase.toInt()
        
        val val0 = when (state) {
            0 -> 1.0f - 0.08f * cos(4f * theta) // Rounded square approximation
            1 -> 1.0f + 0.16f * cos(8f * theta) // 8-pointed starburst
            2 -> 1.0f + 0.16f * cos(5f * theta) // Flower shape (5 petals)
            else -> 1.0f - 0.10f * cos(10f * theta) // Scalloped shape (10 waves)
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
    val baseColor = colors.surfaceVariant
    val highlightColor = colors.onSurface.copy(alpha = 0.12f)
    val shimmerColors = listOf(
        baseColor,
        highlightColor,
        baseColor
    )

    return Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim.value, 0f),
        end = Offset(translateAnim.value + 250f, 0f)
    )
}

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

@Composable
fun ExploreSkeletonGrid(
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues()
) {
    val shimmerBrush = rememberShimmerBrush()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = paddingValues,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Mood / filter chips skeleton row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(4) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(shimmerBrush)
                    )
                }
            }
        }
        
        // 3 horizontal shelves
        items(3) { shelfIndex ->
            Column(modifier = Modifier.fillMaxWidth()) {
                // Shelf Title Placeholder
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .width(160.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmerBrush)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(5) { itemIndex ->
                        Column(modifier = Modifier.width(140.dp)) {
                            // Morphing shape placeholder
                            ShapeShiftingPlaceholder(
                                modifier = Modifier.size(140.dp),
                                shimmerBrush = shimmerBrush
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            // Title line placeholder
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(shimmerBrush)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            // Subtitle line placeholder
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .height(12.dp)
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

@Composable
fun SearchSkeletonList(
    modifier: Modifier = Modifier
) {
    val shimmerBrush = rememberShimmerBrush()
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        items(6) { index ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left morphing/expressive shape placeholder for search item art
                ShapeShiftingPlaceholder(
                    modifier = Modifier.size(56.dp),
                    shimmerBrush = shimmerBrush
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerBrush)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistSkeletonDetail(
    modifier: Modifier = Modifier
) {
    val shimmerBrush = rememberShimmerBrush()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        // 1. Cover Art Placeholder
        ShapeShiftingPlaceholder(
            modifier = Modifier.size(180.dp),
            shimmerBrush = shimmerBrush
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        // 2. Playlist Title
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerBrush)
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        // 3. Count/Duration Subtitle
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerBrush)
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // 4. Play/Shuffle Buttons Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
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
        Spacer(modifier = Modifier.height(24.dp))
        
        // 5. Songs List Placeholders
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(5) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(shimmerBrush)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerBrush)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.3f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(shimmerBrush)
                        )
                    }
                }
            }
        }
    }
}

