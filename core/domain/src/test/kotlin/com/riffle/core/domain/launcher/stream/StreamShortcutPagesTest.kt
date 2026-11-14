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
    fun theCardsAreTheAppsShortcutsInRankOrderEachWithItsLabelsAndAnIconHandle() {
        val shortcuts =
            listOf(
                shortcut(files, "recent", "Recent", long = "Recent files", rank = 2),
                shortcut(files, "search", "Search", long = "Search", rank = 0),
                shortcut(files, "new", "New", rank = 1, enabled = false),
            )
        val page =
            StreamShortcutPages.plan(
                listOf(files),
                emptySet(),
                mapOf(files.identity to shortcuts),
                true,
            ).single()
        val cards = cards(page)
        assertEquals(listOf("search", "new", "recent"), cards.map { it.shortcutId })
        assertEquals(3, page.count)
        // A long label that repeats the short one is not shown twice; one that differs is kept.
        assertNull(cards[0].longLabel)
        assertEquals("Recent files", cards[2].longLabel)
        assertFalse(cards[1].enabled)
        assertEquals("shortcut-icon:personal/com.files/search", cards[0].icon?.key)
        assertTrue(cards.none { it.opensApp })
        assertEquals(StreamContentId("shortcut:com.files:personal/search"), cards[0].id)
    }

    @Test
    fun anAppWithNoShortcutsIsOneCardThatOpensTheApp() {
        val page = StreamShortcutPages.plan(listOf(files), emptySet(), emptyMap(), launcherRole = true).single()
        val card = cards(page).single()
        assertTrue(card.opensApp)
        assertEquals("Files", card.shortLabel)
        assertEquals(1, page.count)
        assertTrue((page.body as StreamPageBody.Content).gaps.isEmpty())
    }

    @Test
    fun withoutTheLauncherRoleThePageIsTheOpenCardAndAnExplainingGapEvenIfShortcutsWereKnown() {
        val page =
            StreamShortcutPages.plan(
                listOf(files),
                emptySet(),
                mapOf(files.identity to listOf(shortcut(files, "recent"))),
                launcherRole = false,
            ).single()
        assertTrue(cards(page).single().opensApp)
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
    fun theOpenCardLaunchesAndAShortcutCardLaunchesButNothingElseIsOffered() {
        val page =
            StreamShortcutPages.plan(
                listOf(files),
                emptySet(),
                mapOf(files.identity to listOf(shortcut(files, "a"), shortcut(files, "b", enabled = false))),
                true,
            ).single()
        val (a, b) = cards(page)
        assertTrue(StreamActionPolicy.offers(a, StreamAction.Open))
        assertFalse(StreamActionPolicy.offers(a, StreamAction.Dismiss))
        // A disabled shortcut cannot be launched; the app's own open card always can.
        assertFalse(StreamActionPolicy.offers(b, StreamAction.Open))
        val open = cards(StreamShortcutPages.plan(listOf(files), emptySet(), emptyMap(), true).single()).single()
        assertTrue(StreamActionPolicy.offers(open.copy(enabled = false), StreamAction.Open))
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
}
