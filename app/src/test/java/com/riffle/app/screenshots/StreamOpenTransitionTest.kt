package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.DockDipKey
import com.riffle.app.launcher.DrawnSurface
import com.riffle.app.launcher.DrawnSurfaceKey
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HOME_SURFACE_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.STREAM_PULL_SURFACE_TEST_TAG
import com.riffle.core.domain.launcher.LauncherShellState
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
 * The Dock's trampoline and fan through the real [HomeDestination], frame by frame (the test clock is stepped by hand;
 * ADR 0003, Amendment 3, Motion): pressing dips the Dock and starts to fade Home, releasing past the threshold bounces
 * the Dock and fans the Stream's front card out of it while Home fades away, closing is the same played backwards, a
 * press that stops short bounces back, and a finger that lands mid-way resolves to a consistent state. Under reduced
 * motion all of it is one short cross-fade. The physics themselves are tested in `core:domain` (`DockMotionTest`,
 * `DockClosingTest`); this is what reaches the screen. Nothing here depends on how long a frame takes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamOpenTransitionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var shellShown by mutableStateOf(false)

    /** Every action the shell has been sent, in order; [record] answers them [shellDelayFrames] frames later. */
    private val sent = mutableListOf<LauncherShellAction>()
    private var answered = 0

    private data class Frame(val home: DrawnSurface?, val stream: DrawnSurface?, val dip: Float)

    private fun show(
        reducedMotion: Boolean,
        startShown: Boolean = false,
    ) {
        shellShown = startShown
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = homeState(reducedMotion),
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

    private fun homeState(reducedMotion: Boolean): LauncherShellState =
        ScreenshotFixtures.homeState().let { base ->
            if (!reducedMotion) {
                base
            } else {
                val motion = base.launcherSettings.motion.copy(reducedMotionPreference = ReducedMotionPreference.ON)
                base.copy(launcherSettings = base.launcherSettings.copy(motion = motion))
            }
        }

    private fun drawn(tag: String): DrawnSurface? =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrNull(DrawnSurfaceKey)

    private fun dip(): Float =
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode().config.getOrNull(DockDipKey) ?: 0f

    private fun frame() = Frame(drawn(HOME_SURFACE_TEST_TAG), drawn(STREAM_PULL_SURFACE_TEST_TAG), dip())

    /** Steps the clock [frames] times; the shell answers each action [shellDelayFrames] frames after it was sent. */
    private fun record(
        frames: Int,
        shellDelayFrames: Int = 0,
    ): List<Frame> {
        val seen = mutableListOf(frame())
        var waited = 0
        repeat(frames) {
            composeRule.mainClock.advanceTimeByFrame()
            seen += frame()
            if (answered < sent.size) {
                if (waited >= shellDelayFrames) {
                    val action = sent[answered++]
                    waited = 0
                    composeRule.runOnUiThread {
                        if (action == LauncherShellAction.OpenStream) shellShown = true
                        if (action == LauncherShellAction.OpenHome) shellShown = false
                    }
                } else {
                    waited++
                }
            }
        }
        return seen
    }

    private fun press(
        distancePx: Float = PRESS_PX,
        release: Boolean = true,
    ) = composeRule.pressDock(distancePx = distancePx, release = release, settle = false)

    private fun List<Frame>.describe() =
        mapIndexed { i, f -> "$i: home=${f.home} stream=${f.stream} dip=${f.dip}" }.joinToString("\n")

    /**
     * Opening: Home only ever fades out and the Stream's front card only ever comes further out of the Dock (it rises,
     * fades in and grows); Home is gone only once the Stream is in place; the Dock springs past rest and settles.
     */
    private fun assertOpensContinuously(frames: List<Frame>) {
        val log = frames.describe()
        var lastHome = 1f
        var lastAlpha = 0f
        var lastRise = 1f
        frames.forEachIndexed { i, f ->
            val home = f.home
            val stream = f.stream
            if (home != null) {
                assertTrue("frame $i: Home came back (flash)\n$log", home.alpha <= lastHome + EPSILON)
                lastHome = home.alpha
            } else {
                assertTrue("frame $i: Home gone before it had faded\n$log", lastHome < 0.5f)
            }
            if (stream != null) {
                assertTrue("frame $i: the card went back into the Dock\n$log", stream.alpha >= lastAlpha - EPSILON)
                assertTrue("frame $i: the card sank\n$log", stream.travelFraction <= lastRise + EPSILON)
                lastAlpha = stream.alpha
                lastRise = stream.travelFraction
            }
            if (home == null) {
                val inPlace = DrawnSurface(1f, 0f)
                assertEquals("frame $i: the Stream must be in place when Home is gone\n$log", inPlace, stream)
            }
            if (stream != null && stream.alpha > 0f && stream.alpha < 1f) {
                assertTrue("frame $i: Home at full opacity while the cards fan out\n$log", (home?.alpha ?: 0f) < 1f)
            }
        }
        assertEquals(DrawnSurface(1f, 0f), frames.last().stream)
        assertEquals(null, frames.last().home)
        assertEquals("the Dock did not come to rest", 0f, frames.last().dip, 0f)
        // A real cross-fade and a real rise happened, not a cut.
        assertTrue(
            "no frame with Home and the cards both part-way\n$log",
            frames.any { f -> (f.home?.alpha ?: 0f) in 0.05f..0.95f && (f.stream?.alpha ?: 0f) in 0.05f..0.95f },
        )
        assertTrue("the card never rose\n$log", frames.any { f -> (f.stream?.travelFraction ?: 0f) in 0.05f..0.95f })
        // The trampoline: the Dock sprang past rest (negative dip) after the press's dip.
        assertTrue("the Dock never sprang back past rest\n$log", frames.any { it.dip < -1f })
    }

    /** Closing: the opening reversed. Home only comes back and the card only goes further into the Dock. */
    private fun assertClosesContinuously(frames: List<Frame>) {
        val log = frames.describe()
        var lastHome = 0f
        var lastAlpha = 1f
        var lastRise = 0f
        frames.forEachIndexed { i, f ->
            val home = f.home
            val stream = f.stream
            if (home != null) {
                assertTrue("frame $i: Home faded back out\n$log", home.alpha >= lastHome - EPSILON)
                lastHome = home.alpha
            }
            if (stream != null) {
                assertTrue("frame $i: the card came back out\n$log", stream.alpha <= lastAlpha + EPSILON)
                // (The frame in which the Stream is already leaving composition draws it at rest, rise 0, unseen.)
                if (stream.alpha > 0f) {
                    assertTrue("frame $i: the card rose again\n$log", stream.travelFraction >= lastRise - EPSILON)
                    lastRise = stream.travelFraction
                }
                lastAlpha = stream.alpha
            } else {
                assertEquals("frame $i: Home must be fully back once the Stream is gone\n$log", 1f, home?.alpha)
            }
            if (home != null && home.alpha < 1f) {
                assertTrue("frame $i: Home fading in with no Stream to leave\n$log", stream != null)
            }
        }
        assertEquals(null, frames.last().stream)
        assertEquals(1f, frames.last().home?.alpha)
        assertEquals(0f, frames.last().dip, 0f)
        assertTrue(
            "the card never gathered into the Dock\n$log",
            frames.any { f -> (f.stream?.travelFraction ?: 0f) in 0.05f..0.95f },
        )
    }

    @Test
    fun pressingTheDockOpensTheStreamAsTheTrampolineAndTheFan() {
        show(reducedMotion = false)
        press()
        assertOpensContinuously(record(frames = 90))
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), sent)
    }

    @Test
    fun aShellThatAnswersSeveralFramesLaterChangesNothing() {
        show(reducedMotion = false)
        press()
        assertOpensContinuously(record(frames = 90, shellDelayFrames = 8))
    }

    @Test
    fun pressingTheDockClosesTheStreamAsTheOpeningReversed() {
        show(reducedMotion = false, startShown = true)
        press()
        val frames = record(frames = 120, shellDelayFrames = 3)
        assertClosesContinuously(frames)
        // The shell is only told once the Dock has risen and Home is back, not while the cards are still going in.
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), sent)
    }

    @Test
    fun theOpenStreamStaysWhereItIsWhileTheDockIsOnlyPressed() {
        show(reducedMotion = false, startShown = true)
        press(release = false)
        val frames = mutableListOf(frame())
        repeat(4) {
            composeRule.mainClock.advanceTimeByFrame()
            frames += frame()
        }
        frames.forEach { assertEquals(DrawnSurface(1f, 0f), it.stream) }
        assertTrue("Home showed while the open Stream was pressed", frames.all { it.home == null })
        assertTrue("the Dock did not dip under the finger", frames.all { it.dip > 1f })
        assertTrue("nothing is asked of the shell before the release: $sent", sent.isEmpty())
    }

    @Test
    fun pressingFromHomeDipsTheDockAndStartsToFadeHomeBeforeAnythingIsReleased() {
        show(reducedMotion = false)
        press(release = false)
        val frames = mutableListOf(frame())
        repeat(4) {
            composeRule.mainClock.advanceTimeByFrame()
            frames += frame()
        }
        frames.forEach { f ->
            assertTrue("Home did not start to fade: $f", f.home!!.alpha < 1f && f.home.alpha > 0.5f)
            assertTrue("the Dock did not dip: $f", f.dip > 1f)
            assertEquals("cards must not appear during the press: $f", null, f.stream)
        }
        assertTrue("nothing is asked of the shell before the release: $sent", sent.isEmpty())

        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput { up() }
        assertOpensContinuously(frames + record(frames = 90, shellDelayFrames = 4))
    }

    @Test
    fun aShallowPressBouncesBackWithNothingAskedOfTheShellAndNoStream() {
        show(reducedMotion = false)
        press(distancePx = SHALLOW_PX)
        val frames = record(frames = 60)
        assertTrue("a shallow press did something: $sent", sent.isEmpty())
        assertTrue("a Stream appeared for a press that stopped short", frames.all { it.stream == null })
        assertTrue("Home faded further than the press did", frames.all { (it.home?.alpha ?: 0f) > 0.5f })
        assertEquals(1f, frames.last().home?.alpha)
        assertEquals(0f, frames.last().dip, 0f)
        assertTrue("the Dock did not bounce back", frames.any { it.dip > 1f })
    }

    @Test
    fun aShallowPressInTheOpenStreamBouncesTheDockAndLeavesTheCardsAlone() {
        show(reducedMotion = false, startShown = true)
        press(distancePx = SHALLOW_PX)
        val frames = record(frames = 60)
        assertTrue("a shallow press did something: $sent", sent.isEmpty())
        frames.forEach { assertEquals(DrawnSurface(1f, 0f), it.stream) }
        assertEquals(0f, frames.last().dip, 0f)
    }

    @Test
    fun underReducedMotionEverythingIsOneShortCrossFadeWithNoDipOrFan() {
        show(reducedMotion = true)
        press(release = false)
        val whilePressed =
            (0 until 3).map {
                composeRule.mainClock.advanceTimeByFrame()
                frame()
            }
        whilePressed.forEach { f ->
            assertEquals("Home must not move while pressed under reduced motion: $f", 1f, f.home?.alpha)
            assertEquals("the Dock must not dip under reduced motion: $f", 0f, f.dip, 0f)
        }
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput { up() }
        val frames = record(frames = 40, shellDelayFrames = 2)
        val log = frames.describe()
        frames.forEach { f ->
            assertEquals("no dip or bounce under reduced motion\n$log", 0f, f.dip, 0f)
            assertEquals("no rise under reduced motion\n$log", 0f, f.stream?.travelFraction ?: 0f, 0f)
            val home = f.home
            val stream = f.stream
            if (home != null && stream != null) {
                assertEquals("a straight cross-fade\n$log", 1f, home.alpha + stream.alpha, 0.001f)
            }
        }
        assertTrue("never cross-faded\n$log", frames.any { f -> f.home != null && f.stream != null })
        assertEquals(DrawnSurface(1f, 0f), frames.last().stream)
        assertEquals(null, frames.last().home)
        val done = frames.indexOfFirst { it.home == null }
        assertTrue("a reduced-motion fade is short, took $done frames\n$log", done in 1..12)
    }

    @Test
    fun underReducedMotionTheStreamFadesBackToHomeToo() {
        show(reducedMotion = true, startShown = true)
        press()
        val frames = record(frames = 40, shellDelayFrames = 2)
        val log = frames.describe()
        assertTrue("no dip under reduced motion\n$log", frames.all { it.dip == 0f })
        assertEquals(null, frames.last().stream)
        assertEquals(1f, frames.last().home?.alpha)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), sent)
    }

    @Test
    fun theAccessibilityActionsRunTheSameTransitionWithoutAGesture() {
        show(reducedMotion = false)
        invokeAction("Show live pages")
        assertOpensWithoutPressDip(record(frames = 90))
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), sent)

        invokeAction("Back to Home")
        assertClosesContinuously(record(frames = 120))
        assertEquals(
            listOf<LauncherShellAction>(LauncherShellAction.OpenStream, LauncherShellAction.OpenHome),
            sent,
        )
    }

    /** From rest the action has no finger to dip under: it still bounces, fans and ends in place. */
    private fun assertOpensWithoutPressDip(frames: List<Frame>) {
        assertEquals(DrawnSurface(1f, 0f), frames.last().stream)
        assertEquals(null, frames.last().home)
        assertEquals(0f, frames.last().dip, 0f)
        assertTrue(frames.any { it.dip < -1f })
        assertTrue(frames.any { f -> (f.stream?.travelFraction ?: 0f) in 0.05f..0.95f })
    }

    @Test
    fun aFingerLandingMidOpeningAndPressingDeepClosesFromWhereItStands() {
        show(reducedMotion = false)
        press()
        val opening = record(frames = 10)
        val midway = opening.last()
        assertTrue("not midway: $midway", (midway.stream?.alpha ?: 0f) in 0.01f..0.99f || midway.home != null)

        press()
        val frames = record(frames = 150, shellDelayFrames = 2)
        val log = (opening + frames).describe()
        assertEquals("the Stream must end closed\n$log", null, frames.last().stream)
        assertEquals("Home must be fully back\n$log", 1f, frames.last().home?.alpha)
        assertEquals(0f, frames.last().dip, 0f)
        assertEquals(
            "the shell was told to open, then to go home\n$log",
            listOf<LauncherShellAction>(LauncherShellAction.OpenStream, LauncherShellAction.OpenHome),
            sent,
        )
    }

    @Test
    fun aFingerLandingMidOpeningAndPressingShallowSettlesTheStreamOpen() {
        show(reducedMotion = false)
        press()
        record(frames = 10)
        press(distancePx = SHALLOW_PX)
        val frames = record(frames = 120, shellDelayFrames = 2)
        val log = frames.describe()
        assertEquals("the Stream must end open\n$log", DrawnSurface(1f, 0f), frames.last().stream)
        assertEquals(null, frames.last().home)
        assertEquals(0f, frames.last().dip, 0f)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), sent)
    }

    @Test
    fun aFingerLandingMidClosingAndPressingShallowTakesTheStreamBackOutAndNeverGoesHome() {
        show(reducedMotion = false, startShown = true)
        press()
        record(frames = 12)
        press(distancePx = SHALLOW_PX)
        val frames = record(frames = 150, shellDelayFrames = 2)
        val log = frames.describe()
        assertEquals("the Stream must end open\n$log", DrawnSurface(1f, 0f), frames.last().stream)
        assertEquals(null, frames.last().home)
        assertEquals(0f, frames.last().dip, 0f)
        assertTrue("the shell was told to go home though the Stream stayed: $sent", sent.isEmpty())
    }

    @Test
    fun aFingerLandingMidClosingAndPressingDeepClosesAgainWithoutAJump() {
        show(reducedMotion = false, startShown = true)
        press()
        val closing = record(frames = 12)
        press()
        val frames = record(frames = 200, shellDelayFrames = 2)
        val log = (closing + frames).describe()
        assertEquals(null, frames.last().stream)
        assertEquals(1f, frames.last().home?.alpha)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), sent)
        // Whatever the finger caught, Home never came back to full opacity before the Stream was gone.
        frames.forEach { f ->
            if (f.stream != null && f.stream.alpha > 0f) {
                assertTrue("Home flashed in over the Stream\n$log", (f.home?.alpha ?: 0f) < 1f)
            }
        }
    }

    private fun invokeAction(label: String) {
        val action =
            composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG)
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .single { it.label == label }
        composeRule.runOnUiThread { action.action() }
        // The shell answers on the next frame, not inside the action.
    }

    private companion object {
        const val PRESS_PX = 150f
        const val SHALLOW_PX = 45f
        const val EPSILON = 0.0005f
    }
}
