package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Dock's size changing when the Stream opens and closes (batch 7, item 3), through the real [HomeDestination] frame
 * by frame (the test clock is stepped by hand): the Dock's glass body grows from its Home-mode length to the
 * contextual mode's in one motion that starts with the press's release and never overshoots, closes the same way back
 * as the cards return, the pinned icons ride the body's start edge (they never jump and never move against it), the
 * tiles that are appended fade in as the body reaches them and are never drawn outside it, Home's grid does not move,
 * and under reduced motion the length snaps. Bottom and right edges.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockResizeMotionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var shellShown by mutableStateOf(false)
    private val sent = mutableListOf<LauncherShellAction>()
    private var answered = 0

    private fun show(
        state: LauncherShellState,
        startShown: Boolean = false,
    ) {
        shellShown = startShown
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = state,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    streamShown = shellShown,
                    onAction = { action -> sent += action },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
    }

    private fun phone(reducedMotion: Boolean = false) = withMotion(ScreenshotFixtures.homeState(), reducedMotion)

    private fun foldable(reducedMotion: Boolean = false) =
        withMotion(ScreenshotFixtures.homeState(HomeLayoutDeviceClass.FOLDABLE, DockPosition.RIGHT), reducedMotion)

    private fun withMotion(
        state: LauncherShellState,
        reducedMotion: Boolean,
    ): LauncherShellState =
        if (!reducedMotion) {
            state
        } else {
            val motion = state.launcherSettings.motion.copy(reducedMotionPreference = ReducedMotionPreference.ON)
            state.copy(launcherSettings = state.launcherSettings.copy(motion = motion))
        }

    /**
     * Presses the Dock down and releases, then steps [frames] frames of the test clock, the shell answering each action
     * one frame after it was sent. The first sample is the frame of the release.
     */
    private fun press(
        edge: DockPosition,
        frames: Int = FRAMES,
    ): List<DockSample> {
        composeRule.pressDock(distancePx = PRESS_PX, edge = edge, release = true, settle = false)
        val samples = mutableListOf(composeRule.sampleDock())
        repeat(frames) {
            composeRule.mainClock.advanceTimeByFrame()
            if (answered < sent.size) {
                val action = sent[answered++]
                composeRule.runOnUiThread {
                    if (action == LauncherShellAction.OpenStream) shellShown = true
                    if (action == LauncherShellAction.OpenHome) shellShown = false
                }
            }
            samples += composeRule.sampleDock()
        }
        return samples
    }

    private val dp get() = composeRule.density.density

    private fun List<DockSample>.lengths(horizontal: Boolean) = map { it.length(horizontal) }

    /** What every opening of the body must be: monotonic, no overshoot, eased, started promptly, about its middle. */
    private fun assertTheBodyExtends(
        samples: List<DockSample>,
        horizontal: Boolean,
    ) {
        val lengths = samples.lengths(horizontal)
        val home = lengths.first()
        val final = lengths.last()
        val log = lengths.mapIndexed { i, l -> "$i:${l.toInt()}" }.joinToString(" ")
        assertTrue("the Dock did not grow: $log", final > home + 40f * dp)
        lengths.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue("frame ${i + 1}: the body shrank while it extends ($a to $b)\n$log", b >= a - 1f)
        }
        assertTrue("it overshot: ${lengths.max()} past $final\n$log", lengths.max() <= final + 1f)
        assertTrue(
            "it jumped instead of easing\n$log",
            lengths.count { it > home + 2f && it < final - 2f } >= MIN_EASED_FRAMES,
        )
        val started = lengths.indexOfFirst { it > home + 2f }
        assertTrue("it started late, at frame $started\n$log", started in 1..MAX_START_FRAME)
        // Centred on its edge the whole time: the middle of the run does not move (a dip is across the run).
        samples.forEachIndexed { i, s ->
            assertEquals(
                "frame $i: the body's middle moved",
                samples.first().centre(horizontal),
                s.centre(horizontal),
                2f,
            )
        }
    }

    /** The pinned run rides the body's start edge: one motion, never two: its offset from that edge never changes. */
    private fun assertThePinnedRunRidesTheBody(
        samples: List<DockSample>,
        horizontal: Boolean,
    ) {
        val offsets =
            samples.map { s ->
                val pin = checkNotNull(s.firstPin(horizontal)) { "no pinned icon in $s" }
                (if (horizontal) pin.rect.left else pin.rect.top) - s.start(horizontal)
            }
        offsets.forEachIndexed { i, offset ->
            assertEquals(
                "frame $i: the first pinned icon moved against the body: $offsets",
                offsets.first(),
                offset,
                2f,
            )
        }
        // And it moves smoothly with the body: never more than the body's own start edge does between two frames.
        samples.zipWithNext().forEachIndexed { i, (a, b) ->
            val pin =
                (b.firstPin(horizontal)!!.rect.let { if (horizontal) it.left else it.top }) -
                    (a.firstPin(horizontal)!!.rect.let { if (horizontal) it.left else it.top })
            val edge = b.start(horizontal) - a.start(horizontal)
            assertEquals("frame ${i + 1}: the pinned icon jumped relative to the body", edge, pin, 2f)
        }
    }

    /** Nothing is drawn outside the body, and what the body reaches comes in rather than appearing whole. */
    private fun assertTheTilesComeInWithTheBody(
        samples: List<DockSample>,
        horizontal: Boolean,
    ) {
        var partway = false
        samples.forEachIndexed { i, s ->
            val start = s.start(horizontal)
            val end = start + s.length(horizontal)
            s.entries.forEach { item ->
                val from = if (horizontal) item.rect.left else item.rect.top
                val to = if (horizontal) item.rect.right else item.rect.bottom
                val alpha = item.alpha ?: return@forEach
                if (to < start + 1f || from > end - 1f) {
                    assertTrue("frame $i: ${item.tag} is drawn outside the body (alpha $alpha)", alpha < 0.02f)
                }
                if (alpha > 0.99f) {
                    assertTrue(
                        "frame $i: ${item.tag} is drawn whole but not inside the body ($from..$to in $start..$end)",
                        from >= start - 3f && to <= end + 3f,
                    )
                }
                if (alpha in 0.05f..0.95f) partway = true
            }
        }
        assertTrue("no tile faded in as the body reached it", partway)
    }

    @Test
    fun aBottomDockExtendsInOneMotionAsTheCardsFanOut() {
        show(phone())
        val samples = press(DockPosition.BOTTOM)
        assertTheBodyExtends(samples, horizontal = true)
        assertThePinnedRunRidesTheBody(samples, horizontal = true)
        assertTheTilesComeInWithTheBody(samples, horizontal = true)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), sent)
        // Home's grid does not re-measure while the Dock extends (its cells keep their size every frame).
        val sizes = samples.first().cellSizes
        assertTrue("the grid is on screen", sizes.isNotEmpty())
        // (Folded, Home leaves the composition once it has faded away: only the frames that have it are compared.)
        samples.forEachIndexed { i, s ->
            if (s.cellSizes.isNotEmpty()) assertEquals("frame $i: Home's grid was measured again", sizes, s.cellSizes)
        }
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun aRightDockExtendsInOneMotionAndHomesGridStaysWhereItIs() {
        show(foldable())
        val samples = press(DockPosition.RIGHT)
        assertTheBodyExtends(samples, horizontal = false)
        assertThePinnedRunRidesTheBody(samples, horizontal = false)
        assertTheTilesComeInWithTheBody(samples, horizontal = false)
        // Unfolded Home stays at full opacity beside the column: every cell has the same bounds at every frame.
        val cells = samples.first().cells
        assertTrue("the grid is on screen", cells.isNotEmpty())
        samples.forEachIndexed { i, s -> assertEquals("frame $i: a cell of Home's grid moved", cells, s.cells) }
    }

    @Test
    fun aBottomDockRetractsAsTheCardsReturnTheOpeningReversed() {
        show(phone(), startShown = true)
        val samples = press(DockPosition.BOTTOM, frames = CLOSING_FRAMES)
        val lengths = samples.lengths(horizontal = true)
        val start = lengths.first()
        val end = lengths.last()
        val log = lengths.mapIndexed { i, l -> "$i:${l.toInt()}" }.joinToString(" ")
        assertTrue("the Dock did not retract: $log", end < start - 40f * dp)
        lengths.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue("frame ${i + 1}: the body grew while it retracts ($a to $b)\n$log", b <= a + 1f)
        }
        assertTrue("it undershot: ${lengths.min()} under $end\n$log", lengths.min() >= end - 1f)
        // It starts with the cards going back in, not after Home is back: the shell has not even been told yet.
        val started = lengths.indexOfFirst { it < start - 2f }
        assertTrue("it retracted late, at frame $started\n$log", started in 1..MAX_START_FRAME)
        assertTrue(
            "the shell is only told once the cards are back: $sent",
            sent == listOf(LauncherShellAction.OpenHome),
        )
        assertTheTilesComeInWithTheBody(samples, horizontal = true)
        assertThePinnedRunRidesTheBody(samples, horizontal = true)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun aRightDockRetractsAsTheCardsReturn() {
        show(foldable(), startShown = true)
        val samples = press(DockPosition.RIGHT, frames = CLOSING_FRAMES)
        val lengths = samples.lengths(horizontal = false)
        val log = lengths.mapIndexed { i, l -> "$i:${l.toInt()}" }.joinToString(" ")
        assertTrue("the Dock did not retract: $log", lengths.last() < lengths.first() - 40f * dp)
        lengths.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue("frame ${i + 1}: the body grew while it retracts ($a to $b)\n$log", b <= a + 1f)
        }
        assertThePinnedRunRidesTheBody(samples, horizontal = false)
        assertTheTilesComeInWithTheBody(samples, horizontal = false)
    }

    @Test
    fun underReducedMotionTheBodySnapsToItsLengthInOneFrame() {
        show(phone(reducedMotion = true))
        val samples = press(DockPosition.BOTTOM, frames = REDUCED_FRAMES)
        val lengths = samples.lengths(horizontal = true)
        val home = lengths.first()
        val final = lengths.last()
        val log = lengths.mapIndexed { i, l -> "$i:${l.toInt()}" }.joinToString(" ")
        assertTrue("the Dock did not grow: $log", final > home + 40f * dp)
        assertEquals("a length between the two was drawn\n$log", 0, lengths.count { it > home + 2f && it < final - 2f })
        // And the content cross-fades: it is dark the moment the mode changes and whole again a few frames later.
        val alphas = samples.map { it.contentAlpha }
        val flipped = lengths.indexOfFirst { it > home + 2f }
        assertTrue("the content never faded: $alphas", alphas.drop(flipped).any { it < 0.5f })
        assertEquals("the content is whole at the end", 1f, alphas.last(), 0.01f)
        assertTrue("it is not cut at rest", samples.last().entries.take(1).all { (it.alpha ?: 1f) > 0.99f })
    }

    @Test
    fun theBodyIsNotEasedOnTheFirstFramesOfHome() {
        // Home's first frames settle (posture, insets, apps arriving): the body has its length from the start.
        show(phone(), startShown = true)
        val first = composeRule.sampleDock()
        composeRule.mainClock.advanceTimeByFrame()
        val second = composeRule.sampleDock()
        assertEquals(first.body.width, second.body.width, 1f)
        assertTrue("the open Stream's Dock is the contextual row from the first frame", first.entries.size >= 4)
    }

    private companion object {
        const val PRESS_PX = 90f
        const val FRAMES = 60
        const val CLOSING_FRAMES = 120
        const val REDUCED_FRAMES = 12
        const val MIN_EASED_FRAMES = 4
        const val MAX_START_FRAME = 6
    }
}
