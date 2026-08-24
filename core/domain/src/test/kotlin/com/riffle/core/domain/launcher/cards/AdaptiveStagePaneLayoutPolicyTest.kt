package com.riffle.core.domain.launcher.cards

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AdaptiveStagePaneLayoutPolicyTest {
    private val policy = AdaptiveStagePaneLayoutPolicy()

    @Test
    fun compactWindowKeepsTheSinglePaneSurface() {
        assertEquals(AdaptiveStagePaneMode.COMPACT, policy.layoutFor(AdaptiveStageWindowLayout(500, 900)).mode)
    }

    @Test
    fun foldedLargeWindowRemainsCompact() {
        val layout =
            policy.layoutFor(
                AdaptiveStageWindowLayout(1_200, 900, posture = AdaptiveStagePosture.PARTIALLY_FOLDED),
            )

        assertEquals(AdaptiveStagePaneMode.COMPACT, layout.mode)
    }

    @Test
    fun unfoldedWindowCanUseStageManager() {
        val layout =
            policy.layoutFor(
                AdaptiveStageWindowLayout(1_200, 900, posture = AdaptiveStagePosture.UNFOLDED),
            )

        assertEquals(AdaptiveStagePaneMode.THREE_PANE, layout.mode)
    }

    @Test
    fun unknownWideWindowRemainsCompactUntilPostureIsConfirmed() {
        val layout = policy.layoutFor(AdaptiveStageWindowLayout(1_200, 900))

        assertEquals(AdaptiveStagePaneMode.COMPACT, layout.mode)
    }

    @Test
    fun foldedPostureKeepsCompactSurfaceOnOneSideOfVerticalHinge() {
        val layout =
            policy.layoutFor(
                AdaptiveStageWindowLayout(
                    widthDp = 1_000,
                    heightDp = 800,
                    separatingHinges = listOf(AdaptiveStageHingeBounds(480, 0, 520, 800)),
                    posture = AdaptiveStagePosture.COMPACT,
                ),
            )

        assertEquals(AdaptiveStagePaneMode.COMPACT, layout.mode)
        assertEquals(480, layout.contentWidthDp)
        assertEquals(0, layout.contentStartDp)
        assertEquals(0, layout.hingeGapDp)
    }

    @Test
    fun expandedWindowDoesNotStretchTheStackToFillIt() {
        val layout =
            policy.layoutFor(
                AdaptiveStageWindowLayout(800, 900, posture = AdaptiveStagePosture.UNFOLDED),
            )

        assertEquals(AdaptiveStagePaneMode.TWO_PANE, layout.mode)
        assertEquals(720, layout.stackWidthDp)
    }

    @Test
    fun desktopWindowShowsSupportingDetailPane() {
        val layout =
            policy.layoutFor(
                AdaptiveStageWindowLayout(1_300, 900, posture = AdaptiveStagePosture.UNFOLDED),
            )

        assertEquals(AdaptiveStagePaneMode.THREE_PANE, layout.mode)
        assertEquals(360, layout.detailWidthDp)
        assertEquals(720, layout.stackWidthDp)
        assertEquals(0, layout.leadingRemainderDp)
        assertEquals(1_080, layout.stackWidthDp + layout.detailWidthDp)
        assertEquals(1_300, layout.contentWidthDp)
    }

    @Test
    fun separatingHingeIsReservedFromPaneWidth() {
        val layout =
            policy.layoutFor(
                AdaptiveStageWindowLayout(
                    widthDp = 1_050,
                    heightDp = 800,
                    separatingHinges = listOf(AdaptiveStageHingeBounds(510, 0, 540, 800)),
                    posture = AdaptiveStagePosture.UNFOLDED,
                ),
            )

        assertEquals(AdaptiveStagePaneMode.THREE_PANE, layout.mode)
        assertEquals(30, layout.hingeGapDp)
        assertEquals(510, layout.leadingRegionWidthDp)
        assertEquals(510, layout.stackWidthDp)
        assertEquals(510, layout.trailingRegionWidthDp)
    }

    @Test
    fun safeInsetsShiftTheVerticalHingeRegionsWithoutChangingThePhysicalGap() {
        val layout =
            policy.layoutFor(
                AdaptiveStageWindowLayout(
                    widthDp = 1_100,
                    heightDp = 800,
                    safeStartDp = 20,
                    safeEndDp = 30,
                    separatingHinges = listOf(AdaptiveStageHingeBounds(520, 0, 550, 800)),
                    posture = AdaptiveStagePosture.UNFOLDED,
                ),
            )

        assertEquals(500, layout.leadingRegionWidthDp)
        assertEquals(520, layout.trailingRegionWidthDp)
        assertEquals(30, layout.hingeGapDp)
        assertEquals(500, layout.stackWidthDp)
    }

    @Test
    fun minimumThreePaneHingeRegionsKeepBothPanesInsideTheContentWidth() {
        val layout =
            policy.layoutFor(
                AdaptiveStageWindowLayout(
                    widthDp = 872,
                    heightDp = 800,
                    safeStartDp = 8,
                    safeEndDp = 8,
                    separatingHinges = listOf(AdaptiveStageHingeBounds(472, 0, 504, 800)),
                    posture = AdaptiveStagePosture.UNFOLDED,
                ),
            )

        assertEquals(AdaptiveStagePaneMode.THREE_PANE, layout.mode)
        assertEquals(464, layout.stackWidthDp)
        assertEquals(360, layout.detailWidthDp)
        // Fits rather than fills. The panes are capped (MAX_STACK_WIDTH_DP, DETAIL_WIDTH_DP), so a
        // wide enough region has width left over -- which used to be exactly what the rail took.
        assertTrue(
            layout.stackWidthDp + layout.leadingRemainderDp + layout.hingeGapDp + layout.detailWidthDp <=
                layout.contentWidthDp,
            "expected the panes to fit $layout",
        )
    }

    @Test
    fun anUnfoldedWindowGivesItsWholeHeightToTheContent() {
        // The horizontal rail used to reserve a strip of this; nothing does now.
        val layout =
            policy.layoutFor(
                AdaptiveStageWindowLayout(800, 900, posture = AdaptiveStagePosture.UNFOLDED),
            )

        assertEquals(AdaptiveStagePaneMode.TWO_PANE, layout.mode)
        assertEquals(900, layout.contentHeightDp)
        assertEquals(0, layout.contentTopDp)
    }

    @Test
    fun horizontalHingeUsesTheLargerSafeRegionWithoutCrossingTheFold() {
        val layout =
            policy.layoutFor(
                AdaptiveStageWindowLayout(
                    widthDp = 800,
                    heightDp = 1_000,
                    safeTopDp = 24,
                    safeBottomDp = 16,
                    separatingHinges = listOf(AdaptiveStageHingeBounds(0, 380, 800, 420)),
                    posture = AdaptiveStagePosture.UNFOLDED,
                ),
            )

        assertEquals(AdaptiveStagePaneMode.TWO_PANE, layout.mode)
        assertEquals(396, layout.contentTopDp)
        assertEquals(564, layout.contentHeightDp)
        assertEquals(0, layout.hingeGapDp)
    }

    @Test
    fun narrowVerticalFoldFallsBackInsideTheLargerRegion() {
        val layout =
            policy.layoutFor(
                AdaptiveStageWindowLayout(
                    widthDp = 720,
                    heightDp = 900,
                    safeStartDp = 12,
                    safeEndDp = 8,
                    separatingHinges = listOf(AdaptiveStageHingeBounds(300, 0, 324, 900)),
                ),
            )

        assertEquals(AdaptiveStagePaneMode.COMPACT, layout.mode)
        assertEquals(312, layout.contentStartDp)
        assertEquals(388, layout.contentWidthDp)
    }

    @Test
    fun stackArrangementNeverProducesSplitRegardlessOfWindowSize() {
        val sizes =
            listOf(
                AdaptiveStageWindowLayout(360, 780),
                AdaptiveStageWindowLayout(500, 900),
                AdaptiveStageWindowLayout(800, 900, posture = AdaptiveStagePosture.UNFOLDED),
                AdaptiveStageWindowLayout(1_300, 900, posture = AdaptiveStagePosture.UNFOLDED),
            )

        sizes.forEach { window ->
            val layout = policy.layoutFor(window, arrangement = AdaptiveStagePaneArrangement.STACK)
            assertNotEquals(AdaptiveStagePaneMode.SPLIT, layout.mode)
        }

        // The default arrangement parameter must behave identically to an explicit STACK.
        sizes.forEach { window ->
            assertNotEquals(AdaptiveStagePaneMode.SPLIT, policy.layoutFor(window).mode)
        }
    }

    @Test
    fun splitArrangementPromotesAWorkablyTallCompactWindowToSplit() {
        val layout =
            policy.layoutFor(
                AdaptiveStageWindowLayout(360, 780),
                arrangement = AdaptiveStagePaneArrangement.SPLIT,
            )

        assertEquals(AdaptiveStagePaneMode.SPLIT, layout.mode)
        assertEquals(layout.contentHeightDp, layout.upperRegionHeightDp + layout.lowerRegionHeightDp)
        assertEquals((layout.contentHeightDp * 0.6f).roundToInt(), layout.upperRegionHeightDp)
    }

    @Test
    fun splitArrangementFallsBackToCompactOnAnUnworkablyShortWindow() {
        val layout =
            policy.layoutFor(
                AdaptiveStageWindowLayout(360, 300),
                arrangement = AdaptiveStagePaneArrangement.SPLIT,
            )

        assertEquals(AdaptiveStagePaneMode.COMPACT, layout.mode)
        assertEquals(0, layout.upperRegionHeightDp)
        assertEquals(0, layout.lowerRegionHeightDp)
    }

    @Test
    fun splitArrangementDoesNotPromoteAWideNonCompactWindow() {
        val layout =
            policy.layoutFor(
                AdaptiveStageWindowLayout(1_300, 900, posture = AdaptiveStagePosture.UNFOLDED),
                arrangement = AdaptiveStagePaneArrangement.SPLIT,
            )

        assertEquals(AdaptiveStagePaneMode.THREE_PANE, layout.mode)
    }
}
