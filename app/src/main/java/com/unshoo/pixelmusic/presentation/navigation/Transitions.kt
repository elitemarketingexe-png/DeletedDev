package com.unshoo.pixelmusic.presentation.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin

// MD3 Expressive — push/pop transitions between screens.
//
// ────────────────────────────────────────────────────────────────────────────────
// HIT-TESTING REGRESSION FIX (why these are tween()s and not spring()s):
//
// Navigation-Compose keeps the *outgoing* destination in composition — rendered on
// top of the incoming one and fully hit-testable — until its exit transition has
// completely settled. This file previously used slow, under-damped springs
// (damping ≈ 0.8–0.95, stiffness ≈ 110–200), which take ~0.6–1s to settle, and the
// fade-out finished ~400ms before the spatial spring did. The result was an
// invisible zombie screen sitting on top of the screen you navigated back to,
// still consuming touches. Because Compose hit-testing delivers a tap to the
// topmost subtree first, tapping "Appearance" on the Settings list actually hit
// the zombie Appearance screen's "App Theme" row at the same coordinates and the
// app navigated to the wrong screen ("previous screen still receives touch events").
//
// Springs in Compose have no fixed end — they only finish once value+velocity fall
// below the visibility threshold, which for damped bouncy specs is humanly
// noticeable. Fixed-duration tweens settle deterministically (PUSH/POP_TOTAL_MS),
// so the popped entry is disposed almost immediately after it becomes transparent;
// the remaining hit-test window is a handful of frames instead of a second.
// ScreenWrapper additionally hard-gates input for any entry that is neither the
// navigation target nor RESUMED, so no future spec change can reintroduce the leak.
//
// The choreography (50% enter from the right + 0.92 scale-in, 25% parallax exit,
// 25% pop-enter parallax, full-width pop-exit slide) is unchanged and matches the
// proven AOSP/PixelPlayer timings: 350ms spatial motion with fades finishing in
// ~200-225ms, i.e. content is fully opaque/invisible well before the slide lands.
// ────────────────────────────────────────────────────────────────────────────────
private const val PUSH_POP_TOTAL_MS = 420
private const val PUSH_POP_FADE_MS = 300
private const val POP_FADE_MS = 280

// MD3 Expressive — Emphasized decelerate/accelerate curves tuned for 120Hz displays.
private val EmphasizedDecelerateEasing = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)
private val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

// Kept as a millisecond value for legacy call sites that used to `delay()` roughly
// as long as a push/pop transition takes before acting.
const val TRANSITION_DURATION = PUSH_POP_TOTAL_MS

// Push: Enter from Right — slides in from 50% of screen width + slight scale up.
fun enterTransition() = slideInHorizontally(
    animationSpec = tween(durationMillis = PUSH_POP_TOTAL_MS, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { (it * 0.5f).toInt() }
) + scaleIn(
    animationSpec = tween(durationMillis = PUSH_POP_TOTAL_MS, easing = EmphasizedDecelerateEasing),
    initialScale = 0.92f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeIn(
    animationSpec = tween(durationMillis = PUSH_POP_FADE_MS, easing = EmphasizedDecelerateEasing)
)

// Push: Exit to Left — recedes 25% (parallax, barely moves) and fades out fast so
// the covered screen stops drawing (and stops being a viable touch target) quickly.
fun exitTransition() = slideOutHorizontally(
    animationSpec = tween(durationMillis = PUSH_POP_TOTAL_MS, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { -(it * 0.25f).toInt() }
) + fadeOut(
    animationSpec = tween(durationMillis = PUSH_POP_FADE_MS, easing = EmphasizedAccelerateEasing)
)

// Pop: Enter from Left — parallax slide-in 25% + subtle scale up.
fun popEnterTransition() = slideInHorizontally(
    animationSpec = tween(durationMillis = PUSH_POP_TOTAL_MS, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { -(it * 0.25f).toInt() }
) + scaleIn(
    animationSpec = tween(durationMillis = PUSH_POP_TOTAL_MS, easing = EmphasizedDecelerateEasing),
    initialScale = 0.96f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeIn(
    animationSpec = tween(durationMillis = POP_FADE_MS, easing = EmphasizedDecelerateEasing)
)

// Pop: Exit to Right — slides FULLY off the right edge + slight scale down.
// Sliding all the way out (not just 50%) means the popped screen leaves the tap
// area entirely by the time it is disposed, and the fast fade means it stops
// being visible long before that — with tweens it is guaranteed gone in 350ms.
fun popExitTransition() = slideOutHorizontally(
    animationSpec = tween(durationMillis = PUSH_POP_TOTAL_MS, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { it }
) + scaleOut(
    animationSpec = tween(durationMillis = PUSH_POP_TOTAL_MS, easing = EmphasizedAccelerateEasing),
    targetScale = 0.94f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeOut(
    animationSpec = tween(durationMillis = POP_FADE_MS, easing = EmphasizedAccelerateEasing)
)
