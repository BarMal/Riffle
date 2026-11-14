package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.dock.DockMotionConstants
import com.riffle.core.domain.launcher.dock.DockMotionTuning
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StackMotionTest {
    private val height = 168f
    private val geometry = StackGeometry(height)
    private val pitch = StreamCardStackSpec.PITCH_DP
    private val gap = StreamCardStackSpec.GAP_DP
    private val eps = 0.001f

    private fun positions(
        count: Int,
        step: Float = 0.05f,
    ): List<Float> = generateSequence(-0.5f) { it + step }.takeWhile { it <= count - 0.5f }.toList()

    // The layout

    @Test
    fun atWholePositionsTheFrontCardIsWholeAndTheOnesBehindPeekAsStripsOneAfterAnother() {
        val front = geometry.frame(0f)
        assertEquals(1f, front.alpha)
        assertEquals(1f, front.scale)
        assertEquals(height, front.topDp, eps)
        assertEquals(0f, front.visibleBottomDp, eps)
        assertEquals(height, front.clipHeightDp, eps)

        for (r in 1..StreamCardStackSpec.VISIBLE_PEEKS) {
            val peek = geometry.frame(r.toFloat())
            assertEquals(1f, peek.alpha)
            assertEquals(height + pitch * r, peek.topDp, eps)
            // Only the top strip of the real card shows: a pitch less the gap to the card in front.
            assertEquals(pitch - gap, peek.visibleHeightDp, eps)
            assertTrue(peek.clipHeightDp < height)
            val inFront = geometry.frame(r - 1f)
            assertEquals(inFront.topDp + gap, peek.visibleBottomDp, eps)
        }
    }

    @Test
    fun theCardBehindTheLastPeekAndTheOneSunkInTheDockAreNotDrawnAtRest() {
        assertEquals(0f, geometry.frame(StreamCardStackSpec.VISIBLE_PEEKS + 1f).alpha)
        assertEquals(0f, geometry.frame(-1f).alpha)
        assertFalse(geometry.frame(-1f).isVisible)
        assertFalse(geometry.frame(5f).isVisible)
        assertEquals(0f, geometry.frame(-3f).alpha)
        // A stack at rest composes the front card and its three peeks, nothing else.
        assertEquals(2..5, geometry.composedRange(2f, 9))
        assertEquals(0..3, geometry.composedRange(0f, 9))
        assertEquals(0..1, geometry.composedRange(0f, 2))
        assertEquals(IntRange.EMPTY, geometry.composedRange(0f, 0))
    }

    @Test
    fun aStackNeverComposesMoreCardsThanItsPolicyAllows() {
        for (count in listOf(1, 2, 7, 50, 500)) {
            for (position in positions(count, 0.1f)) {
                val composed = geometry.composedRange(position, count).count()
                assertTrue(composed <= StreamCardStackSpec.MAX_COMPOSED_CARDS, "$count cards at $position: $composed")
            }
        }
    }

    @Test
    fun aCardMovesUpAndFadesInMonotonicallyAsItIsBroughtBackAndTheFrontOneSinks() {
        // As the position grows by dragging down, every card moves toward the front: its top never rises.
        var previous = geometry.frame(4.9f)
        var r = 4.85f
        while (r >= -1f) {
            val frame = geometry.frame(r)
            assertTrue(frame.topDp <= previous.topDp + eps, "the top rose at r=$r")
            r -= 0.05f
            previous = frame
        }
        // Alpha: 0 sunk, 1 from the front to the third peek, 0 again behind it; both edges are ramps, never jumps.
        var last = geometry.frame(-1f).alpha
        var q = -1f
        while (q <= 4f) {
            val alpha = geometry.frame(q).alpha
            assertTrue(abs(alpha - last) <= 0.06f, "alpha jumped at r=$q")
            last = alpha
            q += 0.01f
        }
    }

    private fun assertNoOverlap(
        count: Int,
        position: Float,
    ) {
        for (index in 1 until count) {
            val behind = geometry.frame(index - position)
            val inFront = geometry.frame(index - 1 - position)
            if (!behind.isVisible || !inFront.isVisible) continue
            val message = "card $index overlaps ${index - 1} at $position"
            assertTrue(behind.visibleBottomDp >= inFront.topDp - eps, message)
        }
    }

    @Test
    fun noCardOverlapsTheOneInFrontOfItAtAnyPositionSoFillsNeverAddUp() {
        for (count in listOf(2, 6)) {
            positions(count).forEach { position -> assertNoOverlap(count, position) }
        }
    }

    @Test
    fun theClipOpensTowardTheFrontAndTheWholeCardIsThereWhenItArrives() {
        var previous = geometry.frame(StreamCardStackSpec.VISIBLE_PEEKS.toFloat()).visibleHeightDp
        var r = StreamCardStackSpec.VISIBLE_PEEKS.toFloat()
        while (r >= 0f) {
            val shown = geometry.frame(r).visibleHeightDp
            assertTrue(shown >= previous - eps, "the clip closed as the card came forward at r=$r")
            previous = shown
            r -= 0.05f
        }
        assertEquals(height, geometry.frame(0f).visibleHeightDp, eps)
        // Part-way from strip to whole card, it shows something in between.
        val half = geometry.frame(0.5f).visibleHeightDp
        assertTrue(half > pitch - gap && half < height)
    }

    @Test
    fun theSinkingCardIsTheWholeCardMovingDownAndFadingBelowTheCardRevealedAboveIt() {
        val sinking = geometry.frame(-0.5f)
        assertEquals(0.5f, sinking.alpha, eps)
        assertEquals(height, sinking.clipHeightDp, eps)
        assertTrue(sinking.topDp < height)
        val revealed = geometry.frame(0.5f)
        assertTrue(revealed.visibleBottomDp >= sinking.topDp - eps)
        // Fully sunk, nothing of it is left above the bottom of the stack.
        assertEquals(0f, geometry.frame(-1f).topDp, eps)
    }

    @Test
    fun aPeekIsTheTopStripOfTheRealCardNotACopy() {
        // The clip is measured down from the card's own top, so strip and whole card line up at every position.
        for (r in listOf(1f, 1.5f, 2f, 3f)) {
            val frame = geometry.frame(r)
            assertTrue(frame.clipHeightDp <= height, "clip taller than the card at r=$r")
            assertTrue(frame.clipHeightDp > 0f)
        }
    }

    // The finger

    @Test
    fun aDragDownBringsOlderCardsForwardOneCardForEveryNinetyDp() {
        assertEquals(1f, StackDrag.cardsFor(DockMotionConstants.STACK_DRAG_DP_PER_CARD))
        assertEquals(-0.5f, StackDrag.cardsFor(-45f))
        assertEquals(1.5f, StackDrag.advance(1f, 0.5f, 5), eps)
        assertEquals(0.5f, StackDrag.advance(1f, -0.5f, 5), eps)
        assertEquals(1.25f, StackDrag.advance(StackDrag.advance(1f, 0.1f, 5), 0.15f, 5), eps)
    }

    @Test
    fun pastEitherEndTheStackResistsAndLetsGoAlongTheSamePath() {
        val band = DockMotionConstants.STACK_RUBBER_BAND
        // Pulled past the first card: a third of the finger's travel.
        assertEquals(-1f * band, StackDrag.advance(0f, -1f, 5), eps)
        // Past the last card the same.
        assertEquals(4f + band, StackDrag.advance(4f, 1f, 5), eps)
        // A move that crosses the end is split there.
        assertEquals(4f + 0.5f * band, StackDrag.advance(3.5f, 1f, 5), eps)
        assertEquals(-0.5f * band, StackDrag.advance(0.5f, -1f, 5), eps)
        // Coming back out of the band travels the same way.
        val pulled = StackDrag.advance(0f, -2f, 5)
        assertEquals(0f, StackDrag.advance(pulled, 2f, 5), eps)
        assertEquals(1f, StackDrag.advance(pulled, 3f, 5), eps)
        val pulledEnd = StackDrag.advance(4f, 2f, 5)
        assertEquals(4f, StackDrag.advance(pulledEnd, -2f, 5), eps)
        // One card has two ends in the same place.
        assertEquals(band, StackDrag.advance(0f, 1f, 1), eps)
        assertEquals(-band, StackDrag.advance(0f, -1f, 1), eps)
        assertEquals(1.2f, StackDrag.clamp(1.2f, 5))
        assertEquals(4f, StackDrag.clamp(4.7f, 5))
        assertEquals(0f, StackDrag.clamp(-0.3f, 5))
    }

    // Release: fling projection and target

    @Test
    fun aReleaseWithoutVelocityGoesToTheNearestCard() {
        assertEquals(2, StackFling.target(1.6f, 0f, 5))
        assertEquals(1, StackFling.target(1.4f, 0f, 5))
        assertEquals(2, StackFling.nearest(1.5f, 5))
        assertEquals(0, StackFling.target(-0.4f, 0f, 5))
        assertEquals(4, StackFling.target(4.4f, 0f, 5))
    }

    @Test
    fun aSlowReleaseStaysOnItsCardAndAFastOneCoastsOnToTheCardItWouldReach() {
        // 1.2 cards with a slow push: still card 1.
        assertEquals(1, StackFling.target(1.2f, 0.5f, 6))
        // 2 cards a second for 0.18 s is a third of a card more: from 1.4 the projection lands on card 2.
        assertEquals(2, StackFling.target(1.4f, 2f, 6))
        // A hard fling goes several cards, in the direction it was moving, and never past either end.
        assertEquals(4, StackFling.target(1f, 15f, 8))
        assertEquals(0, StackFling.target(4f, -40f, 8))
        assertEquals(7, StackFling.target(1f, 400f, 8))
        // Toward newer cards (up) is negative.
        assertEquals(1, StackFling.target(3f, -10f, 8))
    }

    @Test
    fun inertiaScalesTheCoastAndNoneMeansNone() {
        val none = StackFling.target(1f, 15f, 20, inertia = 0f)
        val normal = StackFling.target(1f, 15f, 20, inertia = 1f)
        val more = StackFling.target(1f, 15f, 20, inertia = 2f)
        assertEquals(1, none)
        assertTrue(normal > none && more > normal)
        // The setting is held to its range.
        val most = StackFling.target(1f, 15f, 20, DockMotionConstants.MAX_STACK_INERTIA)
        assertEquals(most, StackFling.target(1f, 15f, 20, 99f))
        assertEquals(none, StackFling.target(1f, 15f, 20, -3f))
    }

    @Test
    fun aVelocityInDpPerSecondBecomesCardsPerSecond() {
        assertEquals(10f, StackFling.velocityCards(10f * DockMotionConstants.STACK_DRAG_DP_PER_CARD))
        assertEquals(-2f, StackFling.velocityCards(-2f * DockMotionConstants.STACK_DRAG_DP_PER_CARD))
    }

    // The spring and its magnet

    private fun run(
        start: StackSpringState,
        target: Int,
        tuning: DockMotionTuning = DockMotionTuning(),
    ): List<StackSpringState> {
        val frames = mutableListOf(start)
        var state = start
        var guard = 0
        while (!StackSpring.isSettled(state, target) && guard++ < MAX_FRAMES) {
            state = StackSpring.advance(state, target, FRAME, tuning)
            frames += state
        }
        assertTrue(guard < MAX_FRAMES, "the stack never settled")
        return frames
    }

    private fun assertSettlesOnItsCard(
        start: Float,
        velocity: Float,
    ) {
        val target = StackFling.target(start, velocity, 5)
        val state = StackSpring.release(start, velocity, target)
        val frames = run(state, target)
        // The last frame is within the settle threshold; the settle itself puts it exactly on the card.
        assertTrue(abs(frames.last().position - target) < DockMotionConstants.STACK_SETTLED_POSITION)
        assertEquals(target.toFloat(), StackSettle.restPosition(target))
        // Never past the target (a spring that carries velocity toward it may not swing beyond).
        if (velocity * (target - start) >= 0f) {
            val side = if (state.position < target) 1f else -1f
            val message = "swung past $target from $start at $velocity"
            frames.forEach { assertTrue((target - it.position) * side >= -eps, message) }
        }
    }

    @Test
    fun theSpringSettlesOnAWholeCardFromAnywhereAndNeverSwingsPastIt() {
        for (start in listOf(0.2f, 0.5f, 0.9f, 3.1f, -0.4f, 4.6f)) {
            listOf(0f, 3f, -3f, 25f, -25f).forEach { velocity -> assertSettlesOnItsCard(start, velocity) }
        }
    }

    @Test
    fun aVelocityTowardTheCardIsHeldToWhatTheSpringCanAbsorbSoItNeverOvershoots() {
        val tuning = DockMotionTuning()
        val state = StackSpring.release(1.9f, 500f, 2, tuning)
        assertEquals(tuning.stackOmega * 0.1f, state.velocity, 0.01f)
        // Away from the card the spring turns it round, with the velocity held to a little way past the card.
        val away = tuning.stackOmega * DockMotionConstants.STACK_MAX_AWAY_CARDS
        assertEquals(-1f, StackSpring.release(1.9f, -1f, 2, tuning).velocity)
        assertEquals(-away, StackSpring.release(1.9f, -500f, 2, tuning).velocity, 0.001f)
        assertEquals(away, StackSpring.release(5.3f, 500f, 4, tuning).velocity, 0.001f)
        assertEquals(0f, StackSpring.release(2f, 0f, 2, tuning).velocity)
    }

    @Test
    fun aHardPushPastTheEndNeverSendsTheStackFarOutOfPlace() {
        for (velocity in listOf(5f, 40f, 400f)) {
            val frames = run(StackSpring.release(4.35f, velocity, 4), 4)
            val farthest = frames.maxOf { it.position }
            assertTrue(farthest < 4.35f + 0.2f, "pushed to $farthest at $velocity")
            assertTrue(frames.all { it.position >= 4f - eps })
        }
    }

    @Test
    fun theMagnetStrengthensThePullWithinAQuarterOfACard() {
        val tuning = DockMotionTuning()
        val far = StackSpring.pullAt(0.25f, tuning)
        val farther = StackSpring.pullAt(1.7f, tuning)
        val near = StackSpring.pullAt(0.1f, tuning)
        val rest = StackSpring.pullAt(0f, tuning)
        assertEquals(1f, far)
        assertEquals(1f, farther)
        assertTrue(near > far)
        assertTrue(rest > near)
        assertEquals(1f + tuning.magnetGain, rest)
        // No magnet, no extra pull.
        assertEquals(1f, StackSpring.pullAt(0f, DockMotionTuning(magnet = 0f)))
        // A stronger magnet pulls harder at the same distance.
        assertTrue(StackSpring.pullAt(0.1f, DockMotionTuning(magnet = 1f)) > near)
    }

    @Test
    fun aStrongerMagnetBringsTheStackHomeSoonerFromNearTheCard() {
        fun framesToSettle(magnet: Float): Int = run(StackSpringState(2.2f), 2, DockMotionTuning(magnet = magnet)).size
        assertTrue(framesToSettle(1f) < framesToSettle(0f))
    }

    @Test
    fun theSpeedSettingMakesTheStackSettleSoonerOrLater() {
        fun frames(speed: Int): Int = run(StackSpringState(0.6f), 1, DockMotionTuning(speedMillis = speed)).size
        assertTrue(frames(100) < frames(200))
        assertTrue(frames(200) < frames(400))
    }

    // The settle rule, the haptic and reduced motion

    @Test
    fun aSettleOnADifferentCardTicksAndOneBackOnTheSameCardDoesNot() {
        assertTrue(StackSettle.shouldTick(startedOn = 0, settledOn = 1))
        assertTrue(StackSettle.shouldTick(startedOn = 3, settledOn = 2))
        assertFalse(StackSettle.shouldTick(startedOn = 2, settledOn = 2))
    }

    @Test
    fun underReducedMotionAReleaseJumpsToTheNearestCardWithNoCoast() {
        // Even a hard fling is not projected: the nearest card to where the finger left it.
        assertEquals(2, StackSettle.reducedTarget(1.7f, 5))
        assertEquals(1, StackSettle.reducedTarget(1.2f, 5))
        assertEquals(0, StackSettle.reducedTarget(-0.8f, 5))
        assertEquals(4, StackSettle.reducedTarget(6f, 5))
        // The short fade that follows runs from partly faded to full over the reduced-motion fade.
        assertEquals(DockMotionConstants.STACK_REDUCED_FADE_FROM, StackSettle.reducedFadeAlpha(0f))
        assertEquals(1f, StackSettle.reducedFadeAlpha(DockMotionConstants.REDUCED_FADE_SECONDS))
        assertEquals(1f, StackSettle.reducedFadeAlpha(10f))
        assertTrue(StackSettle.reducedFadeAlpha(DockMotionConstants.REDUCED_FADE_SECONDS / 2) in 0.5f..0.95f)
    }

    @Test
    fun theNextAndPreviousCardStepsStopAtTheEnds() {
        assertEquals(2, StackSettle.step(1, 5, older = true))
        assertEquals(0, StackSettle.step(1, 5, older = false))
        assertNull(StackSettle.step(4, 5, older = true))
        assertNull(StackSettle.step(0, 5, older = false))
        assertNull(StackSettle.step(0, 1, older = true))
    }

    @Test
    fun aStripAtRestShowsAtLeastTheStripHeightOfItsCardWhateverItsRank() {
        // The header of every card (an icon and a one-line title) lies wholly inside this many dp of the card's top.
        assertEquals(pitch - gap, StreamCardStackSpec.STRIP_HEIGHT_DP, eps)
        for (r in 1..StreamCardStackSpec.VISIBLE_PEEKS) {
            val clip = geometry.frame(r.toFloat()).clipHeightDp
            assertTrue(clip >= StreamCardStackSpec.STRIP_HEIGHT_DP - eps, "rank $r clips the card at $clip dp")
        }
    }

    private companion object {
        const val FRAME = 1f / 60f
        const val MAX_FRAMES = 600
    }
}
