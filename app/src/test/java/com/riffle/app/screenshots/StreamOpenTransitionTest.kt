package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.DrawnSurface
import com.riffle.app.launcher.DrawnSurfaceKey
import com.riffle.app.launcher.HOME_SURFACE_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.STREAM_PULL_SURFACE_TEST_TAG
import com.riffle.app.launcher.dockDynamicSectionTileTestTag
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
 * Opening the Stream from the Dock's Stream button, frame by frame (the test clock is stepped by hand): one continuous
 * reveal. Home never stays at full opacity once the Stream has started to come in, the Stream is always on screen (or
 * moving) while Home is fading, and the frame the shell agrees (a frame or several after the settle) changes nothing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamOpenTransitionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var shellShown by mutableStateOf(false)
    private val actions = mutableListOf<LauncherShellAction>()

    private data class Frame(val home: DrawnSurface?, val stream: DrawnSurface?)

    private fun show(reducedMotion: Boolean) {
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = homeState(reducedMotion),
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = emptyStreamHost(),
                    streamShown = shellShown,
                    onAction = { action ->
                        actions += action
                        if (action == LauncherShellAction.OpenStream) shellShown = true
                        if (action == LauncherShellAction.OpenHome) shellShown = false
                    },
                )
            }
        }
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

    private fun frame() = Frame(home = drawn(HOME_SURFACE_TEST_TAG), stream = drawn(STREAM_PULL_SURFACE_TEST_TAG))

    /** Steps the clock [frames] times; the shell answers [shellDelayFrames] frames after the action it was sent. */
    private fun record(
        frames: Int,
        shellDelayFrames: Int = 0,
    ): List<Frame> {
        val seen = mutableListOf(frame())
        var sinceAction = -1
        repeat(frames) {
            composeRule.mainClock.advanceTimeByFrame()
            seen += frame()
            if (actions.isNotEmpty() && sinceAction < 0) {
                sinceAction = 0
            } else if (sinceAction >= 0) {
                sinceAction++
            }
            if (sinceAction >= shellDelayFrames && actions.isNotEmpty()) {
                val action = actions.removeFirst()
                composeRule.runOnUiThread {
                    if (action == LauncherShellAction.OpenStream) shellShown = true
                    if (action == LauncherShellAction.OpenHome) shellShown = false
                }
                sinceAction = -1
            }
        }
        return seen
    }

    private fun tapStreamButton(
        reducedMotion: Boolean,
        startShown: Boolean,
        shellDelayFrames: Int,
    ): List<Frame> {
        shellShown = startShown
        show(reducedMotion)
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag("Stream")).performClick()
        return record(frames = 90, shellDelayFrames = shellDelayFrames)
    }

    private fun List<Frame>.describe() =
        mapIndexed { i, f -> "$i: home=${f.home} stream=${f.stream}" }.joinToString("\n")

    /** Opening: Home only ever fades out, from the first frame the Stream shows; the Stream only ever comes in. */
    private fun assertOpensContinuously(frames: List<Frame>) {
        val log = frames.describe()
        var lastHome = 1f
        var lastTravel = -1f
        frames.forEachIndexed { i, f ->
            val home = f.home
            val stream = f.stream
            if (home != null) {
                assertTrue("frame $i: Home came back (flash)\n$log", home.alpha <= lastHome)
                lastHome = home.alpha
            } else {
                assertTrue("frame $i: Home gone before it had faded\n$log", lastHome < 0.5f)
            }
            if (stream != null) {
                assertTrue("frame $i: Stream moved back out\n$log", stream.travelFraction >= lastTravel)
                lastTravel = stream.travelFraction
                assertEquals("frame $i: the Stream itself never fades while sliding\n$log", 1f, stream.alpha, 0f)
            }
            if (home != null && home.alpha < 1f) {
                assertTrue("frame $i: Home fading with no Stream coming in\n$log", stream != null)
            }
            if (home != null && stream != null && stream.travelFraction > -1f) {
                assertTrue("frame $i: Home at full opacity as the Stream appears\n$log", home.alpha < 1f)
            }
            if (home == null) {
                assertEquals("frame $i: Stream must be settled when Home is gone\n$log", DrawnSurface(1f, 0f), stream)
            }
        }
        assertEquals(DrawnSurface(1f, 0f), frames.last().stream)
        assertEquals(null, frames.last().home)
    }

    @Test
    fun theStreamButtonOpensTheStreamAsOneContinuousReveal() {
        assertOpensContinuously(tapStreamButton(reducedMotion = false, startShown = false, shellDelayFrames = 0))
    }

    @Test
    fun aShellThatAnswersSeveralFramesLaterChangesNothing() {
        assertOpensContinuously(tapStreamButton(reducedMotion = false, startShown = false, shellDelayFrames = 8))
    }

    @Test
    fun theStreamButtonClosesTheStreamAsOneContinuousReveal() {
        val frames = tapStreamButton(reducedMotion = false, startShown = true, shellDelayFrames = 3)
        val log = frames.describe()
        var lastHome = 0f
        var lastTravel = 0f
        frames.forEachIndexed { i, f ->
            val home = f.home
            val stream = f.stream
            if (home != null) {
                assertTrue("frame $i: Home faded back out\n$log", home.alpha >= lastHome)
                lastHome = home.alpha
            }
            if (stream != null && stream.alpha > 0f) {
                assertTrue("frame $i: the Stream came back in\n$log", stream.travelFraction >= lastTravel)
                lastTravel = stream.travelFraction
            } else {
                assertEquals("frame $i: Home must be fully back once the Stream is gone\n$log", 1f, home?.alpha)
            }
            if (home != null && home.alpha < 1f) {
                assertTrue("frame $i: Home fading with no Stream\n$log", stream != null)
            }
        }
        assertEquals(null, frames.last().stream)
        assertEquals(1f, frames.last().home?.alpha)
    }

    @Test
    fun underReducedMotionTheStreamCutsInWithNoFrameBetween() {
        // Frame 0 is the state right after the tap, before any frame has been drawn: the drawn frames are 1 on.
        val frames = tapStreamButton(reducedMotion = true, startShown = false, shellDelayFrames = 2).drop(1)
        val log = frames.describe()
        val firstCut = frames.indexOfFirst { it.home == null }
        assertTrue("never cut to the Stream\n$log", firstCut in 0..3)
        frames.take(firstCut).forEach { assertEquals("only Home before the cut\n$log", 1f, it.home?.alpha) }
        frames.drop(firstCut).forEach {
            assertEquals("only the Stream after the cut\n$log", DrawnSurface(1f, 0f), it.stream)
            assertEquals("Home is gone after the cut\n$log", null, it.home)
        }
    }

    @Test
    fun aPullTracksTheFingerAndEndsWithoutAFlash() {
        shellShown = false
        show(reducedMotion = false)
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag("Stream")).performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, -40f)) }
        }
        val frames = mutableListOf(frame())
        repeat(4) {
            composeRule.mainClock.advanceTimeByFrame()
            frames += frame()
        }
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag("Stream")).performTouchInput { up() }
        frames += record(frames = 90, shellDelayFrames = 4)
        assertOpensContinuously(frames)
    }
}
