package com.unshoo.pixelmusic.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    // Static shimmer-style placeholder. The previous implementation created an infinite
    // animation for every placeholder cell; during sync/loading this could mean dozens of
    // frame callbacks on midrange devices. A static gradient keeps the skeleton look without
    // stealing frame budget from scrolling/player gestures.
    val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlightColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val brush = remember(baseColor, highlightColor) {
        Brush.linearGradient(
            colors = listOf(baseColor, highlightColor, baseColor),
            start = Offset.Zero,
            end = Offset(220f, 220f)
        )
    }

    Box(modifier = modifier.background(brush = brush))
}
