package com.unshoo.pixelmusic.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme

/**
 * Material 3 Expressive motion tokens — exact values verified against
 * material3 1.5.0-alpha21 (ExpressiveMotionTokens.kt / StandardMotionTokens.kt).
 *
 * Rule: **spatial** springs (x/y, rotation, size, rounded corners) are under-damped
 * (damping 0.6–0.8) so they may overshoot and bounce into place.
 * **effects** springs (color, opacity) are critically damped (damping 1.0) and never overshoot.
 *
 * Speed mapping per M3:
 *  - fast   → small components (switches, buttons)
 *  - default → partial-screen motion (bottom sheets, expanded nav rail)
 *  - slow   → full-screen motion, hero content
 */
object ExpressiveSprings {
    const val FastSpatialDampingRatio = 0.6f
    const val FastSpatialStiffness = 800f

    const val DefaultSpatialDampingRatio = 0.8f
    const val DefaultSpatialStiffness = 380f

    const val SlowSpatialDampingRatio = 0.8f
    const val SlowSpatialStiffness = 200f

    const val FastEffectsDampingRatio = 1f
    const val FastEffectsStiffness = 3800f

    const val DefaultEffectsDampingRatio = 1f
    const val DefaultEffectsStiffness = 1600f

    const val SlowEffectsDampingRatio = 1f
    const val SlowEffectsStiffness = 800f
}

/**
 * Token-style wrappers so call sites read exactly like the M3 spec:
 * `MaterialTheme.motionScheme.defaultSpatial()`.
 *
 * Because the scheme object is stateless and returned as a singleton, these specs
 * are safe to pass into `remember { }` / `Animatable.animateTo()` — they never
 * change identity across recompositions.
 */
@ExperimentalMaterial3ExpressiveApi
fun <T> MotionScheme.fastSpatial(): FiniteAnimationSpec<T> = fastSpatialSpec()

@ExperimentalMaterial3ExpressiveApi
fun <T> MotionScheme.defaultSpatial(): FiniteAnimationSpec<T> = defaultSpatialSpec()

@ExperimentalMaterial3ExpressiveApi
fun <T> MotionScheme.slowSpatial(): FiniteAnimationSpec<T> = slowSpatialSpec()

@ExperimentalMaterial3ExpressiveApi
fun <T> MotionScheme.fastEffects(): FiniteAnimationSpec<T> = fastEffectsSpec()

@ExperimentalMaterial3ExpressiveApi
fun <T> MotionScheme.defaultEffects(): FiniteAnimationSpec<T> = defaultEffectsSpec()

@ExperimentalMaterial3ExpressiveApi
fun <T> MotionScheme.slowEffects(): FiniteAnimationSpec<T> = slowEffectsSpec()
