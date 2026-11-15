package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamShortcutPagesTest {
    private fun identity(
        pkg: String,
        profile: AppProfile = AppProfile.personal(),
    ) = AppIdentity(AppPackageName(pkg), AppActivityName("$pkg.Main"), profile)

    private val files = PinnedApp(identity("com.files"), "Files")
    private val maps = PinnedApp(identity("com.maps"), "Maps")
    private val chat = PinnedApp(identity("com.chat"), "Chat")

    private fun shortcut(
        app: PinnedApp,
        id: String,
        label: String = id,
        long: String? = null,
        rank: Int = 0,
        enabled: Boolean = true,
    ) = AppShortcut(AppShortcutId(id), app.identity, label, long, enabled, null, rank)

    private fun cards(page: StreamPage) =
        (page.body as StreamPageBody.Content).items.map { assertIs<AppShortcutContent>(it) }

    @Test
    fun everyPinnedAppWithNothingLiveGetsAShortcutsPageInTheDocksOrder() {
        val pages =
            StreamShortcutPages.plan(
                pinned = listOf(maps, files),
                liveGroupKeys = emptySet(),
                shortcutsByApp = mapOf(maps.identity to listOf(shortcut(maps, "home"))),
                launcherRole = true,
            )
        assertEquals(listOf("shortcuts/com.maps:personal", "shortcuts/com.files:personal"), pages.map { it.key })
        assertTrue(pages.all { it.section == StreamSection.SHORTCUTS && it.view == StreamView.STACK })
        assertEquals(listOf("Maps", "Files"), pages.map { it.label })
        assertEquals(listOf("com.maps:personal", "com.files:personal"), pages.map { it.groupKey })
    }

    @Test
    fun aPinnedAppThatIsLiveHasItsStackAlreadyAndGetsNoSecondPage() {
        val pages =
            StreamShortcutPages.plan(listOf(chat, files), setOf("com.chat:personal"), emptyMap(), launcherRole = true)
        assertEquals(listOf("shortcuts/com.files:personal"), pages.map { it.key })
    }

    @Test
    fun theQuickActionsAreTheAppsEnabledShortcutsInRankOrderEachWithItsLabelAndAnIconHandle() {
        val shortcuts =
            listOf(
                shortcut(files, "recent", "Recent", long = "Recent files", rank = 2),
                shortcut(files, "search", "Search", long = "Search", rank = 0),
                shortcut(files, "new", "New", rank = 1, enabled = false),
                shortcut(files, "star", "Starred", rank = 1),
            )
        val page =
            StreamShortcutPages.plan(
                listOf(files),
                emptySet(),
                mapOf(files.identity to shortcuts),
                true,
            ).single()
        // The page's stack is the quiet card alone: no shortcut is a card.
        assertEquals(1, page.count)
        assertTrue(cards(page).single().opensApp)
        // A disabled shortcut would be a chip that does nothing, so it is left out; the rest are in the platform's
        // order.
        val actions = page.quickActions
        assertEquals(listOf("search", "star", "recent"), actions.map { it.id })
        assertEquals(listOf("Search", "Starred", "Recent"), actions.map { it.label })
        assertEquals("shortcut-icon:personal/com.files/search", actions[0].icon?.key)
        assertEquals(StreamContentId("shortcut:com.files:personal/search"), actions[0].shortcut.id)
        assertTrue(actions.none { it.shortcut.opensApp })
    }

    @Test
    fun quickActionsAreCappedAtEightAndKeepTheFirstEightByRank() {
        val many = (0 until 12).map { shortcut(files, "s$it", rank = 11 - it) }
        val page = StreamShortcutPages.plan(listOf(files), emptySet(), mapOf(files.identity to many), true).single()
        assertEquals(StreamShortcutPages.MAX_QUICK_ACTIONS, page.quickActions.size)
        assertEquals((11 downTo 4).map { "s$it" }, page.quickActions.map { it.id })
    }

    @Test
    fun anAppWithNothingLiveIsOneQuietCardThatOpensTheAppWhateverItsShortcuts() {
        val page = StreamShortcutPages.plan(listOf(files), emptySet(), emptyMap(), launcherRole = true).single()
        val card = cards(page).single()
        assertTrue(card.opensApp)
        assertEquals("Files", card.shortLabel)
        assertTrue(card.icon != null)
        assertEquals(1, page.count)
        assertTrue(page.quickActions.isEmpty())
        assertTrue((page.body as StreamPageBody.Content).gaps.isEmpty())
        val withShortcuts =
            StreamShortcutPages.plan(
                listOf(files),
                emptySet(),
                mapOf(files.identity to listOf(shortcut(files, "a"))),
                true,
            ).single()
        assertEquals(card, cards(withShortcuts).single())
        assertEquals(listOf("a"), withShortcuts.quickActions.map { it.id })
    }

    @Test
    fun withoutTheLauncherRoleThePageIsTheQuietCardAndAnExplainingGapWithNoQuickActions() {
        val page =
            StreamShortcutPages.plan(
                listOf(files),
                emptySet(),
                mapOf(files.identity to listOf(shortcut(files, "recent"))),
                launcherRole = false,
            ).single()
        assertTrue(cards(page).single().opensApp)
        assertTrue(page.quickActions.isEmpty())
        val gaps = (page.body as StreamPageBody.Content).gaps
        assertEquals(
            listOf(
                StreamGap(
                    StreamSourceIds.SHORTCUTS,
                    StreamSectionResult.PermissionRequired(listOf(StreamSourceIds.SHORTCUTS)),
                ),
            ),
            gaps,
        )
    }

    @Test
    fun theSameAppPinnedTwiceHasOnePageAndAnotherProfileOfIt() {
        val work = PinnedApp(identity("com.files", AppProfile.work()), "Files (work)")
        val pages = StreamShortcutPages.plan(listOf(files, files, work), emptySet(), emptyMap(), true)
        assertEquals(listOf("com.files:personal", "com.files:work"), pages.map { it.groupKey })
    }

    @Test
    fun theQuietCardLaunchesAndAQuickActionLaunchesButNothingElseIsOffered() {
        val page =
            StreamShortcutPages.plan(
                listOf(files),
                emptySet(),
                mapOf(files.identity to listOf(shortcut(files, "a"))),
                true,
            ).single()
        val a = page.quickActions.single().shortcut
        assertTrue(StreamActionPolicy.offers(a, StreamAction.Open))
        assertFalse(StreamActionPolicy.offers(a, StreamAction.Dismiss))
        val quiet = cards(page).single()
        assertTrue(StreamActionPolicy.offers(quiet, StreamAction.Open))
        assertNull(StreamHideItems.itemFor(a))
    }

    @Test
    fun focusOnAPinnedAppLandsOnItsLivePageElseItsShortcutsPageElseNothing() {
        val shortcuts = StreamShortcutPages.plan(listOf(files, chat), emptySet(), emptyMap(), true)
        val live =
            StreamPagePlanner.plan(
                StreamLayout.DEFAULT,
                mapOf(
                    StreamSection.NOTIFICATIONS to
                        StreamSectionResult.Ready(
                            listOf(
                                StreamGroup(
                                    "com.chat:personal",
                                    "Chat",
                                    listOf(
                                        NotificationContent(
                                            id = StreamContentId("n"),
                                            app = StreamAppRef("com.chat", "personal"),
                                            appLabel = "Chat",
                                            title = "t",
                                            text = null,
                                            icon = null,
                                            image = null,
                                            postedAtEpochMillis = 1,
                                            category = null,
                                            priority = null,
                                            target = com.riffle.core.domain.launcher.workspace.ItemTarget.None,
                                            actions = emptySet(),
                                            privacy = StreamPrivacy.VISIBLE,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                ),
            )
        val pages = shortcuts + live
        assertEquals(
            "shortcuts/com.files:personal",
            StreamFocus.resolvePage(StreamFocusTarget.AppShortcuts("com.files:personal"), pages),
        )
        // Chat is live: its notifications page wins over a shortcuts page that exists beside it.
        assertEquals(
            "notifications/com.chat:personal",
            StreamFocus.resolvePage(StreamFocusTarget.AppShortcuts("com.chat:personal"), pages),
        )
        assertNull(StreamFocus.resolvePage(StreamFocusTarget.AppShortcuts("com.gone:personal"), pages))
    }

    private fun liveChatPage(vararg titles: String): StreamPage {
        val items =
            titles.mapIndexed { index, title ->
                com.riffle.core.domain.launcher.workspace.ItemTarget.App("com.chat", "personal").let { target ->
                    NotificationContent(
                        id = StreamContentId("n:$index"),
                        app = StreamAppRef("com.chat", "personal"),
                        appLabel = "Chat",
                        title = title,
                        text = "x",
                        icon = null,
                        image = null,
                        postedAtEpochMillis = 100L - index,
                        category = null,
                        priority = null,
                        target = target,
                        actions = setOf(NotificationAction.OPEN, NotificationAction.DISMISS),
                        privacy = StreamPrivacy.VISIBLE,
                    )
                }
            }
        return StreamPagePlanner.plan(
            StreamLayout.DEFAULT,
            mapOf(
                StreamSection.NOTIFICATIONS to
                    StreamSectionResult.Ready(listOf(StreamGroup("com.chat:personal", "Chat", items, items.size))),
            ),
        ).first { page -> page.section == StreamSection.NOTIFICATIONS }
    }

    @Test
    fun aPinnedAppThatIsLiveHasOnePageOfItsLiveCardsOnlyAndItsShortcutsAreItsQuickActions() {
        val shortcuts =
            StreamShortcutPages.plan(
                listOf(chat, files),
                emptySet(),
                mapOf(
                    chat.identity to
                        listOf(shortcut(chat, "compose", "Compose"), shortcut(chat, "scan", "Scan", rank = 1)),
                ),
                launcherRole = true,
            )
        val live = liveChatPage("Sam", "Priya")
        val pages = StreamShortcutPages.merge(listOf(live), shortcuts)

        // Files has nothing live: its quiet page stays. Chat has one page, the live one: no second page.
        assertEquals(listOf("shortcuts/com.files:personal", "notifications/com.chat:personal"), pages.map { it.key })
        val merged = pages.last()
        assertEquals(StreamSection.NOTIFICATIONS, merged.section)
        val items = (merged.body as StreamPageBody.Content).items
        // Only the live cards, newest first (the live page's own order); the shortcuts are not cards.
        assertEquals(listOf("Sam", "Priya"), items.map { assertIs<NotificationContent>(it).title })
        assertEquals(live.count, merged.count)
        assertEquals(live.lead, merged.lead)
        assertEquals(listOf("compose", "scan"), merged.quickActions.map { it.id })
        // The quiet page has the quiet card and no quick actions of its own here.
        assertTrue(pages.first().quickActions.isEmpty())
    }

    @Test
    fun aLivePageWithoutTheRoleKeepsItsCardsAndTheGapThatSaysWhyThereAreNoQuickActions() {
        val shortcuts = StreamShortcutPages.plan(listOf(chat), emptySet(), emptyMap(), launcherRole = false)
        val merged = StreamShortcutPages.merge(listOf(liveChatPage("Sam")), shortcuts).single()
        assertTrue(merged.quickActions.isEmpty())
        val body = merged.body as StreamPageBody.Content
        assertEquals(1, body.items.size)
        assertEquals(1, body.gaps.size)
    }

    @Test
    fun mergingLeavesPagesOfAppsThatAreNotPinnedAndAppsWithNothingLiveAlone() {
        val shortcuts = StreamShortcutPages.plan(listOf(files), emptySet(), emptyMap(), launcherRole = true)
        val live = liveChatPage("Sam")
        val pages = StreamShortcutPages.merge(listOf(live), shortcuts)
        assertEquals(listOf("shortcuts/com.files:personal", "notifications/com.chat:personal"), pages.map { it.key })
        assertEquals(live, pages.last())
        assertTrue(pages.last().quickActions.isEmpty())
    }
}
