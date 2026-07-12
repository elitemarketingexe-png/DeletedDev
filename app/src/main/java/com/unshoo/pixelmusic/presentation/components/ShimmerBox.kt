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
    val brush = rememberShimmerBrush()
    Box(modifier = modifier.background(brush = brush))
}
