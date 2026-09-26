package com.riffle.core.domain.launcher.dockpull

import com.riffle.core.domain.launcher.dockpull.DockPullTransitionState.Idle
import com.riffle.core.domain.launcher.dockpull.DockPullTransitionState.Settling
import com.riffle.core.domain.launcher.dockpull.DockPullTransitionState.Tracking
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.ModeDockEdges
import com.riffle.core.domain.launcher.home.ModeSurface
import kotlin.test.Test
import kotlin.test.assertEquals

class DockPullFrameTest {
    private val edges = ModeDockEdges(home = DockPosition.LEFT, library = DockPosition.BOTTOM)

    private fun tracking(
        progress: Float,
        reducedMotion: Boolean = false,
    ): Tracking =
        Tracking(
            from = ModeSurface.HOME,
            to = ModeSurface.LIBRARY,
            direction = DockPullDirection.RIGHT,
            travelDp = 100f,
            dragXDp = progress * 100f,
            reducedMotion = reducedMotion,
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
    fun atRestTheDockSitsOnTheSurfacesEdge() {
        val home = Idle(ModeSurface.HOME).frame(edges)
        assertEquals(DockPosition.LEFT, home.dockEdge)
        assertEquals(0f, home.dockOffsetFraction)
        assertEquals(1f, home.dockBackgroundAlpha)
        assertEquals(0f, home.outgoing.offsetFraction)

        assertEquals(DockPosition.BOTTOM, Idle(ModeSurface.LIBRARY).frame(edges).dockEdge)
    }

    @Test
    fun whileTrackingTheDockAndTheOutgoingSurfaceFollowTheFinger() {
        val frame = tracking(0.3f).frame(edges)

        assertEquals(DockPosition.LEFT, frame.dockEdge)
        assertEquals(0.3f, frame.dockOffsetFraction, TOLERANCE)
        assertEquals(0.3f, frame.outgoing.offsetFraction, TOLERANCE)
        // The incoming surface follows from the side the dock came from.
        assertEquals(-0.7f, frame.incoming.offsetFraction, TOLERANCE)
        assertEquals(0.7f, frame.dockBackgroundAlpha, TOLERANCE)
    }

    @Test
    fun aCommittedReleaseMovesTheDockToTheDestinationEdgeAndSettlesItThere() {
        val released = settling(SettleOutcome.COMMIT, releaseProgress = 0.5f, progress = 0.5f).frame(edges)
        assertEquals(DockPosition.BOTTOM, released.dockEdge)
        assertEquals(0.5f, released.dockOffsetFraction, TOLERANCE)

        val halfway = settling(SettleOutcome.COMMIT, releaseProgress = 0.5f, progress = 0.75f).frame(edges)
        assertEquals(0.25f, halfway.dockOffsetFraction, TOLERANCE)
        assertEquals(0.75f, halfway.outgoing.offsetFraction, TOLERANCE)

        val settled = settling(SettleOutcome.COMMIT, releaseProgress = 0.5f, progress = 1f).frame(edges)
        assertEquals(0f, settled.dockOffsetFraction, TOLERANCE)
        assertEquals(1f, settled.dockBackgroundAlpha, TOLERANCE)
        assertEquals(0f, settled.incoming.offsetFraction, TOLERANCE)
    }

    @Test
    fun aFinishedCommitFrameMatchesTheIdleFrameItHandsOffTo() {
        // The two code paths a UI drawing this could read "where the dock sits" from -- the
        // in-flight [Settling] frame and the [Idle] frame the transition controller's `finish` hands
        // off to -- must agree exactly at the handoff instant, or a redraw between them is a visible
        // jump (#1206 dock-transition-consistency). [DockPullTransitionController.finish] only ever
        // runs on a [Settling] whose `progress` has reached `targetProgress` (Animatable.animateTo
        // clamps its last reported value to the target), so that is the frame compared here.
        val finishedCommit = settling(SettleOutcome.COMMIT, releaseProgress = 0.5f, progress = 1f).frame(edges)
        val idleOnDestination = Idle(ModeSurface.LIBRARY).frame(edges)

        // Only what actually positions and shows the dock: [outgoing]/[incoming] describe the two
        // mode surfaces, which a consumer only ever reads while transitioning (guarded on
        // `isTransitioning`), so their values past the handoff are moot rather than required to match.
        assertEquals(idleOnDestination.dockEdge, finishedCommit.dockEdge)
        assertEquals(idleOnDestination.dockOffsetFraction, finishedCommit.dockOffsetFraction, TOLERANCE)
        assertEquals(idleOnDestination.dockBackgroundAlpha, finishedCommit.dockBackgroundAlpha, TOLERANCE)
    }

    @Test
    fun aFinishedCancelFrameMatchesTheIdleFrameItHandsOffTo() {
        val finishedCancel = settling(SettleOutcome.CANCEL, releaseProgress = 0.5f, progress = 0f).frame(edges)
        val idleOnOrigin = Idle(ModeSurface.HOME).frame(edges)

        assertEquals(idleOnOrigin.dockEdge, finishedCancel.dockEdge)
        assertEquals(idleOnOrigin.dockOffsetFraction, finishedCancel.dockOffsetFraction, TOLERANCE)
        assertEquals(idleOnOrigin.dockBackgroundAlpha, finishedCancel.dockBackgroundAlpha, TOLERANCE)
    }

    @Test
    fun aCancelledReleaseSpringsBackOnTheOriginEdge() {
        val frame = settling(SettleOutcome.CANCEL, releaseProgress = 0.2f, progress = 0.1f).frame(edges)

        assertEquals(DockPosition.LEFT, frame.dockEdge)
        assertEquals(0.1f, frame.dockOffsetFraction, TOLERANCE)
    }

    @Test
    fun reducedMotionCrossfadesWithoutTravel() {
        val frame = tracking(0.25f, reducedMotion = true).frame(edges)

        assertEquals(0f, frame.dockOffsetFraction)
        assertEquals(0f, frame.outgoing.offsetFraction)
        assertEquals(0f, frame.incoming.offsetFraction)
        assertEquals(0.75f, frame.outgoing.alpha, TOLERANCE)
        assertEquals(0.25f, frame.incoming.alpha, TOLERANCE)
    }

    @Test
    fun switchNowCommitsFromRestAndLeavesARunningTransitionAlone() {
        val controller = DockPullTransitionController()
        val switched = controller.switchNow(Idle(ModeSurface.LIBRARY), DockPullDirection.UP)

        switched as Settling
        assertEquals(ModeSurface.LIBRARY, switched.from)
        assertEquals(ModeSurface.HOME, switched.endSurface)
        assertEquals(0f, switched.progress)

        val running = tracking(0.4f)
        assertEquals(running, controller.switchNow(running, DockPullDirection.RIGHT))
    }

    private companion object {
        const val TOLERANCE = 0.0001f
    }
}
