package com.riffle.app.launcher.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import com.riffle.core.domain.launcher.designsystem.RiffleEasingTokens
import com.riffle.core.domain.launcher.designsystem.RiffleMotionTokens
import com.riffle.core.domain.launcher.designsystem.RiffleSpringTokens

/**
 * The Riffle motion vocabulary: three springs, two tweens, one easing family.
 *
 * Every helper takes the launcher's `reducedMotion` setting (threaded through composables as a
 * plain Boolean) and returns [snap] when it is on, so reduced motion reaches the same end state
 * without travel. Call sites that deliberately keep a short fade under reduced motion should branch
 * themselves and use [RiffleMotionTokens.DURATION_REDUCED_MOTION_MILLIS].
 */
object RiffleMotion {
    /** Small, direct changes: handles, toggles, reorder nudges. dampingRatio 1.0, stiffness 1500. */
    fun <T> snappy(
        reducedMotion: Boolean = false,
        visibilityThreshold: T? = null,
    ): FiniteAnimationSpec<T> = springOrSnap(RiffleMotionTokens.Snappy, reducedMotion, visibilityThreshold)

    /** Page and panel settles, container resizes. dampingRatio 1.0, stiffness 400. */
    fun <T> smooth(
        reducedMotion: Boolean = false,
        visibilityThreshold: T? = null,
    ): FiniteAnimationSpec<T> = springOrSnap(RiffleMotionTokens.Smooth, reducedMotion, visibilityThreshold)

    /** Large, expressive travel such as a card coming to rest. dampingRatio 0.8, stiffness 200. */
    fun <T> gentle(
        reducedMotion: Boolean = false,
        visibilityThreshold: T? = null,
    ): FiniteAnimationSpec<T> = springOrSnap(RiffleMotionTokens.Gentle, reducedMotion, visibilityThreshold)

    /** Raw spring for APIs that need a [SpringSpec]; reduced motion is the caller's responsibility. */
    fun <T> springSpec(
        tokens: RiffleSpringTokens,
        visibilityThreshold: T? = null,
    ): SpringSpec<T> =
        spring(
            dampingRatio = tokens.dampingRatio,
            stiffness = tokens.stiffness,
            visibilityThreshold = visibilityThreshold,
        )

    val StandardEasing: Easing = RiffleMotionTokens.StandardEasing.toEasing()
    val EmphasizedDecelerateEasing: Easing = RiffleMotionTokens.EmphasizedDecelerateEasing.toEasing()
    val EmphasizedAccelerateEasing: Easing = RiffleMotionTokens.EmphasizedAccelerateEasing.toEasing()

    /** Non-physical transitions (fades, colour): 300 ms, standard easing. */
    fun <T> standard(reducedMotion: Boolean = false): FiniteAnimationSpec<T> =
        if (reducedMotion) {
            snap()
        } else {
            tween(durationMillis = RiffleMotionTokens.DURATION_STANDARD_MILLIS, easing = StandardEasing)
        }

    /** Entering, attention-worthy transitions: 500 ms, emphasized decelerate. */
    fun <T> emphasized(reducedMotion: Boolean = false): FiniteAnimationSpec<T> =
        if (reducedMotion) {
            snap()
        } else {
            tween(
                durationMillis = RiffleMotionTokens.DURATION_EMPHASIZED_MILLIS,
                easing = EmphasizedDecelerateEasing,
            )
        }

    private fun <T> springOrSnap(
        tokens: RiffleSpringTokens,
        reducedMotion: Boolean,
        visibilityThreshold: T?,
    ): FiniteAnimationSpec<T> = if (reducedMotion) snap() else springSpec(tokens, visibilityThreshold)
}

private fun RiffleEasingTokens.toEasing(): Easing = CubicBezierEasing(x1, y1, x2, y2)
