package com.riffle.app.screenshots

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.DOCK_CHOICE_LABEL_TEST_TAG
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.dockDynamicSectionTileTestTag
import com.riffle.app.launcher.stream.StreamHost
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.random.Random

/**
 * Closing the Stream by pressing the Dock, the way a real finger does it (the owner's report: "I could only exit the
 * context menu by using the home button, the dock gesture didn't work"). The earlier press tests drag from the Dock's
 * centre, 150 px or a fifth of the window at a time, and lift within 160 ms; a person lands on a tile or an app icon
 * (the contextual row is edge to edge), drags a short way, wobbles sideways, rests for a moment and lifts, the whole
 * touch lasting half a second. These tests do that through the real [HomeDestination] with the real Stream wiring:
 * the finger lands ON a contextual tile or ON a pinned app's icon in the row, the travel is bounded by the room
 * between the finger and the bottom of the screen (a press must be reachable), and the time is the test clock's.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockRealFingerPressTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val executor =
        Executors.newSingleThreadExecutor { Thread(it, "dock-real-finger").apply { isDaemon = true } }
    private lateinit var host: StreamHost
    private var shellShown by mutableStateOf(false)
    private val sent = mutableListOf<LauncherShellAction>()

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private fun show(startShown: Boolean) {
        shellShown = startShown
        host = realWiringStreamHost(executor = executor)
        composeRule.setContent {
            ScreenshotBackdrop {
                HomeDestination(
                    state = ScreenshotFixtures.homeState(),
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
        if (startShown) {
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag("Agenda"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        }
    }

    // The nodes a finger lands on ----------------------------------------------------------------------------------

    /** A node whose whole extent is on screen (a clipped one is half hidden at the row's edge), most central first. */
    private fun fullyVisible(nodes: List<SemanticsNode>): List<SemanticsNode> {
        val width = composeRule.onRoot().fetchSemanticsNode().size.width
        return nodes
            .filter { node ->
                val bounds = node.boundsInRoot
                bounds.width >= node.size.width - 1f && bounds.left >= 0f && bounds.right <= width
            }
            .sortedBy { node -> kotlin.math.abs(node.positionInRoot.x + node.size.width / 2f - width / 2f) }
    }

    private fun tileCentre(): Offset {
        val nodes =
            TILES.flatMap { label ->
                composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag(label)).fetchSemanticsNodes()
            }
        return centreOf(fullyVisible(nodes).first())
    }

    private fun pinnedIconCentre(): Offset {
        val pinned =
            composeRule.onAllNodes(
                SemanticsMatcher("a pinned app's icon") { node ->
                    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("dock-item:") == true
                },
            ).fetchSemanticsNodes()
        assertTrue("no pinned icon is in view in the contextual row", fullyVisible(pinned).isNotEmpty())
        return centreOf(fullyVisible(pinned).first())
    }

    private fun centreOf(node: SemanticsNode): Offset =
        Offset(node.positionInRoot.x + node.size.width / 2f, node.positionInRoot.y + node.size.height / 2f)

    // The finger ---------------------------------------------------------------------------------------------------

    /**
     * A real press: lands on [landing] (root coordinates), drags down over [dragMillis] with the wobble of a thumb
     * (sideways drift of a few dp, [driftDp] more of it by the end, uneven speed), rests for [restMillis] with a
     * tremor, lifts. The travel is the smaller of [wantDp] and what is left above the bottom of the screen. Returns
     * the time the touch took on the test clock.
     */
    private fun realPress(
        landing: Offset,
        dragMillis: Long = 350L,
        restMillis: Long = 150L,
        wantDp: Float = 44f,
        driftDp: Float = 0f,
    ): Long {
        val density = composeRule.density.density
        val rootHeight = composeRule.onRoot().fetchSemanticsNode().size.height
        val roomPx = rootHeight - landing.y - SCREEN_EDGE_MARGIN_DP * density
        val travelPx = min(wantDp * density, roomPx)
        assertTrue(
            "no room for a real press: ${travelPx / density} dp is left below the finger above the screen's edge",
            travelPx / density >= REACHABLE_DP,
        )
        val random = Random(SEED)
        val started = composeRule.mainClock.currentTime
        composeRule.onRoot().performTouchInput { down(landing) }
        val steps = (dragMillis / STEP_MILLIS).toInt()
        for (step in 1..steps) {
            val fraction = step / steps.toFloat()
            // Slow, then quicker, then slow: the thumb's own ease.
            val eased = fraction * fraction * (3f - 2f * fraction)
            val wobble = (random.nextFloat() - 0.5f) * 2f * WOBBLE_DP * density
            val target = landing + Offset(wobble + driftDp * density * eased, travelPx * eased)
            composeRule.onRoot().performTouchInput { moveTo(target, delayMillis = STEP_MILLIS) }
            composeRule.mainClock.advanceTimeBy(STEP_MILLIS)
        }
        val rest = (restMillis / STEP_MILLIS).toInt()
        for (step in 1..rest) {
            val tremor = Offset((random.nextFloat() - 0.5f) * density, (random.nextFloat() - 0.5f) * density)
            val target = landing + Offset(driftDp * density, travelPx) + tremor
            composeRule.onRoot().performTouchInput { moveTo(target, delayMillis = STEP_MILLIS) }
            composeRule.mainClock.advanceTimeBy(STEP_MILLIS)
        }
        composeRule.onRoot().performTouchInput { up() }
        val took = composeRule.mainClock.currentTime - started
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
        return took
    }

    private fun label(): Boolean =
        composeRule.onAllNodesWithTag(DOCK_CHOICE_LABEL_TEST_TAG).fetchSemanticsNodes().isNotEmpty()

    // The tests ----------------------------------------------------------------------------------------------------

    @Test
    fun aRealPressStartedOnAContextualTileReturnsHome() {
        show(startShown = true)
        val took = realPress(tileCentre())
        assertTrue("the press was not a real one: $took ms", took >= MIN_REAL_PRESS_MILLIS)
        assertTrue("the Dock press did not return Home: $sent", LauncherShellAction.OpenHome in sent)
        assertTrue("the Stream was left open: $sent", LauncherShellAction.OpenStream !in sent)
    }

    @Test
    fun aRealPressStartedOnAPinnedAppInTheContextualRowReturnsHome() {
        show(startShown = true)
        val took = realPress(pinnedIconCentre())
        assertTrue("the press was not a real one: $took ms", took >= MIN_REAL_PRESS_MILLIS)
        assertTrue("the Dock press did not return Home: $sent", LauncherShellAction.OpenHome in sent)
    }

    @Test
    fun anUnhurriedPressHeldLongStillReturnsHome() {
        show(startShown = true)
        realPress(tileCentre(), dragMillis = 450L, restMillis = 600L)
        assertTrue("an unhurried press did not return Home: $sent", LauncherShellAction.OpenHome in sent)
        assertTrue("the Dock was left choosing", !label())
    }

    @Test
    fun aThumbThatDriftsAlongTheRowButStaysOnItsTileStillReturnsHome() {
        show(startShown = true)
        // Past the slide distance (14 dp), so the press does turn into choosing; it ends on the tile it began on.
        realPress(tileCentre(), driftDp = 18f)
        assertTrue("a drifting press did not return Home: $sent", LauncherShellAction.OpenHome in sent)
    }

    @Test
    fun aMostlyHorizontalDragOnATileStillScrollsTheRowAndDoesNotPress() {
        show(startShown = true)
        val landing = tileCentre()
        val node = composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag("Agenda")).fetchSemanticsNodes().first()
        val before = node.positionInRoot.x
        val density = composeRule.density.density
        composeRule.onRoot().performTouchInput {
            down(landing)
            // 120 dp along the row with a little downward drift, well short of a press's 28 dp.
            repeat(DRAG_STEPS) { step ->
                val fraction = (step + 1) / DRAG_STEPS.toFloat()
                moveTo(landing + Offset(-120f * density * fraction, 6f * density * fraction), delayMillis = 16L)
            }
            up()
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
        val after =
            composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag("Agenda")).fetchSemanticsNodes().first()
                .positionInRoot.x
        assertTrue("the row did not scroll: $before -> $after", kotlin.math.abs(after - before) > 20f)
        assertTrue("a horizontal drag pressed the Dock: $sent", sent.isEmpty())
    }

    @Test
    fun aQuickFlickOnATileStillReturnsHome() {
        show(startShown = true)
        realPress(tileCentre(), dragMillis = 128L, restMillis = 0L)
        assertTrue("a quick press did not return Home: $sent", LauncherShellAction.OpenHome in sent)
    }

    @Test
    fun aRealPressFromHomeStillOpensTheStream() {
        show(startShown = false)
        val dock = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode()
        val landing =
            Offset(dock.positionInRoot.x + dock.size.width / 2f, dock.positionInRoot.y + dock.size.height / 2f)
        realPress(landing)
        assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenStream), sent)
    }

    private companion object {
        val TILES = listOf("Now", "Recents", "Agenda", "Tasks", "Feeds")
        const val SEED = 17
        const val DRAG_STEPS = 10
        const val STEP_MILLIS = 16L
        const val SETTLE_MILLIS = 1_000L
        const val WOBBLE_DP = 3f
        const val MIN_REAL_PRESS_MILLIS = 450L

        /**
         * How close to the bottom of the screen a thumb goes. The fixture has no navigation bar inset, so here the Dock
         * stands closer to the edge than on a phone with gesture navigation (which adds its own strip below the Dock):
         * what is reachable here is a lower bound for a device.
         */
        const val SCREEN_EDGE_MARGIN_DP = 8f

        /** The Dock's own press threshold, with a margin: the room has to be there for a press to be possible. */
        const val REACHABLE_DP = 32f
    }
}
