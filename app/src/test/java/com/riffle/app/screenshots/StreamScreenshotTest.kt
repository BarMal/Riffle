package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamLayoutReducer
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
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
 * The Stream (ADR 0003, Views; P13 and P14): the pager of pages on compact phones, every section's page in both views
 * with its spine, the section states as pages, an expanded notification thread and now-playing queue, the whole
 * surface in the dark, with large text and with reduced transparency, the Show as menu, and unfolded: two pages side by
 * side and the Stage view with its rail, full and collapsed. The clock, the images and the locale are fixed
 * (`StreamFixtures`), so a render is the same every run.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun pagerOpensOnTheFirstPageWithItsSpine() = surface()

    @Test
    fun nowStack() = page(StreamPageKeys.NOW, StreamView.STACK)

    @Test
    fun nowList() = page(StreamPageKeys.NOW, StreamView.LIST)

    @Test
    fun notificationsOneAppStack() = page(StreamPageKeys.CHAT, StreamView.STACK)

    @Test
    fun notificationsOneAppList() = page(StreamPageKeys.CHAT, StreamView.LIST)

    @Test
    fun notificationsAnotherAppStack() = page(StreamPageKeys.MAIL, StreamView.STACK)

    @Test
    fun recentsStack() = page(StreamPageKeys.RECENTS, StreamView.STACK)

    @Test
    fun recentsList() = page(StreamPageKeys.RECENTS, StreamView.LIST)

    @Test
    fun agendaStack() = page(StreamPageKeys.AGENDA, StreamView.STACK)

    @Test
    fun agendaList() = page(StreamPageKeys.AGENDA, StreamView.LIST)

    @Test
    fun tasksStack() = page(StreamPageKeys.TASKS, StreamView.STACK)

    @Test
    fun tasksList() = page(StreamPageKeys.TASKS, StreamView.LIST)

    @Test
    fun feedsOneFeedStack() = page(StreamPageKeys.FEED_A, StreamView.STACK)

    @Test
    fun feedsOneFeedList() = page(StreamPageKeys.FEED_A, StreamView.LIST)

    @Test
    fun feedsAnotherFeedStack() = page(StreamPageKeys.FEED_B, StreamView.STACK)

    @Test
    fun tasksNotConnected() = state(StreamSection.TASKS, StreamSectionResult.NotConnected())

    @Test
    fun tasksEmpty() = state(StreamSection.TASKS, StreamSectionResult.Empty)

    @Test
    fun tasksTokenRejected() =
        state(
            StreamSection.TASKS,
            StreamSectionResult.NotConnected(com.riffle.core.domain.launcher.stream.NotConnectedReason.TOKEN_REJECTED),
        )

    @Test
    fun tasksError() = state(StreamSection.TASKS, StreamSectionResult.Unavailable)

    @Test
    fun tasksStale() =
        state(
            StreamSection.TASKS,
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
        state(StreamSection.NOTIFICATIONS, StreamSectionResult.PermissionRequired(listOf(SourceIds.NOTIFICATIONS)))

    @Test
    fun notificationsEmpty() = state(StreamSection.NOTIFICATIONS, StreamSectionResult.Empty)

    @Test
    fun agendaEmpty() = state(StreamSection.AGENDA, StreamSectionResult.Empty)

    @Test
    fun feedsOff() = state(StreamSection.FEEDS, StreamSectionResult.Off)

    @Test
    fun recentsLoading() = state(StreamSection.RECENTS, StreamSectionResult.Loading)

    @Test
    fun nowUnavailable() = state(StreamSection.NOW, StreamSectionResult.Unavailable)

    @Test
    fun notificationExpandedToItsThreadWithReply() {
        surface(initialPageKey = StreamPageKeys.CHAT)
        composeRule.onAllNodesWithText("Reply").onFirst().performClick()
        composeRule.captureScreen()
    }

    @Test
    fun nowPlayingExpandedToItsQueue() {
        surface(initialPageKey = StreamPageKeys.NOW)
        composeRule.onNodeWithContentDescription("Show queue").performClick()
        composeRule.captureScreen()
    }

    @Test
    fun spineLaterInTheRowWithTheCurrentPageEmphasised() = surface(initialPageKey = StreamPageKeys.TASKS)

    @Test
    fun showAsMenuOnAPageHeader() {
        surface(initialPageKey = StreamPageKeys.MAIL)
        composeRule.onNodeWithContentDescription("Notifications options").performClick()
        composeRule.captureScreen()
    }

    @Test
    fun everySectionHiddenOffersThemBack() {
        val layout =
            StreamSection.entries.fold(StreamLayout.DEFAULT) { acc, section ->
                StreamLayoutReducer.reduce(acc, StreamLayoutAction.SetEnabled(section, false))
            }
        surface(layout = layout)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun pagerInTheDark() = surface(initialPageKey = StreamPageKeys.CHAT)

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun pagerWithLargeFont() = surface(initialPageKey = StreamPageKeys.CHAT)

    @Test
    fun pagerWithReducedTransparencyAndReducedMotion() =
        surface(initialPageKey = StreamPageKeys.CHAT, reducedTransparency = true, reducedMotion = true)

    @Test
    fun aSectionHiddenAndAnotherReordered() {
        val layout =
            listOf(
                StreamLayoutAction.SetEnabled(StreamSection.FEEDS, false),
                StreamLayoutAction.Move(StreamSection.AGENDA, -3),
                StreamLayoutAction.SetView(StreamSection.NOTIFICATIONS, StreamView.LIST),
            ).fold(StreamLayout.DEFAULT, StreamLayoutReducer::reduce)
        surface(layout = layout)
    }

    private fun page(
        key: String,
        view: StreamView,
    ) {
        val layout =
            StreamSection.entries.fold(StreamLayout.DEFAULT) { acc, section ->
                StreamLayoutReducer.reduce(acc, StreamLayoutAction.SetView(section, view))
            }
        surface(layout = layout, initialPageKey = key)
    }

    private fun state(
        section: StreamSection,
        result: StreamSectionResult,
    ) {
        val key = StreamPagePlanner.keyOf(section)
        surface(results = defaultResults() + (section to result), initialPageKey = key)
    }

    private fun defaultResults() = StreamSection.entries.associateWith { StreamFixtures.ready(it) }

    private fun surface(
        layout: StreamLayout = StreamLayout.DEFAULT,
        results: Map<StreamSection, StreamSectionResult> = defaultResults(),
        initialPageKey: String? = null,
        reducedTransparency: Boolean = false,
        reducedMotion: Boolean = false,
        wide: Boolean = false,
        paneWidth: Dp? = null,
    ) {
        composeRule.setContent {
            ScreenshotBackdrop(reducedTransparency = reducedTransparency) {
                Box(
                    modifier =
                        if (paneWidth != null) {
                            Modifier.width(
                                paneWidth,
                            ).fillMaxSize()
                        } else {
                            Modifier.fillMaxSize()
                        },
                ) {
                    StreamSurface(
                        layout = layout,
                        results = results,
                        env = StreamFixtures.environment(reducedMotion),
                        callbacks = StreamCallbacks(),
                        contentPadding = PaddingValues(top = 48.dp, bottom = 96.dp),
                        initialPageKey = initialPageKey,
                        wideLayout = wide,
                    )
                }
            }
        }
        composeRule.captureScreen()
    }
}

/** The keys of the fixture pages (see [StreamPagePlanner.keyOf]). */
internal object StreamPageKeys {
    val NOW = StreamPagePlanner.keyOf(StreamSection.NOW)
    val CHAT = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "chat:personal")
    val MAIL = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "mail:personal")
    val RECENTS = StreamPagePlanner.keyOf(StreamSection.RECENTS)
    val AGENDA = StreamPagePlanner.keyOf(StreamSection.AGENDA)
    val TASKS = StreamPagePlanner.keyOf(StreamSection.TASKS)
    val FEED_A = StreamPagePlanner.keyOf(StreamSection.FEEDS, "feed-a")
    val FEED_B = StreamPagePlanner.keyOf(StreamSection.FEEDS, "feed-b")
}
