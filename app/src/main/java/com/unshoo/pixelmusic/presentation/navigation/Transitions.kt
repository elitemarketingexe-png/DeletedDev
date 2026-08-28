package com.unshoo.pixelmusic.presentation.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin

// MD3 Expressive — Fluid page transitions with micro-scale & coordinated fades.
//
// ────────────────────────────────────────────────────────────────────────────────
// HIT-TESTING & SMOOTHNESS TUNING:
//
// Uses Emphasized curves tuned for fluid 60/120/144Hz displays with calibrated
// spatial offsets (40% slide + subtle scale depth + progressive fade-in/fade-out).
// Fixed-duration tweens settle deterministically so outgoing destinations are
// cleaned up promptly without touch-event conflicts, while ScreenWrapper provides
// lifecycle-level input isolation.
// ────────────────────────────────────────────────────────────────────────────────
private const val PUSH_POP_TOTAL_MS = 380
private const val PUSH_POP_FADE_MS = 260
private const val POP_FADE_MS = 240

// Main Tab switching timings (snappy yet smooth)
private const val TAB_TRANSITION_MS = 340
private const val TAB_FADE_IN_MS = 280
private const val TAB_FADE_OUT_MS = 180

// MD3 Expressive — Emphasized decelerate/accelerate curves
private val EmphasizedDecelerateEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
private val StandardDecelerateEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

// Kept as a millisecond value for legacy call sites that used to delay()
const val TRANSITION_DURATION = PUSH_POP_TOTAL_MS

// ────────────────────────────────────────────────────────────────────────────────
// HIERARCHICAL NAVIGATION (Push / Pop between sub-screens)
// ────────────────────────────────────────────────────────────────────────────────

// Push: Enter from Right — slides in smoothly from 40% of width with subtle micro-scale
fun enterTransition(): EnterTransition = slideInHorizontally(
    animationSpec = tween(durationMillis = PUSH_POP_TOTAL_MS, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { (it * 0.40f).toInt() }
) + scaleIn(
    animationSpec = tween(durationMillis = PUSH_POP_TOTAL_MS, easing = EmphasizedDecelerateEasing),
    initialScale = 0.95f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeIn(
    animationSpec = tween(durationMillis = PUSH_POP_FADE_MS, easing = StandardDecelerateEasing)
)

// Push: Exit to Left — gentle parallax slide-out (18%) with soft micro scale-down & fade
fun exitTransition(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(durationMillis = PUSH_POP_TOTAL_MS, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { -(it * 0.18f).toInt() }
) + scaleOut(
    animationSpec = tween(durationMillis = PUSH_POP_TOTAL_MS, easing = EmphasizedAccelerateEasing),
    targetScale = 0.97f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeOut(
    animationSpec = tween(durationMillis = PUSH_POP_FADE_MS, easing = EmphasizedAccelerateEasing)
)

// Pop: Enter from Left — subtle parallax re-entry (18%) with micro scale recovery & fade
fun popEnterTransition(): EnterTransition = slideInHorizontally(
    animationSpec = tween(durationMillis = PUSH_POP_TOTAL_MS, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { -(it * 0.18f).toInt() }
) + scaleIn(
    animationSpec = tween(durationMillis = PUSH_POP_TOTAL_MS, easing = EmphasizedDecelerateEasing),
    initialScale = 0.97f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeIn(
    animationSpec = tween(durationMillis = POP_FADE_MS, easing = StandardDecelerateEasing)
)

// Pop: Exit to Right — slides off screen to the right with gentle scale-down & prompt fade
fun popExitTransition(): ExitTransition = slideOutHorizontally(
    animationSpec = tween(durationMillis = PUSH_POP_TOTAL_MS, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { (it * 0.85f).toInt() }
) + scaleOut(
    animationSpec = tween(durationMillis = PUSH_POP_TOTAL_MS, easing = EmphasizedAccelerateEasing),
    targetScale = 0.95f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeOut(
    animationSpec = tween(durationMillis = POP_FADE_MS, easing = EmphasizedAccelerateEasing)
)

// ────────────────────────────────────────────────────────────────────────────────
// MAIN TAB PEER NAVIGATION (Home <-> Explore <-> Search <-> Library)
// Bidirectional Left-to-Right / Right-to-Left with micro scale & clean fade
// ────────────────────────────────────────────────────────────────────────────────

private fun getTabOrder(route: String?): Int = when (route) {
    Screen.Home.route -> 0
    Screen.Explore.route -> 1
    Screen.Search.route -> 2
    Screen.Library.route -> 3
    else -> -1
}

fun mainTabEnterTransition(
    fromRoute: String?,
    toRoute: String?,
    fallback: EnterTransition = enterTransition()
): EnterTransition {
    val fromIndex = getTabOrder(fromRoute)
    val toIndex = getTabOrder(toRoute)
    if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) return fallback

    // Moving forward in tabs (e.g. Home -> Library): enter from right (+X)
    // Moving backward in tabs (e.g. Library -> Home): enter from left (-X)
    val directionMultiplier = if (toIndex > fromIndex) 1 else -1

    return slideInHorizontally(
        animationSpec = tween(durationMillis = TAB_TRANSITION_MS, easing = EmphasizedDecelerateEasing),
        initialOffsetX = { (it * 0.28f * directionMultiplier).toInt() }
    ) + scaleIn(
        animationSpec = tween(durationMillis = TAB_TRANSITION_MS, easing = EmphasizedDecelerateEasing),
        initialScale = 0.96f,
        transformOrigin = TransformOrigin(0.5f, 0.5f)
    ) + fadeIn(
        animationSpec = tween(durationMillis = TAB_FADE_IN_MS, easing = StandardDecelerateEasing)
    )
}

fun mainTabExitTransition(
    fromRoute: String?,
    toRoute: String?,
    fallback: ExitTransition = exitTransition()
): ExitTransition {
    val fromIndex = getTabOrder(fromRoute)
    val toIndex = getTabOrder(toRoute)
    if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) return fallback

    // Moving forward (e.g. Home -> Library): exit to left (-X)
    // Moving backward (e.g. Library -> Home): exit to right (+X)
    val directionMultiplier = if (toIndex > fromIndex) -1 else 1

    return slideOutHorizontally(
        animationSpec = tween(durationMillis = TAB_TRANSITION_MS, easing = EmphasizedAccelerateEasing),
        targetOffsetX = { (it * 0.22f * directionMultiplier).toInt() }
    ) + scaleOut(
        animationSpec = tween(durationMillis = TAB_TRANSITION_MS, easing = EmphasizedAccelerateEasing),
        targetScale = 0.97f,
        transformOrigin = TransformOrigin(0.5f, 0.5f)
    ) + fadeOut(
        animationSpec = tween(durationMillis = TAB_FADE_OUT_MS, easing = EmphasizedAccelerateEasing)
    )
}

