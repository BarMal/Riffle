package com.riffle.core.domain.launcher.cards

import kotlin.test.Test
import kotlin.test.assertEquals

class TimeScapePaneLayoutPolicyTest {
    private val policy = TimeScapePaneLayoutPolicy()

    @Test
    fun compactWindowKeepsTheSinglePaneSurface() {
        assertEquals(TimeScapePaneMode.COMPACT, policy.layoutFor(TimeScapeWindowLayout(500, 900)).mode)
    }

    @Test
    fun expandedWindowShowsRailWithoutStretchingTheSpline() {
        val layout = policy.layoutFor(TimeScapeWindowLayout(800, 900))

        assertEquals(TimeScapePaneMode.TWO_PANE, layout.mode)
        assertEquals(104, layout.railWidthDp)
        assertEquals(560, layout.splineWidthDp)
    }

    @Test
    fun desktopWindowShowsSupportingDetailPane() {
        val layout = policy.layoutFor(TimeScapeWindowLayout(1_300, 900))

        assertEquals(TimeScapePaneMode.THREE_PANE, layout.mode)
        assertEquals(360, layout.detailWidthDp)
        assertEquals(560, layout.splineWidthDp)
    }

    @Test
    fun separatingHingeIsReservedFromPaneWidth() {
        val layout =
            policy.layoutFor(
                TimeScapeWindowLayout(
                    widthDp = 1_050,
                    heightDp = 800,
                    separatingHinges = listOf(TimeScapeHingeBounds(510, 0, 540, 800)),
                ),
            )

        assertEquals(TimeScapePaneMode.THREE_PANE, layout.mode)
        assertEquals(30, layout.hingeGapDp)
        assertEquals(510, layout.leadingRegionWidthDp)
        assertEquals(510, layout.railWidthDp + layout.splineWidthDp)
        assertEquals(510, layout.trailingRegionWidthDp)
    }

    @Test
    fun safeInsetsShiftTheVerticalHingeRegionsWithoutChangingThePhysicalGap() {
        val layout =
            policy.layoutFor(
                TimeScapeWindowLayout(
                    widthDp = 1_100,
                    heightDp = 800,
                    safeStartDp = 20,
                    safeEndDp = 30,
                    separatingHinges = listOf(TimeScapeHingeBounds(520, 0, 550, 800)),
                ),
            )

        assertEquals(500, layout.leadingRegionWidthDp)
        assertEquals(520, layout.trailingRegionWidthDp)
        assertEquals(30, layout.hingeGapDp)
        assertEquals(500, layout.railWidthDp + layout.splineWidthDp)
    }

    @Test
    fun horizontalHingeUsesTheLargerSafeRegionWithoutCrossingTheFold() {
        val layout =
            policy.layoutFor(
                TimeScapeWindowLayout(
                    widthDp = 800,
                    heightDp = 1_000,
                    safeTopDp = 24,
                    safeBottomDp = 16,
                    separatingHinges = listOf(TimeScapeHingeBounds(0, 380, 800, 420)),
                ),
            )

        assertEquals(TimeScapePaneMode.TWO_PANE, layout.mode)
        assertEquals(396, layout.contentTopDp)
        assertEquals(564, layout.contentHeightDp)
        assertEquals(0, layout.hingeGapDp)
    }

    @Test
    fun narrowVerticalFoldFallsBackInsideTheLargerRegion() {
        val layout =
            policy.layoutFor(
                TimeScapeWindowLayout(
                    widthDp = 720,
                    heightDp = 900,
                    safeStartDp = 12,
                    safeEndDp = 8,
                    separatingHinges = listOf(TimeScapeHingeBounds(300, 0, 324, 900)),
                ),
            )

        assertEquals(TimeScapePaneMode.COMPACT, layout.mode)
        assertEquals(312, layout.contentStartDp)
        assertEquals(388, layout.contentWidthDp)
    }
}
