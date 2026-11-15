package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.DOCK_CARD_ACTIONS_TEST_TAG
import com.riffle.app.launcher.HOME_DOCK_PULL_TEST_TAG
import com.riffle.app.launcher.HOME_DOCK_SURFACE_TEST_TAG
import com.riffle.app.launcher.HOME_GRID_CELL_TEST_TAG
import com.riffle.app.launcher.LauncherShellContent
import com.riffle.app.launcher.sources.SourceChangeSource
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionGateway
import com.riffle.core.domain.launcher.stream.StreamActionResult
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockActionsRowMotionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "swipe-dismiss").apply { isDaemon = true } }
    private var shell by mutableStateOf<LauncherShellState?>(null)
    private val sent = mutableListOf<Pair<String, StreamAction>>()
    private val chatPackage = ScreenshotFixtures.chat.identity.packageName.value
    private val chatPage = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "$chatPackage:personal")

    /** Everyone watching the listener's notifications (the Stream's page and the Dock's live apps both are). */
    private val watchers = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    private val changes =
        SourceChangeSource { listener ->
            watchers += listener
            return@SourceChangeSource { watchers -= listener }
        }
    private val active = mutableListOf<LauncherNotification>()

    /** Whether the gateway removes a dismissed notification (the listener does); false: the dismissal does not take. */
    private var dismissTakes = true

    private val gateway =
        object : StreamActionGateway {
            override fun perform(
                content: StreamContent,
                action: StreamAction,
            ): StreamActionResult {
                val note = content as? NotificationContent ?: return StreamActionResult.NotOffered
                sent += (note.title.orEmpty()) to action
                if (action == StreamAction.Dismiss && dismissTakes) {
                    synchronized(active) { active.removeAll { it.key.value == note.notificationKey } }
                    watchers.forEach { it() }
                }
                return StreamActionResult.Performed
            }
        }

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private fun note(
        index: Int,
        title: String,
        dismissible: Boolean = true,
    ) = LauncherNotification(
        key = LauncherNotificationKey("0|chat|$index"),
        packageName = AppPackageName(chatPackage),
        canDismiss = dismissible,
        title = title,
        text = "The $title message.",
        messages = emptyList(),
        postedAtEpochMillis = StreamFixtures.NOW - index * MINUTE,
    )

    private fun show(
        reducedMotion: Boolean = true,
        firstDismissible: Boolean = true,
    ) {
        active += listOf(note(1, "First", firstDismissible), note(2, "Second"), note(3, "Third"), note(4, "Fourth"))
        val host =
            realWiringStreamHost(
                executor = executor,
                activeNotificationsNow = { synchronized(active) { active.toList() } },
                notificationChanges = changes,
                installedApps = ScreenshotFixtures.installedApps,
                canReply = { true },
                canSnooze = { true },
                actions = gateway,
            ).also { it.pages.remember(HomeLayoutDeviceClass.PHONE, chatPage) }
        val base =
            ScreenshotFixtures.homeState().withHomePages(2).withManyApps().withAFullFirstPage()
                .copy(destination = ShellDestination.STREAM)
        val settings = base.launcherSettings
        shell =
            base.copy(
                launcherSettings =
                    settings.copy(
                        motion =
                            settings.motion.copy(
                                reducedMotionPreference =
                                    if (reducedMotion) ReducedMotionPreference.ON else ReducedMotionPreference.OFF,
                            ),
                    ),
            )
        composeRule.setContent {
            ScreenshotBackdrop {
                LauncherShellContent(
                    state = checkNotNull(shell),
                    appIconLoader = SolidColorAppIconLoader(),
                    stream = host,
                    onAction = { action -> shell = checkNotNull(shell).answering(action) },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) {
            composeRule.onAllNodesWithText("First").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    private fun LauncherShellState.withAFullFirstPage(): LauncherShellState {
        val first = homeLayout.pages.first()
        val columns = first.grid.columns
        val items =
            installedApps.take(columns * first.grid.rows).mapIndexed { index, app ->
                ScreenshotFixtures.shortcut(
                    app,
                    GridPlacement(cell = GridCell(column = index % columns, row = index / columns)),
                )
            }
        val layout =
            homeLayout.copy(pages = homeLayout.pages.map { if (it.id == first.id) it.copy(items = items) else it })
        return copy(
            homeLayout = layout,
            homeLayoutSet = homeLayoutSet.copy(layouts = mapOf(homeLayoutSet.activeKey to layout)),
        )
    }

    private fun bounds(tag: String): Rect? =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    private class Frame(
        val area: Rect,
        val body: Rect,
        val row: Rect?,
        val cells: List<Pair<Int, Int>>,
    )

    private fun frame() =
        Frame(
            area = checkNotNull(bounds(HOME_DOCK_PULL_TEST_TAG)),
            body = checkNotNull(bounds(HOME_DOCK_SURFACE_TEST_TAG)),
            row = bounds(DOCK_CARD_ACTIONS_TEST_TAG),
            cells =
                composeRule.onAllNodesWithTag(HOME_GRID_CELL_TEST_TAG).fetchSemanticsNodes()
                    .map { it.size.width to it.size.height },
        )

    private fun List<Frame>.rowHeights() = map { it.row?.height ?: 0f }

    @Test
    fun theActionsRowGrowsInOneMotionAndTheDockBodyStaysWhereItIs() {
        show(reducedMotion = false)
        composeRule.mainClock.autoAdvance = false
        val frames = mutableListOf(frame())
        composeRule.onNodeWithText("First").performClick()
        repeat(FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            frames += frame()
        }
        val heights = frames.rowHeights()
        val log = heights.mapIndexed { i, h -> "$i:${h.toInt()}" }.joinToString(" ")
        val final = heights.last()
        assertTrue("the actions row did not appear: $log", final > 40f)
        heights.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue("frame ${i + 1}: the row shrank while it grows ($a to $b)\n$log", b >= a - 1f)
        }
        assertTrue("it overshot: ${heights.max()} past $final\n$log", heights.max() <= final + 1f)
        assertTrue(
            "it jumped instead of easing\n$log",
            heights.count { it > 2f && it < final - 2f } >= MIN_EASED_FRAMES,
        )
        // The Dock body does not move or change while the row grows above it: no sinking, no catching up.
        val body = frames.first().body
        frames.forEachIndexed { i, f ->
            assertEquals("frame $i: the Dock body moved: ${f.body} against $body", body.top, f.body.top, 2f)
            assertEquals("frame $i: the Dock body changed size", body.height, f.body.height, 2f)
            assertEquals("frame $i: the Dock body changed size", body.width, f.body.width, 2f)
        }
        // The area is what it holds, at every frame: the row stands inside it, above the Dock body, never over it.
        frames.forEachIndexed { i, f ->
            val row = f.row?.takeIf { it.height > 0f } ?: return@forEachIndexed
            val inside = row.top >= f.area.top - 1f && row.bottom <= f.area.bottom + 1f
            assertTrue("frame $i: the row is outside the area: $row in ${f.area}", inside)
            assertTrue("frame $i: the row is over the Dock body: $row on $body", row.bottom <= body.top + 1f)
        }
        // Home's grid does not move for it.
        frames.forEachIndexed { i, f -> assertEquals("frame $i: Home's grid changed", frames.first().cells, f.cells) }
    }

    @Test
    fun theRowGoesInTheSameMotionWhenTheCardCloses() {
        show(reducedMotion = false)
        composeRule.onNodeWithText("First").performClick()
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
        val open = frame()
        assertTrue("the row is open", (open.row?.height ?: 0f) > 40f)
        // Closing by a tap on the open card: the row goes in the same motion, the Dock body stays.
        composeRule.onNodeWithTag(STREAM_OPEN_CARD_TEST_TAG).performClick()
        val frames = mutableListOf(frame())
        repeat(FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            frames += frame()
        }
        val heights = frames.rowHeights()
        val log = heights.mapIndexed { i, h -> "$i:${h.toInt()}" }.joinToString(" ")
        assertTrue("the row did not go: $log", heights.last() < 2f)
        heights.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue("frame ${i + 1}: the row grew while it closes ($a to $b)\n$log", b <= a + 1f)
        }
        frames.forEachIndexed { i, f ->
            assertEquals("frame $i: the Dock body moved", open.body.top, f.body.top, 2f)
        }
    }

    private companion object {
        const val MINUTE = 60_000L
        const val WAIT_MILLIS = 10_000L
        const val FRAMES = 45
        const val MIN_EASED_FRAMES = 4
    }
}
