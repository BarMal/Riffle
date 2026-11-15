package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.LauncherShellContent
import com.riffle.app.launcher.sources.SourceChangeSource
import com.riffle.app.launcher.stream.DismissArmedKey
import com.riffle.app.launcher.stream.DismissCueAlphaKey
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_PAGER_TEST_TAG
import com.riffle.app.launcher.stream.SwipeOffsetKey
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

/**
 * Swiping the front card of a notification sideways to dismiss it (device fixes, round 5, item 2), through the real
 * shell content with a gateway that removes what it is told to dismiss, as the listener does. The rule that settles
 * the conflict with paging between stacks: a horizontal drag is a card's wherever it starts on a card, and it
 * dismisses only on the front card of a notification that offers Dismiss; on any other card (a strip, a notification that cannot be dismissed) the card
 * resists a few dp and settles back and the pager never gets the drag; only the empty room pages. Four chat
 * notifications, "First" in front.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamSwipeDismissTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "swipe-dismiss").apply { isDaemon = true } }
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

    // Helpers ------------------------------------------------------------------------------------------------------

    private fun frontTitle(): String? =
        listOf("First", "Second", "Third", "Fourth").firstOrNull { title ->
            composeRule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
        }

    private fun theCardIsOpen() =
        composeRule.onAllNodes(hasTestTag(STREAM_OPEN_CARD_TEST_TAG)).fetchSemanticsNodes().isNotEmpty()

    /** Where the pager has scrolled to: it changes when the page does. */
    private fun pagerPosition(): Float =
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).fetchSemanticsNode()
            .config[SemanticsProperties.HorizontalScrollAxisRange].value()

    private fun SemanticsNodeInteraction.swipeSideways(
        dxDp: Float,
        durationMillis: Long,
    ) = performTouchInput {
        swipe(center, center + Offset(dxDp.dp.toPx(), 0f), durationMillis = durationMillis)
    }

    private fun waitForTheFrontCardToBe(title: String) {
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { frontTitle() == title }
        composeRule.waitForIdle()
        assertEquals(title, frontTitle())
    }

    // The swipe ------------------------------------------------------------------------------------------------------

    @Test
    fun aSwipePastTheThresholdDismissesThroughTheGatewayAndTheNextCardComesForward() {
        show()
        composeRule.onNodeWithText("First").swipeSideways(250f, durationMillis = 300)
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { sent.isNotEmpty() }
        assertEquals(listOf("First" to StreamAction.Dismiss), sent)
        waitForTheFrontCardToBe("Second")
    }

    @Test
    fun aSwipeTheOtherWayDismissesToo() {
        show()
        composeRule.onNodeWithText("First").swipeSideways(-250f, durationMillis = 300)
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { sent.isNotEmpty() }
        assertEquals(listOf("First" to StreamAction.Dismiss), sent)
        waitForTheFrontCardToBe("Second")
    }

    @Test
    fun aShortSlowSwipeSettlesBackAndSendsNothing() {
        show()
        composeRule.onNodeWithText("First").swipeSideways(40f, durationMillis = 800)
        composeRule.waitForIdle()
        assertTrue("nothing was sent: $sent", sent.isEmpty())
        assertEquals("First", frontTitle())
    }

    @Test
    fun aFastFlingDismissesEvenOverAShortDistance() {
        show()
        // 70 dp is far short of 40% of the card, but it is 2000 dp per second.
        composeRule.onNodeWithText("First").swipeSideways(70f, durationMillis = 35)
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { sent.isNotEmpty() }
        assertEquals(listOf("First" to StreamAction.Dismiss), sent)
        waitForTheFrontCardToBe("Second")
    }

    @Test
    fun withTheMotionOnTheCardFliesOffAndTheNextCardComesForward() {
        show(reducedMotion = false)
        composeRule.onNodeWithText("First").swipeSideways(250f, durationMillis = 300)
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { sent.isNotEmpty() }
        assertEquals(listOf("First" to StreamAction.Dismiss), sent)
        waitForTheFrontCardToBe("Second")
    }

    @Test
    fun aDismissalThatDoesNotTakeBringsTheCardBack() {
        show(reducedMotion = false)
        dismissTakes = false
        composeRule.onNodeWithText("First").swipeSideways(250f, durationMillis = 300)
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { sent.isNotEmpty() }
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        val card = composeRule.onNodeWithText("First").fetchSemanticsNode()
        val root = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("the card is back in the window: ${card.boundsInRoot}", card.boundsInRoot.right <= root.right + 1f)
        assertEquals("First", frontTitle())
    }

    /** The front card's slot (the node that carries "Card 1 of N", the swipe's hooks and the Dismiss action). */
    private fun frontSlot() =
        composeRule.onAllNodes(
            androidx.compose.ui.test.SemanticsMatcher("the front card") { node ->
                node.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("Card 1 of") == true
            },
        ).fetchSemanticsNodes().first()

    /** How far the card of [node] has been taken sideways, in px (positive: right): the swipe's own hook. */
    private fun offsetOf(node: androidx.compose.ui.semantics.SemanticsNode) =
        node.config.getOrNull(SwipeOffsetKey) ?: error("no swipe on the node")

    @Test
    fun aNotificationThatCannotBeDismissedIsNeverDismissedAndItsCardResistsAndSettlesBack() {
        show(firstDismissible = false)
        val before = pagerPosition()
        composeRule.onNodeWithText("First").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(-25f * dp, 0f), delayMillis = 16) }
        }
        composeRule.waitForIdle()
        // Mid-drag: the card gave a few dp the way the finger went, and the pager did not move.
        val moved = -offsetOf(frontSlot())
        assertTrue("the card resisted by a few dp (not zero, not a page): $moved px", moved > 1f && moved <= 13f * dp)
        assertEquals(before, pagerPosition(), 0.5f)
        composeRule.onNodeWithText("First").performTouchInput { up() }
        composeRule.waitForIdle()
        assertTrue("nothing was sent: $sent", sent.isEmpty())
        assertEquals(before, pagerPosition(), 0.5f)
        assertEquals("settled back", 0f, offsetOf(frontSlot()), 0.5f)
        assertEquals("First", frontTitle())
    }

    @Test
    fun aFastSwipeOnANonDismissibleCardStillDoesNotPageOrDismiss() {
        show(firstDismissible = false)
        val before = pagerPosition()
        composeRule.onNodeWithText("First").swipeSideways(-250f, durationMillis = 60)
        composeRule.waitForIdle()
        assertTrue("nothing was sent: $sent", sent.isEmpty())
        assertEquals(before, pagerPosition(), 0.5f)
        assertEquals("First", frontTitle())
    }

    @Test
    fun aSwipeOnAStripResistsLikeAnyOtherCardNeitherPagingNorDismissingIt() {
        show()
        val before = pagerPosition()
        val strip = composeRule.onNode(hasContentDescription("Second", substring = true))
        strip.performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(-25f * dp, 0f), delayMillis = 16) }
        }
        composeRule.waitForIdle()
        val moved = -offsetOf(strip.fetchSemanticsNode())
        assertTrue("the strip resisted by a few dp: $moved px", moved > 1f && moved <= 13f * dp)
        assertEquals(before, pagerPosition(), 0.5f)
        strip.performTouchInput { up() }
        composeRule.waitForIdle()
        assertTrue("nothing was sent: $sent", sent.isEmpty())
        assertEquals(before, pagerPosition(), 0.5f)
        // Still behind the front card: a strip is never dismissed, and it is back where it was.
        assertEquals("First", frontTitle())
        assertEquals("settled back", 0f, offsetOf(strip.fetchSemanticsNode()), 0.5f)
    }

    @Test
    fun theDismissCueAppearsOnlyPastTheThresholdAndReleasingThereDismisses() {
        show()
        val width = composeRule.onNodeWithText("First").fetchSemanticsNode().size.width.toFloat()
        composeRule.onNodeWithText("First").performTouchInput {
            down(center)
            repeat(5) { moveBy(Offset(width * 0.3f / 5f, 0f), delayMillis = 16) }
        }
        composeRule.waitForIdle()
        var slot = frontSlot()
        assertEquals(false, slot.config.getOrNull(DismissArmedKey))
        assertEquals(0f, slot.config.getOrNull(DismissCueAlphaKey))
        composeRule.onNodeWithText("First").performTouchInput {
            repeat(5) { moveBy(Offset(width * 0.25f / 5f, 0f), delayMillis = 16) }
        }
        composeRule.waitForIdle()
        slot = frontSlot()
        assertEquals("past 40% of the width the card is armed", true, slot.config.getOrNull(DismissArmedKey))
        assertEquals(1f, slot.config.getOrNull(DismissCueAlphaKey) ?: 0f, 0.001f)
        assertTrue("nothing is sent before the finger lifts: $sent", sent.isEmpty())
        composeRule.onNodeWithText("First").performTouchInput {
            advanceEventTime(300)
            up()
        }
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { sent.isNotEmpty() }
        assertEquals(listOf("First" to StreamAction.Dismiss), sent)
    }

    @Test
    fun aCardThatResistsNeverShowsTheDismissCue() {
        show(firstDismissible = false)
        composeRule.onNodeWithText("First").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(30f * dp, 0f), delayMillis = 16) }
        }
        composeRule.waitForIdle()
        val slot = frontSlot()
        assertEquals(false, slot.config.getOrNull(DismissArmedKey))
        assertEquals(0f, slot.config.getOrNull(DismissCueAlphaKey))
        composeRule.onNodeWithText("First").performTouchInput { up() }
    }

    @Test
    fun aSwipeOnTheEmptyRoomAboveTheStackChangesThePage() {
        show()
        val before = pagerPosition()
        composeRule.onRoot().performTouchInput {
            swipe(Offset(width * 0.8f, 150f * dp), Offset(width * 0.2f, 150f * dp), durationMillis = 300)
        }
        composeRule.waitForIdle()
        assertTrue("nothing was sent: $sent", sent.isEmpty())
        assertTrue("the page changed: $before -> ${pagerPosition()}", pagerPosition() != before)
    }

    @Test
    fun thePagersAccessibilityActionsStillChangeThePageWithoutAnySwipe() {
        show()
        val before = pagerPosition()
        val next =
            composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].first { it.label == "Next page" }
        composeRule.runOnUiThread { next.action() }
        composeRule.waitForIdle()
        assertTrue("the page changed: $before -> ${pagerPosition()}", pagerPosition() != before)
        val previous =
            composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).fetchSemanticsNode()
                .config[SemanticsActions.CustomActions].first { it.label == "Previous page" }
        composeRule.runOnUiThread { previous.action() }
        composeRule.waitForIdle()
        assertEquals(before, pagerPosition(), 0.5f)
    }

    @Test
    fun withTheMotionOnACardThatResistsSettlesBackAndTheStackStaysPut() {
        show(reducedMotion = false, firstDismissible = false)
        val before = pagerPosition()
        composeRule.onNodeWithText("First").performTouchInput {
            down(center)
            repeat(8) { moveBy(Offset(-40f * dp, 0f), delayMillis = 16) }
        }
        composeRule.waitForIdle()
        assertTrue(-offsetOf(frontSlot()) > 1f)
        composeRule.onNodeWithText("First").performTouchInput { up() }
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { kotlin.math.abs(offsetOf(frontSlot())) < 0.5f }
        assertEquals(before, pagerPosition(), 0.5f)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun theFrontCardOffersDismissAsAnAccessibilityAction() {
        show()
        val slot =
            composeRule.onAllNodes(
                androidx.compose.ui.test.SemanticsMatcher("the front card") { node ->
                    node.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("Card 1 of") == true
                },
            ).fetchSemanticsNodes().first()
        val dismiss =
            slot.config.getOrNull(SemanticsActions.CustomActions)?.firstOrNull { it.label == "Dismiss" }
                ?: error("no Dismiss action on the front card")
        composeRule.runOnUiThread { dismiss.action() }
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { sent.isNotEmpty() }
        assertEquals(listOf("First" to StreamAction.Dismiss), sent)
        waitForTheFrontCardToBe("Second")
    }

    @Test
    fun aSwipeOnTheOpenCardsHeaderDismissesItAndTheNextCardComesForward() {
        show()
        composeRule.onNodeWithText("First").performClick()
        composeRule.waitForIdle()
        assertTrue("the card opened", theCardIsOpen())
        val card = composeRule.onNodeWithTag(STREAM_OPEN_CARD_TEST_TAG).fetchSemanticsNode()
        composeRule.onRoot().performTouchInput {
            swipe(
                Offset(card.positionInRoot.x + card.size.width * 0.2f, card.positionInRoot.y + 40f * dp),
                Offset(card.positionInRoot.x + card.size.width * 0.9f, card.positionInRoot.y + 40f * dp),
                durationMillis = 300,
            )
        }
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { sent.isNotEmpty() }
        assertEquals(listOf("First" to StreamAction.Dismiss), sent)
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { !theCardIsOpen() }
        waitForTheFrontCardToBe("Second")
    }

    private companion object {
        const val MINUTE = 60_000L
        const val WAIT_MILLIS = 10_000L
    }
}
