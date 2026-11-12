package com.riffle.core.domain.launcher.dockpull

import com.riffle.core.domain.launcher.gestures.GestureThresholds
import kotlin.test.Test
import kotlin.test.assertEquals

class DockPullDragTest {
    private fun drag(
        direction: DockPullDirection = DockPullDirection.UP,
        travel: Float = 800f,
    ) = DockPullDrag(direction, travelDp = travel)

    @Test
    fun progressTracksTheFingerAlongThePullOneToOne() {
        val pulled = drag().dragged(40f, -200f)
        assertEquals(0.25f, pulled.progress, 1e-4f)
        assertEquals(200f, pulled.gestureDistanceDp)
    }

    @Test
    fun movementAcrossOrIntoTheEdgeNeverMovesProgress() {
        assertEquals(0f, drag().dragged(300f, 0f).progress)
        assertEquals(0f, drag().dragged(0f, 300f).progress)
        assertEquals(1f, drag().dragged(0f, -9_000f).progress)
    }

    @Test
    fun everyDockEdgePullsAwayFromItsEdge() {
        assertEquals(0.5f, drag(DockPullDirection.DOWN).dragged(0f, 400f).progress)
        assertEquals(0.5f, drag(DockPullDirection.RIGHT).dragged(400f, 0f).progress)
        assertEquals(0.5f, drag(DockPullDirection.LEFT).dragged(-400f, 0f).progress)
    }

    @Test
    fun aCaughtSettleResumesFromItsProgress() {
        val caught = DockPullDrag(DockPullDirection.UP, 800f, startProgress = 0.6f).dragged(0f, 80f)
        assertEquals(0.5f, caught.progress, 1e-4f)
    }

    @Test
    fun aReleaseCommitsPastTheCommitFractionOfTheTravel() {
        val fraction = GestureThresholds.DOCK_PULL_COMMIT_FRACTION
        val past = drag().dragged(0f, -(fraction * 800f + 2f))
        val short = drag().dragged(0f, -(fraction * 800f - 2f))
        assertEquals(SettleOutcome.COMMIT, past.release(0f, 0f))
        assertEquals(SettleOutcome.CANCEL, short.release(0f, 0f))
    }

    @Test
    fun aFastFlingTowardTheInteriorCommitsAShortPull() {
        val flung = drag().dragged(0f, -30f)
        assertEquals(
            SettleOutcome.COMMIT,
            flung.release(0f, -GestureThresholds.DOCK_PULL_COMMIT_VELOCITY_DP_PER_SECOND - 1f),
        )
    }

    @Test
    fun aFastFlingBackTowardTheEdgeCancelsALongPull() {
        val flungBack = drag().dragged(0f, -500f)
        assertEquals(
            SettleOutcome.CANCEL,
            flungBack.release(0f, GestureThresholds.DOCK_PULL_COMMIT_VELOCITY_DP_PER_SECOND + 1f),
        )
    }

    @Test
    fun aTwitchNeverCountsAsAFling() {
        val twitch = drag().dragged(0f, -2f)
        assertEquals(SettleOutcome.CANCEL, twitch.release(0f, -5_000f))
    }

    @Test
    fun theStreamFrameSlidesInFromTheEdgeAndFadesHomeOut() {
        assertEquals(StreamPullFrame(-1f, 1f, 1f), streamPullFrame(0f, opening = true, reducedMotion = false))
        assertEquals(StreamPullFrame(0f, 1f, 0f), streamPullFrame(1f, opening = true, reducedMotion = false))
        assertEquals(-0.5f, streamPullFrame(0.5f, opening = true, reducedMotion = false).streamOffsetFraction)
    }

    @Test
    fun closingSendsTheStreamOnTowardTheInteriorAndFadesHomeIn() {
        assertEquals(StreamPullFrame(0f, 1f, 0f), streamPullFrame(0f, opening = false, reducedMotion = false))
        assertEquals(StreamPullFrame(1f, 1f, 1f), streamPullFrame(1f, opening = false, reducedMotion = false))
    }

    @Test
    fun reducedMotionCrossfadesWithoutSliding() {
        val half = streamPullFrame(0.25f, opening = true, reducedMotion = true)
        assertEquals(0f, half.streamOffsetFraction)
        assertEquals(0.25f, half.streamAlpha)
        assertEquals(0.75f, half.homeAlpha)
        val closing = streamPullFrame(0.25f, opening = false, reducedMotion = true)
        assertEquals(0.75f, closing.streamAlpha)
    }
}
