package com.riffle.core.domain.launcher.search

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.library.LibraryHit
import com.riffle.core.domain.launcher.stream.StreamCardSizes
import com.riffle.core.domain.launcher.stream.StreamShortcutPages
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SearchStackTest {
    private fun identity(name: String) = AppIdentity(AppPackageName("com.example.$name"), AppActivityName(".Main"))

    private fun app(name: String) = InstalledApp(identity(name), name.replaceFirstChar { it.uppercase() })

    private fun shortcut(
        appName: String,
        id: String,
        rank: Int = 0,
        enabled: Boolean = true,
    ) = AppShortcut(AppShortcutId(id), identity(appName), shortLabel = id, enabled = enabled, rank = rank)

    private val maps = app("maps")
    private val notes = app("notes")
    private val clock = app("clock")

    private fun hits(vararg apps: InstalledApp) = apps.map { LibraryHit.App(it) }

    @Test
    fun theBestMatchIsTheFrontCardAndTheNextOnesFollowInRankOrder() {
        val cards = SearchStack.plan("m", hits(maps, notes, clock))

        assertEquals(
            listOf("Maps", "Notes", "Clock"),
            cards.filterIsInstance<SearchStackCard.App>().map { it.app.label },
        )
        assertIs<SearchStackCard.App>(cards.first())
    }

    @Test
    fun anAppCardCarriesItsEnabledShortcutsInPlatformRankOrderCappedLikeAStreamPage() {
        val shortcuts =
            (1..12).map { shortcut("maps", "s$it", rank = 20 - it) } + shortcut("maps", "off", rank = 0, enabled = false)
        val card =
            SearchStack.plan("maps", hits(maps), mapOf(maps.identity to shortcuts)).first() as SearchStackCard.App

        assertEquals(StreamShortcutPages.MAX_QUICK_ACTIONS, card.quickActions.size)
        assertEquals("s12", card.quickActions.first().id)
        assertTrue(card.quickActions.none { it.id == "off" })
    }

    @Test
    fun aCardHasMoreWhenItHasQuickActionsOrANotificationAndNotOtherwise() {
        val key = StreamShortcutPages.appKeyOf(notes.identity)
        val cards =
            SearchStack.plan(
                query = "n",
                hits = hits(maps, notes, clock),
                shortcutsByApp = mapOf(maps.identity to listOf(shortcut("maps", "route"))),
                notificationsByApp = mapOf(key to SearchStackNotification(count = 2, newestLine = "Reminder")),
            )

        assertEquals(listOf(true, true, false, false), cards.map { it.hasMore })
        assertEquals(2, (cards[1] as SearchStackCard.App).notification?.count)
    }

    @Test
    fun anAppWithNoNotificationsHasNoNotificationLine() {
        val key = StreamShortcutPages.appKeyOf(maps.identity)
        val card =
            SearchStack.plan("m", hits(maps), notificationsByApp = mapOf(key to SearchStackNotification(count = 0)))
                .first() as SearchStackCard.App

        assertEquals(null, card.notification)
        assertFalse(card.hasMore)
    }

    @Test
    fun searchWebIsTheLastCardAndTheOnlyOneWhenNothingMatches() {
        val withResults = SearchStack.plan("maps", hits(maps, notes))
        assertEquals(SearchStackCard.Web("maps", noResults = false), withResults.last())
        assertEquals(3, withResults.size)

        val none = SearchStack.plan("  zzz ", emptyList())
        assertEquals(listOf<SearchStackCard>(SearchStackCard.Web("zzz", noResults = true)), none)
    }

    @Test
    fun withoutWebSearchThereIsNoWebCardAndABlankQueryHasNoCards() {
        assertEquals(1, SearchStack.plan("maps", hits(maps), webSearch = false).size)
        assertEquals(emptyList(), SearchStack.plan("maps", emptyList(), webSearch = false))
        assertEquals(emptyList(), SearchStack.plan("   ", hits(maps)))
    }

    @Test
    fun aShortcutThatIsAlreadyAQuickActionOfItsAppIsNotAStripOfItsOwn() {
        val route = shortcut("maps", "route")
        val extra = shortcut("notes", "capture")
        val cards =
            SearchStack.plan(
                query = "r",
                hits = hits(maps) + LibraryHit.Shortcut(route) + LibraryHit.Shortcut(extra),
                shortcutsByApp = mapOf(maps.identity to listOf(route)),
            )

        assertEquals(listOf("app", "shortcut", "web"), cards.map { it.key.substringBefore(':') })
        assertEquals(extra, (cards[1] as SearchStackCard.Shortcut).shortcut)
    }

    @Test
    fun settingsAreStripsWithNothingMoreAndKeepTheirPlaceInTheRanking() {
        val entry = LauncherSearchSettingsEntry(LauncherSearchSettingsEntryId("dock"), "Dock", "Where it sits", "Layout")
        val cards = SearchStack.plan("d", hits(maps) + LibraryHit.Setting(LauncherSearchResult.Setting(entry)))

        assertEquals(listOf("app", "setting", "web"), cards.map { it.key.substringBefore(':') })
        assertFalse(cards[1].hasMore)
    }

    @Test
    fun cardKeysAreStableWhateverTheQueryAndDistinct() {
        val a = SearchStack.plan("m", hits(maps, notes))
        val b = SearchStack.plan("ma", hits(maps))
        assertEquals(a.first().key, b.first().key)
        assertEquals(a.map { it.key }.toSet().size, a.size)
    }

    @Test
    fun aTapOnAStripBringsItForwardOnlyWhenItHasMoreToShow() {
        val withMore = SearchStackCard.App(maps, SearchStack.plan("m", hits(maps), mapOf(maps.identity to listOf(shortcut("maps", "route")))).let { (it.first() as SearchStackCard.App).quickActions }, null)
        val plain = SearchStackCard.App(notes, emptyList(), null)

        assertEquals(StripTapOutcome.BRING_TO_FRONT, SearchStackTap.onStrip(withMore))
        assertEquals(StripTapOutcome.OPEN, SearchStackTap.onStrip(plain))
        assertEquals(StripTapOutcome.OPEN, SearchStackTap.onStrip(SearchStackCard.Web("m", noResults = false)))
    }

    @Test
    fun theStacksHeightAndFrontCardDoNotDependOnTheResultsAndOnlyTheRoomShortensIt() {
        for (sizes in listOf(StreamCardSizes.FOLDED, StreamCardSizes.UNFOLDED)) {
            val inset = 24f
            val desired = SearchStackLayout.desiredHeightDp(sizes, inset)
            assertEquals(sizes.maxPeeks, SearchStackLayout.peeksFor(sizes, desired, inset))
            // The room (a keyboard) takes strips away, never the front card's height.
            val tight = desired - sizes.pitchDp * 2
            assertEquals(sizes.maxPeeks - 2, SearchStackLayout.peeksFor(sizes, tight, inset))
            assertEquals(0, SearchStackLayout.peeksFor(sizes, 10f, inset))
            assertEquals(SearchStackLayout.frontHeightDp(sizes), SearchStackLayout.frontHeightDp(sizes))
        }
        assertTrue(
            SearchStackLayout.frontHeightDp(StreamCardSizes.UNFOLDED) > SearchStackLayout.frontHeightDp(StreamCardSizes.FOLDED),
        )
    }
}
