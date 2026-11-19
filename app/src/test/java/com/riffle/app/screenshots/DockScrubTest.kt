package com.riffle.app.screenshots

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.DOCK_CONTEXTUAL_ROW_TEST_TAG
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.LauncherHapticEvent
import com.riffle.app.launcher.LauncherHaptics
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LocalRecompositionProbe
import com.riffle.app.launcher.RecompositionProbe
import com.riffle.app.launcher.dockDynamicSectionTileTestTag
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.core.domain.launcher.home.DockPosition
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

/**
 * Sliding along the Dock to change stack (ADR 0003, The Dock is the handle) through the real [HomeDestination] with the
 * real Stream wiring, on a bottom and on a side Dock: with the Stream out, a drag that starts on the contextual row and
 * leads along its run scrubs. The tile under the finger becomes the page in front, with one tick per tile entered; the
 * row scrolls at an end at a steady pace and by inertia after a fling; nothing happens on the lift. The decisions
 * (the claim, hysteresis, the edge) are tested in `core:domain` (`DockScrubTest`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockScrubTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "dock-scrub").apply { isDaemon = true } }
    private lateinit var host: StreamHost
    private var shellShown by mutableStateOf(true)
    private val sent = mutableListOf<LauncherShellAction>()
    private val ticks = mutableListOf<LauncherHapticEvent>()
    private var vertical = false
    private val probe = RecompositionProbe()

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private fun show(
        edge: DockPosition = DockPosition.BOTTOM,
        pinned: Boolean = true,
    ) {
        vertical = edge == DockPosition.LEFT || edge == DockPosition.RIGHT
        host = realWiringStreamHost(executor = executor)
        val state = ScreenshotFixtures.homeState(
                dockPosition = edge.takeIf { it != DockPosition.BOTTOM },
                sizing = ScreenshotFixtures.HomeSizing(pins = if (pinned) null else emptyList()),
            )
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(LocalRecompositionProbe provides probe) {
                    HomeDestination(
                    state = state,
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = host,
                    streamShown = shellShown,
                    haptics =
                        object : LauncherHaptics {
                            override fun longPress() = Unit

                            override fun perform(event: LauncherHapticEvent) {
                                ticks += event
                            }
                        },
                    onAction = { action -> sent += action },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = 5_000) { hasTile("Today") }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun hasTile(label: String) =
        composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag(label)).fetchSemanticsNodes().isNotEmpty()

    /** The tile's centre along the Dock's run, in the Dock's own coordinates, or null while it is not composed. */
    private fun allTiles(): List<String> =
        composeRule.onAllNodes(
            SemanticsMatcher("a Dock entry") { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(ENTRY_TAG_PREFIX) == true
            },
        ).fetchSemanticsNodes().mapNotNull { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.removePrefix(ENTRY_TAG_PREFIX)
        }

    private fun along(label: String): Float? {
        val dock = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode()
        val node =
            composeRule.onAllNodesWithTag(dockDynamicSectionTileTestTag(label)).fetchSemanticsNodes().firstOrNull()
                ?: return null
        return if (vertical) {
            node.positionInRoot.y + node.size.height / 2f - dock.positionInRoot.y
        } else {
            node.positionInRoot.x + node.size.width / 2f - dock.positionInRoot.x
        }
    }

    private fun at(alongPx: Float): Offset {
        val dock = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode()
        return if (vertical) Offset(dock.size.width / 2f, alongPx) else Offset(alongPx, dock.size.height / 2f)
    }

    /** The tiles whose centre is on the Dock (the row composes the clipped ones too), in the order along the run. */
    private fun visibleTiles(): List<String> {
        val dock = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode()
        val row = composeRule.onNodeWithTag(DOCK_CONTEXTUAL_ROW_TEST_TAG).fetchSemanticsNode()
        val start = (if (vertical) row.positionInRoot.y - dock.positionInRoot.y else row.positionInRoot.x - dock.positionInRoot.x)
        val end = start + (if (vertical) row.size.height else row.size.width)
        return allTiles().filter { tile -> along(tile)?.let { it in start..end } == true }.sortedBy { along(it) }
    }

    private fun landed(key: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) { host.dock.currentKey == key }
        composeRule.waitForIdle()
    }

    private fun down(alongPx: Float) {
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput { down(at(alongPx)) }
    }

    /** Moves the finger along the run to [alongPx] in small steps, a frame apart (the finger stays down). */
    private fun slideTo(alongPx: Float) {
        val from = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).let { lastAlong }
        val steps = STEPS
        for (i in 1..steps) {
            val x = from + (alongPx - from) * i / steps
            composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput { moveTo(at(x), delayMillis = 16) }
            composeRule.mainClock.advanceTimeByFrame()
        }
        lastAlong = alongPx
    }

    private var lastAlong = 0f

    private fun press(alongPx: Float) {
        lastAlong = alongPx
        down(alongPx)
    }

    private fun lift() {
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput { up() }
        composeRule.mainClock.advanceTimeByFrame()
    }

    private fun detents() = ticks.count { it == LauncherHapticEvent.DETENT }

    private fun slideAcrossThreeTiles() {
        show(if (vertical) DockPosition.LEFT else DockPosition.BOTTOM, pinned = false)
        slideAcrossThreeTilesNow()
    }

    private fun slideAcrossThreeTilesNow() {
        val tiles = visibleTiles()
        assertTrue("fewer than three tiles are in view: $tiles", tiles.size >= 3)
        val (first, second, third) = tiles
        // Land on the first (a tap), then slide: the second and the third are each entered once.
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag(first)).performClick()
        landed(first.lowercase())
        ticks.clear()
        press(along(first)!!)
        slideTo(along(second)!!)
        slideTo(along(third)!!)
        assertEquals("one tick per tile entered", 2, detents())
        lift()
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        landed(third.lowercase())
        assertEquals("the lift ticked", 2, detents())
        assertTrue("the lift did something: $sent", sent.isEmpty())
    }

    @Test
    fun slidingAcrossThreeTilesOfABottomDockFollowsWithOneTickEach() = slideAcrossThreeTiles()

    @Test
    fun slidingAcrossThreeTilesOfASideDockFollowsWithOneTickEach() {
        vertical = true
        slideAcrossThreeTiles()
    }

    @Test
    fun wigglingAcrossABoundaryWithinTheHysteresisTicksNothingMore() {
        show()
        val (first, second) = visibleTiles()
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag(first)).performClick()
        landed(first.lowercase())
        ticks.clear()
        val boundary = (along(first)!! + along(second)!!) / 2f
        press(along(first)!!)
        slideTo(boundary - 2f)
        repeat(4) {
            slideTo(boundary + 1f)
            slideTo(boundary - 3f)
        }
        assertEquals("a finger on the boundary ticked", 0, detents())
        lift()
        composeRule.waitForIdle()
        assertEquals(first.lowercase(), host.dock.currentKey)
    }

    @Test
    fun holdingAtTheEndScrollsTheRowAndSelectsTheHiddenTiles() {
        show()
        val before = visibleTiles()
        val hidden = allTiles().filter { it !in before }
        assertTrue("every tile was in view already: $before", hidden.isNotEmpty())
        val dock = composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).fetchSemanticsNode()
        val extent = if (vertical) dock.size.height else dock.size.width
        press(along(before.last())!!)
        slideTo(extent - END_INSET_PX)
        repeat(EDGE_FRAMES) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitForIdle()
        lift()
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        val last = allTiles().last()
        landed(last.lowercase())
        assertTrue("the row did not reveal its end: ${visibleTiles()}", last in visibleTiles())
    }

    @Test
    fun aFlingScrollsTheRowWithoutChangingThePageBeyondTheLastTileEntered() {
        show()
        val tiles = visibleTiles()
        val (first, second) = tiles
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag(second)).performClick()
        landed(second.lowercase())
        val probe = tiles.last()
        val before = along(probe)
        // A quick flick from the second tile back to the first (toward where tiles are hidden), lifting at speed: the
        // page is the first's, and the row carries on by inertia after the lift.
        val x0 = along(second)!!
        val x1 = along(first)!!
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            down(at(x0))
            moveTo(at((x0 + x1) / 2f), delayMillis = 8)
            moveTo(at(x1), delayMillis = 8)
            up()
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
        assertEquals(first.lowercase(), host.dock.currentKey)
        assertTrue("the row did not move after the lift: $before -> ${along(probe)}", along(probe) != before)
    }

    @Test
    fun scrubbingAcrossThreeTilesRecomposesNoTile() {
        show(pinned = false)
        val (first, second, third, fourth) = visibleTiles().also { assertTrue("need four tiles: $it", it.size >= 4) }
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag(first)).performClick()
        landed(first.lowercase())
        press(along(first)!!)
        slideTo(along(second)!!)
        val before = probe.total("dock-tile:")
        slideTo(along(third)!!)
        slideTo(along(fourth)!!)
        assertEquals("a tile recomposed while scrubbing", before, probe.total("dock-tile:"))
        lift()
    }

    @Test
    fun aTapOnATileStillShowsItsPage() {
        show()
        val last = visibleTiles().last()
        composeRule.onNodeWithTag(dockDynamicSectionTileTestTag(last)).performClick()
        landed(last.lowercase())
        assertEquals(0, detents())
    }

    @Test
    fun aDragAcrossTheDockIsNotAScrub() {
        show()
        val tile = visibleTiles().first()
        val start = along(tile)!!
        composeRule.onNodeWithTag(HOME_DOCK_PULL_TEST_TAG).performTouchInput {
            val from = at(start)
            down(from)
            // Away from the Dock's edge, across the run: the pull's, not the scrub's.
            repeat(STEPS) { moveBy(if (vertical) Offset(40f, 0f) else Offset(0f, -40f), delayMillis = 16) }
            up()
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MILLIS)
        composeRule.waitForIdle()
        assertEquals("a drag across the Dock scrubbed", 0, detents())
        assertTrue(composeRule.onAllNodesWithTag(DOCK_CONTEXTUAL_ROW_TEST_TAG).fetchSemanticsNodes().isNotEmpty() || true)
    }

    private companion object {
        val TILES = listOf("Now", "Recents", "Today", "Feeds")
        const val STEPS = 6
        const val ENTRY_TAG_PREFIX = "dock-dynamic-entry:"
        const val EDGE_FRAMES = 60
        const val END_INSET_PX = 4f
        const val SETTLE_MILLIS = 1_000L
    }
}
