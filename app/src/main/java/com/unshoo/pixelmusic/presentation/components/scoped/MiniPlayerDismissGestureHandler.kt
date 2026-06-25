package com.unshoo.pixelmusic.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private enum class MiniDismissDragPhase { IDLE, TENSION, SNAPPING, FREE_DRAG }

/**
 * Keeps mini-player dismiss gesture behavior isolated from the sheet host.
 *
 * Immersive dismiss micro-animations:
 *  - TENSION phase: card resists with max 30dp translation, fades slightly,
 *    and tilts up to 3° to hint at the gesture direction.
 *  - SNAPPING → FREE_DRAG: haptic fires, card unlocks and follows the finger
 *    with a subtle scale-down (0.96) and rotation (±8°) for a depth / card-lift feel.
 *  - Dismiss: card accelerates off-screen with a decelerate curve, fading to 0
 *    and rotating to ±14° as it exits (feels like tossing a card away).
 *  - Cancel: card snaps back with a gentle spring so it feels lively but not bouncy.
 */
internal class MiniPlayerDismissGestureHandler(
    private val scope: CoroutineScope,
    private val density: Density,
    private val hapticFeedback: HapticFeedback,
    private val offsetAnimatable: Animatable<Float, AnimationVector1D>,
    /** Alpha 0→1 driven by swipe progress, exposed for graphicsLayer. */
    val dismissAlpha: Animatable<Float, AnimationVector1D>,
    /** Rotation degrees, exposed for graphicsLayer. */
    val dismissRotation: Animatable<Float, AnimationVector1D>,
    /** Scale 0→1 driven by swipe progress, exposed for graphicsLayer. */
    val dismissScale: Animatable<Float, AnimationVector1D>,
    private val screenWidthPx: Float,
    private val onDismissPlaylistAndShowUndo: () -> Unit,
    private val onDismissStarted: () -> Unit = {}
) {
    private var dragPhase: MiniDismissDragPhase = MiniDismissDragPhase.IDLE
    private var accumulatedDragX: Float = 0f
    private var offsetJob: Job? = null

    // Precomputed thresholds
    private val snapThresholdPx get() = 100f * density.density
    private val maxTensionOffsetPx get() = 30f * density.density
    private val dismissThreshold get() = (screenWidthPx * 0.4f).coerceAtLeast(1f)

    fun onDragStart() {
        dragPhase = MiniDismissDragPhase.TENSION
        accumulatedDragX = 0f
        offsetJob?.cancel()
        offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offsetAnimatable.stop()
        }
    }

    fun onHorizontalDrag(dragAmount: Float) {
        accumulatedDragX += dragAmount

        when (dragPhase) {
            MiniDismissDragPhase.TENSION -> {
                if (abs(accumulatedDragX) < snapThresholdPx) {
                    val dragFraction = (abs(accumulatedDragX) / snapThresholdPx).coerceIn(0f, 1f)
                    val tensionOffset = lerp(0f, maxTensionOffsetPx, dragFraction)
                    val tensionRotation = lerp(0f, 3f, dragFraction) * accumulatedDragX.sign
                    val tensionAlpha = lerp(1f, 0.85f, dragFraction)
                    offsetJob?.cancel()
                    offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        offsetAnimatable.snapTo(tensionOffset * accumulatedDragX.sign)
                        dismissRotation.snapTo(tensionRotation)
                        dismissAlpha.snapTo(tensionAlpha)
                        dismissScale.snapTo(1f) // no scale in tension phase yet
                    }
                } else {
                    dragPhase = MiniDismissDragPhase.SNAPPING
                }
            }

            MiniDismissDragPhase.SNAPPING -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                offsetJob?.cancel()
                offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    // Card springs into "free" position — feels like unlocking a latch
                    offsetAnimatable.animateTo(
                        targetValue = accumulatedDragX,
                        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)
                    )
                }
                dragPhase = MiniDismissDragPhase.FREE_DRAG
            }

            MiniDismissDragPhase.FREE_DRAG -> {
                val progress = (abs(accumulatedDragX) / screenWidthPx).coerceIn(0f, 1f)
                // Tilt up to 8° in direction of drag, fade to 80%, scale down to 0.95
                val targetRotation = lerp(0f, 8f, progress) * accumulatedDragX.sign
                val targetAlpha = lerp(1f, 0.8f, progress)
                val targetScale = lerp(1f, 0.95f, progress)
                offsetJob?.cancel()
                offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    // Finger tracking: snap offset but animate the visual properties
                    offsetAnimatable.snapTo(accumulatedDragX)
                    dismissRotation.snapTo(targetRotation)
                    dismissAlpha.snapTo(targetAlpha)
                    dismissScale.snapTo(targetScale)
                }
            }

            MiniDismissDragPhase.IDLE -> Unit
        }
    }

    fun onDragEnd() {
        dragPhase = MiniDismissDragPhase.IDLE
        offsetJob?.cancel()

        if (abs(accumulatedDragX) > dismissThreshold) {
            onDismissStarted()
            val targetDismissOffset = if (accumulatedDragX < 0) -screenWidthPx else screenWidthPx
            val finalRotation = if (accumulatedDragX < 0) -14f else 14f
            // Decelerate easing: fast flick, smooth exit
            val exitEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
            offsetJob = scope.launch {
                launch {
                    // Card flies off-screen — fast start, glides to final position
                    offsetAnimatable.animateTo(
                        targetValue = targetDismissOffset,
                        animationSpec = tween(durationMillis = 280, easing = exitEasing)
                    )
                }
                launch {
                    // Rotation increases as card exits, like a tossed card spinning slightly
                    dismissRotation.animateTo(
                        targetValue = finalRotation,
                        animationSpec = tween(durationMillis = 280, easing = exitEasing)
                    )
                }
                launch {
                    // Fade to fully transparent as card exits
                    dismissAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 220, easing = exitEasing)
                    )
                }
                launch {
                    // Scale down slightly as card exits
                    dismissScale.animateTo(
                        targetValue = 0.88f,
                        animationSpec = tween(durationMillis = 280, easing = exitEasing)
                    )
                }

                // Wait for longest animation then commit and reset
                onDismissPlaylistAndShowUndo()
                offsetAnimatable.snapTo(0f)
                dismissRotation.snapTo(0f)
                dismissAlpha.snapTo(1f)
                dismissScale.snapTo(1f)
            }
        } else {
            // Not enough drag — spring back cleanly, no bounce
            offsetJob = scope.launch {
                launch {
                    offsetAnimatable.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                launch {
                    // Return rotation to zero with a matching spring
                    dismissRotation.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                launch {
                    dismissAlpha.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 180)
                    )
                }
                launch {
                    dismissScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
            }
        }
    }

    fun onDragCancel() {
        dragPhase = MiniDismissDragPhase.IDLE
        accumulatedDragX = 0f
        offsetJob?.cancel()
        offsetJob = scope.launch {
            launch {
                offsetAnimatable.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            launch {
                dismissRotation.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            launch {
                dismissAlpha.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 150))
            }
            launch {
                dismissScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }
}

@Composable
internal fun rememberMiniPlayerDismissGestureHandler(
    scope: CoroutineScope,
    density: Density,
    hapticFeedback: HapticFeedback,
    offsetAnimatable: Animatable<Float, AnimationVector1D>,
    dismissAlpha: Animatable<Float, AnimationVector1D>,
    dismissRotation: Animatable<Float, AnimationVector1D>,
    dismissScale: Animatable<Float, AnimationVector1D>,
    screenWidthPx: Float,
    onDismissPlaylistAndShowUndo: () -> Unit,
    onDismissStarted: () -> Unit
): MiniPlayerDismissGestureHandler {
    val onDismissPlaylistAndShowUndoState = rememberUpdatedState(onDismissPlaylistAndShowUndo)
    val onDismissStartedState = rememberUpdatedState(onDismissStarted)
    return remember(scope, density, hapticFeedback, offsetAnimatable, dismissAlpha, dismissRotation, dismissScale, screenWidthPx) {
        MiniPlayerDismissGestureHandler(
            scope = scope,
            density = density,
            hapticFeedback = hapticFeedback,
            offsetAnimatable = offsetAnimatable,
            dismissAlpha = dismissAlpha,
            dismissRotation = dismissRotation,
            dismissScale = dismissScale,
            screenWidthPx = screenWidthPx,
            onDismissPlaylistAndShowUndo = { onDismissPlaylistAndShowUndoState.value() },
            onDismissStarted = { onDismissStartedState.value() }
        )
    }
}

internal fun Modifier.miniPlayerDismissHorizontalGesture(
    enabled: Boolean,
    handler: MiniPlayerDismissGestureHandler
): Modifier {
    if (!enabled) return this
    return this.pointerInput(enabled, handler) {
        detectHorizontalDragGestures(
            onDragStart = { handler.onDragStart() },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                handler.onHorizontalDrag(dragAmount)
            },
            onDragEnd = { handler.onDragEnd() },
            onDragCancel = { handler.onDragCancel() }
        )
    }
}
