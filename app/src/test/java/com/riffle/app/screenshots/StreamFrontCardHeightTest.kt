package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_PAGER_TEST_TAG
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamDockLink
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.app.launcher.stream.streamStackCardTag
import com.riffle.core.domain.launcher.notifications.NotificationPriority
import com.riffle.core.domain.launcher.stream.BrowseCardHeight
import com.riffle.core.domain.launcher.stream.NotificationAction
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.NotificationMessage
import com.riffle.core.domain.launcher.stream.StreamAppRef
import com.riffle.core.domain.launcher.stream.StreamCardStackSpec
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The front card of a notification shows more and is as tall as what it shows (device fixes, round 6, item 4), through
 * the real stack: a one-line message is a short card with no empty room under it, a long text takes up to four lines,
 * a conversation shows its last messages; the stack stays anchored at its bottom whatever the front card's height, and
 * the strips of the cards behind sit on the front card's top, so they are lower behind a short card.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamFrontCardHeightTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val page = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "chat:personal")
    private val density get() = composeRule.density.density

    private fun note(
        id: Int,
        title: String,
        text: String?,
        thread: List<NotificationMessage> = emptyList(),
    ) = NotificationContent(
        id = StreamContentId("n:chat:$id"),
        app = StreamAppRef("chat", "personal"),
        appLabel = "Chat",
        title = title,
        text = text,
        icon = null,
        image = null,
        postedAtEpochMillis = StreamFixtures.NOW - id * 60_000L,
        category = null,
        priority = NotificationPriority.DEFAULT,
        target = ItemTarget.None,
        actions = setOf(NotificationAction.OPEN, NotificationAction.DISMISS),
        privacy = StreamPrivacy.VISIBLE,
        notificationKey = "k:chat:$id",
        thread = thread,
    )

    private val long =
        "The quarterly report is ready for review. It covers the three regions, the new accounts opened since " +
            "March, the changes to the support rota and the plan for the autumn release. Please read it before " +
            "Thursday's meeting and send comments to the team by Wednesday evening at the latest, thank you."

    private val short = note(1, "Short", "Step C diffs (+1 more)")
    private val tall = note(2, "Tall", long)
    private val chat =
        note(
            3,
            "Sam",
            "Are we still on?",
            thread =
                listOf(
                    NotificationMessage("Sam", "Morning!", 1L),
                    NotificationMessage("Sam", "Got a minute?", 2L),
                    NotificationMessage(null, "Sure", 3L),
                    NotificationMessage("Sam", "Are we still on?", 4L),
                ),
        )
    private val other = note(4, "Other", "One line")

    private fun show(vararg notes: NotificationContent) {
        val results: Map<StreamSection, StreamSectionResult> =
            StreamSection.layoutSections.associateWith<StreamSection, StreamSectionResult> {
                StreamSectionResult.Empty
            } +
                (
                    StreamSection.NOTIFICATIONS to
                        StreamSectionResult.Ready(listOf(StreamGroup("chat:personal", "Chat", notes.toList())))
                )
        composeRule.setContent {
            ScreenshotBackdrop {
                Box(modifier = Modifier.fillMaxSize()) {
                    StreamSurface(
                        layout = StreamLayout.DEFAULT,
                        results = results,
                        env = StreamFixtures.environment(reducedMotion = true),
                        callbacks = StreamCallbacks(),
                        initialPageKey = page,
                        dockLink = StreamDockLink(),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun slot(id: Int): SemanticsNode =
        composeRule.onNodeWithTag(streamStackCardTag("n:chat:$id")).fetchSemanticsNode()

    private fun heightDp(id: Int): Float = slot(id).size.height / density

    private fun topDp(id: Int): Float = slot(id).positionInRoot.y / density

    private fun bottomDp(id: Int): Float = topDp(id) + heightDp(id)

    /** The strip right behind the front card is drawn a step smaller: its slot's size is the card's own, unscaled. */
    private fun stripHeightDp(id: Int): Float = heightDp(id) * (1f - StreamCardStackSpec.SCALE_STEP)

    private fun stripBottomDp(id: Int): Float = topDp(id) + stripHeightDp(id)

    @Test
    fun aOneLineMessageIsAShortCardNotTheOldFixedHeight() {
        show(short, other)
        assertEquals(BrowseCardHeight.MIN_DP, heightDp(1), 0.6f)
        assertTrue(heightDp(1) < StreamCardStackSpec.CARD_HEIGHT_DP)
        // Nothing empty under the line: its text ends just above the card's bottom padding.
        val line = composeRule.onNodeWithText("Step C diffs (+1 more)", useUnmergedTree = true).fetchSemanticsNode()
        val under = bottomDp(1) - (line.positionInRoot.y + line.size.height) / density
        assertTrue("room under the line: $under dp", under in 7f..12f)
        composeRule.captureScreen()
    }

    @Test
    fun aLongTextTakesFourLinesAndTheCardGrowsToHoldThem() {
        show(tall, other)
        val text = composeRule.onNodeWithText(long, useUnmergedTree = true).fetchSemanticsNode()
        val lines = text.size.height / density / BrowseCardHeight.LINE_DP
        assertEquals("four lines of body", BrowseCardHeight.MAX_BODY_LINES.toFloat(), lines, 0.1f)
        assertEquals(BrowseCardHeight.notification(bodyDp = 4 * BrowseCardHeight.LINE_DP), heightDp(2), LINES)
        assertTrue(heightDp(2) <= BrowseCardHeight.MAX_DP + 0.6f)
        // The text ends inside the card.
        assertTrue((text.positionInRoot.y + text.size.height) / density <= bottomDp(2) - 7f)
        composeRule.captureScreen()
    }

    @Test
    fun aConversationShowsItsLastThreeMessagesAsSenderAndText() {
        show(chat, other)
        composeRule.onNodeWithText("Sam: Got a minute?", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("You: Sure", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Sam: Are we still on?", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Sam: Morning!", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(BrowseCardHeight.notification(bodyDp = 3 * BrowseCardHeight.LINE_DP), heightDp(3), LINES)
    }

    @Test
    fun theStackStaysAnchoredAtItsBottomAndTheStripsSitOnTheFrontCardsTop() {
        show(short, tall, other)
        val shortBottom = bottomDp(1)
        val shortTop = topDp(1)
        // The strip behind ends on the front card's top (less the gap), a strip tall.
        assertEquals(StreamCardStackSpec.STRIP_HEIGHT_DP, stripHeightDp(2), 0.6f)
        assertEquals(shortTop - StreamCardStackSpec.GAP_DP, stripBottomDp(2), 0.6f)
        val stripTopBehindShort = topDp(2)
        // Bring the tall card to the front: the same bottom, a higher top, and the strip behind it higher too.
        composeRule.onNodeWithTag(streamStackCardTag("n:chat:2")).performClick()
        composeRule.waitForIdle()
        assertEquals("the stack's bottom does not move", shortBottom, bottomDp(2), 0.6f)
        assertTrue("the tall card's top is higher", topDp(2) < shortTop - 30f)
        assertEquals(topDp(2) - StreamCardStackSpec.GAP_DP, stripBottomDp(4), 0.6f)
        assertTrue("strips are lower behind a short card", stripTopBehindShort > topDp(4) + 30f)
        composeRule.captureScreen()
    }

    @Test
    fun aDragMovesFromAShortCardToATallOneAndBack() {
        show(short, tall, other)

        fun drag(dyDp: Float) {
            composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput {
                down(Offset(centerX, height * 0.9f))
                repeat(10) { moveBy(Offset(0f, dyDp.dp.toPx() / 10), delayMillis = 16) }
                advanceEventTime(300)
                up()
            }
            composeRule.waitForIdle()
        }
        val bottom = bottomDp(1)
        drag(70f)
        assertEquals(BrowseCardHeight.notification(bodyDp = 4 * BrowseCardHeight.LINE_DP), heightDp(2), LINES)
        assertEquals(bottom, bottomDp(2), 0.6f)
        drag(-70f)
        assertEquals(BrowseCardHeight.MIN_DP, heightDp(1), 0.6f)
        assertEquals(bottom, bottomDp(1), 0.6f)
    }

    @Test
    fun aShortCardOpensInPlaceFromItsOwnHeight() {
        show(short, other)
        val bottom = bottomDp(1)
        composeRule.onNodeWithText("Short").performClick()
        composeRule.waitForIdle()
        val open = composeRule.onNodeWithTag(STREAM_OPEN_CARD_TEST_TAG).fetchSemanticsNode()
        // Open, it is at least as tall as it was and still stands on the stack's bottom or above it.
        assertTrue(open.size.height / density >= BrowseCardHeight.MIN_DP)
        assertTrue((open.positionInRoot.y + open.size.height) / density <= bottom + 0.6f)
        // The strip of the card behind sits just above the open card's top.
        val gapToOpen = open.positionInRoot.y / density - stripBottomDp(4)
        assertTrue(
            "the strip sits on the open card: $gapToOpen dp",
            gapToOpen in -0.6f..StreamCardStackSpec.GAP_DP + 0.6f,
        )
    }

    private companion object {
        /** A line of text is a whole number of pixels: four of them may be a dp taller than four times 20 dp. */
        const val LINES = 1.5f
    }
}
