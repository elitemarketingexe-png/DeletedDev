package com.unshoo.pixelmusic.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.MutatorMutex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal class SheetMotionController(
    private val translationY: Animatable<Float, AnimationVector1D>,
    private val expansionFraction: Animatable<Float, AnimationVector1D>,
    private val mutex: MutatorMutex,
    private val expandAnimationSpec: AnimationSpec<Float>,
    private val collapseAnimationSpec: AnimationSpec<Float> = expandAnimationSpec,
    private val expandedY: Float = 0f
) {
    /**
     * Animates the sheet to the target state. The correct easing curve is chosen
     * automatically (expand vs collapse), so callers never need to pass a spec.
     *
     * [animationSpec] can be provided as an override for gesture-driven flings
     * where [initialVelocity] must be matched to a tween duration.
     */
    suspend fun animateTo(
        targetExpanded: Boolean,
        canExpand: Boolean,
        collapsedY: Float,
        animationSpec: AnimationSpec<Float>? = null,
        initialVelocity: Float = 0f
    ) {
        val targetFraction = if (canExpand && targetExpanded) 1f else 0f
        val targetY = if (targetExpanded) expandedY else collapsedY
        val velocityScale = (collapsedY - expandedY).coerceAtLeast(1f)

        // Already at target and not running — nothing to do.
        if (
            translationY.value == targetY &&
            expansionFraction.value == targetFraction &&
            !translationY.isRunning &&
            !expansionFraction.isRunning
        ) {
            return
        }

        // Pick the right curve for expand vs collapse unless the caller overrides.
        val spec = animationSpec ?: if (targetExpanded) expandAnimationSpec else collapseAnimationSpec

        mutex.mutate {
            coroutineScope {
                launch {
                    translationY.animateTo(
                        targetValue = targetY,
                        initialVelocity = initialVelocity,
                        animationSpec = spec
                    )
                }
                launch {
                    expansionFraction.animateTo(
                        targetValue = targetFraction,
                        initialVelocity = initialVelocity / velocityScale,
                        animationSpec = spec
                    )
                }
            }
        }
    }

    suspend fun stop() {
        translationY.stop()
        expansionFraction.stop()
    }

    suspend fun snapTo(translationYValue: Float, expansionFractionValue: Float) {
        mutex.mutate {
            translationY.snapTo(translationYValue)
            expansionFraction.snapTo(expansionFractionValue)
        }
    }

    suspend fun snapToDirect(translationYValue: Float, expansionFractionValue: Float) {
        translationY.snapTo(translationYValue)
        expansionFraction.snapTo(expansionFractionValue)
    }

    suspend fun snapCollapsed(collapsedY: Float) {
        snapTo(
            translationYValue = collapsedY,
            expansionFractionValue = 0f
        )
    }

    suspend fun syncToExpansion(collapsedY: Float) {
        val adjustedY = collapsedY + (expandedY - collapsedY) * expansionFraction.value
        if (translationY.value == adjustedY && !translationY.isRunning) return
        mutex.mutate {
            translationY.snapTo(adjustedY)
        }
    }
}
