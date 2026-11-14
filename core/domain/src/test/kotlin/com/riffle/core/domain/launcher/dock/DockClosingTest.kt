package com.riffle.core.domain.launcher.dock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DockClosingTest {
    private val plan = DockClosingPlan.of()
    private val hz = DockMotionConstants.TRAJECTORY_HZ

    private fun frames(step: Float = 1f / 240f): List<Pair<Float, DockMotionFrame>> {
        val out = mutableListOf<Pair<Float, DockMotionFrame>>()
        var t = 0f
        while (t <= plan.durationSeconds + step) {
            out += t to plan.frameAt(t)
            t += step
        }
        return out
    }

    @Test
    fun theOpeningItIsMirroredFromStartsWhereAPressEndsAndEndsAtRest() {
        val first = plan.opening.first()
        assertEquals(DockMotionConstants.FAN_START, first.p)
        assertEquals(DockMotionConstants.DIP_MAX_DP, first.dip)
        assertEquals(1f, plan.opening.last().p, 0.01f)
        assertEquals(0f, plan.opening.last().dip, 0.5f)
        assertTrue(plan.opening.zipWithNext().all { (a, b) -> b.p >= a.p }, "the opening went backwards")
    }

    @Test
    fun theClosingIsTheOpeningPlayedBackwardsInTime() {
        val n = plan.opening.size
        for (k in 0 until n) {
            // The middle of the interval each sample is shown for.
            val t = (n - 1 - k + 0.5f) / hz
            val frame = plan.frameAt(t)
            assertEquals(plan.opening[k].p, frame.p, 1e-6f, "progress of sample $k")
            assertEquals(plan.opening[k].dip, frame.dip, 1e-4f, "dip of sample $k")
        }
        assertEquals(n.toFloat() / hz, plan.reversedSeconds)
    }

    @Test
    fun theClosingStartsWithTheCardsInPlaceAndEndsWithHomeBackAndTheDockAtRest() {
        val start = plan.frameAt(0f)
        assertEquals(plan.opening.last().p, start.p)
        assertEquals(0f, plan.frameAt(plan.durationSeconds).p)
        assertEquals(0f, plan.frameAt(plan.durationSeconds).dip)
        assertEquals(0f, plan.frameAt(plan.durationSeconds + 5f).p)
        assertTrue(plan.isFinished(plan.durationSeconds))
        assertTrue(!plan.isFinished(plan.durationSeconds - 0.01f))
    }

    @Test
    fun cardsGatherSpeedIntoTheDockAndNeverComeBackOut() {
        val ps = frames().map { it.second.p }
        assertTrue(ps.zipWithNext().all { (a, b) -> b <= a + 1e-6f }, "progress rose during the closing")
        // Slow at the start, quick at the end of the reversed part: the opening settled gently, so the reverse leaves
        // gently and arrives fast.
        val early = plan.frameAt(0f).p - plan.frameAt(0.03f).p
        val late = plan.frameAt(plan.reversedSeconds - 0.03f).p - plan.frameAt(plan.reversedSeconds).p
        assertTrue(late > early * 2f, "the cards did not gather speed (early $early, late $late)")
    }

    @Test
    fun theDocksBounceRunsInReverseAndEndsInTheDipBeforeItRisesToRest() {
        val atEndOfBounce = plan.frameAt(plan.reversedSeconds - 1e-4f)
        assertEquals(DockMotionConstants.DIP_MAX_DP, atEndOfBounce.dip, 0.01f)
        assertEquals(DockMotionConstants.FAN_START, atEndOfBounce.p, 0.01f)
        // The dock was above rest at some point on the way (the opening's overshoot played backwards)...
        assertTrue(frames().any { it.second.dip < -1f })
        // ...and the rise after it takes Home from 0.3 to 0 and the dock from the dip to rest, together.
        val midRise = plan.frameAt(plan.reversedSeconds + plan.riseSeconds / 2f)
        assertTrue(midRise.p in 0.01f..0.29f && midRise.dip in 1f..23f)
        val rise = frames().filter { it.first >= plan.reversedSeconds }.map { it.second }
        assertTrue(rise.zipWithNext().all { (a, b) -> b.dip <= a.dip + 1e-4f && b.p <= a.p + 1e-4f })
    }

    @Test
    fun theLastCardOutIsTheFirstCardBackIn() {
        val sequence = frames()

        fun timeGone(index: Int): Float =
            sequence.first { (_, frame) -> DockFan.cardFrame(frame.p, index).alpha == 0f }.first

        val gone = (0 until DockMotionConstants.FAN_CARDS).map(::timeGone)
        assertTrue(gone[2] < gone[1] && gone[1] < gone[0], "cards did not go back in last-out-first: $gone")
        // And the front card is still on screen after the others have gone.
        val whenLastOtherIsGone = DockFan.cardFrame(plan.frameAt(gone[1]).p, 0)
        assertTrue(whenLastOtherIsGone.alpha > 0f)
    }

    @Test
    fun theOpeningsFirstCardOutIsTheClosingsLastCardIn() {
        // The same fan, read the other way: the card that appears first on the way out goes in last on the way back.
        fun firstSeenWhenOpening(index: Int) = plan.opening.indexOfFirst { DockFan.cardFrame(it.p, index).alpha > 0f }

        val appearing = (0 until DockMotionConstants.FAN_CARDS).map(::firstSeenWhenOpening)
        assertTrue(appearing[0] <= appearing[1] && appearing[1] <= appearing[2], "opening order: $appearing")
    }

    @Test
    fun aDipAlreadyShowingWhenTheClosingStartsDiesAwayInsteadOfSnapping() {
        val join = plan.joinAt(DockMotionFrame(1f, 9f))
        assertEquals(0f, join.timeSeconds)
        assertEquals(9f, plan.frameAt(0f, join).dip, 0.05f)
        assertTrue(plan.frameAt(0.3f, join).dip - plan.frameAt(0.3f).dip < 0.1f)
        // The rise to rest is not disturbed by it.
        assertEquals(0f, plan.frameAt(plan.durationSeconds, join).dip, 0.01f)
    }

    @Test
    fun aStreamCaughtMidwayJoinsTheClosingWhereItsProgressIs() {
        assertEquals(0f, plan.joinAt(DockMotionFrame(1f, 0f)).timeSeconds)
        for (p in listOf(0.9f, 0.7f, 0.5f, 0.35f)) {
            val join = plan.joinAt(DockMotionFrame(p, 3f))
            assertTrue(join.timeSeconds > 0f, "p=$p")
            assertEquals(p, plan.frameAt(0f, join).p, 0.03f, "joined at the wrong progress for p=$p")
            // No jump in what is on screen: the carried dip makes up the difference.
            assertEquals(3f, plan.frameAt(0f, join).dip, 1e-3f)
            // And it is over once the rest of the playback has run.
            assertTrue(plan.isFinished(plan.durationSeconds - join.timeSeconds + 0.001f, join))
        }
        // Later in the closing for lower progress.
        val times = listOf(0.9f, 0.7f, 0.5f).map { plan.joinAt(DockMotionFrame(it, 0f)).timeSeconds }
        assertTrue(times[0] < times[1] && times[1] < times[2])
    }

    @Test
    fun aStreamCaughtDuringTheFinalRiseJoinsInsideIt() {
        val join = plan.joinAt(DockMotionFrame(0.15f, 5f))
        assertTrue(join.timeSeconds > plan.reversedSeconds && join.timeSeconds < plan.durationSeconds)
        assertEquals(0.15f, plan.frameAt(0f, join).p, 0.005f)
        assertEquals(0f, plan.joinAt(DockMotionFrame(0f, 0f)).timeSeconds - plan.durationSeconds, 0.01f)
    }

    @Test
    fun theSpeedSettingStretchesTheClosingToo() {
        val slow = DockClosingPlan.of(DockMotionTuning(speedMillis = 400))
        assertTrue(slow.durationSeconds > plan.durationSeconds)
        assertTrue(slow.riseSeconds > plan.riseSeconds)
        assertEquals(1f, slow.opening.last().p, 0.01f)
    }

    @Test
    fun theWholeClosingTakesAboutAsLongAsTheOpeningDid() {
        assertTrue(plan.durationSeconds in 0.3f..1.2f, "closing takes ${plan.durationSeconds}s")
    }
}
