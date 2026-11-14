package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.DOCK_CHOICE_LABEL_TEST_TAG
import com.riffle.app.launcher.DOCK_CONTEXTUAL_ROW_TEST_TAG
import com.riffle.app.launcher.DockChoiceLabelKey
import com.riffle.app.launcher.DrawnSurfaceKey
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HOME_SURFACE_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.dockDynamicSectionTileTestTag
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.dock.DockMotionConstants
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executors
import kotlin.math.abs

/**
 * Hold to choose (ADR 0003, Amendment 3, One gesture, third depth) through the real [HomeDestination] with the real
 * Stream wiring: a Dock pressed past the threshold and held (or slid sideways) stops toggling and starts choosing; the
 * Dock shows its contextual row early with a ring and a name label on the item under the finger, and lifting opens that
 * item's page, from Home with the fan and from the open Stream as a switch. What is asserted is outcomes (which page
 * the Stream landed on, which action the shell was sent, what the Dock draws), driving the test clock rather than
 * waiting. The decisions themselves (when choosing starts, the nearest item, edge scrolling, cancel, every release) are
 * tested in `core:domain` (`DockChoiceTest`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockHoldToChooseTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "dock-choose").apply { isDaemon = true } }
    private lateinit var host: StreamHost
    private var shellShown by mutableStateOf(false)
    private val sent = mutableListOf<LauncherShellAction>()

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private fun show(
        startShown: Boolean = false,
        reducedMotion: Boolean = false,
    ) {
        shellShown = startShown
        host = realWiringStreamHost(executor = executor)
        val base = ScreenshotFixtures.homeState()
        val state =
            if (!reducedMotion) {
                base
            } else {
                val motion = base.launcherSettings.motion.copy(reducedMotionPreference = ReducedMotionPreference.ON)
                base.copy(launcherSettings = base.launcherSettings.copy(motion = motion))
            }
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = state,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = host,
                    streamShown = shellShown,
                    onAction = { action ->
                        sent += action
                        if (action == LauncherShellAction.OpenStream) shellShown = true
                        if (action == LauncherShellAction.OpenHome) shellShown = false
                    },
                )
            }
        }
        composeRule.waitForIdle()
        if (startShown) waitForTile("Today")
    }

    private fun waitForTile(label: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag(label)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun hasTile(label: String) =
        composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag(label)).fetchSemanticsNodes().isNotEmpty()

    private fun label(): String? =
        composeRule.onAllNodesWithTag(DOCK_CHOICE_LABEL_TEST_TAG).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrNull(DockChoiceLabelKey)

    private fun homeAlpha(): Float? =
        composeRule.onAllNodesWithTag(HOME_SURFACE_TEST_TAG).fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrNull(DrawnSurfaceKey)?.alpha

    /** Presses the Dock down by [PRESS_PX] and keeps the finger there. */
    private fun pressAndHold() {
        composeRule.pressDock(distancePx = PRESS_PX, release = false, settle = false)
    }

    /** Waits (on the test clock) until choosing has begun and the Dock has its tiles. */
    private fun awaitChoosing(tile: String = "Today") {
        composeRule.mainClock.advanceTimeBy(HOLD_PLUS_MILLIS)
        waitForTile(tile)
        composeRule.mainClock.advanceTimeByFrame()
    }

    /**
     * The tile nearest the middle of the screen, other than [not]: away from the row's end zones, where holding the
     * finger would scroll the row and so change what it is over.
     */
    private fun middleTile(not: String? = null): String {
        val width = composeRule.onRoot().fetchSemanticsNode().size.width
        return TILES.filter { it != not }
            .mapNotNull { label ->
                composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag(label)).fetchSemanticsNodes().firstOrNull()
                    ?.let { node -> label to abs(node.positionInRoot.x + node.size.width / 2f - width / 2f) }
            }
            .minByOrNull { it.second }!!.first
    }

    /** Moves the finger over [tile]'s centre along the run, at the Dock's mid-height (the finger stays down). */
    private fun moveOver(tile: String) {
        val dock = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode()
        val node = composeRule.onNodeWithTag(dockDynamicSectionTileTestTag(tile)).fetchSemanticsNode()
        val x = node.positionInRoot.x + node.size.width / 2f - dock.positionInRoot.x
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput { moveTo(Offset(x, center.y)) }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun lift() {
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput { up() }
    }

    /** Lets the shell's answer arrive and the Stream land: until the pager's current page is [key]. */
    private fun awaitLanded(key: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { host.dock.currentKey == key }
        composeRule.waitForIdle()
    }

    @Test
    fun aQuickPressFromHomeStillOpensTheStreamAndNeverStartsChoosing() {
        show()
        composeRule.pressDock(distancePx = PRESS_PX, settle = false)
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
        assertTrue("a quick press did not open: $sent", LauncherShellAction.OpenStream in sent)
        assertNull("a label showed", label())
    }

    @Test
    fun holdingThePressedDockExtendsItEarlyAndLiftingOpensTheItemUnderTheFinger() {
        show()
        assertTrue("the Home Dock already has tiles", !hasTile("Today"))
        pressAndHold()
        awaitChoosing()

        // The Dock extended: its contextual row and tiles are there while the Stream is not shown, and Home is dimmed.
        composeRule.onNodeWithTag(DOCK_CONTEXTUAL_ROW_TEST_TAG).assertExists()
        assertTrue("choosing opened the Stream before the lift: $sent", sent.isEmpty())
        assertNotNull("no label while choosing", label())
        assertTrue("Home was not dimmed: ${homeAlpha()}", (homeAlpha() ?: 1f) < 0.3f)

        val target = middleTile()
        moveOver(target)
        assertEquals(target, label())
        lift()
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        awaitLanded(target.lowercase())
        assertTrue("the lift did not open the Stream: $sent", LauncherShellAction.OpenStream in sent)
        assertNull("the label stayed after the lift", label())
    }

    @Test
    fun slidingSidewaysWhilePressedStartsChoosingAtOnceWithoutWaiting() {
        show()
        composeRule.pressDock(distancePx = PRESS_PX, release = false, settle = false)
        // Slide along the run well beyond the slide distance; no time passes beyond the move's own few milliseconds.
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            moveBy(Offset(SLIDE_PX, 0f), delayMillis = 16)
        }
        waitForTile("Today")
        composeRule.mainClock.advanceTimeByFrame()
        assertNotNull("sliding did not start choosing", label())
        lift()
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
        assertTrue(LauncherShellAction.OpenStream in sent)
    }

    @Test
    fun draggingWellAboveTheDockBeforeTheLiftCancelsAndChangesNothing() {
        show()
        pressAndHold()
        awaitChoosing()
        assertNotNull(label())
        val dockHeight = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode().size.height
        // More than one Dock height above the Dock's top edge: the finger started inside the Dock, pressed down, so go
        // up from there by the press, the Dock and a margin.
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            moveBy(Offset(0f, -(PRESS_PX + dockHeight * 2.5f)), delayMillis = 16)
        }
        composeRule.mainClock.advanceTimeByFrame()
        assertNull("the label named an item while cancelling", label())
        lift()
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
        assertTrue("a cancelled choice did something: $sent", sent.isEmpty())
        assertTrue("the Dock stayed extended", !hasTile("Today"))
        assertEquals("Home did not come back", 1f, homeAlpha() ?: 1f, 0.001f)
    }

    @Test
    fun choosingWhileTheStreamIsOutSwitchesPagesAndDoesNotClose() {
        show(startShown = true)
        // With the Stream out holding still never chooses; sliding along the run does, at once.
        pressAndHold()
        composeRule.mainClock.advanceTimeBy(HOLD_PLUS_MILLIS)
        composeRule.mainClock.advanceTimeByFrame()
        assertNull("holding still chose in the open Stream", label())
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            moveBy(Offset(SLIDE_PX, 0f), delayMillis = 16)
        }
        composeRule.mainClock.advanceTimeByFrame()
        assertNotNull("no label while choosing from the open Stream", label())
        // Hold the finger at the row's end: it scrolls there (edge scrolling) and the last tile is under the finger.
        holdAtEnd(atStart = false)
        assertEquals("Feeds", label())
        lift()
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        awaitLanded("feeds")
        assertTrue("choosing closed the Stream: $sent", LauncherShellAction.OpenHome !in sent)
    }

    @Test
    fun aQuickPressWhileTheStreamIsOutStillClosesIt() {
        show(startShown = true)
        composeRule.pressDock(distancePx = PRESS_PX, settle = false)
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitUntil(timeoutMillis = 5_000) { LauncherShellAction.OpenHome in sent }
        assertNull(label())
    }

    @Test
    fun holdingNearTheStartOfTheRowScrollsItBackAndTheSelectionFollows() {
        show()
        pressAndHold()
        awaitChoosing()
        // The early extend scrolled the row to bring the live entries and tiles into view; the pinned icons are behind.
        val probe = middleTile()
        moveOver(probe)
        val before = tileX(probe)
        holdAtEnd(atStart = true)
        val after = tileX(probe)
        assertTrue("the row did not scroll back: $before -> $after", after > before + 30f)
        assertNotNull("no selection while scrolling", label())
        lift()
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
        assertTrue(LauncherShellAction.OpenStream in sent)
    }

    /** Parks the finger at the row's start or end until the row has scrolled as far as it goes (the app idles then). */
    private fun holdAtEnd(atStart: Boolean) {
        val dock = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode()
        val x = if (atStart) END_INSET_PX else dock.size.width - END_INSET_PX
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput { moveTo(Offset(x, center.y)) }
        repeat(EDGE_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitForIdle()
    }

    @Test
    fun underReducedMotionTheSelectionStillWorks() {
        show(reducedMotion = true)
        pressAndHold()
        awaitChoosing()
        val target = middleTile()
        moveOver(target)
        assertEquals(target, label())
        lift()
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        awaitLanded(target.lowercase())
        assertTrue(LauncherShellAction.OpenStream in sent)
    }

    private fun tileX(label: String): Float =
        composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag(label)).fetchSemanticsNodes().firstOrNull()
            ?.positionInRoot?.x ?: Float.NEGATIVE_INFINITY

    private companion object {
        val TILES = listOf("Now", "Recents", "Today", "Feeds")
        const val PRESS_PX = 150f
        const val SLIDE_PX = 120f
        const val EDGE_FRAMES = 60
        const val END_INSET_PX = 4f
        const val SETTLE_MILLIS = 1_000L
        const val HOLD_PLUS_MILLIS = DockMotionConstants.DEFAULT_HOLD_MILLIS + 120L
    }
}
