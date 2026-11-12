package com.riffle.app.launcher.stream

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
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
 * The Stream's pages on a real touch pipeline (ADR 0003, Views; docs/product/gestures.md): a sideways swipe changes the
 * page and moves the spine, a vertical swipe moves the stack inside the page and never the pager, the spine's tabs and
 * the pager's accessibility actions are the non-gesture ways, and the Stage view brings a rail stage to the centre.
 */
@RunWith(AndroidJUnit4::class)
class StreamPagesInteractionTest {
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
        StreamSection.entries.associateWith { StreamSectionResult.Empty } +
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

    private fun show(
        layout: StreamLayout = StreamLayout.DEFAULT,
        wide: Boolean = false,
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
                            reducedMotion = true,
                            formats = SystemStreamFormats(),
                            nowEpochMillis = NOW,
                        ),
                    callbacks = StreamCallbacks(),
                    initialPageKey = chat,
                    wideLayout = wide,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun marker(key: String) = composeRule.onNodeWithTag(streamSpineMarkerTestTag(key))

    @Test
    fun aSidewaysSwipeChangesThePageAndTheSpineFollows() {
        show()
        marker(chat).assertIsSelected()

        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        marker(mail).assertIsSelected()
        composeRule.onNodeWithText("Only mail").assertIsDisplayed()
    }

    @Test
    fun aVerticalSwipeMovesTheStackAndNeverThePager() {
        show()
        composeRule.onNodeWithText("First chat").assertIsDisplayed()
        composeRule.onNodeWithText("Card 1 of 3").assertIsDisplayed()

        // Inside the stack, below the page's header row: the default swipe runs the pager's whole height, so a swipe down
        // starts over the header (not the stack) and a fast one flings the stack to its last card.
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput {
            swipeUp(startY = height * STACK_LOW, endY = height * STACK_HIGH)
        }
        composeRule.waitForIdle()

        // The page stayed; the stack moved on from its first card (a fling may carry it more than one).
        marker(chat).assertIsSelected()
        composeRule.onNodeWithText("Card 1 of 3").assertDoesNotExist()
        composeRule.onNode(hasText("Card ", substring = true)).assertIsDisplayed()

        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput {
            swipeDown(startY = height * STACK_HIGH, endY = height * STACK_LOW)
        }
        composeRule.waitForIdle()
        marker(chat).assertIsSelected()
        composeRule.onNodeWithText("Card 1 of 3").assertIsDisplayed()
        composeRule.onNodeWithText("First chat").assertIsDisplayed()
    }

    @Test
    fun theSpineAndThePagerActionsAreTheNonGestureWays() {
        show()
        marker(mail).performClick()
        composeRule.waitForIdle()
        marker(mail).assertIsSelected()

        val labels =
            composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).fetchSemanticsNode().config[SemanticsActions.CustomActions]
                .map { it.label }
        assertEquals(listOf("Previous page", "Next page"), labels)
        val role =
            composeRule.onNodeWithTag(
                streamSpineMarkerTestTag(mail),
            ).fetchSemanticsNode().config[SemanticsProperties.Role]
        assertTrue(role.toString().contains("Tab"))
    }

    @Test
    fun theStageBringsARailStageToTheCentre() {
        show(layout = StreamLayout(wideView = com.riffle.core.domain.launcher.stream.StreamWideView.STAGE), wide = true)
        composeRule.onNodeWithTag(STREAM_STAGE_RAIL_TEST_TAG).assertIsDisplayed()

        composeRule.onNodeWithTag(streamStageThumbTestTag(mail)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Only mail").assertIsDisplayed()
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.TestTag, streamStageThumbTestTag(chat)),
        ).assertIsDisplayed()
    }

    private object NoIcons : AppIconLoader {
        override fun iconFor(identity: AppIdentity): ImageBitmap? = null

        override fun colorFor(identity: AppIdentity): Color? = null
    }

    private companion object {
        const val STACK_LOW = 0.8f
        const val STACK_HIGH = 0.4f
        const val MINUTE = 60_000L
        const val NOW = 1_800_000_000_000L
    }
}
