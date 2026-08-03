package com.unshoo.pixelmusic.presentation.navigation

import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import com.unshoo.pixelmusic.ui.theme.ExpressiveSprings

// Material 3 Expressive — Next-Gen Android Page Push/Pop Transitions
// Sourced from ExpressiveSprings motion tokens (SlowSpatial & DefaultEffects)
private val PUSH_POP_SPATIAL_SPRING = spring<IntOffset>(
    dampingRatio = ExpressiveSprings.SlowSpatialDampingRatio,
    stiffness = ExpressiveSprings.SlowSpatialStiffness
)

private val PUSH_POP_SCALE_SPRING = spring<Float>(
    dampingRatio = ExpressiveSprings.SlowSpatialDampingRatio,
    stiffness = ExpressiveSprings.SlowSpatialStiffness
)

private val PUSH_POP_FADE_SPRING = spring<Float>(
    dampingRatio = ExpressiveSprings.DefaultEffectsDampingRatio,
    stiffness = ExpressiveSprings.DefaultEffectsStiffness
)

const val TRANSITION_DURATION = 400

// Push Enter: Slide in 40% horizontally + subtle 16px vertical lift + scale up (0.92f to 1.0f)
fun enterTransition() = slideInHorizontally(
    animationSpec = PUSH_POP_SPATIAL_SPRING,
    initialOffsetX = { (it * 0.40f).toInt() }
) + slideInVertically(
    animationSpec = PUSH_POP_SPATIAL_SPRING,
    initialOffsetY = { 36 }
) + scaleIn(
    animationSpec = PUSH_POP_SCALE_SPRING,
    initialScale = 0.94f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeIn(
    animationSpec = PUSH_POP_FADE_SPRING
)

// Push Exit: Recede left 20% + scale down slightly (1.0f to 0.96f)
fun exitTransition() = slideOutHorizontally(
    animationSpec = PUSH_POP_SPATIAL_SPRING,
    targetOffsetX = { -(it * 0.20f).toInt() }
) + scaleOut(
    animationSpec = PUSH_POP_SCALE_SPRING,
    targetScale = 0.96f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeOut(
    animationSpec = PUSH_POP_FADE_SPRING
)

// Pop Enter: Return right 20% + scale up (0.96f to 1.0f)
fun popEnterTransition() = slideInHorizontally(
    animationSpec = PUSH_POP_SPATIAL_SPRING,
    initialOffsetX = { -(it * 0.20f).toInt() }
) + scaleIn(
    animationSpec = PUSH_POP_SCALE_SPRING,
    initialScale = 0.96f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeIn(
    animationSpec = PUSH_POP_FADE_SPRING
)

// Pop Exit: Slide out right 40% + slight scale down (1.0f to 0.94f) + 16px drop
fun popExitTransition() = slideOutHorizontally(
    animationSpec = PUSH_POP_SPATIAL_SPRING,
    targetOffsetX = { (it * 0.40f).toInt() }
) + slideOutVertically(
    animationSpec = PUSH_POP_SPATIAL_SPRING,
    targetOffsetY = { 36 }
) + scaleOut(
    animationSpec = PUSH_POP_SCALE_SPRING,
    targetScale = 0.94f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeOut(
    animationSpec = PUSH_POP_FADE_SPRING
)
