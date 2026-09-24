package com.riffle.core.domain.launcher.designsystem

/**
 * Framework-free values for the Riffle design language (docs/product/design-language.md).
 *
 * All sizes are density-independent pixels (dp) expressed as plain numbers so the scales can be
 * unit tested without Compose; the app module wraps them in `Dp` / `AnimationSpec` types.
 */
object RiffleSpacingScale {
    const val XXS = 2
    const val XS = 4
    const val S = 8
    const val M = 12
    const val L = 16
    const val XL = 24
    const val XXL = 32
    const val XXXL = 48

    /** Ascending, so a layout can snap an arbitrary measurement to the nearest step. */
    val steps: List<Int> = listOf(XXS, XS, S, M, L, XL, XXL, XXXL)
}

/** One radius scale for every rounded surface. `full` (a pill) is expressed as 50% in the app layer. */
object RiffleRadiusScale {
    const val NONE = 0
    const val S = 8
    const val M = 16
    const val L = 24
    const val XL = 32

    val steps: List<Int> = listOf(S, M, L, XL)
}

/** Four elevation levels (dp), shared by tonal elevation and the single soft shadow. */
object RiffleElevationScale {
    const val LEVEL_0 = 0
    const val LEVEL_1 = 1
    const val LEVEL_2 = 3
    const val LEVEL_3 = 6
    const val LEVEL_4 = 12

    val levels: List<Int> = listOf(LEVEL_1, LEVEL_2, LEVEL_3, LEVEL_4)
}

/**
 * Spring parameters with the same meaning as Compose's `spring(dampingRatio, stiffness)`:
 * a damping ratio of 1 is critically damped (no overshoot); lower values overshoot.
 */
data class RiffleSpringTokens(
    val dampingRatio: Float,
    val stiffness: Float,
) {
    init {
        require(dampingRatio > 0f) { "dampingRatio must be positive" }
        require(stiffness > 0f) { "stiffness must be positive" }
    }
}

/** Cubic-bezier control points, identical in meaning to CSS `cubic-bezier(x1, y1, x2, y2)`. */
data class RiffleEasingTokens(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
)

/**
 * Riffle has exactly three springs. Pick by the distance and weight of the thing that moves,
 * never by taste per call site.
 */
object RiffleMotionTokens {
    /** Small, direct state changes: handles, toggles, reorder nudges. Same as Compose's default spring. */
    val Snappy = RiffleSpringTokens(dampingRatio = 1f, stiffness = 1_500f)

    /** Page and panel settles, container size changes. Critically damped, unhurried. */
    val Smooth = RiffleSpringTokens(dampingRatio = 1f, stiffness = 400f)

    /** Large, expressive travel such as a card coming to rest. A hint of overshoot, no wobble. */
    val Gentle = RiffleSpringTokens(dampingRatio = 0.8f, stiffness = 200f)

    val springs: List<RiffleSpringTokens> = listOf(Snappy, Smooth, Gentle)

    const val DURATION_SHORT_MILLIS = 150
    const val DURATION_STANDARD_MILLIS = 300
    const val DURATION_EMPHASIZED_MILLIS = 500

    /** Short crossfade used in place of travel when reduced motion is on and a snap would be jarring. */
    const val DURATION_REDUCED_MOTION_MILLIS = 80

    val StandardEasing = RiffleEasingTokens(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerateEasing = RiffleEasingTokens(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerateEasing = RiffleEasingTokens(0.3f, 0f, 0.8f, 0.15f)
}
