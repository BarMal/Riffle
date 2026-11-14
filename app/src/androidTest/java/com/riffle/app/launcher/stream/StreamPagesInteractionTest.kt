package com.riffle.app.launcher.stream

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.RiffleLauncherTheme
import com.riffle.app.launcher.expressions.NoExpressionImageLoader
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.notifications.NotificationPriority
import com.riffle.core.domain.launcher.stream.NotificationAction
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.StreamAppRef
import com.riffle.core.domain.launcher.stream.StreamContentId
import com.riffle.core.domain.launcher.stream.StreamGroup
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamPrivacy
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.workspace.ItemTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Stream's pages on a real touch pipeline (ADR 0003, Views, Amendment 3; docs/product/gestures.md): a sideways swipe
 * changes the page and tells the Dock (which moves its ring), a vertical swipe moves the stack inside the page and never
 * the pager, and the pager's accessibility actions are the non-gesture ways (the Dock's tiles are the other, tested with
 * the Dock). The stack is anchored to the Dock (dock modes slice 4a):
 * dragging down brings older cards (peeking above the front one) toward the front, a fling carries it on, a tap on a
 * peeking card brings it to the front and the stack's own Next and Previous card actions do the same without a drag.
 */
@RunWith(AndroidJUnit4::class)
class StreamPagesInteractionTest {
    /** The front card says where it is in its stack ("Card 2 of 3") as its state description; there is no pill. */
    private fun position(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    private val anyPosition =
        SemanticsMatcher("a card position") { node ->
            node.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("Card ") == true
        }

    @get:Rule
    val composeRule = createComposeRule()

    private val chat = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "chat:personal")
    private val mail = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "mail:personal")

    private fun note(
        app: String,
        id: Int,
        title: String,
    ) = NotificationContent(
        id = StreamContentId("n:$app:$id"),
        app = StreamAppRef(app, "personal"),
        appLabel = app.replaceFirstChar { it.uppercase() },
        title = title,
        text = "Body of $title",
        icon = null,
        image = null,
        postedAtEpochMillis = NOW - id * MINUTE,
        category = null,
        priority = NotificationPriority.DEFAULT,
        target = ItemTarget.None,
        actions = setOf(NotificationAction.OPEN, NotificationAction.DISMISS),
        privacy = StreamPrivacy.VISIBLE,
        notificationKey = "k:$app:$id",
    )

    private val results: Map<StreamSection, StreamSectionResult> =
        StreamSection.layoutSections.associateWith { StreamSectionResult.Empty } +
            (
                StreamSection.NOTIFICATIONS to
                    StreamSectionResult.Ready(
                        listOf(
                            StreamGroup(
                                "chat:personal",
                                "Chat",
                                listOf(
                                    note("chat", 1, "First chat"),
                                    note("chat", 2, "Second chat"),
                                    note("chat", 3, "Third chat"),
                                ),
                            ),
                            StreamGroup("mail:personal", "Mail", listOf(note("mail", 1, "Only mail"))),
                        ),
                    )
            )

    /** What the Stream tells the Dock: the page in front. */
    private val dock = StreamDockLink()

    private fun show(
        layout: StreamLayout = StreamLayout.DEFAULT,
        reduced: Boolean = true,
    ) {
        composeRule.setContent {
            RiffleLauncherTheme {
                StreamSurface(
                    layout = layout,
                    results = results,
                    env =
                        StreamEnvironment(
                            imageLoader = NoExpressionImageLoader,
                            appIconLoader = NoIcons,
                            installedApps = emptyList(),
                            reducedMotion = reduced,
                            formats = SystemStreamFormats(),
                            nowEpochMillis = NOW,
                        ),
                    callbacks = StreamCallbacks(),
                    initialPageKey = chat,
                    dockLink = dock,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertOpen(key: String) {
        composeRule.waitForIdle()
        assertEquals(key, dock.currentKey)
    }

    @Test
    fun aSidewaysSwipeChangesThePageAndTheDockFollows() {
        show()
        assertOpen(chat)

        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        assertOpen(mail)
        composeRule.onNodeWithText("Only mail").assertIsDisplayed()
    }

    @Test
    fun aVerticalSwipeMovesTheStackAndNeverThePager() {
        show(reduced = false)
        composeRule.onNodeWithText("First chat").assertIsDisplayed()
        composeRule.onNode(position("Card 1 of 3")).assertExists()

        // Inside the stack: the default swipe runs the pager's whole height, so these start high and end low in it
        // (dragging down brings the older cards, which peek above the front one, toward the front), and a fast one
        // flings the stack to its last card.
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput {
            swipeDown(startY = height * STACK_HIGH, endY = height * STACK_LOW)
        }
        composeRule.waitForIdle()

        // The page stayed; the stack moved on from its first card (a fling may carry it more than one).
        assertOpen(chat)
        composeRule.onAllNodes(position("Card 1 of 3")).assertCountEquals(0)
        composeRule.onNode(anyPosition).assertExists()

        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput {
            swipeUp(startY = height * STACK_LOW, endY = height * STACK_HIGH)
        }
        composeRule.waitForIdle()
        assertOpen(chat)
        composeRule.onNode(position("Card 1 of 3")).assertExists()
        composeRule.onNodeWithText("First chat").assertIsDisplayed()
    }

    /** A slow drag of [fingerDy] px (down is positive) from low in the pager, then still, then up: no fling. */
    private fun dragSlowly(fingerDy: Float) {
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput {
            down(Offset(centerX, height * STACK_LOW))
            repeat(DRAG_STEPS) { moveBy(Offset(0f, fingerDy / DRAG_STEPS), delayMillis = 16) }
            advanceEventTime(300)
            up()
        }
        composeRule.waitForIdle()
    }

    private fun assertFront(position: Int) {
        composeRule.waitForIdle()
        composeRule.onNode(position("Card $position of 3")).assertExists()
    }

    @Test
    fun aDragBringsTheNextCardToTheFrontAndASettleLandsOnAWholeCard() {
        show()
        val card = with(composeRule.density) { 90.dp.toPx() }
        dragSlowly(card * 1.2f)
        assertFront(2)
        // Less than half a card goes back to where it was.
        dragSlowly(card * 0.3f)
        assertFront(2)
        dragSlowly(-card * 1.3f)
        assertFront(1)
        // Past the first card the stack gives (a rubber band) and comes back.
        dragSlowly(-card * 2f)
        assertFront(1)
        assertOpen(chat)
    }

    @Test
    fun aHardFlingCarriesTheStackToItsLastCardAndNoFurther() {
        show(reduced = false)
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput {
            swipeDown(startY = height * STACK_HIGH, endY = height * STACK_LOW, durationMillis = 100)
        }
        assertFront(3)
        composeRule.onNodeWithText("Third chat").assertIsDisplayed()
    }

    @Test
    fun aTapOnAPeekingCardBringsItToTheFront() {
        show()
        composeRule.onNodeWithTag(streamStackCardTag("n:chat:2")).performClick()
        assertFront(2)
        composeRule.onNodeWithText("Second chat").assertIsDisplayed()
        composeRule.onNodeWithTag(streamStackCardTag("n:chat:3")).performClick()
        assertFront(3)
        // The card before the front one has sunk into the Dock: it is not a strip any more.
        composeRule.onNodeWithTag(streamStackCardTag("n:chat:2")).assertDoesNotExist()
    }

    private fun stackAction(label: String): () -> Unit {
        val node =
            composeRule.onNode(
                SemanticsMatcher("the stack") { node ->
                    node.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true &&
                        node.config.getOrNull(SemanticsProperties.TestTag) == null
                },
            ).fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].first { it.label == label }
        // Fetch the node off the main thread (fetching synchronises), then run only the action on it.
        return { composeRule.runOnUiThread { action.action() } }
    }

    @Test
    fun nextAndPreviousCardAreTheStacksOwnActionsSoNothingNeedsTheDrag() {
        show()
        val next = stackAction("Next card")
        val previous = stackAction("Previous card")
        next()
        assertFront(2)
        next()
        assertFront(3)
        previous()
        assertFront(2)
        previous()
        assertFront(1)
        composeRule.onNodeWithText("First chat").assertIsDisplayed()
    }

    @Test
    fun aPeekingCardReadsItsHiddenBodyToTalkBack() {
        show()
        val peek = composeRule.onNodeWithTag(streamStackCardTag("n:chat:2")).fetchSemanticsNode()
        val description = peek.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty()
        assertTrue(description, "Body of Second chat" in description)
        assertTrue(peek.config.contains(SemanticsActions.OnClick))
    }

    @Test
    fun thePagersActionsAreTheNonGestureWaysToChangePage() {
        show()
        assertOpen(chat)

        val labels =
            composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).fetchSemanticsNode().config[SemanticsActions.CustomActions]
                .map { it.label }
        assertEquals(listOf("Previous page", "Next page"), labels)

        // Fetch the node off the main thread (fetching synchronises), then run only the action on it.
        val next =
            composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).fetchSemanticsNode().config[SemanticsActions.CustomActions]
                .first { it.label == "Next page" }
        composeRule.runOnUiThread { next.action() }
        composeRule.waitForIdle()
        assertOpen(mail)
    }

    @Test
    fun thePagesAreInTheDocksOrderForItsTilesAndThereIsNoSpine() {
        show()
        composeRule.waitForIdle()
        // Live apps first (the newer notification's app leads), then the built-in pages.
        assertEquals(listOf(chat, mail), dock.pages.map { it.key }.take(2))
        composeRule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Tab),
        ).assertCountEquals(0)
    }

    private object NoIcons : AppIconLoader {
        override fun iconFor(identity: AppIdentity): ImageBitmap? = null

        override fun colorFor(identity: AppIdentity): Color? = null
    }

    private companion object {
        const val STACK_LOW = 0.8f
        const val STACK_HIGH = 0.4f
        const val DRAG_STEPS = 10
        const val MINUTE = 60_000L
        const val NOW = 1_800_000_000_000L
    }
}
