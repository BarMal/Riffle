package com.riffle.core.domain.launcher.gestures

import kotlin.math.abs
import kotlin.math.sign

/** What the home gesture layer should do with one pointer event. */
enum class HomeGestureDisposition {
    /** Every pointer has lifted: the gesture is over. */
    END,

    /** Keep tracking and fire an action once a threshold is crossed. */
    TRACK,

    /**
     * A child (card stack, pager, dock shelf) consumed a pointer, so it owns this touch. Keep
     * watching only in case the touch grows into a multi-finger mode gesture; never fire otherwise.
     */
    YIELDED,
}

/**
 * Decides, pointer event by pointer event, whether the home gesture layer or a child owns a touch.
 *
 * Rules (docs/product/gestures.md, "Resolution rules"):
 * 1. A child that consumes a pointer first owns single- and two-finger touches: the home layer
 *    yields for the rest of that touch.
 * 2. As soon as [claimPointerCount] fingers are down, the home layer claims the touch -- it
 *    consumes the pointers ahead of its children (Initial pass), which cancels a card-stack or
 *    pager drag already in progress -- and may fire even though it had yielded, because a
 *    three-finger swipe is a mode gesture wherever it starts.
 * 3. Once claimed, pointers the home layer consumed itself never count as a child's claim.
 *
 * One instance per touch (one `awaitEachGesture` iteration).
 */
class HomeGestureArbiter(
    private val claimPointerCount: Int = GestureThresholds.MULTI_FINGER_CLAIM_POINTER_COUNT,
) {
    init {
        require(claimPointerCount >= 2) { "A claim needs more than one finger." }
    }

    var claimed: Boolean = false
        private set

    private var yielded: Boolean = false

    /** Whether a recognised gesture may fire now. */
    val mayFire: Boolean
        get() = claimed || !yielded

    /**
     * Called on the Initial pass, before any child sees the event. Returns true when the home
     * layer should consume every change now, claiming the touch from its children.
     */
    fun shouldClaim(pressedPointerCount: Int): Boolean {
        if (pressedPointerCount >= claimPointerCount) claimed = true
        return claimed && pressedPointerCount > 0
    }

    /** Called on the Final pass, after children have had their chance to consume. */
    fun dispositionFor(
        activePointerCount: Int,
        hasConsumedActivePointer: Boolean,
    ): HomeGestureDisposition {
        if (activePointerCount == 0) return HomeGestureDisposition.END
        if (!claimed && hasConsumedActivePointer) yielded = true
        return if (mayFire) HomeGestureDisposition.TRACK else HomeGestureDisposition.YIELDED
    }
}

/**
 * Turns a child scroller's *unconsumed* drag (nested-scroll `available`) into a home swipe.
 *
 * A card stack clamped at its first/last card leaves the drag unconsumed and the nested-scroll
 * system hands the remainder to its ancestors. The home layer cannot see that drag through the
 * pointer stream (the stack's `Modifier.scrollable` still consumes the pointer changes), so it
 * listens here instead: once the leftover travel crosses the home swipe threshold, the touch
 * counts as a one-finger swipe in that direction -- e.g. swipe up past the last card opens the app
 * drawer when that is the configured one-finger-up action.
 *
 * `Modifier.scrollable` withholds the platform touch slop before it reports any drag; when the
 * child consumed nothing at all (the drag started against the boundary), that slop is credited
 * back so the hand-off commits at the same finger travel as the same swipe anywhere else.
 *
 * One instance per home surface; [reset] at the end of every drag (nested-scroll pre-fling).
 */
class OverscrollHandOffTracker {
    private var overscrollPx = 0f
    private var childConsumedAnything = false
    private var fired = false

    /**
     * Records one nested-scroll step along the tracked axis and returns the signed drag distance
     * to interpret as a home swipe the first time the threshold is crossed, or null.
     */
    fun onPostScroll(
        consumedPx: Float,
        availablePx: Float,
        thresholdPx: Float,
        touchSlopPx: Float = 0f,
    ): Float? {
        if (consumedPx != 0f) childConsumedAnything = true
        overscrollPx += availablePx
        val credit = if (childConsumedAnything) 0f else touchSlopPx * overscrollPx.sign
        val effective = overscrollPx + credit
        val crosses = !fired && overscrollPx != 0f && abs(effective) >= thresholdPx
        if (crosses) fired = true
        return effective.takeIf { crosses }
    }

    fun reset() {
        overscrollPx = 0f
        childConsumedAnything = false
        fired = false
    }
}
