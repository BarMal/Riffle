package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamSectionBlock
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamLayoutReducer
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.stream.StreamView
import com.riffle.core.domain.launcher.workspace.SourceIds
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Stream (ADR 0003, P4): every section in both views, drawn by the real section block from typed content, on
 * compact
 * phones, plus the section states, an expanded notification thread and now-playing queue, and the whole surface in the
 * dark, with large text, and unfolded (where text caps its width at 640 dp). The clock, the images and the locale are
 * fixed (`StreamFixtures`), so a render is the same every run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nowStack() = section(StreamSection.NOW, StreamView.STACK)

    @Test
    fun nowList() = section(StreamSection.NOW, StreamView.LIST)

    @Test
    fun notificationsStack() = section(StreamSection.NOTIFICATIONS, StreamView.STACK)

    @Test
    fun notificationsList() = section(StreamSection.NOTIFICATIONS, StreamView.LIST)

    @Test
    fun recentsStack() = section(StreamSection.RECENTS, StreamView.STACK)

    @Test
    fun recentsList() = section(StreamSection.RECENTS, StreamView.LIST)

    @Test
    fun agendaStack() = section(StreamSection.AGENDA, StreamView.STACK)

    @Test
    fun agendaList() = section(StreamSection.AGENDA, StreamView.LIST)

    @Test
    fun tasksStack() = section(StreamSection.TASKS, StreamView.STACK, StreamFixtures.ready(StreamSection.TASKS))

    @Test
    fun tasksList() = section(StreamSection.TASKS, StreamView.LIST, StreamFixtures.ready(StreamSection.TASKS))

    @Test
    fun feedsStack() = section(StreamSection.FEEDS, StreamView.STACK)

    @Test
    fun feedsList() = section(StreamSection.FEEDS, StreamView.LIST)

    @Test
    fun tasksNotConnected() = section(StreamSection.TASKS, StreamView.LIST, StreamSectionResult.NotConnected())

    @Test
    fun tasksEmpty() = section(StreamSection.TASKS, StreamView.STACK, StreamSectionResult.Empty)

    @Test
    fun tasksEmptyList() = section(StreamSection.TASKS, StreamView.LIST, StreamSectionResult.Empty)

    @Test
    fun tasksNotConnectedStack() = section(StreamSection.TASKS, StreamView.STACK, StreamSectionResult.NotConnected())

    @Test
    fun tasksTokenRejected() =
        section(
            StreamSection.TASKS,
            StreamView.LIST,
            StreamSectionResult.NotConnected(com.riffle.core.domain.launcher.stream.NotConnectedReason.TOKEN_REJECTED),
        )

    @Test
    fun tasksErrorStack() = section(StreamSection.TASKS, StreamView.STACK, StreamSectionResult.Unavailable)

    @Test
    fun tasksErrorList() = section(StreamSection.TASKS, StreamView.LIST, StreamSectionResult.Unavailable)

    @Test
    fun tasksStaleList() =
        section(
            StreamSection.TASKS,
            StreamView.LIST,
            StreamSectionResult.Ready(
                listOf(
                    com.riffle.core.domain.launcher.stream.StreamGroup(
                        com.riffle.core.domain.launcher.stream.StreamGroupKeys.TASKS,
                        null,
                        StreamFixtures.staleTasks,
                    ),
                ),
            ),
        )

    @Test
    fun notificationsPermissionRequired() =
        section(
            StreamSection.NOTIFICATIONS,
            StreamView.STACK,
            StreamSectionResult.PermissionRequired(listOf(SourceIds.NOTIFICATIONS)),
        )

    @Test
    fun agendaEmpty() = section(StreamSection.AGENDA, StreamView.LIST, StreamSectionResult.Empty)

    @Test
    fun feedsOff() = section(StreamSection.FEEDS, StreamView.STACK, StreamSectionResult.Off)

    @Test
    fun recentsLoading() = section(StreamSection.RECENTS, StreamView.STACK, StreamSectionResult.Loading)

    @Test
    fun nowUnavailable() = section(StreamSection.NOW, StreamView.STACK, StreamSectionResult.Unavailable)

    @Test
    fun notificationExpandedToItsThreadWithReply() {
        composeRule.setContent {
            StreamBackdrop {
                StreamSectionBlock(
                    section = StreamSection.NOTIFICATIONS,
                    view = StreamView.STACK,
                    result = StreamFixtures.ready(StreamSection.NOTIFICATIONS),
                    env = StreamFixtures.environment(),
                    callbacks = StreamFixtures.callbacks(),
                    canMoveUp = true,
                    canMoveDown = true,
                    onShowAs = {},
                )
            }
        }
        // The stack composes the focused card and its neighbours; the focused one is the leftmost.
        val replyButtons = composeRule.onAllNodesWithText("Reply")
        val focused = replyButtons.fetchSemanticsNodes().withIndex().minBy { it.value.boundsInRoot.left }.index
        replyButtons[focused].performClick()
        composeRule.captureScreen()
    }

    @Test
    fun nowPlayingExpandedToItsQueue() {
        composeRule.setContent {
            StreamBackdrop {
                StreamSectionBlock(
                    section = StreamSection.NOW,
                    view = StreamView.STACK,
                    result = StreamSectionResult.Ready(StreamFixtures.ready(StreamSection.NOW).groups.take(1)),
                    env = StreamFixtures.environment(),
                    callbacks = StreamFixtures.callbacks(),
                    canMoveUp = false,
                    canMoveDown = true,
                    onShowAs = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Show queue").performClick()
        composeRule.captureScreen()
    }

    @Test
    fun surfaceWithEverySection() = surface()

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun surfaceWithEverySectionDark() = surface()

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun surfaceWithEverySectionLargeFont() = surface()

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun surfaceWithEverySectionUnfolded() = surface()

    @Test
    fun surfaceWithReducedTransparencyAndReducedMotion() = surface(reducedTransparency = true, reducedMotion = true)

    @Test
    fun surfaceWithASectionHiddenAndAnotherReordered() {
        val layout =
            listOf(
                StreamLayoutAction.SetEnabled(StreamSection.FEEDS, false),
                StreamLayoutAction.Move(StreamSection.AGENDA, -3),
                StreamLayoutAction.SetView(StreamSection.NOTIFICATIONS, StreamView.LIST),
            ).fold(StreamLayout.DEFAULT, StreamLayoutReducer::reduce)
        surface(layout = layout)
    }

    private fun section(
        section: StreamSection,
        view: StreamView,
        result: StreamSectionResult = StreamFixtures.ready(section),
    ) {
        composeRule.setContent {
            StreamBackdrop {
                StreamSectionBlock(
                    section = section,
                    view = view,
                    result = result,
                    env = StreamFixtures.environment(),
                    callbacks = StreamFixtures.callbacks(),
                    canMoveUp = true,
                    canMoveDown = true,
                    onShowAs = {},
                )
            }
        }
        composeRule.captureScreen()
    }

    private fun surface(
        layout: StreamLayout = StreamLayout.DEFAULT,
        reducedTransparency: Boolean = false,
        reducedMotion: Boolean = false,
    ) {
        composeRule.setContent {
            ScreenshotBackdrop(reducedTransparency = reducedTransparency) {
                StreamSurface(
                    layout = layout,
                    results = StreamSection.entries.associateWith { StreamFixtures.ready(it) },
                    env = StreamFixtures.environment(reducedMotion),
                    callbacks = StreamCallbacks(),
                    contentPadding = PaddingValues(top = 48.dp, bottom = 96.dp),
                )
            }
        }
        composeRule.captureScreen()
    }
}

/** One section on the screenshot wallpaper, scrolled so a tall one is not cut off by the window. */
@Composable
private fun StreamBackdrop(content: @Composable () -> Unit) {
    ScreenshotBackdrop {
        Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) { content() }
    }
}
