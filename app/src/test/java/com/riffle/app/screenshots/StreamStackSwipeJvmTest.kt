package com.riffle.app.screenshots

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
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
import com.riffle.app.launcher.stream.StreamEnvironment
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.app.launcher.stream.SystemStreamFormats
import com.riffle.app.launcher.stream.streamSpineMarkerTestTag
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The device test `StreamPagesInteractionTest.aVerticalSwipeMovesTheStackAndNeverThePager` without an emulator. A swipe
 * inside the stack moves the stack (a fast one flings it more than a card) and never the pager; the pager's default
 * full-height swipe down starts over the page header, outside the stack, so the swipes here start inside it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamStackSwipeJvmTest {
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
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("First chat").assertIsDisplayed()
        composeRule.onNodeWithText("Card 1 of 3").assertIsDisplayed()

        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput {
            swipeUp(startY = height * STACK_LOW, endY = height * STACK_HIGH)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(streamSpineMarkerTestTag(chat)).assertIsSelected()
        composeRule.onNodeWithText("Card 1 of 3").assertDoesNotExist()
        composeRule.onNode(hasText("Card ", substring = true)).assertIsDisplayed()

        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput {
            swipeDown(startY = height * STACK_HIGH, endY = height * STACK_LOW)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(streamSpineMarkerTestTag(chat)).assertIsSelected()
        composeRule.onNodeWithText("Card 1 of 3").assertIsDisplayed()
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
