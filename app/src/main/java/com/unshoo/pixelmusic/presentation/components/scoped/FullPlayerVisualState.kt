package com.unshoo.pixelmusic.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.util.lerp

@Stable
internal class FullPlayerVisualState(
    private val expansionFraction: Animatable<Float, AnimationVector1D>,
    private val initialOffsetY: Float
) {
    /**
     * Maps expansion fraction to content alpha using a wider, faster window:
     *   - Fade-in begins at fraction=0.20 (was 0.18)
     *   - Alpha reaches 1.0 at fraction=0.88 (was 1.00)
     *
     * On COLLAPSE: the full player background clears at 88% instead of 100%,
     * eliminating the white/colored gradient that lingered until the sheet
     * fully settled. On EXPAND: content appears marginally earlier for a snappier feel.
     */
    val contentAlpha: Float
        get() {
            val f = expansionFraction.value
            return ((f - 0.20f) / 0.68f).coerceIn(0f, 1f)
        }

    /**
     * Squared alpha gives an acceleration curve: translationY starts slowly
     * and arrives fast, matching Material 3 Emphasized Decelerate motion.
     */
    val translationY: Float
        get() {
            val a = contentAlpha
            return lerp(initialOffsetY, 0f, a * a)
        }

    /**
     * Deeper scale range (0.88→1.0 vs old 0.92→1.0) for a more immersive
     * depth effect without being distracting.
     */
    val contentScale: Float
        get() = lerp(0.88f, 1f, contentAlpha)
}

@Composable
internal fun rememberFullPlayerVisualState(
    expansionFraction: Animatable<Float, AnimationVector1D>,
    initialOffsetY: Float
): FullPlayerVisualState {
    return remember(expansionFraction, initialOffsetY) {
        FullPlayerVisualState(expansionFraction, initialOffsetY)
    }
}
