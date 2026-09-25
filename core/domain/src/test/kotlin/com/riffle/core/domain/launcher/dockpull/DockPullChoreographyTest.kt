package com.riffle.core.domain.launcher.dockpull

import com.riffle.core.domain.launcher.dockpull.DockPullTransitionState.Idle
import com.riffle.core.domain.launcher.dockpull.DockPullTransitionState.Settling
import com.riffle.core.domain.launcher.dockpull.DockPullTransitionState.Tracking
import com.riffle.core.domain.launcher.home.DockOrientation
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.ModeDockEdges
import com.riffle.core.domain.launcher.home.ModeSurface
import kotlin.test.Test
import kotlin.test.assertEquals

class DockPullChoreographyTest {
    private val edges = ModeDockEdges(home = DockPosition.LEFT, library = DockPosition.BOTTOM)

    private fun tracking(progress: Float): Tracking =
        Tracking(
            from = ModeSurface.HOME,
            to = ModeSurface.LIBRARY,
            direction = DockPullDirection.RIGHT,
            travelDp = 100f,
            dragXDp = progress * 100f,
        )

    private fun settling(
        outcome: SettleOutcome,
        releaseProgress: Float,
        progress: Float,
    ): Settling =
        Settling(
            from = ModeSurface.HOME,
            to = ModeSurface.LIBRARY,
            direction = DockPullDirection.RIGHT,
            travelDp = 100f,
            outcome = outcome,
            releaseProgress = releaseProgress,
            progress = progress,
        )

    @Test
    fun theDockBackgroundIsOpaqueAtRest() {
        assertEquals(1f, Idle(ModeSurface.HOME).dockBackgroundAlpha)
    }

    @Test
    fun theDockBackgroundFadesInProportionToThePull() {
        assertEquals(1f, dockBackgroundAlphaForPull(0f))
        assertEquals(0.75f, dockBackgroundAlphaForPull(0.25f), TOLERANCE)
        assertEquals(0f, dockBackgroundAlphaForPull(1f))
        assertEquals(0.4f, tracking(0.6f).dockBackgroundAlpha, TOLERANCE)
    }

    @Test
    fun pullAlphaIsClampedOutsideTheProgressRange() {
        assertEquals(1f, dockBackgroundAlphaForPull(-0.5f))
        assertEquals(0f, dockBackgroundAlphaForPull(1.5f))
    }

    @Test
    fun aCommittingSettleFadesTheBackgroundBackIn() {
        assertEquals(0.4f, settling(SettleOutcome.COMMIT, 0.6f, 0.6f).dockBackgroundAlpha, TOLERANCE)
        assertEquals(0.7f, settling(SettleOutcome.COMMIT, 0.6f, 0.8f).dockBackgroundAlpha, TOLERANCE)
        assertEquals(1f, settling(SettleOutcome.COMMIT, 0.6f, 1f).dockBackgroundAlpha, TOLERANCE)
    }

    @Test
    fun aCancellingSettleFadesTheBackgroundBackIn() {
        assertEquals(0.4f, settling(SettleOutcome.CANCEL, 0.6f, 0.6f).dockBackgroundAlpha, TOLERANCE)
        assertEquals(0.7f, settling(SettleOutcome.CANCEL, 0.6f, 0.3f).dockBackgroundAlpha, TOLERANCE)
        assertEquals(1f, settling(SettleOutcome.CANCEL, 0.6f, 0f).dockBackgroundAlpha, TOLERANCE)
    }

    @Test
    fun settleFractionMeasuresTheWayToTheTarget() {
        assertEquals(0.5f, settling(SettleOutcome.COMMIT, 0.6f, 0.8f).settleFraction, TOLERANCE)
        assertEquals(0.5f, settling(SettleOutcome.CANCEL, 0.6f, 0.3f).settleFraction, TOLERANCE)
        // Released exactly on the target: already settled.
        assertEquals(1f, settling(SettleOutcome.COMMIT, 1f, 1f).settleFraction)
        assertEquals(1f, settling(SettleOutcome.CANCEL, 0f, 0f).settleFraction)
    }

    @Test
    fun theDockHeadsForTheSurfaceTheStateEndsOn() {
        assertEquals(DockPosition.LEFT, Idle(ModeSurface.HOME).targetDockEdge(edges))
        assertEquals(DockPosition.BOTTOM, Idle(ModeSurface.LIBRARY).targetDockEdge(edges))
        assertEquals(DockPosition.BOTTOM, tracking(0.3f).targetDockEdge(edges))
        assertEquals(DockPosition.BOTTOM, settling(SettleOutcome.COMMIT, 0.6f, 0.7f).targetDockEdge(edges))
        assertEquals(DockPosition.LEFT, settling(SettleOutcome.CANCEL, 0.3f, 0.1f).targetDockEdge(edges))
    }

    @Test
    fun theIconsTakeTheTargetEdgesOrientation() {
        assertEquals(DockOrientation.VERTICAL, Idle(ModeSurface.HOME).targetDockOrientation(edges))
        assertEquals(DockOrientation.HORIZONTAL, tracking(0.3f).targetDockOrientation(edges))
        assertEquals(
            DockOrientation.VERTICAL,
            settling(SettleOutcome.CANCEL, 0.3f, 0.1f).targetDockOrientation(edges),
        )
    }

    @Test
    fun theSameEdgeForBothSurfacesKeepsTheDockWhereItIs() {
        val shared = ModeDockEdges(home = DockPosition.BOTTOM, library = DockPosition.BOTTOM)

        assertEquals(DockPosition.BOTTOM, tracking(0.5f).targetDockEdge(shared))
        assertEquals(DockPosition.BOTTOM, Idle(ModeSurface.HOME).targetDockEdge(shared))
    }

    @Test
    fun idleProgressIsZero() {
        assertEquals(0f, Idle(ModeSurface.LIBRARY).progress)
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
