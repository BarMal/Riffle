package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.dock.DockMotionConstants
import com.riffle.core.domain.launcher.dock.DockMotionTuning
import com.riffle.core.domain.launcher.settings.OpenCardPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenCardMotionTest {
    private fun run(
        from: OpenCardSpringState,
        target: Float,
        tuning: DockMotionTuning = DockMotionTuning(),
    ): List<OpenCardSpringState> {
        val frames = mutableListOf(from)
        var state = from
        var guard = 0
        while (!OpenCardSpring.isSettled(state, target) && guard++ < 600) {
            state = OpenCardSpring.advance(state, target, 1f / 60f, tuning)
            frames += state
        }
        return frames
    }

    @Test
    fun theGrowthNeverOvershootsAndEndsAtOne() {
        val frames = run(OpenCardSpringState(0f), 1f)
        assertTrue(frames.all { it.progress <= 1f + 1e-6f && it.progress >= 0f })
        assertTrue(frames.zipWithNext().all { (a, b) -> b.progress >= a.progress - 1e-6f })
        assertTrue(OpenCardSpring.isSettled(frames.last(), 1f))
    }

    @Test
    fun theShrinkNeverUndershootsAndEndsAtZero() {
        val frames = run(OpenCardSpringState(1f), 0f)
        assertTrue(frames.all { it.progress >= -1e-6f && it.progress <= 1f })
        assertTrue(OpenCardSpring.isSettled(frames.last(), 0f))
    }

    @Test
    fun aSlowerSpeedSettingTakesLonger() {
        val fast = run(OpenCardSpringState(0f), 1f, DockMotionTuning(speedMillis = 150)).size
        val slow = run(OpenCardSpringState(0f), 1f, DockMotionTuning(speedMillis = 400)).size
        assertTrue(slow > fast, "$slow vs $fast")
    }

    @Test
    fun theSameInputGivesTheSamePathWhateverTheFrameRate() {
        val sixty = (1..30).fold(OpenCardSpringState(0f)) { s, _ -> OpenCardSpring.advance(s, 1f, 1f / 60f) }
        val ninety = (1..45).fold(OpenCardSpringState(0f)) { s, _ -> OpenCardSpring.advance(s, 1f, 1f / 90f) }
        assertEquals(sixty.progress, ninety.progress, 0.01f)
    }

    @Test
    fun aStalledFrameCannotExplodeTheSimulation() {
        val after = OpenCardSpring.advance(OpenCardSpringState(0f), 1f, seconds = 30f)
        assertTrue(after.progress in 0f..1f)
    }

    @Test
    fun theReducedMotionFadeRisesFromItsStartToFullOverTheReducedFade() {
        assertEquals(DockMotionConstants.OPEN_CARD_REDUCED_FADE_FROM, OpenCardSpring.reducedFadeAlpha(0f), 1e-6f)
        assertEquals(1f, OpenCardSpring.reducedFadeAlpha(DockMotionConstants.REDUCED_FADE_SECONDS), 1e-6f)
        assertTrue(OpenCardSpring.reducedFadeAlpha(DockMotionConstants.REDUCED_FADE_SECONDS / 2) in 0.4f..1f)
    }

    private fun frame(
        progress: Float,
        placement: OpenCardPlacement,
        strips: Float = 100f,
    ) = OpenCardLayout.frame(
        progress = progress,
        areaHeightPx = 1000f,
        bottomInsetPx = 50f,
        browseHeightPx = 300f,
        fullHeightPx = 500f,
        stripsPx = strips,
        placement = placement,
    )

    @Test
    fun atRestBrowsedTheCardIsWhereItsBrowseCardStands() {
        val frame = frame(0f, OpenCardPlacement.CENTRE)
        assertEquals(300f, frame.heightPx)
        // Just above the Dock's gap: the bottom of the area less the gap less the card.
        assertEquals(1000f - 50f - 300f, frame.topPx)
    }

    @Test
    fun openAtTheCentreItSitsMidwayBetweenTheStripsAndTheDock() {
        val frame = frame(1f, OpenCardPlacement.CENTRE)
        assertEquals(500f, frame.heightPx)
        // The room is 100 (below the strips) to 950 (above the Dock's gap): 850 tall; a 500 card leaves 175 each side.
        assertEquals(100f + 175f, frame.topPx)
    }

    @Test
    fun openJustAboveTheDockItsBottomIsTheDocksGapAndGrowsOutOfTheDockIsTheSame() {
        val above = frame(1f, OpenCardPlacement.ABOVE_DOCK)
        assertEquals(950f, above.topPx + above.heightPx)
        assertEquals(above, frame(1f, OpenCardPlacement.GROWS_OUT_OF_DOCK))
    }

    @Test
    fun aCardTallerThanTheRoomIsHeldToItAndNeverCoversTheStrips() {
        val tall =
            OpenCardLayout.frame(
                1f,
                1000f,
                50f,
                300f,
                fullHeightPx = 5000f,
                stripsPx = 168f,
                placement = OpenCardPlacement.CENTRE,
            )
        assertEquals(1000f - 50f - 168f, tall.heightPx)
        assertEquals(168f, tall.topPx)
    }

    @Test
    fun theGrowthIsContinuousFromBrowseToOpen() {
        val heights = (0..10).map { frame(it / 10f, OpenCardPlacement.CENTRE).heightPx }
        assertTrue(heights.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(300f, heights.first())
        assertEquals(500f, heights.last())
    }

    @Test
    fun theStripsTakeAPitchPerCardBehindAndNoMoreThanThePeeksThereAre() {
        assertEquals(0f, OpenCardLayout.stripsPx(0, 56f))
        assertEquals(112f, OpenCardLayout.stripsPx(2, 56f))
        assertEquals(StreamCardStackSpec.VISIBLE_PEEKS * 56f, OpenCardLayout.stripsPx(9, 56f))
    }

    @Test
    fun theStripsStandOnlyWhereTheyLeaveTheOpenCardItsRoom() {
        // A tall area: three strips (168) and a 240 card fit with room to spare.
        assertTrue(OpenCardLayout.stripsFit(900f, 50f, 168f, 240f))
        // The keyboard is up: 400 of area leaves 182 for the card under three strips, so they are not drawn.
        assertEquals(false, OpenCardLayout.stripsFit(400f, 50f, 168f, 240f))
        // Exactly enough is enough; with no strips wanted the card needs only its own room.
        assertTrue(OpenCardLayout.stripsFit(458f, 50f, 168f, 240f))
        assertTrue(OpenCardLayout.stripsFit(300f, 50f, 0f, 240f))
    }
}
