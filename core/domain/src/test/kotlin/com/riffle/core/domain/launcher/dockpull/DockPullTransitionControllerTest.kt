package com.riffle.core.domain.launcher.dockpull

import com.riffle.core.domain.launcher.dockpull.DockPullTransitionState.Idle
import com.riffle.core.domain.launcher.dockpull.DockPullTransitionState.Settling
import com.riffle.core.domain.launcher.dockpull.DockPullTransitionState.Tracking
import com.riffle.core.domain.launcher.home.ModeSurface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DockPullTransitionControllerTest {
    private val controller = DockPullTransitionController()
    private val home = Idle(ModeSurface.HOME)

    /** A fresh upward pull (bottom dock) over [TRAVEL_DP]. */
    private fun pullUp(from: DockPullTransitionState = home): DockPullTransitionState =
        controller.start(from, DockPullDirection.UP, TRAVEL_DP)

    /** Drags [dp] toward the interior of a bottom dock (screen y decreases). */
    private fun DockPullTransitionState.up(dp: Float): DockPullTransitionState = controller.drag(this, 0f, -dp)

    @Test
    fun aPullFromHomeTracksTowardLibrary() {
        val tracking = assertIs<Tracking>(pullUp())

        assertEquals(ModeSurface.HOME, tracking.from)
        assertEquals(ModeSurface.LIBRARY, tracking.to)
        assertEquals(DockPullDirection.UP, tracking.direction)
        assertEquals(0f, tracking.progress)
    }

    @Test
    fun aPullFromLibraryTracksTowardHome() {
        val started = controller.start(Idle(ModeSurface.LIBRARY), DockPullDirection.RIGHT, TRAVEL_DP)
        val tracking = assertIs<Tracking>(started)

        assertEquals(ModeSurface.LIBRARY, tracking.from)
        assertEquals(ModeSurface.HOME, tracking.to)
    }

    @Test
    fun progressTracksTheFingerOneToOne() {
        assertEquals(0.25f, pullUp().up(50f).progress, TOLERANCE)
        assertEquals(0.75f, pullUp().up(50f).up(100f).progress, TOLERANCE)
    }

    @Test
    fun crosswiseMovementDoesNotMoveProgress() {
        val state = controller.drag(pullUp().up(40f), 300f, 0f)

        assertEquals(0.2f, state.progress, TOLERANCE)
    }

    @Test
    fun draggingIntoTheEdgeHoldsProgressAtZero() {
        assertEquals(0f, pullUp().up(-60f).progress)
    }

    @Test
    fun progressStopsAtOne() {
        assertEquals(1f, pullUp().up(TRAVEL_DP * 3).progress)
    }

    @Test
    fun aPullCanBeTakenBackMidGesture() {
        // Out past the start into the edge, then back: progress follows the net drag, not a ratchet.
        assertEquals(0.3f, pullUp().up(100f).up(-40f).progress, TOLERANCE)
        assertEquals(0.1f, pullUp().up(-40f).up(60f).progress, TOLERANCE)
    }

    @Test
    fun releasingPastTheDistanceThresholdCommits() {
        val settling = assertIs<Settling>(controller.release(pullUp().up(TRAVEL_DP * 0.5f)))

        assertEquals(SettleOutcome.COMMIT, settling.outcome)
        assertEquals(0.5f, settling.releaseProgress, TOLERANCE)
        assertEquals(ModeSurface.LIBRARY, settling.endSurface)
    }

    @Test
    fun releasingShortOfTheDistanceThresholdCancels() {
        val settling = assertIs<Settling>(controller.release(pullUp().up(TRAVEL_DP * 0.2f)))

        assertEquals(SettleOutcome.CANCEL, settling.outcome)
        assertEquals(ModeSurface.HOME, settling.endSurface)
    }

    @Test
    fun aFlingTowardTheInteriorCommitsShortOfTheDistance() {
        val settling = assertIs<Settling>(controller.release(pullUp().up(20f), velocityYDpPerSecond = -900f))

        assertEquals(SettleOutcome.COMMIT, settling.outcome)
    }

    @Test
    fun aFlingBackTowardTheEdgeCancelsPastTheDistance() {
        val settling = assertIs<Settling>(controller.release(pullUp().up(150f), velocityYDpPerSecond = 900f))

        assertEquals(SettleOutcome.CANCEL, settling.outcome)
    }

    @Test
    fun aSlowReleaseIsDecidedByDistance() {
        val settling = assertIs<Settling>(controller.release(pullUp().up(20f), velocityYDpPerSecond = -300f))

        assertEquals(SettleOutcome.CANCEL, settling.outcome)
    }

    @Test
    fun aTapWithAFastFlickNeverSwitches() {
        val settling = assertIs<Settling>(controller.release(pullUp().up(3f), velocityYDpPerSecond = -5_000f))

        assertEquals(SettleOutcome.CANCEL, settling.outcome)
    }

    @Test
    fun crosswiseVelocityIsNotAFling() {
        val settling =
            assertIs<Settling>(
                controller.release(pullUp().up(20f), velocityXDpPerSecond = 5_000f, velocityYDpPerSecond = 0f),
            )

        assertEquals(SettleOutcome.CANCEL, settling.outcome)
    }

    @Test
    fun sideDocksCommitAlongTheirOwnAxis() {
        val rightDockPull = controller.start(home, DockPullDirection.LEFT, TRAVEL_DP)
        val pulled = controller.drag(rightDockPull, -30f, 0f)

        val settling = assertIs<Settling>(controller.release(pulled, velocityXDpPerSecond = -1_000f))

        assertEquals(SettleOutcome.COMMIT, settling.outcome)
    }

    @Test
    fun aCancelledGestureSpringsBack() {
        val settling = assertIs<Settling>(controller.cancel(pullUp().up(180f)))

        assertEquals(SettleOutcome.CANCEL, settling.outcome)
        assertEquals(0f, settling.targetProgress)
    }

    @Test
    fun aCommittedSettleFinishesOnTheDestination() {
        val settling = controller.release(pullUp().up(150f))
        val animated = controller.settleProgress(settling, 1f)

        assertEquals(Idle(ModeSurface.LIBRARY), controller.finish(animated))
    }

    @Test
    fun aCancelledSettleFinishesWhereItStarted() {
        val settling = controller.release(pullUp().up(10f))

        assertEquals(Idle(ModeSurface.HOME), controller.finish(settling))
    }

    @Test
    fun settleProgressIsClamped() {
        val settling = controller.release(pullUp().up(150f))

        assertEquals(1f, controller.settleProgress(settling, 1.4f).progress)
        assertEquals(0f, controller.settleProgress(settling, -0.2f).progress)
    }

    @Test
    fun aPullDuringASettleResumesFromTheCurrentProgress() {
        val committing = controller.settleProgress(controller.release(pullUp().up(150f)), 0.9f)

        // The new pull's own direction and travel are ignored: the dock is still mid-flight on the
        // original pull's axis.
        val resumed = assertIs<Tracking>(controller.start(committing, DockPullDirection.RIGHT, 999f))

        assertEquals(ModeSurface.HOME, resumed.from)
        assertEquals(ModeSurface.LIBRARY, resumed.to)
        assertEquals(DockPullDirection.UP, resumed.direction)
        assertEquals(TRAVEL_DP, resumed.travelDp)
        assertEquals(0.9f, resumed.progress, TOLERANCE)
    }

    @Test
    fun anInterruptedCommitCanBeDraggedBackAndCancelled() {
        val committing = controller.settleProgress(controller.release(pullUp().up(150f)), 0.9f)

        val draggedBack = controller.start(committing, DockPullDirection.UP, TRAVEL_DP).up(-160f)
        assertEquals(0.1f, draggedBack.progress, TOLERANCE)

        val settling = assertIs<Settling>(controller.release(draggedBack))
        assertEquals(SettleOutcome.CANCEL, settling.outcome)
        assertEquals(Idle(ModeSurface.HOME), controller.finish(settling))
    }

    @Test
    fun anInterruptedCancelCanBePushedOnToCommit() {
        val cancelling = controller.settleProgress(controller.release(pullUp().up(60f)), 0.2f)

        val pushedOn = controller.start(cancelling, DockPullDirection.UP, TRAVEL_DP).up(40f)
        val settling = assertIs<Settling>(controller.release(pushedOn, velocityYDpPerSecond = -800f))

        assertEquals(SettleOutcome.COMMIT, settling.outcome)
        assertEquals(Idle(ModeSurface.LIBRARY), controller.finish(settling))
    }

    @Test
    fun catchingASettleAndLettingGoIsDecidedByWhereItWasCaught() {
        val committing = controller.settleProgress(controller.release(pullUp().up(150f)), 0.8f)
        val caught = controller.start(committing, DockPullDirection.UP, TRAVEL_DP)

        val settling = assertIs<Settling>(controller.release(caught, velocityYDpPerSecond = 5_000f))

        // No movement, so the (backward) velocity is not a fling; 0.8 is past the threshold.
        assertEquals(SettleOutcome.COMMIT, settling.outcome)
    }

    @Test
    fun eventsThatMeanNothingInTheCurrentStateAreIgnored() {
        assertSame(home, controller.drag(home, 0f, -50f))
        assertSame(home, controller.release(home, 0f, -5_000f))
        assertSame(home, controller.cancel(home))
        assertSame(home, controller.settleProgress(home, 0.5f))
        assertSame(home, controller.finish(home))

        val tracking = pullUp().up(30f)
        assertSame(tracking, controller.start(tracking, DockPullDirection.DOWN, 10f))
        assertSame(tracking, controller.settleProgress(tracking, 0.9f))
        assertSame(tracking, controller.finish(tracking))

        val settling = controller.release(tracking)
        assertSame(settling, controller.drag(settling, 0f, -50f))
        assertSame(settling, controller.release(settling))
        assertSame(settling, controller.cancel(settling))
    }

    @Test
    fun reducedMotionIsCarriedThroughTheWholeTransition() {
        val tracking = controller.start(home, DockPullDirection.UP, TRAVEL_DP, reducedMotion = true).up(150f)
        val settling = controller.settleProgress(controller.release(tracking), 1f)
        val idle = controller.finish(settling)

        assertTrue(tracking.reducedMotion)
        assertTrue(settling.reducedMotion)
        assertEquals(Idle(ModeSurface.LIBRARY, reducedMotion = true), idle)
    }

    @Test
    fun aPullInheritsTheIdleStatesReducedMotionUnlessToldOtherwise() {
        assertTrue(pullUp(Idle(ModeSurface.HOME, reducedMotion = true)).reducedMotion)
        assertEquals(
            false,
            controller.start(Idle(ModeSurface.HOME, reducedMotion = true), DockPullDirection.UP, TRAVEL_DP, false)
                .reducedMotion,
        )
    }

    @Test
    fun reducedMotionCanChangeInAnyState() {
        val states = listOf(home, pullUp().up(10f), controller.release(pullUp().up(10f)))

        states.forEach { state ->
            val changed = controller.withReducedMotion(state, reducedMotion = true)
            assertTrue(changed.reducedMotion)
            assertEquals(state.progress, changed.progress)
            assertEquals(state::class, changed::class)
        }
    }

    @Test
    fun thresholdsAreValidated() {
        assertFailsWith<IllegalArgumentException> { DockPullThresholds(commitFraction = 0f) }
        assertFailsWith<IllegalArgumentException> { DockPullThresholds(commitFraction = 1f) }
        assertFailsWith<IllegalArgumentException> { DockPullThresholds(commitVelocityDpPerSecond = 0f) }
        assertFailsWith<IllegalArgumentException> { DockPullThresholds(minimumFlingDistanceDp = -1f) }
        assertFailsWith<IllegalArgumentException> { controller.start(home, DockPullDirection.UP, 0f) }
    }

    @Test
    fun customThresholdsApply() {
        val eager =
            DockPullTransitionController(
                DockPullThresholds(
                    commitFraction = 0.1f,
                    commitVelocityDpPerSecond = 100f,
                    minimumFlingDistanceDp = 0f,
                ),
            )
        val pulled = eager.drag(eager.start(home, DockPullDirection.UP, TRAVEL_DP), 0f, -25f)

        assertEquals(SettleOutcome.COMMIT, (eager.release(pulled) as Settling).outcome)
    }

    @Test
    fun outcomeRuleCoversEachBranch() {
        val thresholds = DockPullThresholds()

        assertEquals(SettleOutcome.COMMIT, thresholds.outcomeFor(0.05f, 10f, 700f))
        assertEquals(SettleOutcome.CANCEL, thresholds.outcomeFor(0.95f, -10f, -700f))
        assertEquals(SettleOutcome.COMMIT, thresholds.outcomeFor(0.3f, 60f, 0f))
        assertEquals(SettleOutcome.CANCEL, thresholds.outcomeFor(0.29f, 58f, 599f))
        assertEquals(SettleOutcome.CANCEL, thresholds.outcomeFor(0.02f, 4f, 10_000f))
    }

    private companion object {
        const val TRAVEL_DP = 200f
        const val TOLERANCE = 0.0001f
    }
}
