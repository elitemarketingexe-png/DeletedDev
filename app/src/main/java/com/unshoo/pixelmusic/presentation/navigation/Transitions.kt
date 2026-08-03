package com.unshoo.pixelmusic.presentation.navigation

import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import com.unshoo.pixelmusic.ui.theme.ExpressiveSprings

// MD3 Expressive — push/pop transitions between screens.
//
// These specs are sourced from the same ExpressiveSprings tokens (ui/theme/Motion.kt,
// verified against material3 1.5.0-alpha21) that already back MaterialTheme.motionScheme
// everywhere else in the app — bottom sheets, cards, the pill nav bar. Previously this file
// used fixed-duration tween()s with a hand-rolled cubic-bezier "emphasized" curve, which
// *looked* M3-ish but wasn't actually driven by the app's motion scheme, so it couldn't be
// interrupted/retargeted like a real spring (e.g. a fast back-swipe had to fight or restart
// a running tween instead of smoothly reversing from its current position/velocity), and it
// slowly drifted out of sync with the token values used everywhere else in the app.
//
// Per M3's own speed mapping (fast = small components, default = partial-screen motion,
// slow = full-screen/hero motion), a screen push/pop is full-screen hero content, so it uses
// the "slow spatial" spring for position + scale, and "default effects" for fade — opacity
// should still settle briskly no matter how large the moving element is.
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

// Kept as a millisecond value for legacy call sites (SettingsScreen / SettingsCategoryScreen)
// that `delay()` roughly as long as a push/pop transition takes before acting. Springs don't
// have an exact duration the way tween() did, so this is an approximate visual-settle time for
// PUSH_POP_SPATIAL_SPRING (slow spatial, dampingRatio 0.8 / stiffness 200) — not the literal
// animation spec any more, just a "wait roughly this long" heuristic for those two call sites.
const val TRANSITION_DURATION = 450

// Push: Enter from Right — slides in 50% of screen width + slight scale up (mirrors popExit's weight)
fun enterTransition() = slideInHorizontally(
    animationSpec = PUSH_POP_SPATIAL_SPRING,
    initialOffsetX = { (it * 0.5f).toInt() }
) + scaleIn(
    animationSpec = PUSH_POP_SCALE_SPRING,
    initialScale = 0.92f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeIn(
    animationSpec = PUSH_POP_FADE_SPRING
)

// Push: Exit to Left — recedes 25% (parallax, barely moves).
fun exitTransition() = slideOutHorizontally(
    animationSpec = PUSH_POP_SPATIAL_SPRING,
    targetOffsetX = { -(it * 0.25f).toInt() }
) + fadeOut(
    animationSpec = PUSH_POP_FADE_SPRING
)

// Pop: Enter from Left — parallax slide-in 25% + subtle scale up.
fun popEnterTransition() = slideInHorizontally(
    animationSpec = PUSH_POP_SPATIAL_SPRING,
    initialOffsetX = { -(it * 0.25f).toInt() }
) + scaleIn(
    animationSpec = PUSH_POP_SCALE_SPRING,
    initialScale = 0.95f
) + fadeIn(
    animationSpec = PUSH_POP_FADE_SPRING
)

// Pop: Exit to Right — slides out 50% + slight scale down.
fun popExitTransition() = slideOutHorizontally(
    animationSpec = PUSH_POP_SPATIAL_SPRING,
    targetOffsetX = { (it * 0.5f).toInt() }
) + scaleOut(
    animationSpec = PUSH_POP_SCALE_SPRING,
    targetScale = 0.92f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeOut(
    animationSpec = PUSH_POP_FADE_SPRING
)
