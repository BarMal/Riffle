package com.riffle.core.domain.launcher.gestures

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GestureThresholdsTest {
    @Test
    fun theReferenceDensityKeepsTheHistoricalPixelFeel() {
        val reference = GestureThresholdsPx.Reference

        assertEquals(80f, reference.homeSwipePx, TOLERANCE_PX)
        assertEquals(80f, reference.dockShelfTogglePx, TOLERANCE_PX)
        assertEquals(24f, reference.dockShelfClaimPx, TOLERANCE_PX)
    }

    @Test
    fun thresholdsScaleWithDensity() {
        val phone = GestureThresholdsPx.resolve(density = 2f)
        val tablet = GestureThresholdsPx.resolve(density = 4f)

        assertEquals(phone.homeSwipePx * 2f, tablet.homeSwipePx)
        assertEquals(phone.dockShelfClaimPx * 2f, tablet.dockShelfClaimPx)
    }

    @Test
    fun theShelfClaimNeverDropsBelowThePlatformTouchSlop() {
        val thresholds = GestureThresholdsPx.resolve(density = 1f, touchSlopPx = 20f)

        assertEquals(20f, thresholds.dockShelfClaimPx)
    }

    @Test
    fun theShelfClaimsBeforeTheHomeLayerCanCommit() {
        assertTrue(GestureThresholds.DOCK_SHELF_CLAIM_DP < GestureThresholds.HOME_SWIPE_DP)
        assertTrue(GestureThresholds.DOCK_SHELF_CLAIM_DP < GestureThresholds.DOCK_SHELF_TOGGLE_DP)
    }

    @Test
    fun rejectsANonPositiveDensity() {
        assertFailsWith<IllegalArgumentException> { GestureThresholdsPx.resolve(density = 0f) }
    }

    private companion object {
        const val TOLERANCE_PX = 0.5f
    }
}
