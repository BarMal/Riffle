package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.riffle.app.launcher.LauncherShellContent
import com.riffle.app.launcher.sources.SourceChangeSource
import com.riffle.app.launcher.stream.CARD_OWN_ACTIONS_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.settings.CardActionsPlacement
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.NotificationOwnAction
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * The notification's own action buttons on the open card (device fixes, round 5, item 3), through the real shell
 * content on the narrowest folded screen (320 dp): each a text button of at least 48 dp, three fitting with Reply's
 * field, a long title ellipsized, a tap reaching the gateway by id, each also a custom accessibility action, and the
 * card closing cleanly when "mark as read" makes its notification go.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = NARROW_FOLDED)
class OpenCardOwnActionsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val executor = Executors.newSingleThreadExecutor { Thread(it, "own-actions").apply { isDaemon = true } }
    private var shell by mutableStateOf<LauncherShellState?>(null)
    private val sent = CopyOnWriteArrayList<Pair<String, StreamAction>>()
    private val active = mutableListOf<LauncherNotification>()
    private val watchers = CopyOnWriteArrayList<() -> Unit>()
    private val changes =
        SourceChangeSource { listener ->
            watchers += listener
            return@SourceChangeSource { watchers -= listener }
        }
    private val chatPackage = ScreenshotFixtures.chat.identity.packageName.value
    private val chatPage = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "$chatPackage:personal")
    private val dp get() = composeRule.density.density

    private val longTitle = "Like this conversation and everything that was ever said in it"

    private val firstOwnActions =
        listOf(
            NotificationOwnAction("provider:1", "Mark as read"),
            NotificationOwnAction("provider:2", "Archive"),
            NotificationOwnAction("provider:3", longTitle),
        )

    /** Archive fails (its intent was cancelled); Mark as read makes the notification go, as the app does. */
    private val gateway =
        object : StreamActionGateway {
            override fun perform(
                content: StreamContent,
                action: StreamAction,
            ): StreamActionResult {
                val note = content as? NotificationContent ?: return StreamActionResult.NotOffered
                sent += note.title.orEmpty() to action
                if (action == StreamAction.OwnAction("provider:1")) {
                    synchronized(active) { active.removeAll { it.key.value == note.notificationKey } }
                    watchers.forEach { it() }
                }
                return if (action == StreamAction.OwnAction("provider:2")) {
                    StreamActionResult.Failed
                } else {
                    StreamActionResult.Performed
                }
            }
        }

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private fun note(
        index: Int,
        title: String,
    ) = LauncherNotification(
        key = LauncherNotificationKey("0|chat|$index"),
        packageName = AppPackageName(chatPackage),
        canDismiss = true,
        title = title,
        text = "The $title message.",
        messages = emptyList(),
        postedAtEpochMillis = StreamFixtures.NOW - index * MINUTE,
    )

    private fun show(placement: CardActionsPlacement) {
        active += listOf(note(1, "First"), note(2, "Second"))
        val host =
            realWiringStreamHost(
                executor = executor,
                activeNotificationsNow = { synchronized(active) { active.toList() } },
                notificationChanges = changes,
                installedApps = ScreenshotFixtures.installedApps,
                canReply = { true },
                canSnooze = { true },
                ownActions = { key -> if (key.value == "0|chat|1") firstOwnActions else emptyList() },
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
                        stream = settings.stream.copy(cardActionsPlacement = placement),
                        motion = settings.motion.copy(reducedMotionPreference = ReducedMotionPreference.ON),
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
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) {
            composeRule.onAllNodesWithText("First").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("First").performClick()
        composeRule.waitForIdle()
        assertTrue("the card opened", theCardIsOpen())
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

    private fun theCardIsOpen() =
        composeRule.onAllNodes(hasTestTag(STREAM_OPEN_CARD_TEST_TAG)).fetchSemanticsNodes().isNotEmpty()

    private fun boundsOf(text: String): Rect = composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot

    private fun buttonTitles() = listOf("Mark as read", "Archive")

    private fun assertTheButtonsFit() {
        val window = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
        val buttons = buttonTitles().map { boundsOf(it) }
        buttons.forEach {
            assertTrue("a button is a 48 dp target: ${it.height / dp} dp", it.height >= 48f * dp - 1f)
            assertTrue("a button is at least 48 dp wide: ${it.width / dp} dp", it.width >= 48f * dp - 1f)
            assertTrue("a button is inside the window: $it", it.left >= window.left && it.right <= window.right + 1f)
        }
        // They share one row without overlapping, in the notification's order, and the third, long, title is
        // ellipsized inside the window.
        assertTrue("in order, without overlap: $buttons", buttons[0].right <= buttons[1].left + 1f)
        val row =
            composeRule.onAllNodesWithTag(
                CARD_OWN_ACTIONS_TEST_TAG,
                useUnmergedTree = true,
            ).fetchSemanticsNodes().single().boundsInRoot
        assertTrue("the row is inside the window: $row", row.left >= window.left && row.right <= window.right + 1f)
        // Reply's field is there with them.
        composeRule.onNodeWithText("Write a reply").assertIsDisplayed()
    }

    // The scenarios -----------------------------------------------------------------------------------------------

    @Test
    fun onTheNarrowestFoldedCardThreeButtonsFitBesideRepliesFieldInTheDocksRow() {
        show(CardActionsPlacement.IN_DOCK)
        assertTheButtonsFit()
        composeRule.captureScreen()
    }

    @Test
    fun andOnTheCardItselfWhenTheActionsSitThere() {
        show(CardActionsPlacement.ON_CARD)
        assertTheButtonsFit()
        composeRule.captureScreen()
        val card = composeRule.onNode(hasTestTag(STREAM_OPEN_CARD_TEST_TAG)).fetchSemanticsNode().boundsInRoot
        listOf("Mark as read", "Archive").forEach {
            val b = boundsOf(it)
            assertTrue("$it is on the card: $b in $card", b.left >= card.left - 1f && b.right <= card.right + 1f)
        }
    }

    @Test
    fun aTapOnAButtonReachesTheGatewayByIdAndAFailureLeavesTheCardOpenWithNothingCrashing() {
        show(CardActionsPlacement.IN_DOCK)
        composeRule.onNodeWithText("Archive").performClick()
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { sent.isNotEmpty() }
        assertEquals(listOf("First" to StreamAction.OwnAction("provider:2")), sent.toList())
        composeRule.waitForIdle()
        assertTrue("the card is still open after the failure", theCardIsOpen())
    }

    @Test
    fun markAsReadMakesTheNotificationGoAndTheCardClosesCleanlyWithTheNextCardForward() {
        show(CardActionsPlacement.IN_DOCK)
        composeRule.onNodeWithText("Mark as read").performClick()
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { sent.isNotEmpty() }
        assertEquals(listOf("First" to StreamAction.OwnAction("provider:1")), sent.toList())
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { !theCardIsOpen() }
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) {
            composeRule.onAllNodesWithText("Second").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        assertTrue("the next card is in front", composeRule.onAllNodesWithText("First").fetchSemanticsNodes().isEmpty())
        assertTrue(
            "and no buttons are left",
            composeRule.onAllNodesWithTag(
                CARD_OWN_ACTIONS_TEST_TAG,
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun eachButtonIsAlsoACustomAccessibilityActionOfTheOpenCard() {
        show(CardActionsPlacement.ON_CARD)
        val nodes =
            composeRule.onAllNodes(
                hasAnyAncestor(hasTestTag(STREAM_OPEN_CARD_TEST_TAG)) or hasTestTag(STREAM_OPEN_CARD_TEST_TAG),
            )
                .fetchSemanticsNodes()
        val actions = nodes.flatMap { it.config.getOrNull(SemanticsActions.CustomActions).orEmpty() }
        assertTrue("Mark as read: ${actions.map { it.label }}", actions.any { it.label == "Mark as read" })
        assertTrue("Archive", actions.any { it.label == "Archive" })
        assertTrue("the long one", actions.any { it.label == longTitle })
        val like = actions.first { it.label == longTitle }
        composeRule.runOnUiThread { like.action() }
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { sent.isNotEmpty() }
        assertEquals(listOf("First" to StreamAction.OwnAction("provider:3")), sent.toList())
    }

    @Test
    fun aNotificationWithNoOwnButtonsHasNoRow() {
        show(CardActionsPlacement.IN_DOCK)
        // Bring the second card, which has none, to the front and open it.
        composeRule.onNodeWithText("Mark as read").performClick()
        composeRule.waitUntil(timeoutMillis = WAIT_MILLIS) { !theCardIsOpen() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Second").performClick()
        composeRule.waitForIdle()
        assertTrue(theCardIsOpen())
        assertTrue(
            composeRule.onAllNodesWithTag(
                CARD_OWN_ACTIONS_TEST_TAG,
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty(),
        )
    }

    private companion object {
        const val MINUTE = 60_000L
        const val WAIT_MILLIS = 10_000L
    }
}

/** The narrowest screen a folded phone has in the device tests: 320 dp wide. */
private const val NARROW_FOLDED = "w320dp-h640dp-normal-notlong-notround-any-xhdpi-keyshidden-nonav"
