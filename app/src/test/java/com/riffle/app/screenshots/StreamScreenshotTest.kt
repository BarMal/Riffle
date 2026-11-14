package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamCard
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.designsystem.CardTint
import com.riffle.core.domain.launcher.settings.StreamSettings
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.PinnedApp
import com.riffle.core.domain.launcher.stream.StreamAppRef
import com.riffle.core.domain.launcher.stream.StreamContentId
import com.riffle.core.domain.launcher.stream.StreamGap
import com.riffle.core.domain.launcher.stream.StreamGroup
import com.riffle.core.domain.launcher.stream.StreamGroupKeys
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamLayoutReducer
import com.riffle.core.domain.launcher.stream.StreamPage
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.stream.StreamShortcutPages
import com.riffle.core.domain.launcher.stream.StreamSourceIds
import com.riffle.core.domain.launcher.stream.StreamView
import com.riffle.core.domain.launcher.stream.TaskContent
import com.riffle.core.domain.launcher.workspace.SourceIds
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.ZoneOffset

/**
 * The Stream (ADR 0003, Views; P13 and P14): the pager of pages on compact phones, every section's page in both views
 * in the Dock's order (no spine or header), the section states as pages, an expanded notification thread and
 * now-playing queue, the whole
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
    fun pagerOpensOnTheFirstPage() = surface()

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
    fun todayStack() = page(StreamPageKeys.TODAY, StreamView.STACK)

    @Test
    fun todayList() = page(StreamPageKeys.TODAY, StreamView.LIST)

    @Test
    fun feedsOneFeedStack() = page(StreamPageKeys.FEED_A, StreamView.STACK)

    @Test
    fun feedsOneFeedList() = page(StreamPageKeys.FEED_A, StreamView.LIST)

    @Test
    fun feedsAnotherFeedStack() = page(StreamPageKeys.FEED_B, StreamView.STACK)

    @Test
    fun todayNotConnected() = state(StreamSection.TODAY, StreamSectionResult.NotConnected())

    @Test
    fun todayEmpty() = state(StreamSection.TODAY, StreamSectionResult.Empty)

    @Test
    fun todayTokenRejected() =
        state(
            StreamSection.TODAY,
            StreamSectionResult.NotConnected(com.riffle.core.domain.launcher.stream.NotConnectedReason.TOKEN_REJECTED),
        )

    @Test
    fun todayError() = state(StreamSection.TODAY, StreamSectionResult.Unavailable)

    @Test
    fun todayOnlyTasksStale() = state(StreamSection.TODAY, todayOf(tasks = StreamFixtures.staleTasks))

    @Test
    fun todayOnlyTasksWithNoEvents() = state(StreamSection.TODAY, todayOf(tasks = StreamFixtures.tasks))

    @Test
    fun todayConnectedWithNothingOnIt() =
        state(
            StreamSection.TODAY,
            StreamSectionResult.Ready(
                emptyList(),
                listOf(
                    StreamGap(StreamSourceIds.TASKS, StreamSectionResult.Empty),
                ),
            ),
        )

    @Test
    fun todayEventsWithNoTasksAccount() =
        state(
            StreamSection.TODAY,
            todayOf(
                events = listOf(StreamFixtures.nextEvent, StreamFixtures.lunchEvent),
                gaps =
                    listOf(
                        StreamGap(
                            StreamSourceIds.TASKS,
                            StreamSectionResult.NotConnected(),
                        ),
                    ),
            ),
        )

    @Test
    fun todayNextEventUnderWayWithItsCountdown() =
        state(
            StreamSection.TODAY,
            todayOf(
                events =
                    listOf(
                        StreamFixtures.nextEvent.copy(
                            startEpochMillis = StreamFixtures.NOW - 20 * 60_000L,
                            endEpochMillis = StreamFixtures.NOW + 10 * 60_000L,
                        ),
                        StreamFixtures.lunchEvent,
                    ),
                tasks = StreamFixtures.tasks,
            ),
        )

    @Test
    fun shortcutsOfAPinnedApp() = shortcuts(StreamFixtures.shortcutsOf(ScreenshotFixtures.files))

    @Test
    fun shortcutsOfAPinnedAppWithNone() = shortcuts(emptyMap())

    @Test
    fun shortcutsOfAPinnedAppWithoutTheLauncherRole() = shortcuts(emptyMap(), launcherRole = false)

    @Test
    fun notificationsPermissionRequired() =
        state(StreamSection.NOTIFICATIONS, StreamSectionResult.PermissionRequired(listOf(SourceIds.NOTIFICATIONS)))

    @Test
    fun notificationsEmpty() = state(StreamSection.NOTIFICATIONS, StreamSectionResult.Empty)

    @Test
    fun feedsOff() = state(StreamSection.FEEDS, StreamSectionResult.Off)

    @Test
    fun recentsLoading() = state(StreamSection.RECENTS, StreamSectionResult.Loading)

    @Test
    fun nowUnavailable() = state(StreamSection.NOW, StreamSectionResult.Unavailable)

    @Test
    fun notificationExpandedToItsThreadWithReply() {
        surface(initialPageKey = StreamPageKeys.CHAT)
        composeRule.onNodeWithText("Are we still on for lunch tomorrow?").performClick()
        composeRule.captureScreen()
    }

    @Test
    fun nowPlayingExpandedToItsQueue() {
        surface(initialPageKey = StreamPageKeys.NOW)
        composeRule.onNodeWithText("Midnight City").performClick()
        composeRule.captureScreen()
    }

    @Test
    fun cardsTintedWithTheirAppsColourLightTheme() = tintedCards()

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun cardsTintedWithTheirAppsColourDarkTheme() = tintedCards()

    @Test
    fun cardsTintedAtTheMaximumStrength() = tintedCards(strength = CardTint.MAX_STRENGTH_PERCENT)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun cardsTintedAtTheMaximumStrengthDarkTheme() = tintedCards(strength = CardTint.MAX_STRENGTH_PERCENT)

    @Test
    fun aPageLaterInTheDocksOrderWithNoSpineOrHeader() = surface(initialPageKey = StreamPageKeys.TODAY)

    @Test
    fun everySectionHiddenOffersThemBack() {
        val layout =
            StreamSection.layoutSections.fold(StreamLayout.DEFAULT) { acc, section ->
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
                StreamLayoutAction.Move(StreamSection.TODAY, -3),
                StreamLayoutAction.SetView(StreamSection.NOTIFICATIONS, StreamView.LIST),
            ).fold(StreamLayout.DEFAULT, StreamLayoutReducer::reduce)
        surface(layout = layout)
    }

    /** One browse card per app, six apps with six different icon colours: what the fill is tinted with. */
    private fun tintedCards(strength: Int = CardTint.DEFAULT_STRENGTH_PERCENT) {
        val apps = ScreenshotFixtures.installedApps.take(6)
        composeRule.setContent {
            ScreenshotBackdrop {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    apps.forEachIndexed { index, app ->
                        StreamCard(
                            content =
                                StreamFixtures.chatNotification.copy(
                                    id = StreamContentId("tint:$index"),
                                    app = StreamAppRef(app.identity.packageName.value, "personal"),
                                    appLabel = app.label,
                                    title = "Sam",
                                    person = null,
                                    picture = null,
                                    image = null,
                                    thread = emptyList(),
                                ),
                            env =
                                StreamFixtures.environment()
                                    .copy(cardSettings = StreamSettings(cardTintPercent = strength)),
                            callbacks = StreamCallbacks(),
                            groupKey = null,
                            modifier = Modifier.fillMaxWidth().height(112.dp),
                        )
                    }
                }
            }
        }
        composeRule.captureScreen()
    }

    private fun page(
        key: String,
        view: StreamView,
    ) {
        val layout =
            StreamSection.layoutSections.fold(StreamLayout.DEFAULT) { acc, section ->
                StreamLayoutReducer.reduce(acc, StreamLayoutAction.SetView(section, view))
            }
        surface(layout = layout, initialPageKey = key)
    }

    /** Today with only what is given: events by day, tasks and the gaps. */
    private fun todayOf(
        events: List<EventContent> = emptyList(),
        tasks: List<TaskContent> = emptyList(),
        gaps: List<StreamGap> = emptyList(),
    ): StreamSectionResult.Ready {
        fun dayOf(event: EventContent) =
            Instant.ofEpochMilli(event.startEpochMillis).atZone(ZoneOffset.UTC).toLocalDate().toString()
        val days = events.groupBy(::dayOf).toSortedMap().map { (day, items) -> StreamGroup(day, null, items) }
        val taskGroup = tasks.takeIf { it.isNotEmpty() }?.let { StreamGroup(StreamGroupKeys.TASKS, null, it) }
        return StreamSectionResult.Ready(days + listOfNotNull(taskGroup), gaps)
    }

    /** The shortcuts pages of two pinned apps (Files, then Maps), the first in front. */
    private fun shortcuts(
        shortcutsByApp: AppShortcutsByApp,
        launcherRole: Boolean = true,
    ) {
        val pinned =
            listOf(
                PinnedApp(ScreenshotFixtures.files.identity, "Files"),
                PinnedApp(ScreenshotFixtures.maps.identity, "Maps"),
            )
        val pages = StreamShortcutPages.plan(pinned, emptySet(), shortcutsByApp, launcherRole)
        surface(
            initialPageKey = pages.first().key,
            shortcutPages = pages,
            pinnedGroupKeys = pinned.map { StreamShortcutPages.appKeyOf(it.identity) },
        )
    }

    private fun state(
        section: StreamSection,
        result: StreamSectionResult,
    ) {
        val key = StreamPagePlanner.keyOf(section)
        surface(results = defaultResults() + (section to result), initialPageKey = key)
    }

    private fun defaultResults() = StreamSection.layoutSections.associateWith { StreamFixtures.ready(it) }

    @Suppress("LongParameterList")
    private fun surface(
        layout: StreamLayout = StreamLayout.DEFAULT,
        results: Map<StreamSection, StreamSectionResult> = defaultResults(),
        initialPageKey: String? = null,
        reducedTransparency: Boolean = false,
        reducedMotion: Boolean = false,
        wide: Boolean = false,
        paneWidth: Dp? = null,
        shortcutPages: List<StreamPage> = emptyList(),
        pinnedGroupKeys: List<String> = emptyList(),
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
                        shortcutPages = shortcutPages,
                        pinnedGroupKeys = pinnedGroupKeys,
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
    val TODAY = StreamPagePlanner.keyOf(StreamSection.TODAY)
    val FEED_A = StreamPagePlanner.keyOf(StreamSection.FEEDS, "feed-a")
    val FEED_B = StreamPagePlanner.keyOf(StreamSection.FEEDS, "feed-b")
}
