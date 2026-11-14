package com.riffle.app.launcher.stream

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.RiffleLauncherTheme
import com.riffle.app.launcher.expressions.NoExpressionImageLoader
import com.riffle.app.launcher.notifications.NotificationStageActionGateway
import com.riffle.app.launcher.notifications.NotificationStageActionResult
import com.riffle.app.launcher.rss.NoopFeedArticleCacheRepository
import com.riffle.app.launcher.workspace.ItemLaunchPort
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.notifications.NotificationPriority
import com.riffle.core.domain.launcher.stream.NotificationAction
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.PinnedApp
import com.riffle.core.domain.launcher.stream.RecentAppAction
import com.riffle.core.domain.launcher.stream.RecentAppContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionResult
import com.riffle.core.domain.launcher.stream.StreamAppRef
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamContentId
import com.riffle.core.domain.launcher.stream.StreamGroup
import com.riffle.core.domain.launcher.stream.StreamGroupKeys
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamPage
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamPrivacy
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.stream.StreamShortcutPages
import com.riffle.core.domain.launcher.stream.TaskContent
import com.riffle.core.domain.launcher.workspace.ItemTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pages dock modes slice 4b adds, on a real touch pipeline: a sideways swipe from a live app's page to a pinned app's
 * shortcuts page (they are in the Dock's order, pinned apps first), a tap on a shortcut card launching it through the
 * real gateway over a fake launcher, a task ticked off the Today page's checklist reaching the Tasks action, and
 * Recents drawn as cards of a stack. Outcomes are asserted, never pixels or timing.
 */
@RunWith(AndroidJUnit4::class)
class StreamShortcutsTodayInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val files =
        AppIdentity(AppPackageName("com.riffle.files"), AppActivityName(".Main"))
    private val pinned = listOf(PinnedApp(files, "Files"))
    private val chat = StreamPagePlanner.keyOf(StreamSection.NOTIFICATIONS, "chat:personal")
    private val filesPage = StreamShortcutPages.pageKeyOf(StreamShortcutPages.appKeyOf(files))

    private val actions = mutableListOf<Pair<StreamContent, StreamAction>>()

    /** What the fake launcher was asked to start, in order. */
    private val launched = mutableListOf<String>()

    private val dock = StreamDockLink()

    private val gateway =
        AndroidStreamActionGateway(
            notifications = NotificationStageActionGateway { _, _ -> NotificationStageActionResult.Performed },
            replyActionId = { null },
            snooze = { _, _ -> false },
            media =
                object : MediaTransport {
                    override fun perform(
                        packageName: String,
                        action: StreamAction,
                    ): StreamActionResult = StreamActionResult.NotOffered
                },
            feedCache = NoopFeedArticleCacheRepository,
            feedChanged = {},
            launch =
                object : ItemLaunchPort {
                    override fun launchActivity(identity: AppIdentity) =
                        true.also { launched += "app:${identity.packageName.value}" }

                    override fun launchPackage(packageName: String) = true.also { launched += "package:$packageName" }

                    override fun launchShortcut(shortcut: AppShortcut) =
                        true.also { launched += "shortcut:${shortcut.id.value}" }

                    override fun openDeepLink(uri: String) = true

                    override fun dismissNotification(key: String) = true
                },
            openClock = { true },
        )

    private fun note(id: Int) =
        NotificationContent(
            id = StreamContentId("n:chat:$id"),
            app = StreamAppRef("chat", "personal"),
            appLabel = "Chat",
            title = "Chat $id",
            text = "Body $id",
            icon = null,
            image = null,
            postedAtEpochMillis = NOW - id * MINUTE,
            category = null,
            priority = NotificationPriority.DEFAULT,
            target = ItemTarget.None,
            actions = setOf(NotificationAction.OPEN),
            privacy = StreamPrivacy.VISIBLE,
            notificationKey = "k:chat:$id",
        )

    private fun emptyResults(): Map<StreamSection, StreamSectionResult> =
        StreamSection.layoutSections.associateWith { StreamSectionResult.Empty }

    private fun shortcutPages(shortcutsByApp: Map<AppIdentity, List<AppShortcut>>): List<StreamPage> =
        StreamShortcutPages.plan(pinned, emptySet(), shortcutsByApp, launcherRole = true)

    private fun shortcut(id: String) = AppShortcut(AppShortcutId(id), files, id.replaceFirstChar { it.uppercase() })

    private fun show(
        results: Map<StreamSection, StreamSectionResult>,
        initialPageKey: String?,
        shortcutPages: List<StreamPage> = emptyList(),
    ) {
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
                    callbacks =
                        StreamCallbacks(
                            onAction = { content, action ->
                                actions += content to action
                                gateway.perform(content, action)
                            },
                        ),
                    initialPageKey = initialPageKey,
                    dockLink = dock,
                    shortcutPages = shortcutPages,
                    pinnedGroupKeys = pinned.map { StreamShortcutPages.appKeyOf(it.identity) },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun todayWithTasks(tasks: List<TaskContent>) =
        StreamSectionResult.Ready(listOf(StreamGroup(StreamGroupKeys.TASKS, null, tasks)))

    @Test
    fun swipingFromALiveAppsPageReachesAPinnedAppsShortcutsPage() {
        val results =
            emptyResults() +
                (
                    StreamSection.NOTIFICATIONS to
                        StreamSectionResult.Ready(listOf(StreamGroup("chat:personal", "Chat", listOf(note(1)))))
                )
        show(
            results,
            initialPageKey = chat,
            shortcutPages = shortcutPages(mapOf(files to listOf(shortcut("recent"), shortcut("search")))),
        )
        // The pinned app's page comes before the live app's, as its icon does in the Dock.
        assertEquals(listOf(filesPage, chat), dock.pages.map { it.key }.take(2))
        assertEquals(chat, dock.currentKey)
        composeRule.onNodeWithText("Chat 1").assertIsDisplayed()

        composeRule.onNodeWithTag(STREAM_PAGER_TEST_TAG).performTouchInput { swipeRight() }
        composeRule.waitForIdle()

        assertEquals(filesPage, dock.currentKey)
        composeRule.onNodeWithText("Recent").assertIsDisplayed()
    }

    @Test
    fun tappingAShortcutCardLaunchesThatShortcutThroughTheFakeLauncher() {
        show(
            emptyResults(),
            initialPageKey = filesPage,
            shortcutPages = shortcutPages(mapOf(files to listOf(shortcut("recent"), shortcut("search")))),
        )
        composeRule.onNodeWithTag(streamShortcutTagFor("recent")).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("shortcut:recent"), launched)
        assertEquals(StreamAction.Open, actions.single().second)
    }

    @Test
    fun anAppWithNoShortcutsIsOneOpenCardThatLaunchesTheApp() {
        show(emptyResults(), initialPageKey = filesPage, shortcutPages = shortcutPages(emptyMap()))
        composeRule.onNodeWithText("Open Files").assertIsDisplayed()
        composeRule.onNodeWithTag(streamShortcutTagFor(null)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("app:com.riffle.files"), launched)
    }

    @Test
    fun tickingATaskOnTheTodayCardSendsTheCompleteAction() {
        val tasks =
            listOf(
                TaskContent(StreamContentId("todoist:1"), "Renew passport", overdue = true),
                TaskContent(StreamContentId("todoist:2"), "Send the invoice"),
            )
        show(emptyResults() + (StreamSection.TODAY to todayWithTasks(tasks)), initialPageKey = "today")
        composeRule.onNodeWithText("To do today").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Mark Send the invoice done").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(StreamAction.Complete), actions.map { it.second })
        assertEquals("todoist:2", actions.single().first.id.value)
    }

    @Test
    fun recentsShowsCardsOfAStackNotAGridOfTiles() {
        fun recent(
            id: String,
            label: String,
        ) = RecentAppContent(
            id = StreamContentId("recent:$id"),
            app = StreamAppRef("com.riffle.$id", "personal"),
            label = label,
            icon = null,
            lastUsedEpochMillis = NOW - MINUTE,
            target = ItemTarget.App("com.riffle.$id", "personal"),
            actions = setOf(RecentAppAction.LAUNCH),
        )
        val apps = listOf(recent("chat", "Chat"), recent("mail", "Mail"), recent("maps", "Maps"))
        val recents = StreamSectionResult.Ready(listOf(StreamGroup(StreamGroupKeys.RECENTS, null, apps)))
        val results = emptyResults() + (StreamSection.RECENTS to recents)
        show(results, initialPageKey = "recents")

        // Each app is a card of one stack: the front one is whole, the others peek behind it.
        composeRule.onNodeWithTag(streamStackCardTag("recent:chat")).assertIsDisplayed()
        composeRule.onNodeWithTag(streamStackCardTag("recent:mail")).assertExists()
        composeRule.onNodeWithTag(streamStackCardTag("recent:maps")).assertExists()
        composeRule.onNodeWithText("Chat").assertIsDisplayed()

        composeRule.onNodeWithText("Chat").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(StreamAction.Open), actions.map { it.second })
        assertTrue(actions.single().first.id.value == "recent:chat")
    }

    private fun streamShortcutTagFor(shortcutId: String?) = "stream-shortcut:${shortcutId.orEmpty()}"

    private object NoIcons : AppIconLoader {
        override fun iconFor(identity: AppIdentity): ImageBitmap? = null

        override fun colorFor(identity: AppIdentity): Color? = null
    }

    private companion object {
        const val MINUTE = 60_000L
        const val NOW = 1_800_000_000_000L
    }
}
