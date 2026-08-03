package com.riffle.core.domain.launcher.cards

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TimeScapePaneLayoutPolicyTest {
    private val policy = TimeScapePaneLayoutPolicy()

    @Test
    fun compactWindowKeepsTheSinglePaneSurface() {
        assertEquals(TimeScapePaneMode.COMPACT, policy.layoutFor(TimeScapeWindowLayout(500, 900)).mode)
    }

    @Test
    fun foldedLargeWindowRemainsCompact() {
        val layout =
            policy.layoutFor(
                TimeScapeWindowLayout(1_200, 900, posture = TimeScapePosture.PARTIALLY_FOLDED),
            )

        assertEquals(TimeScapePaneMode.COMPACT, layout.mode)
    }

    @Test
    fun unfoldedWindowCanUseStageManager() {
        val layout =
            policy.layoutFor(
                TimeScapeWindowLayout(1_200, 900, posture = TimeScapePosture.UNFOLDED),
            )

        assertEquals(TimeScapePaneMode.THREE_PANE, layout.mode)
    }

    @Test
    fun unknownWideWindowRemainsCompactUntilPostureIsConfirmed() {
        val layout = policy.layoutFor(TimeScapeWindowLayout(1_200, 900))

        assertEquals(TimeScapePaneMode.COMPACT, layout.mode)
    }

    @Test
    fun foldedPostureKeepsCompactSurfaceOnOneSideOfVerticalHinge() {
        val layout =
            policy.layoutFor(
                TimeScapeWindowLayout(
                    widthDp = 1_000,
                    heightDp = 800,
                    separatingHinges = listOf(TimeScapeHingeBounds(480, 0, 520, 800)),
                    posture = TimeScapePosture.COMPACT,
                ),
            )

        assertEquals(TimeScapePaneMode.COMPACT, layout.mode)
        assertEquals(480, layout.contentWidthDp)
        assertEquals(0, layout.contentStartDp)
        assertEquals(0, layout.hingeGapDp)
    }

    @Test
    fun expandedWindowShowsRailWithoutStretchingTheSpline() {
        val layout =
            policy.layoutFor(
                TimeScapeWindowLayout(800, 900, posture = TimeScapePosture.UNFOLDED),
            )

        assertEquals(TimeScapePaneMode.TWO_PANE, layout.mode)
        assertEquals(104, layout.railWidthDp)
        assertEquals(560, layout.splineWidthDp)
    }

    @Test
    fun desktopWindowShowsSupportingDetailPane() {
        val layout =
            policy.layoutFor(
                TimeScapeWindowLayout(1_300, 900, posture = TimeScapePosture.UNFOLDED),
            )

        assertEquals(TimeScapePaneMode.THREE_PANE, layout.mode)
        assertEquals(360, layout.detailWidthDp)
        assertEquals(560, layout.splineWidthDp)
        assertEquals(0, layout.leadingRemainderDp)
        assertEquals(1_024, layout.railWidthDp + layout.splineWidthDp + layout.detailWidthDp)
        assertEquals(1_300, layout.contentWidthDp)
    }

    @Test
    fun separatingHingeIsReservedFromPaneWidth() {
        val layout =
            policy.layoutFor(
                TimeScapeWindowLayout(
                    widthDp = 1_050,
                    heightDp = 800,
                    separatingHinges = listOf(TimeScapeHingeBounds(510, 0, 540, 800)),
                    posture = TimeScapePosture.UNFOLDED,
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
                    posture = TimeScapePosture.UNFOLDED,
                ),
            )

        assertEquals(500, layout.leadingRegionWidthDp)
        assertEquals(520, layout.trailingRegionWidthDp)
        assertEquals(30, layout.hingeGapDp)
        assertEquals(500, layout.railWidthDp + layout.splineWidthDp)
    }

    @Test
    fun trailingRailIsReservedInsideMinimumThreePaneHingeRegions() {
        val layout =
            policy.layoutFor(
                window =
                    TimeScapeWindowLayout(
                        widthDp = 872,
                        heightDp = 800,
                        safeStartDp = 8,
                        safeEndDp = 8,
                        separatingHinges = listOf(TimeScapeHingeBounds(368, 0, 400, 800)),
                        posture = TimeScapePosture.UNFOLDED,
                    ),
                railSide = TimeScapeRailSide.TRAILING,
            )

        assertEquals(TimeScapePaneMode.THREE_PANE, layout.mode)
        assertEquals(360, layout.splineWidthDp)
        assertEquals(360, layout.detailWidthDp)
        assertEquals(464, layout.trailingRegionWidthDp)
        assertEquals(
            layout.contentWidthDp,
            layout.splineWidthDp +
                layout.leadingRemainderDp +
                layout.hingeGapDp +
                layout.detailWidthDp +
                layout.railWidthDp,
        )
    }

    @Test
    fun leadingRailRemainsReservedInsideMinimumThreePaneHingeRegions() {
        val layout =
            policy.layoutFor(
                window =
                    TimeScapeWindowLayout(
                        widthDp = 872,
                        heightDp = 800,
                        safeStartDp = 8,
                        safeEndDp = 8,
                        separatingHinges = listOf(TimeScapeHingeBounds(472, 0, 504, 800)),
                        posture = TimeScapePosture.UNFOLDED,
                    ),
                railSide = TimeScapeRailSide.LEADING,
            )

        assertEquals(TimeScapePaneMode.THREE_PANE, layout.mode)
        assertEquals(104, layout.railWidthDp)
        assertEquals(360, layout.splineWidthDp)
        assertEquals(360, layout.detailWidthDp)
        assertEquals(
            layout.contentWidthDp,
            layout.railWidthDp +
                layout.splineWidthDp +
                layout.leadingRemainderDp +
                layout.hingeGapDp +
                layout.detailWidthDp,
        )
    }

    @Test
    fun topRailReservesHeightInsteadOfWidth() {
        val layout =
            policy.layoutFor(
                TimeScapeWindowLayout(800, 900, posture = TimeScapePosture.UNFOLDED),
                railSide = TimeScapeRailSide.TOP,
            )

        assertEquals(TimeScapePaneMode.TWO_PANE, layout.mode)
        assertEquals(0, layout.railWidthDp)
        assertEquals(96, layout.railHeightDp)
        assertEquals(560, layout.splineWidthDp)
        assertEquals(804, layout.contentHeightDp)
        assertEquals(96, layout.contentTopDp)
    }

    @Test
    fun bottomRailReservesHeightWithoutShiftingContentDown() {
        val layout =
            policy.layoutFor(
                TimeScapeWindowLayout(1_300, 900, posture = TimeScapePosture.UNFOLDED),
                railSide = TimeScapeRailSide.BOTTOM,
            )

        assertEquals(TimeScapePaneMode.THREE_PANE, layout.mode)
        assertEquals(0, layout.railWidthDp)
        assertEquals(96, layout.railHeightDp)
        assertEquals(360, layout.detailWidthDp)
        assertEquals(804, layout.contentHeightDp)
        assertEquals(0, layout.contentTopDp)
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
                    posture = TimeScapePosture.UNFOLDED,
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

    @Test
    fun stackArrangementNeverProducesSplitRegardlessOfWindowSize() {
        val sizes =
            listOf(
                TimeScapeWindowLayout(360, 780),
                TimeScapeWindowLayout(500, 900),
                TimeScapeWindowLayout(800, 900, posture = TimeScapePosture.UNFOLDED),
                TimeScapeWindowLayout(1_300, 900, posture = TimeScapePosture.UNFOLDED),
            )

        sizes.forEach { window ->
            val layout = policy.layoutFor(window, arrangement = TimeScapePaneArrangement.STACK)
            assertNotEquals(TimeScapePaneMode.SPLIT, layout.mode)
        }

        // The default arrangement parameter must behave identically to an explicit STACK.
        sizes.forEach { window ->
            assertNotEquals(TimeScapePaneMode.SPLIT, policy.layoutFor(window).mode)
        }
    }

    @Test
    fun splitArrangementPromotesAWorkablyTallCompactWindowToSplit() {
        val layout =
            policy.layoutFor(
                TimeScapeWindowLayout(360, 780),
                arrangement = TimeScapePaneArrangement.SPLIT,
            )

        assertEquals(TimeScapePaneMode.SPLIT, layout.mode)
        assertEquals(layout.contentHeightDp, layout.upperRegionHeightDp + layout.lowerRegionHeightDp)
        assertEquals((layout.contentHeightDp * 0.6f).roundToInt(), layout.upperRegionHeightDp)
    }

    @Test
    fun splitArrangementFallsBackToCompactOnAnUnworkablyShortWindow() {
        val layout =
            policy.layoutFor(
                TimeScapeWindowLayout(360, 300),
                arrangement = TimeScapePaneArrangement.SPLIT,
            )

        assertEquals(TimeScapePaneMode.COMPACT, layout.mode)
        assertEquals(0, layout.upperRegionHeightDp)
        assertEquals(0, layout.lowerRegionHeightDp)
    }

    @Test
    fun splitArrangementDoesNotPromoteAWideNonCompactWindow() {
        val layout =
            policy.layoutFor(
                TimeScapeWindowLayout(1_300, 900, posture = TimeScapePosture.UNFOLDED),
                arrangement = TimeScapePaneArrangement.SPLIT,
            )

        assertEquals(TimeScapePaneMode.THREE_PANE, layout.mode)
    }
}
