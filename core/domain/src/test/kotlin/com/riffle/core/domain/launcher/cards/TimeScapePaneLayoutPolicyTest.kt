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
}
