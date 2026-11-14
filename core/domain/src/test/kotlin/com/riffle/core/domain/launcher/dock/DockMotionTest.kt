package com.riffle.core.domain.launcher.dock

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DockMotionTest {
    private val tuning = DockMotionTuning()

    /** Runs the springs from [start] toward [target] at 60 frames a second until they settle; returns every frame. */
    private fun run(
        start: DockSpringState,
        target: Float,
        tuning: DockMotionTuning = this.tuning,
    ): List<DockSpringState> {
        val frames = mutableListOf(start)
        var state = start
        var guard = 0
        while (!DockSpring.isSettled(state, target) && guard++ < MAX_FRAMES) {
            state = DockSpring.advance(state, target, FRAME, tuning)
            frames += state
        }
        assertTrue(guard < MAX_FRAMES, "the springs never settled")
        return frames
    }

    // The dip under the finger

    @Test
    fun theDipStartsAtZeroAndRisesWithResistanceButNeverReachesItsLimit() {
        assertEquals(0f, DockPressCurve.dipDp(0f))
        assertEquals(0f, DockPressCurve.dipDp(-12f))
        var previous = 0f
        var previousStep = Float.MAX_VALUE
        for (travel in 5..400 step 5) {
            val dip = DockPressCurve.dipDp(travel.toFloat())
            val step = dip - previous
            assertTrue(step > 0f, "the dip stopped following the finger at $travel dp")
            assertTrue(step <= previousStep + EPSILON, "resistance did not rise at $travel dp")
            assertTrue(dip < DockMotionConstants.DIP_MAX_DP, "the dip reached its limit at $travel dp")
            previous = dip
            previousStep = step
        }
    }

    @Test
    fun theDipFollowsTheFingerAtFirstAndMatchesThePrototypesCurve() {
        // 24 * (1 - exp(-dy / 60)): 0.4 dp of dip per dp of travel at first, 9 dp at the commit threshold.
        assertEquals(0.4f, DockPressCurve.dipDp(1f), 0.01f)
        assertEquals(8.96f, DockPressCurve.dipDp(DockPress.COMMIT_DP), 0.05f)
    }

    @Test
    fun pressingFromHomeFadesHomeAGentleAmountAndPressingFromTheOpenStreamOnlyDipsTheDock() {
        val fromHome = DockPressCurve.frame(fromOpen = false, travelDp = DockPress.COMMIT_DP)
        assertTrue(fromHome.p > 0f && fromHome.p < DockMotionConstants.PRESS_FADE_MAX)
        assertTrue(fromHome.dip > 0f)
        assertEquals(0f, DockPressCurve.frame(false, 0f).p)
        assertTrue(DockPressCurve.fade(10_000f) <= DockMotionConstants.PRESS_FADE_MAX)
        val fromOpen = DockPressCurve.frame(fromOpen = true, travelDp = DockPress.COMMIT_DP)
        assertEquals(1f, fromOpen.p, "the cards do not move while pressing in the contextual mode")
        assertEquals(fromHome.dip, fromOpen.dip)
    }

    // The springs

    @Test
    fun theSpringThatCarriesHomeAndTheCardsNeverOvershootsAtTheDefaults() {
        val frames = run(DockRelease.opening(DockPressCurve.frame(false, DockPress.COMMIT_DP)), target = 1f)
        assertTrue(frames.all { it.p <= 1f }, "progress overshot: ${frames.maxOf { it.p }}")
        assertTrue(frames.zipWithNext().all { (a, b) -> b.p >= a.p }, "progress went backwards")
        assertEquals(1f, frames.last().p, SETTLED)
    }

    @Test
    fun theProgressSpringNeverOvershootsWhateverTheTuning() {
        for (speed in listOf(100, 200, 600)) {
            for (magnet in listOf(0f, 0.4f, 1f)) {
                for (trampoline in listOf(0f, 0.5f, 1f)) {
                    val tuned = DockMotionTuning(speed, trampoline, magnet)
                    val frames = run(DockRelease.opening(DockMotionFrame(0.1f, 9f)), target = 1f, tuning = tuned)
                    assertTrue(frames.all { it.p <= 1f }, "overshoot at $tuned")
                }
            }
        }
    }

    @Test
    fun theDocksDipOvershootsUpwardAndSettlesAtRestTheTrampoline() {
        val frames = run(DockRelease.opening(DockPressCurve.frame(false, DockPress.COMMIT_DP)), target = 1f)
        val highest = frames.minOf { it.dip }
        assertTrue(highest < -1f, "the dock never sprang past rest (highest $highest)")
        assertTrue(frames.first().dip > 0f)
        assertEquals(0f, frames.last().dip, SETTLED_DIP)
    }

    @Test
    fun theTrampolineSettingControlsTheBounce() {
        fun overshoot(trampoline: Float): Float {
            val tuned = DockMotionTuning(trampoline = trampoline)
            return -run(DockRelease.opening(DockMotionFrame(0.1f, 9f)), target = 1f, tuning = tuned)
                .minOf { it.dip }
        }
        assertTrue(overshoot(0f) < 0.05f, "a dead dock still bounced")
        assertTrue(overshoot(1f) > overshoot(0.5f))
        assertTrue(overshoot(0.5f) > overshoot(0.2f))
    }

    @Test
    fun theSpeedSettingScalesTheSettleTime() {
        fun frames(speed: Int) =
            run(DockRelease.opening(DockMotionFrame(0.1f, 9f)), 1f, DockMotionTuning(speedMillis = speed)).size
        assertTrue(frames(400) > frames(200))
        assertTrue(frames(200) > frames(100))
        // The default settles well inside a second.
        assertTrue(frames(200) * FRAME < 1f)
    }

    @Test
    fun aShallowPressBouncesBackToRestAndNeverFadesHomeFurther() {
        val pressed = DockPressCurve.frame(fromOpen = false, travelDp = 15f)
        val frames = run(DockRelease.shallow(pressed), target = 0f)
        assertTrue(frames.all { it.p in 0f..pressed.p }, "Home faded further or went negative")
        assertTrue(frames.zipWithNext().all { (a, b) -> b.p <= a.p })
        assertEquals(0f, frames.last().p, SETTLED)
        assertEquals(0f, frames.last().dip, SETTLED_DIP)
        assertTrue(DockSpring.isSettled(frames.last(), 0f))
    }

    @Test
    fun aShallowPressFromTheOpenStreamLeavesTheCardsWhereTheyAre() {
        val frames = run(DockRelease.shallow(DockPressCurve.frame(fromOpen = true, travelDp = 15f)), target = 1f)
        assertTrue(frames.all { it.p == 1f })
        assertEquals(0f, frames.last().dip, SETTLED_DIP)
    }

    @Test
    fun theActionRouteStartsFromRestWithASmallDipAndKick() {
        val state = DockRelease.fromAction(DockMotionFrame(0f, 0f))
        assertEquals(DockMotionConstants.ACTION_DIP_DP, state.dip)
        assertTrue(state.dipVelocity < 0f)
        val frames = run(state, target = 1f)
        assertEquals(1f, frames.last().p, SETTLED)
        assertEquals(0f, frames.last().dip, SETTLED_DIP)
    }

    @Test
    fun theSameInputGivesTheSameTrajectoryWhateverTheFrameRate() {
        val start = DockRelease.opening(DockMotionFrame(0.11f, 9f))
        var coarse = start
        var fine = start
        repeat(20) { coarse = DockSpring.advance(coarse, 1f, 1f / 40f) }
        repeat(40) { fine = DockSpring.advance(fine, 1f, 1f / 80f) }
        assertEquals(fine.p, coarse.p, 0.01f)
        assertEquals(fine.dip, coarse.dip, 0.5f)
    }

    @Test
    fun aStalledFrameCannotBlowTheSimulationUp() {
        val state = DockSpring.advance(DockRelease.opening(DockMotionFrame(0.1f, 9f)), 1f, 5f)
        assertTrue(state.p in 0f..1f)
        assertTrue(abs(state.dip) < DockMotionConstants.DIP_MAX_DP)
    }

    @Test
    fun theTuningDefaultsAreThePrototypesAndGroupedForSettings() {
        assertEquals(200, tuning.speedMillis)
        assertEquals(0.5f, tuning.trampoline)
        assertEquals(0.4f, tuning.magnet)
        assertEquals(15.38f, tuning.omega, 0.01f)
        assertEquals(0.675f, tuning.dipDamping, 0.001f)
        assertEquals(1.6f, tuning.magnetGain, 0.001f)
        assertEquals(1f, tuning.timeScale)
        assertTrue(tuning.dipDamping < 1f, "the dip spring must be under-damped")
    }

    // The fan

    @Test
    fun theFrontCardLeavesFirstAndEachCardBehindStartsLater() {
        val starts = (0 until DockMotionConstants.FAN_CARDS).map { index -> firstProgressAbove(index) { it > 0f } }
        assertTrue(starts[0] < starts[1] && starts[1] < starts[2], "cards did not start in order: $starts")
        val lands = (0 until DockMotionConstants.FAN_CARDS).map { index -> firstProgressAbove(index) { it >= 1f } }
        assertTrue(lands[0] < lands[1] && lands[1] < lands[2], "cards did not land in order: $lands")
        assertTrue(lands.all { it < 1f }, "a card landed only at the very end")
        var p = 0f
        while (p <= 1f) {
            val locals = (0 until DockMotionConstants.FAN_CARDS).map { DockFan.localProgress(p, it) }
            assertTrue(locals[0] >= locals[1] && locals[1] >= locals[2], "a later card was ahead at p=$p")
            p += 0.01f
        }
    }

    private fun firstProgressAbove(
        index: Int,
        test: (Float) -> Boolean,
    ): Float {
        var p = 0f
        while (p <= 1f && !test(DockFan.localProgress(p, index))) p += 0.001f
        return p
    }

    @Test
    fun cardsRiseFromTheDockFadeInAndGrowToFullSize() {
        val inDock = DockFan.cardFrame(0f, 0)
        assertEquals(0f, inDock.alpha)
        assertEquals(1f, inDock.rise)
        assertEquals(DockMotionConstants.FAN_SCALE_FROM, inDock.scale)
        val inPlace = DockFan.cardFrame(1f, 0)
        assertEquals(FanFrame(1f, 0f, 1f), inPlace)
        // Nothing of the fan has started while Home is only being pressed.
        assertEquals(0f, DockFan.cardFrame(DockMotionConstants.PRESS_FADE_MAX, 0).alpha)
        // Solid before it has arrived.
        val mid = DockFan.cardFrame(0.6f, 0)
        assertTrue(mid.alpha > 1f - mid.rise)
        assertTrue(mid.rise in 0.01f..0.99f && mid.scale in 0.83f..0.99f)
    }

    @Test
    fun theWholeFanRestsExactlyInPlaceAtFullProgress() {
        for (index in 0 until DockMotionConstants.FAN_CARDS + 2) {
            assertEquals(FanFrame(1f, 0f, 1f), DockFan.cardFrame(1f, index), "card $index")
        }
    }

    @Test
    fun aPageThatIsNotAStackRisesAsOnePieceWithTheFrontCardsTiming() {
        for (p in listOf(0f, 0.2f, 0.4f, 0.55f, 0.8f, 1f)) {
            assertEquals(DockFan.cardFrame(p, 0), DockFan.pieceFrame(p), "at $p")
        }
        assertEquals(DockFan.riseDp(DockMotionConstants.FAN_CARDS - 1), DockFan.riseDp(7))
        assertTrue(DockFan.riseDp(1) > DockFan.riseDp(0))
    }

    @Test
    fun homeFadesOutAsTheCardsComeInWithASlightScaleDown() {
        assertEquals(HomeFrame(1f, 1f), DockFan.homeFrame(0f))
        assertEquals(0f, DockFan.homeFrame(1f).alpha)
        assertEquals(1f - DockMotionConstants.HOME_SCALE_DOWN, DockFan.homeFrame(1f).scale)
        assertEquals(0.7f, DockFan.homeFrame(0.3f).alpha, 0.0001f)
        assertTrue(DockFan.homeFrame(0.5f).alpha < DockFan.homeFrame(0.4f).alpha)
        assertEquals(0f, DockFan.homeFrame(2f).alpha, "progress past the end must not bring Home back")
    }

    // Reduced motion

    @Test
    fun underReducedMotionEveryFrameIsAPlainCrossFade() {
        for (p in listOf(0f, 0.1f, 0.3f, 0.5f, 0.77f, 1f)) {
            for (index in 0..4) {
                assertEquals(FanFrame(p, 0f, 1f), DockFan.cardFrame(p, index, reduced = true), "card $index at $p")
            }
            assertEquals(HomeFrame(1f - p, 1f), DockFan.homeFrame(p, reduced = true))
            assertEquals(DockFan.cardFrame(p, 2, reduced = true), DockFan.pieceFrame(p, reduced = true))
        }
    }

    @Test
    fun theReducedTimelineIsOneShortStraightFadeEitherWay() {
        assertEquals(0f, DockReducedTimeline.progress(0f, 1f, 0f))
        assertEquals(0.5f, DockReducedTimeline.progress(0f, 1f, DockMotionConstants.REDUCED_FADE_SECONDS / 2), 0.0001f)
        assertEquals(1f, DockReducedTimeline.progress(0f, 1f, 10f))
        assertEquals(0f, DockReducedTimeline.progress(1f, 0f, 10f))
        assertEquals(0.75f, DockReducedTimeline.progress(1f, 0f, DockMotionConstants.REDUCED_FADE_SECONDS / 4), 0.0001f)
        // From wherever it was caught.
        assertEquals(0.4f, DockReducedTimeline.progress(0.4f, 1f, 0f))
        assertFalse(DockReducedTimeline.isFinished(0.05f))
        assertTrue(DockReducedTimeline.isFinished(DockMotionConstants.REDUCED_FADE_SECONDS))
        assertTrue(DockMotionConstants.REDUCED_FADE_SECONDS < 0.2f, "a reduced-motion fade is short")
    }

    private companion object {
        const val FRAME = 1f / 60f
        const val MAX_FRAMES = 600
        const val EPSILON = 1e-4f
        const val SETTLED = 0.003f
        const val SETTLED_DIP = 0.2f
    }
}
