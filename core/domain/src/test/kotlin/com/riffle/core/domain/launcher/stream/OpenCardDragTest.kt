package com.riffle.core.domain.launcher.stream

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenCardDragTest {
    private val threshold = OpenCardHandoff.THRESHOLD_DP
    private val eps = 0.001f

    @Test
    fun theThresholdIsADeliberateDrag() {
        assertEquals(56f, threshold)
    }

    @Test
    fun belowTheThresholdTheStackDoesNotMoveAtAll() {
        val handoff = OpenCardHandoff()
        var moved = 0f
        repeat(10) { moved += handoff.leftover(5f) }
        // 50 dp of overscroll: nothing moved, and the finger is still out past the edge.
        assertEquals(0f, moved, eps)
        assertTrue(handoff.active)
        assertEquals(50f, handoff.accumulated, eps)
    }

    @Test
    fun pastTheThresholdTheStackFollowsByWhatExceedsIt() {
        val handoff = OpenCardHandoff()
        var moved = 0f
        repeat(20) { moved += handoff.leftover(10f) }
        // 200 dp of overscroll: the stack has followed the 144 dp beyond the first 56.
        assertEquals(200f - threshold, moved, eps)
        assertEquals(moved, handoff.moved, eps)
    }

    @Test
    fun theStackFollowsUpwardsTooWithTheSign() {
        val handoff = OpenCardHandoff()
        var moved = 0f
        repeat(10) { moved += handoff.leftover(-10f) }
        assertEquals(-(100f - threshold), moved, eps)
    }

    @Test
    fun aFingerThatComesBackGivesTheStackBackBeforeTheBodyScrolls() {
        val handoff = OpenCardHandoff()
        repeat(20) { handoff.leftover(10f) }
        // Back up by 30 dp: all of it is taken from the overscroll, and the stack gives that way.
        val back = handoff.retract(-30f)
        assertEquals(-30f, back.consumedDp, eps)
        assertEquals(-30f, back.stackDp, eps)
        // Back further than the overscroll: it is all undone, the stack is home and the rest is the body's.
        val more = handoff.retract(-200f)
        assertEquals(-(200f - 30f), more.consumedDp, eps)
        assertEquals(0f, handoff.accumulated, eps)
        assertEquals(0f, handoff.moved, eps)
        assertFalse(handoff.active)
    }

    @Test
    fun aFingerGoingTheSameWayTakesNothingBackAndNothingAccumulatedTakesNothing() {
        val handoff = OpenCardHandoff()
        assertEquals(OpenCardHandoff.Retraction(0f, 0f), handoff.retract(-20f))
        handoff.leftover(30f)
        assertEquals(OpenCardHandoff.Retraction(0f, 0f), handoff.retract(10f))
        assertEquals(30f, handoff.accumulated, eps)
    }

    @Test
    fun aRetractionBelowTheThresholdMovesNoStackEither() {
        val handoff = OpenCardHandoff()
        handoff.leftover(40f)
        val back = handoff.retract(-15f)
        assertEquals(-15f, back.consumedDp, eps)
        assertEquals(0f, back.stackDp, eps)
    }

    @Test
    fun liftingTheFingerEndsTheHandOff() {
        val handoff = OpenCardHandoff()
        repeat(20) { handoff.leftover(10f) }
        handoff.reset()
        assertFalse(handoff.active)
        assertEquals(0f, handoff.leftover(40f), eps)
        assertEquals(0f, handoff.moved, eps)
    }

    @Test
    fun theCardNextToTheFrontIsAsOpenAsItIsNear() {
        assertEquals(1f, OpenCardBlend.openness(0f), eps)
        assertEquals(0.5f, OpenCardBlend.openness(0.5f), eps)
        assertEquals(0.5f, OpenCardBlend.openness(-0.5f), eps)
        assertEquals(0f, OpenCardBlend.openness(1f), eps)
        assertEquals(0f, OpenCardBlend.openness(-3f), eps)
    }

    @Test
    fun theOpenFramesTopFollowsThePositionBetweenTwoCards() {
        val tops = mapOf(0 to 100f, 1 to 200f, 2 to 400f)
        assertEquals(100f, OpenCardBlend.followTop(0f) { tops.getValue(it) }, eps)
        assertEquals(150f, OpenCardBlend.followTop(0.5f) { tops.getValue(it) }, eps)
        assertEquals(200f, OpenCardBlend.followTop(1f) { tops.getValue(it) }, eps)
        assertEquals(300f, OpenCardBlend.followTop(1.5f) { tops.getValue(it) }, eps)
        assertEquals(OpenCardBlend.lerp(10f, 20f, 0.25f), 12.5f, eps)
    }
}
