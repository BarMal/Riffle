package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The front card never blinks out and the stack never leaves the Dock (owner's recordings, batch 7 bugs A, B): through
 * the real shell content, with the listener re-posting the front notification (same key, new text and time) at rest,
 * while its card is open, while it closes and after a swipe that the app ignores, and with the keyboard coming and
 * going and the cards being dismissed one by one. At every frame the front card is drawn (or the open card is) and
 * stands on the Dock, and the stack position is held to the cards.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamFrontCardStaysTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val executor = java.util.concurrent.Executor { it.run() }
    private var shell by mutableStateOf<LauncherShellState?>(null)
    private val sent = mutableListOf<Pair<String, StreamAction>>()
    private val chatPackage = ScreenshotFixtures.chat.identity.packageName.value
    private val chatPage = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "$chatPackage:personal")
    private val dp get() = composeRule.density.density

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

    private var revision = 0

    private fun note(
        index: Int,
        title: String,
        dismissible: Boolean = true,
    ) = LauncherNotification(
        key = LauncherNotificationKey("0|chat|$index"),
        packageName = AppPackageName(chatPackage),
        canDismiss = dismissible,
        title = title,
        text = "The $title message. Edit $revision.",
        messages = emptyList(),
        postedAtEpochMillis = StreamFixtures.NOW - index * MINUTE + revision * 1_000L,
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

    // Helpers ------------------------------------------------------------------------------------------------------

    private fun frontTitle(): String? =
        listOf("First", "Second", "Third", "Fourth").firstOrNull { title ->
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }

    private fun theCardIsOpen() =
        composeRule.onAllNodes(hasTestTag(STREAM_OPEN_CARD_TEST_TAG)).fetchSemanticsNodes().isNotEmpty()

    private fun SemanticsNodeInteraction.swipeSideways(
        dxDp: Float,
        durationMillis: Long,
    ) = performTouchInput {
        swipe(center, center + Offset(dxDp.dp.toPx(), 0f), durationMillis = durationMillis)
    }

    private val dock get() = com.riffle.app.launcher.HOME_DOCK_SURFACE_TEST_TAG

    /** The front card of a stack, or the one card of a page that has only one (a lone card is no stack's slot). */
    private fun frontSlotOrNull() =
        composeRule.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher("the front card") { node ->
                node.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("Card 1 of") == true
            },
        ).fetchSemanticsNodes().firstOrNull()
            ?: composeRule.onAllNodes(
                androidx.compose.ui.test.SemanticsMatcher("the lone card") { node ->
                    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("stream-stack-card:") == true
                },
            ).fetchSemanticsNodes().singleOrNull()

    private fun openCardNode() =
        composeRule.onAllNodes(hasTestTag(STREAM_OPEN_CARD_TEST_TAG)).fetchSemanticsNodes().firstOrNull()

    /** The front card (or the open card) is drawn and stands on the Dock: no hole, no stack at the top. */
    private fun assertTheFrontCardStandsOnTheDock(what: String) {
        val dockTop = composeRule.onNodeWithTag(dock).fetchSemanticsNode().boundsInRoot.top
        val front = frontSlotOrNull()
        val open = openCardNode()
        assertTrue("$what: neither the front card nor the open card is drawn", front != null || open != null)
        val bottom = (front ?: open)!!.boundsInRoot.bottom
        // The front card stands on the Dock; an open card grows toward the middle (it may stand higher, never lower).
        val floor = if (front != null) dockTop - 100f * dp else 0f
        assertTrue(
            "$what: the card's bottom ($bottom) is not just above the Dock's top ($dockTop)",
            bottom <= dockTop + 4f * dp && bottom >= floor,
        )
        assertTrue("$what: the card is at the top of the screen", (front ?: open)!!.boundsInRoot.top > 40f * dp)
    }

    private fun repost(edit: () -> Unit = { revision++ }) {
        composeRule.runOnUiThread {
            synchronized(active) {
                edit()
                val first = active.indexOfFirst { it.key.value == "0|chat|1" }
                if (first >= 0) active[first] = note(1, "First")
            }
            watchers.forEach { it() }
        }
    }

    private fun framesWith(
        frames: Int,
        what: String,
        every: (Int) -> Unit = {},
    ) {
        composeRule.mainClock.autoAdvance = false
        repeat(frames) { i ->
            every(i)
            composeRule.mainClock.advanceTimeByFrame()
            assertTheFrontCardStandsOnTheDock("$what, frame $i")
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    @Test
    fun repostingTheFrontNotificationAtRestNeverLeavesAHole() {
        show(reducedMotion = false)
        assertTheFrontCardStandsOnTheDock("before")
        framesWith(120, "a re-post at rest") { i -> if (i % 10 == 0) repost() }
        assertEquals("First", frontTitle())
    }

    @Test
    fun repostingWhileTheCardIsOpenAndWhileItClosesNeverLeavesAHole() {
        show(reducedMotion = false)
        composeRule.onNodeWithText("First").performClick()
        framesWith(40, "opening")
        assertTrue("the card is open", theCardIsOpen())
        framesWith(60, "a re-post while open") { i -> if (i % 7 == 0) repost() }
        composeRule.onNodeWithTag(STREAM_OPEN_CARD_TEST_TAG).performClick()
        framesWith(80, "a re-post while it closes") { i -> if (i % 5 == 0) repost() }
        assertTrue("the card closed", !theCardIsOpen())
        assertTheFrontCardStandsOnTheDock("after")
        assertEquals("First", frontTitle())
    }

    @Test
    fun aSwipeTheAppIgnoresBringsTheNextCardForwardAtOnceAndTheCardBackOnce() {
        show(reducedMotion = false)
        dismissTakes = false
        composeRule.onNodeWithText("First").swipeSideways(250f, durationMillis = 300)
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { sent.isNotEmpty() }
        // Every frame of the next three seconds has a front card (the next one has come forward at once).
        composeRule.mainClock.autoAdvance = false
        val titles = mutableListOf<String?>()
        repeat(200) {
            composeRule.mainClock.advanceTimeByFrame()
            assertTheFrontCardStandsOnTheDock("after an ignored dismissal, frame $it")
            titles += frontTitle()
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertEquals("the card came back once", "First", frontTitle())
        // And it did not cycle: once back it stays.
        assertEquals(1, titles.zipWithNext().count { (a, b) -> a != "First" && b == "First" })
        assertTrue(
            "and it did not go again: $titles",
            titles.dropWhile { it != "First" }.dropWhile { it == "First" }.isEmpty(),
        )
    }

    @Test
    fun aKeyboardThatComesAndGoesWithACardOpenLeavesTheStackOnTheDock() {
        show(reducedMotion = false)
        composeRule.onNodeWithText("First").performClick()
        composeRule.waitForIdle()
        composeRule.setImeInset((300f * dp).toInt())
        assertTrue(theCardIsOpen())
        composeRule.onNodeWithTag(STREAM_OPEN_CARD_TEST_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.setImeInset(0)
        framesWith(60, "after the keyboard went")
        assertEquals("First", frontTitle())
    }

    @Test
    fun dismissingCardsOneByOneAndReFillingKeepsTheStackOnTheDock() {
        show(reducedMotion = false)
        repeat(3) {
            val title = frontTitle()!!
            composeRule.onNodeWithText(title).swipeSideways(250f, durationMillis = 300)
            composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { frontTitle() != title }
            composeRule.waitForIdle()
            assertTheFrontCardStandsOnTheDock("after dismissing $title")
        }
        assertEquals("Fourth", frontTitle())
        repost()
        composeRule.waitForIdle()
        assertTheFrontCardStandsOnTheDock("after a re-post of the last card")
        composeRule.onNodeWithText("Fourth").performClick()
        framesWith(30, "opening the last card")
        composeRule.onNodeWithTag(STREAM_OPEN_CARD_TEST_TAG).performClick()
        framesWith(60, "closing the last card")
    }

    private companion object {
        const val MINUTE = 60_000L
        const val WAIT_MILLIS = 10_000L
    }
}
