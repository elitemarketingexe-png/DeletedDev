package com.unshoo.pixelmusic.presentation.utils

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Applies a hardware-accelerated Gaussian blur on API 31+ (Android 12+) using RenderEffect.
 * On older platforms (API < 31), applies graceful fallback.
 */
fun Modifier.gaussianBlur(
    radius: Dp = 25.dp,
    clipShape: Shape? = null
): Modifier = this.then(
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val radiusPx = radius.value * 3f
        Modifier
            .then(if (clipShape != null) Modifier.clip(clipShape) else Modifier)
            .graphicsLayer {
                if (radiusPx > 0f) {
                    renderEffect = RenderEffect
                        .createBlurEffect(
                            radiusPx,
                            radiusPx,
                            Shader.TileMode.CLAMP
                        )
                        .asComposeRenderEffect()
                }
            }
    } else {
        Modifier.then(if (clipShape != null) Modifier.clip(clipShape) else Modifier)
    }
)

/**
 * Modern Glassmorphism effect combining Gaussian Blur, translucent surface tint,
 * and a subtle border highlight.
 */
@Composable
fun Modifier.glassmorphism(
    shape: Shape = RoundedCornerShape(24.dp),
    blurRadius: Dp = 30.dp,
    tintColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
    borderWidth: Dp = 1.dp
): Modifier {
    return this
        .gaussianBlur(radius = blurRadius, clipShape = shape)
        .background(color = tintColor, shape = shape)
        .border(width = borderWidth, color = borderColor, shape = shape)
}

/**
 * Glassmorphic pill container for floating bars, miniplayer, and quick controls.
 */
@Composable
fun Modifier.glassmorphicPill(
    shape: Shape = RoundedCornerShape(32.dp),
    blurRadius: Dp = 24.dp,
    tintColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f)
): Modifier {
    val highlightGradient = Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
            Color.Transparent
        )
    )
    return this
        .gaussianBlur(radius = blurRadius, clipShape = shape)
        .background(color = tintColor, shape = shape)
        .border(
            width = 1.dp,
            brush = highlightGradient,
            shape = shape
        )
}
