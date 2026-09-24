package com.riffle.core.domain.launcher.gestures

/**
 * Every launcher gesture threshold, in density-independent pixels.
 *
 * Riffle used to hard-code these as raw pixels (80px to commit a home swipe, 24px for the dock shelf
 * to claim a drag), which meant the same physical swipe committed at very different finger travel
 * on a 2x phone and a 3.5x tablet. The dp values below keep today's feel at the reference density
 * the pixel values were tuned on ([REFERENCE_DENSITY], a 420dpi phone) and scale from there.
 *
 * See docs/product/gestures.md for which region owns which gesture.
 */
object GestureThresholds {
    /** The density the historical pixel thresholds were tuned against (420dpi = 2.625x). */
    const val REFERENCE_DENSITY: Float = 2.625f

    /** Travel along the dominant axis that commits a 1/2/3-finger home swipe (was 80px). */
    const val HOME_SWIPE_DP: Float = 30.5f

    /** Travel away from the dock edge that expands/collapses the shelf (was 80px). */
    const val DOCK_SHELF_TOGGLE_DP: Float = 30.5f

    /**
     * Travel away from the dock edge after which the shelf claims (consumes) the drag so the home
     * gesture layer cannot also act on it (was 24px). Must stay below [HOME_SWIPE_DP].
     */
    const val DOCK_SHELF_CLAIM_DP: Float = 9f

    /** Upward travel, past the platform touch slop, that fires the dock swipe-up action (was 80px). */
    const val DOCK_SWIPE_UP_DP: Float = 30.5f

    /**
     * Pointer count at which the home gesture layer claims the touch outright, ahead of any child
     * (card stack, pager): a three-finger swipe is a mode gesture wherever it starts.
     */
    const val MULTI_FINGER_CLAIM_POINTER_COUNT: Int = 3
}

/**
 * [GestureThresholds] resolved to pixels for one display.
 *
 * @param touchSlopPx the platform touch slop (`ViewConfiguration.touchSlop`), already in pixels. The
 * dock shelf's claim threshold never drops below it, so the shelf never claims a drag the platform
 * would still treat as a tap.
 */
data class GestureThresholdsPx(
    val homeSwipePx: Float,
    val dockShelfTogglePx: Float,
    val dockShelfClaimPx: Float,
    val dockSwipeUpPx: Float,
) {
    init {
        require(homeSwipePx > 0f && dockShelfTogglePx > 0f && dockShelfClaimPx > 0f && dockSwipeUpPx > 0f) {
            "Gesture thresholds must be positive."
        }
    }

    companion object {
        fun resolve(
            density: Float,
            touchSlopPx: Float = 0f,
        ): GestureThresholdsPx {
            require(density > 0f) { "Density must be positive." }
            return GestureThresholdsPx(
                homeSwipePx = GestureThresholds.HOME_SWIPE_DP * density,
                dockShelfTogglePx = GestureThresholds.DOCK_SHELF_TOGGLE_DP * density,
                dockShelfClaimPx = maxOf(GestureThresholds.DOCK_SHELF_CLAIM_DP * density, touchSlopPx),
                dockSwipeUpPx = GestureThresholds.DOCK_SWIPE_UP_DP * density,
            )
        }

        /** The thresholds at [GestureThresholds.REFERENCE_DENSITY] -- the historical pixel values. */
        val Reference: GestureThresholdsPx = resolve(GestureThresholds.REFERENCE_DENSITY)
    }
}
