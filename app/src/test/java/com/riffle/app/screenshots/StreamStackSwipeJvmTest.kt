package com.riffle.app.screenshots

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.RiffleLauncherTheme
import com.riffle.app.launcher.expressions.NoExpressionImageLoader
import com.riffle.app.launcher.stream.STREAM_PAGER_TEST_TAG
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamDockLink
import com.riffle.app.launcher.stream.StreamEnvironment
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.app.launcher.stream.SystemStreamFormats
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The device test `StreamPagesInteractionTest.aVerticalSwipeMovesTheStackAndNeverThePager` without an emulator. A swipe
 * inside the stack moves the stack (a fast one flings it more than a card) and never the pager; the swipes here start
 * inside the stack, and the page that stays in front is the one the Dock rings. Dragging down brings older cards toward
 * the front (the stack is bottom-anchored), so the swipes run the other way round than before dock modes slice 4a.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamStackSwipeJvmTest {
    private val dock = StreamDockLink()

    /** The front card says where it is in its stack ("Card 2 of 3") as its state description; there is no pill. */
    private fun position(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    private val anyPosition =
        SemanticsMatcher("a card position") { node ->
            node.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("Card ") == true
        }

    @get:Rule
    val composeRule = createComposeRule()

    private val chat = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "chat:personal")

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

    @Test
    fun aVerticalSwipeMovesTheStackAndNeverThePager() {
        val results: Map<StreamSection, StreamSectionResult> =
            StreamSection.entries.associateWith<StreamSection, StreamSectionResult> { StreamSectionResult.Empty } +
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
        composeRule.setContent {
            RiffleLauncherTheme {
                StreamSurface(
                    layout = StreamLayout.DEFAULT,
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
                    wideLayout = false,
                    dockLink = dock,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("First chat").assertIsDisplayed()
        composeRule.onNode(position("Card 1 of 3")).assertExists()

        // Dragging down brings older cards toward the front (the stack is anchored to the Dock, older cards peek above
        // the front one), and a fast swipe flings it past the first card.
        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput {
            swipeDown(startY = height * STACK_HIGH, endY = height * STACK_LOW)
        }
        composeRule.waitForIdle()
        assertEquals(chat, dock.currentKey)
        composeRule.onAllNodes(position("Card 1 of 3")).assertCountEquals(0)
        composeRule.onNode(anyPosition).assertExists()

        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput {
            swipeUp(startY = height * STACK_LOW, endY = height * STACK_HIGH)
        }
        composeRule.waitForIdle()
        assertEquals(chat, dock.currentKey)
        composeRule.onNode(position("Card 1 of 3")).assertExists()
        composeRule.onNodeWithText("First chat").assertIsDisplayed()
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
