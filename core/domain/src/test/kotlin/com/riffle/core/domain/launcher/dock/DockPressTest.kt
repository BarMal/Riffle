package com.riffle.core.domain.launcher.dock

import com.riffle.core.domain.launcher.dockpull.DockPullClaim
import com.riffle.core.domain.launcher.dockpull.DockPullDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockPressTest {
    @Test
    fun pressingTowardTheEdgeCountsAndPullingAwayDoesNot() {
        // A bottom dock is pulled up (UP), so it is pressed down.
        assertEquals(20f, DockPress.travelDp(DockPullDirection.UP, 3f, 20f))
        assertEquals(0f, DockPress.travelDp(DockPullDirection.UP, 3f, -20f))
        // A left dock is pulled right, so it is pressed left.
        assertEquals(15f, DockPress.travelDp(DockPullDirection.RIGHT, -15f, 4f))
        assertEquals(15f, DockPress.travelDp(DockPullDirection.DOWN, 0f, -15f))
        assertEquals(15f, DockPress.travelDp(DockPullDirection.LEFT, 15f, 0f))
    }

    @Test
    fun aReleaseCommitsOnlyOnceThePressPassesTheThreshold() {
        assertFalse(DockPress.commits(0f))
        assertFalse(DockPress.commits(DockPress.COMMIT_DP - 0.1f))
        assertTrue(DockPress.commits(DockPress.COMMIT_DP))
        assertTrue(DockPress.commits(DockPress.COMMIT_DP * 3))
    }

    @Test
    fun progressRunsFromZeroToOneAndStaysThere() {
        assertEquals(0f, DockPress.progress(0f))
        assertEquals(0.5f, DockPress.progress(DockPress.COMMIT_DP / 2))
        assertEquals(1f, DockPress.progress(DockPress.COMMIT_DP))
        assertEquals(1f, DockPress.progress(DockPress.COMMIT_DP * 4))
    }

    @Test
    fun theThresholdIsShortAndBelowAHomeSwipe() {
        assertTrue(DockPress.COMMIT_DP in 16f..40f)
    }

    private fun claim(
        pull: DockPullDirection,
        dx: Float,
        dy: Float,
    ) = DockPress.claimFor(pull, dx, dy, touchSlopPx = 8f)

    @Test
    fun nothingIsDecidedInsideTouchSlop() {
        assertEquals(DockPullClaim.UNDECIDED, claim(DockPullDirection.UP, 2f, 5f))
    }

    @Test
    fun aDragTowardTheEdgeClaimsThePress() {
        assertEquals(DockPullClaim.CLAIM, claim(DockPullDirection.UP, 2f, 10f))
        assertEquals(DockPullClaim.CLAIM, claim(DockPullDirection.DOWN, 2f, -10f))
        assertEquals(DockPullClaim.CLAIM, claim(DockPullDirection.RIGHT, -10f, 3f))
        assertEquals(DockPullClaim.CLAIM, claim(DockPullDirection.LEFT, 10f, 3f))
    }

    @Test
    fun aPullAwayFromTheEdgeIsNotAPress() {
        assertEquals(DockPullClaim.REJECT, claim(DockPullDirection.UP, 0f, -10f))
    }

    @Test
    fun aDragAlongTheRunIsLeftToTheScroll() {
        assertEquals(DockPullClaim.REJECT, claim(DockPullDirection.UP, 10f, 4f))
        assertEquals(DockPullClaim.REJECT, claim(DockPullDirection.RIGHT, -3f, 10f))
    }

    @Test
    fun theDominantAxisAtTheMomentSlopIsCrossedDecidesDiagonalsToo() {
        // A thumb's diagonal: mostly down with some drift along the run is a press (not the row's scroll)...
        assertEquals(DockPullClaim.CLAIM, claim(DockPullDirection.UP, 7f, 9f))
        assertEquals(DockPullClaim.CLAIM, claim(DockPullDirection.UP, -7f, 9f))
        assertEquals(DockPullClaim.CLAIM, claim(DockPullDirection.UP, 20f, 21f))
        // ...mostly along the run is the scroll's, even with some downward component, and an exact tie is not a press.
        assertEquals(DockPullClaim.REJECT, claim(DockPullDirection.UP, 9f, 7f))
        assertEquals(DockPullClaim.REJECT, claim(DockPullDirection.UP, 8f, 8f))
        // A drag up (away from the edge) is never a press, however vertical.
        assertEquals(DockPullClaim.REJECT, claim(DockPullDirection.UP, 3f, -12f))
        // 35 dp down with 18 sideways is a press.
        assertEquals(DockPullClaim.CLAIM, claim(DockPullDirection.UP, 18f, 35f))
    }
}
