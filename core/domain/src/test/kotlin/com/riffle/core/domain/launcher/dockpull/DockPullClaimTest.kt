package com.riffle.core.domain.launcher.dockpull

import kotlin.test.Test
import kotlin.test.assertEquals

class DockPullClaimTest {
    @Test
    fun nothingIsDecidedInsideTouchSlop() {
        assertEquals(DockPullClaim.UNDECIDED, claim(DockPullDirection.UP, 3f, -5f))
    }

    @Test
    fun aDragAlongThePullClaimsIt() {
        assertEquals(DockPullClaim.CLAIM, claim(DockPullDirection.UP, 2f, -10f))
        assertEquals(DockPullClaim.CLAIM, claim(DockPullDirection.RIGHT, 10f, 3f))
        assertEquals(DockPullClaim.CLAIM, claim(DockPullDirection.LEFT, -10f, 3f))
        assertEquals(DockPullClaim.CLAIM, claim(DockPullDirection.DOWN, 1f, 10f))
    }

    @Test
    fun aDragIntoTheEdgeIsNotAPull() {
        assertEquals(DockPullClaim.REJECT, claim(DockPullDirection.UP, 0f, 10f))
    }

    @Test
    fun aDragAlongTheRunIsLeftToTheSectionScroll() {
        // Bottom dock: its run is horizontal.
        assertEquals(DockPullClaim.REJECT, claim(DockPullDirection.UP, 10f, -4f))
        // Left dock: its run is vertical.
        assertEquals(DockPullClaim.REJECT, claim(DockPullDirection.RIGHT, 3f, -10f))
    }

    private fun claim(
        direction: DockPullDirection,
        dx: Float,
        dy: Float,
    ): DockPullClaim = dockPullClaimFor(direction, dx = dx, dy = dy, touchSlopPx = 8f)
}
