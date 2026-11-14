package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.RiffleLauncherTheme
import com.riffle.app.launcher.stream.BROWSE_OWN_ACTIONS_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.app.launcher.stream.STREAM_PAGER_TEST_TAG
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamDockLink
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.app.launcher.stream.streamStackCardTag
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.notifications.NotificationPriority
import com.riffle.core.domain.launcher.stream.NotificationAction
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.NotificationOwnAction
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamAppRef
import com.riffle.core.domain.launcher.stream.StreamContent
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
 * A live notification's own buttons on its browse card (device fixes, round 6, item 3), through the real stack on a
 * 320 dp screen: an ongoing trip shows "End Trip", "Prev" and "Next" on the front card, each a 48 dp target and a
 * custom action; a tap on one performs it and does not open the card; a tap on the card still opens it; a vertical
 * drag that starts on a button moves the stack; and a notification that is not live keeps its buttons for the open
 * card.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class LiveNotificationBrowseActionsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val page = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "nav:personal")
    private val actions = mutableListOf<Pair<StreamContent, StreamAction>>()
    private val own =
        listOf(
            NotificationOwnAction("provider:0", "End Trip"),
            NotificationOwnAction("provider:1", "Prev"),
            NotificationOwnAction("provider:2", "Next"),
        )

    private fun note(
        id: Int,
        title: String,
        live: Boolean,
    ) = NotificationContent(
        id = StreamContentId("n:nav:$id"),
        app = StreamAppRef("nav", "personal"),
        appLabel = "Navigator",
        title = title,
        text = "3 stops away",
        icon = null,
        image = null,
        postedAtEpochMillis = StreamFixtures.NOW - id * 60_000L,
        category = NotificationCategory.NAVIGATION,
        priority = NotificationPriority.DEFAULT,
        target = ItemTarget.None,
        // Ongoing: it cannot be dismissed, so it has no swipe.
        actions = setOf(NotificationAction.OPEN),
        privacy = StreamPrivacy.VISIBLE,
        notificationKey = "k:nav:$id",
        ownActions = own,
        live = live,
    )

    private fun show(vararg notes: NotificationContent) {
        val results: Map<StreamSection, StreamSectionResult> =
            StreamSection.layoutSections.associateWith<StreamSection, StreamSectionResult> {
                StreamSectionResult.Empty
            } +
                (
                    StreamSection.NOTIFICATIONS to
                        StreamSectionResult.Ready(listOf(StreamGroup("nav:personal", "Navigator", notes.toList())))
                )
        composeRule.setContent {
            RiffleLauncherTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    StreamSurface(
                        layout = StreamLayout.DEFAULT,
                        results = results,
                        env = StreamFixtures.environment(reducedMotion = true),
                        callbacks = StreamCallbacks(onAction = { content, action -> actions += content to action }),
                        initialPageKey = page,
                        dockLink = StreamDockLink(),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** One of the row's buttons: its title on a node that takes a click (the card's own text does not include it). */
    private fun button(title: String) = hasText(title) and androidx.compose.ui.test.hasClickAction()

    @Test
    fun aLiveNotificationShowsItsButtonsOnTheFrontCardEachA48DpTarget() {
        show(note(1, "Get off at Angel", live = true), note(2, "Earlier", live = false))
        val density = composeRule.density.density
        for (title in listOf("End Trip", "Prev", "Next")) {
            composeRule.onNode(button(title)).assertIsDisplayed()
            val button = composeRule.onNode(button(title)).fetchSemanticsNode()
            assertTrue("$title is 48 dp tall: ${button.size.height / density}", button.size.height / density >= 47.5f)
            assertTrue("$title is 48 dp wide: ${button.size.width / density}", button.size.width / density >= 47.5f)
        }
        // The whole row is inside the card.
        val card = composeRule.onNodeWithTag(streamStackCardTag("n:nav:1")).fetchSemanticsNode()
        val row = composeRule.onNodeWithTag(BROWSE_OWN_ACTIONS_TEST_TAG, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue(
            "the buttons end inside the card",
            row.positionInRoot.y + row.size.height <= card.positionInRoot.y + card.size.height + 0.5f,
        )
        composeRule.captureScreen()
    }

    @Test
    fun aTapOnAButtonPerformsItAndDoesNotOpenTheCard() {
        show(note(1, "Get off at Angel", live = true), note(2, "Earlier", live = false))
        composeRule.onNode(button("Next")).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf<StreamAction>(StreamAction.OwnAction("provider:2")), actions.map { it.second })
        assertEquals(0, composeRule.onAllNodesWithTag(STREAM_OPEN_CARD_TEST_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun aTapOnTheCardStillOpensItInPlace() {
        show(note(1, "Get off at Angel", live = true), note(2, "Earlier", live = false))
        composeRule.onNodeWithText("Get off at Angel").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(STREAM_OPEN_CARD_TEST_TAG).assertIsDisplayed()
        assertEquals(emptyList<StreamAction>(), actions.map { it.second })
    }

    @Test
    fun theButtonsAreCustomActionsOfTheCard() {
        show(note(1, "Get off at Angel", live = true), note(2, "Earlier", live = false))
        val labels =
            composeRule.onNode(hasText("Get off at Angel") and androidx.compose.ui.test.hasClickAction())
                .fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertTrue(labels.map { it.label }.containsAll(listOf("End Trip", "Prev", "Next")))
        labels.first { it.label == "Prev" }.action()
        assertEquals(listOf<StreamAction>(StreamAction.OwnAction("provider:1")), actions.map { it.second })
    }

    @Test
    fun aVerticalDragThatStartsOnAButtonMovesTheStackAndPerformsNothing() {
        show(note(1, "Get off at Angel", live = true), note(2, "Earlier", live = false))
        val button = composeRule.onNode(button("Next")).fetchSemanticsNode()
        val pager = composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).fetchSemanticsNode()
        val start =
            button.positionInRoot + Offset(button.size.width / 2f, button.size.height / 2f) - pager.positionInRoot
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput {
            down(start)
            repeat(10) { moveBy(Offset(0f, 7.dp.toPx()), delayMillis = 16) }
            advanceEventTime(300)
            up()
        }
        composeRule.waitForIdle()
        frontCardIsNow("n:nav:2")
        assertEquals(emptyList<StreamAction>(), actions.map { it.second })
    }

    private fun frontCardIsNow(id: String) {
        val front = composeRule.onNodeWithTag(streamStackCardTag(id)).fetchSemanticsNode()
        assertTrue(front.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("Card 2 ") == true)
    }

    @Test
    fun aNotificationThatIsNotLiveKeepsItsButtonsForTheOpenCard() {
        show(note(1, "Trip saved", live = false), note(2, "Earlier", live = false))
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(
                BROWSE_OWN_ACTIONS_TEST_TAG,
                useUnmergedTree = true,
            ).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun aLiveCardBehindTheFrontOneIsOneNodeAndItsButtonsAreOutOfReach() {
        show(note(1, "Front", live = false), note(2, "Trip", live = true))
        // The strip is one node (the whole card's text, one action): no button of the card under it can be tapped.
        val strip = composeRule.onNodeWithTag(streamStackCardTag("n:nav:2")).fetchSemanticsNode()
        assertEquals(null, strip.config.getOrNull(SemanticsActions.CustomActions))
        assertEquals(0, composeRule.onAllNodes(button("Next")).fetchSemanticsNodes().size)
    }
}
