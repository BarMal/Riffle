package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.LauncherHapticEvent
import com.riffle.app.launcher.LauncherHaptics
import com.riffle.app.launcher.LocalLauncherHaptics
import com.riffle.app.launcher.stream.ContentEntry
import com.riffle.app.launcher.stream.Notice
import com.riffle.app.launcher.stream.NoticeEntry
import com.riffle.app.launcher.stream.STREAM_CARD_ICON_TAG
import com.riffle.app.launcher.stream.STREAM_CARD_TITLE_TAG
import com.riffle.app.launcher.stream.STREAM_OPEN_CARD_TEST_TAG
import com.riffle.app.launcher.stream.StackEntry
import com.riffle.app.launcher.stream.StackEntryCard
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.app.launcher.stream.ToDoEntry
import com.riffle.app.launcher.stream.TomorrowEntry
import com.riffle.app.launcher.stream.streamStackCardTag
import com.riffle.core.domain.launcher.settings.StreamSettings
import com.riffle.core.domain.launcher.stream.AppShortcutContent
import com.riffle.core.domain.launcher.stream.NotConnectedReason
import com.riffle.core.domain.launcher.stream.PinnedApp
import com.riffle.core.domain.launcher.stream.StreamAppRef
import com.riffle.core.domain.launcher.stream.StreamCardStackSpec
import com.riffle.core.domain.launcher.stream.StreamContentId
import com.riffle.core.domain.launcher.stream.StreamGroup
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.stream.StreamShortcutPages
import com.riffle.core.domain.launcher.stream.TodayCard
import com.riffle.core.domain.launcher.stream.TodayNotice
import com.riffle.core.domain.launcher.workspace.SourceIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the strip of a card says, for every kind of card (device fix round 5, item 4). A card behind the one in front
 * shows only the top 52 dp of itself (`StreamCardStackSpec.STRIP_HEIGHT_DP`), so every card's header, an icon or an
 * avatar and a one-line title, has to lie inside that. Each kind is drawn at the size a stack draws it at, clipped to a
 * strip, and its icon and title nodes (the unmerged tree keeps them) must lie wholly inside the strip. A merged page
 * (a notification and its app's shortcuts) is then checked through the real stack, with a card open.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamStripsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val density get() = composeRule.density.density
    private val env = StreamFixtures.environment(reducedMotion = true)

    /** [entry] drawn as a stack draws a card behind the front one: the real card, clipped to its top strip. */
    private fun strip(
        entry: StackEntry,
        env: com.riffle.app.launcher.stream.StreamEnvironment = this.env,
    ) {
        composeRule.setContent {
            ScreenshotBackdrop {
                Box(
                    modifier =
                        Modifier
                            .size(CARD_WIDTH.dp, StreamCardStackSpec.STRIP_HEIGHT_DP.dp)
                            .clipToBounds()
                            .testTag(STRIP_TAG),
                ) {
                    Box(modifier = Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)) {
                        StackEntryCard(
                            entry = entry,
                            env = env,
                            callbacks = StreamCallbacks(),
                            groupKey = null,
                            onOpen = {},
                            modifier = Modifier.size(CARD_WIDTH.dp, entry.height),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertStripShowsIconAndTitle(
        kind: String,
        stripTag: String = STRIP_TAG,
    ) {
        val strip = composeRule.onNodeWithTag(stripTag, useUnmergedTree = true).fetchSemanticsNode()
        val icon = firstInside(strip, STREAM_CARD_ICON_TAG)
        val title = firstInside(strip, STREAM_CARD_TITLE_TAG)
        assertTrue("$kind: the strip has an icon or an avatar", icon != null)
        assertTrue("$kind: the strip has a title", title != null)
        for ((name, node) in listOf("icon" to icon!!, "title" to title!!)) {
            assertInside(kind, name, node, strip)
        }
        val said = title.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
        assertTrue("$kind: the title says something: '$said'", said.isNotBlank())
    }

    /** The first node tagged [tag] among the descendants of [strip] (the unmerged tree). */
    private fun firstInside(
        strip: SemanticsNode,
        tag: String,
    ): SemanticsNode? =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
            .firstOrNull { node -> generateSequence(node) { it.parent }.any { it.id == strip.id } }

    private fun assertInside(
        kind: String,
        what: String,
        node: SemanticsNode,
        strip: SemanticsNode,
    ) {
        // Position and size, not bounds: a node's bounds are clipped by the strip, which would hide what is cut off.
        val top = node.positionInRoot.y - strip.positionInRoot.y
        val bottom = top + node.size.height
        val left = node.positionInRoot.x - strip.positionInRoot.x
        val right = left + node.size.width
        val tolerance = 0.5f * density
        assertTrue("$kind: the $what starts inside its strip ($top px)", top >= -tolerance)
        assertTrue(
            "$kind: the $what ends inside its strip ($bottom px of ${strip.size.height})",
            bottom <= strip.size.height + tolerance,
        )
        assertTrue("$kind: the $what is wider than nothing", node.size.width > 0 && node.size.height > 0)
        assertTrue(
            "$kind: the $what is inside the strip's width",
            left >= -tolerance && right <= strip.size.width + tolerance,
        )
    }

    // One test per kind ---------------------------------------------------------------------------------------------

    @Test
    fun aNotificationFromAPersonShowsItsAvatarAndWhoItIsFrom() {
        strip(ContentEntry(StreamFixtures.chatNotification))
        assertStripShowsIconAndTitle("notification")
    }

    @Test
    fun aNotificationThatIsNotAboutAPersonShowsTheAppsIconAndItsTitle() {
        strip(ContentEntry(StreamFixtures.mailNotification))
        assertStripShowsIconAndTitle("notification without a person")
    }

    @Test
    fun anEventShowsItsIconAndTitle() {
        strip(ContentEntry(StreamFixtures.lunchEvent))
        assertStripShowsIconAndTitle("event")
    }

    @Test
    fun anAlarmShowsItsIconAndTitle() {
        strip(ContentEntry(StreamFixtures.alarm))
        assertStripShowsIconAndTitle("alarm")
    }

    @Test
    fun todaysNextEventShowsItsIconAndTitle() {
        strip(ContentEntry(StreamFixtures.nextEvent, nextUp = true))
        assertStripShowsIconAndTitle("next event")
    }

    @Test
    fun nowPlayingShowsTheAppsIconAndTheTrackAtTheTopNotBehindTheTransport() {
        strip(ContentEntry(StreamFixtures.media))
        assertStripShowsIconAndTitle("media")
    }

    @Test
    fun anArticleWithALeadImageShowsItsIconAndHeadlineAboveTheImage() {
        strip(ContentEntry(StreamFixtures.articles.first()))
        assertStripShowsIconAndTitle("article with an image")
    }

    @Test
    fun anArticleWithoutAnImageShowsItsIconAndHeadline() {
        strip(ContentEntry(StreamFixtures.articles.last()))
        assertStripShowsIconAndTitle("article")
    }

    @Test
    fun aTaskShowsItsCheckboxAndTitle() {
        strip(ContentEntry(StreamFixtures.tasks.first()))
        assertStripShowsIconAndTitle("task")
    }

    @Test
    fun aRecentAppShowsItsIconAndName() {
        strip(ContentEntry(StreamFixtures.recents.first(), caption = "Often used"))
        assertStripShowsIconAndTitle("recent app")
    }

    @Test
    fun aShortcutShowsItsIconAndNameAtTheTopNotCentredInTheCard() {
        strip(ContentEntry(shortcut("recent", "Recent", "Recent files")))
        assertStripShowsIconAndTitle("shortcut with a long label")
    }

    @Test
    fun aShortcutWithoutALongLabelShowsItsIconAndName() {
        strip(ContentEntry(shortcut("search", "Search", null)))
        assertStripShowsIconAndTitle("shortcut")
    }

    @Test
    fun theCardThatOpensAnAppShowsItsIconAndName() {
        strip(ContentEntry(shortcut(null, null, null)))
        assertStripShowsIconAndTitle("open the app")
    }

    @Test
    fun aDisabledShortcutShowsItsIconAndName() {
        strip(ContentEntry(shortcut("scan", "Scan document", "Scan a document", enabled = false)))
        assertStripShowsIconAndTitle("disabled shortcut")
    }

    @Test
    fun theTodoCardShowsItsIconAndTitle() {
        strip(ToDoEntry(TodayCard.ToDo(StreamFixtures.tasks, left = 3, overdue = 1)))
        assertStripShowsIconAndTitle("to do")
    }

    @Test
    fun tomorrowShowsItsIconAndTitle() {
        strip(TomorrowEntry(TodayCard.Tomorrow(eventCount = 2, taskCount = 1, firstEvent = StreamFixtures.feedEvent)))
        assertStripShowsIconAndTitle("tomorrow")
    }

    @Test
    fun theShortcutsNoticeShowsAnIconAndATitle() {
        strip(NoticeEntry("shortcuts:0", Notice.ShortcutsNeedHome))
        assertStripShowsIconAndTitle("shortcuts notice")
    }

    @Test
    fun theAllowAccessNoticeShowsAnIconAndATitle() {
        strip(NoticeEntry("today:0", Notice.Today(TodayNotice.Permission(listOf(SourceIds.CALENDAR)))))
        assertStripShowsIconAndTitle("permission notice")
    }

    @Test
    fun theTasksNotConnectedNoticeShowsAnIconAndATitle() {
        strip(NoticeEntry("today:0", Notice.Today(TodayNotice.TasksNotConnected(NotConnectedReason.NO_ACCOUNT))))
        assertStripShowsIconAndTitle("tasks not connected notice")
    }

    @Test
    fun theTokenRejectedNoticeShowsAnIconAndATitle() {
        strip(NoticeEntry("today:0", Notice.Today(TodayNotice.TasksNotConnected(NotConnectedReason.TOKEN_REJECTED))))
        assertStripShowsIconAndTitle("token rejected notice")
    }

    @Test
    fun theTasksUnavailableNoticeShowsAnIconAndATitle() {
        strip(NoticeEntry("today:0", Notice.Today(TodayNotice.TasksUnavailable)))
        assertStripShowsIconAndTitle("tasks unavailable notice")
    }

    // A merged page, through the real stack --------------------------------------------------------------------------

    private val chatGroup = StreamShortcutPages.appKeyOf(ScreenshotFixtures.chat.identity)
    private val chat = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, chatGroup)

    private fun shortcut(
        id: String?,
        short: String?,
        long: String?,
        enabled: Boolean = true,
    ): AppShortcutContent {
        val app = ScreenshotFixtures.chat
        return AppShortcutContent(
            id = StreamContentId("shortcut:chat:${id ?: "app"}"),
            app = StreamAppRef(app.identity.packageName.value, "personal"),
            appIdentity = app.identity,
            appLabel = "Chat",
            shortcutId = id,
            shortLabel = short,
            longLabel = long,
            icon = com.riffle.core.domain.launcher.workspace.ItemImageHandle("icon"),
            enabled = enabled,
            disabledMessage = null.takeIf { enabled } ?: "Camera access is off",
        )
    }

    /** One notification of Chat followed by its three shortcuts: a pinned app that is also live. */
    private fun showMergedPage() {
        val results: Map<StreamSection, StreamSectionResult> =
            StreamSection.layoutSections.associateWith<StreamSection, StreamSectionResult> {
                StreamSectionResult.Empty
            } +
                (
                    StreamSection.NOTIFICATIONS to
                        StreamSectionResult.Ready(
                            listOf(StreamGroup(chatGroup, "Chat", listOf(StreamFixtures.chatNotification))),
                        )
                )
        val pinned = listOf(PinnedApp(ScreenshotFixtures.chat.identity, "Chat"))
        val pages =
            StreamShortcutPages.plan(pinned, emptySet(), StreamFixtures.shortcutsOf(ScreenshotFixtures.chat), true)
        val haptics =
            object : LauncherHaptics {
                override fun longPress() = Unit

                override fun perform(event: LauncherHapticEvent) = Unit
            }
        composeRule.setContent {
            CompositionLocalProvider(LocalLauncherHaptics provides haptics) {
                ScreenshotBackdrop {
                    Box(Modifier.fillMaxSize()) {
                        StreamSurface(
                            layout = StreamLayout.DEFAULT,
                            results = results,
                            env = env.copy(cardSettings = StreamSettings()),
                            callbacks = StreamCallbacks(),
                            initialPageKey = chat,
                            shortcutPages = pages,
                            pinnedGroupKeys = pinned.map { StreamShortcutPages.appKeyOf(it.identity) },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private val shortcutSlots =
        listOf("recent", "search", "scan").map {
            streamStackCardTag("shortcut:${StreamShortcutPages.appKeyOf(ScreenshotFixtures.chat.identity)}/$it")
        }

    @Test
    fun theStripsOfAMergedPageEachShowAnIconAndATitleAtRest() {
        showMergedPage()
        shortcutSlots.forEach { assertStripShowsIconAndTitle("merged page, at rest: $it", it) }
    }

    @Test
    fun theStripsOfAMergedPageEachShowAnIconAndATitleWithTheNotificationOpen() {
        showMergedPage()
        composeRule.onNodeWithTag(streamStackCardTag(StreamFixtures.chatNotification.id.value)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(STREAM_OPEN_CARD_TEST_TAG).assertExists()
        shortcutSlots.forEach { tag ->
            val strip = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue("$tag stands above the open card", strip.size.height > 0)
            assertStripShowsIconAndTitle("merged page, notification open: $tag", tag)
        }
        composeRule.captureScreen()
    }

    @Test
    fun theMergedPageHasOneNotificationAndThreeShortcutCards() {
        showMergedPage()
        val slots =
            composeRule.onAllNodes(
                SemanticsMatcher("a card slot") { node ->
                    node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("stream-stack-card:") == true
                },
            ).fetchSemanticsNodes()
        assertEquals(4, slots.size)
    }

    private companion object {
        const val STRIP_TAG = "strip"
        const val CARD_WIDTH = 288
    }
}
