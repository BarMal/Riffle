package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.DockPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shelf comes out of the edge the dock is on, so the gesture that opens it is a pull away from
 * that edge -- upward from a bottom dock, sideways from a side one.
 */
class DockShelfGestureEdgeTest {
    @Test
    fun aSideDockOpensOnAPullAwayFromItsEdge() {
        assertEquals(
            true,
            expandedState(isExpanded = false, x = PAST_THRESHOLD, y = 10f, position = DockPosition.LEADING),
        )
        assertEquals(
            true,
            expandedState(isExpanded = false, x = -PAST_THRESHOLD, y = 10f, position = DockPosition.TRAILING),
        )
    }

    @Test
    fun aSideDockClosesOnAPushBackTowardItsEdge() {
        assertEquals(
            false,
            expandedState(isExpanded = true, x = -PAST_THRESHOLD, y = 10f, position = DockPosition.LEADING),
        )
        assertEquals(
            false,
            expandedState(isExpanded = true, x = PAST_THRESHOLD, y = 10f, position = DockPosition.TRAILING),
        )
    }

    @Test
    fun aSwipeUpMeansNothingToASideDock() {
        // It is along the dock's run, not away from its edge, so it is not the shelf's gesture.
        assertEquals(
            null,
            expandedState(isExpanded = false, x = 0f, y = -PAST_THRESHOLD, position = DockPosition.LEADING),
        )
    }

    @Test
    fun aSideDocksOpeningPullFollowsTheLayoutDirection() {
        // The leading edge is the right one in RTL, so the pull that opens it points the other way.
        assertEquals(
            true,
            dockShelfGestureExpandedState(
                isExpanded = false,
                horizontalDragPx = -PAST_THRESHOLD,
                verticalDragPx = 0f,
                position = DockPosition.LEADING,
                isRtl = true,
            ),
        )
        assertEquals(
            null,
            dockShelfGestureExpandedState(
                isExpanded = false,
                horizontalDragPx = PAST_THRESHOLD,
                verticalDragPx = 0f,
                position = DockPosition.LEADING,
                isRtl = true,
            ),
        )
    }

    @Test
    fun aBottomDockKeepsTheSwipeItAlwaysHad() {
        assertEquals(
            true,
            expandedState(isExpanded = false, x = 10f, y = -PAST_THRESHOLD, position = DockPosition.BOTTOM),
        )
        assertEquals(
            false,
            expandedState(isExpanded = true, x = 10f, y = PAST_THRESHOLD, position = DockPosition.BOTTOM),
        )
        assertEquals(
            null,
            expandedState(isExpanded = false, x = PAST_THRESHOLD, y = 0f, position = DockPosition.BOTTOM),
        )
    }

    @Test
    fun theDragIsClaimedOnlyInTheDirectionTheShelfWouldMove() {
        assertTrue(
            dockShelfGestureClaimsDrag(
                isExpanded = false,
                horizontalDragPx = PAST_CLAIM_THRESHOLD,
                verticalDragPx = 0f,
                position = DockPosition.LEADING,
            ),
        )
        // Pushing into a closed side dock's own edge is not the start of opening it.
        assertFalse(
            dockShelfGestureClaimsDrag(
                isExpanded = false,
                horizontalDragPx = -PAST_CLAIM_THRESHOLD,
                verticalDragPx = 0f,
                position = DockPosition.LEADING,
            ),
        )
    }

    private fun expandedState(
        isExpanded: Boolean,
        x: Float,
        y: Float,
        position: DockPosition,
    ): Boolean? =
        dockShelfGestureExpandedState(
            isExpanded = isExpanded,
            horizontalDragPx = x,
            verticalDragPx = y,
            position = position,
        )

    private companion object {
        private const val PAST_THRESHOLD = 90f
        private const val PAST_CLAIM_THRESHOLD = 30f
    }
}
