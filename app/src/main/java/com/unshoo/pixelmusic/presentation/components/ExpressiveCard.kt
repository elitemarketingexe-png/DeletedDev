package com.unshoo.pixelmusic.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.unshoo.pixelmusic.ui.theme.defaultSpatial
import com.unshoo.pixelmusic.ui.theme.fastSpatial

/**
 * M3 Expressive card: press feedback through a fast spatial spring (scale)
 * plus a subtle corner-radius morph — spatial springs overshoot, so releasing
 * a card gives a tiny, physical "pop" back to rest.
 *
 * Used for tappable cards across pages (Quick Picks, shelves, grids) so the
 * whole app shares one press language instead of per-screen hardcoded tweens.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    cornerMorphAmount: Dp = 4.dp,
    pressScale: Float = 0.97f,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable ColumnScope.() -> Unit
) {
    val motionScheme = MaterialTheme.motionScheme
    val isPressed by interactionSource.collectIsPressedAsState()

    // Scale: fast spatial spring — small component motion per the M3 speed map.
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressScale else 1f,
        animationSpec = motionScheme.fastSpatial(),
        label = "ExpressiveCardScale"
    )

    // Shape morph: corners contract while pressed (rounded → slightly squarer),
    // a spatial property animated with the default spatial spring.
    val animatedCorner by animateDpAsState(
        targetValue = if (isPressed) (cornerRadius - cornerMorphAmount).coerceAtLeast(0.dp) else cornerRadius,
        animationSpec = motionScheme.defaultSpatial(),
        label = "ExpressiveCardCorner"
    )

    Card(
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        enabled = enabled,
        shape = RoundedCornerShape(animatedCorner),
        colors = colors,
        elevation = elevation,
        border = border,
        interactionSource = interactionSource
    ) {
        content()
    }
}
