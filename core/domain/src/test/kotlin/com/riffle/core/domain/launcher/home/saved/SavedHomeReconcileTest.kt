package com.riffle.core.domain.launcher.home.saved

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.holds
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedHomeReconcileTest {
    private var counter = 0
    private val everything = listOf("mail", "maps", "music", "notes", "photos", "clock", "news").map(::identity).toSet()

    private fun saved(
        vararg pages: LauncherPage,
        noProvider: Boolean = false,
    ) = SavedHomeCapture.capture(
        pages = pages.toList(),
        providerOf = if (noProvider) ({ null }) else providers(10 to "calendar", 11 to "weather", 12 to "tasks"),
        id = "saved-1",
        name = "Layout",
        kind = SavedHomeLayoutKind.SAVED,
        savedAtEpochMillis = 5L,
    )

    private fun context(
        installed: Set<com.riffle.core.domain.launcher.apps.AppIdentity> = everything,
        hidden: Set<com.riffle.core.domain.launcher.apps.AppIdentity> = emptySet(),
        dock: Set<DockApp> = emptySet(),
        grid: GridDimensions = GRID,
        live: List<LauncherPage> = emptyList(),
        liveProviders: (HostedWidgetId) -> WidgetProviderIdentity? = providers(),
    ) = SavedHomeContext(installed, hidden, dock, grid, live, liveProviders) { "n${counter++}" }

    private fun ReconciledHome.items() = pages.flatMap { it.items }

    private fun ReconciledHome.apps() = items().filterIsInstance<AppShortcutItem>().map { it.label }

    @Test
    fun anAppThatIsNoLongerInstalledIsDropped() {
        val result =
            SavedHomeReconciler.reconcile(
                saved(page("p", app("mail", 0, 0), app("maps", 1, 0))),
                context(installed = setOf(identity("mail"))),
            )
        assertEquals(listOf("mail"), result.apps())
        assertEquals(1, result.droppedApps)
    }

    @Test
    fun aFolderLeftWithOneAppStaysAFolderAndAnEmptyFolderIsRemoved() {
        val result =
            SavedHomeReconciler.reconcile(
                saved(page("p", folder("work", 0, 0, "mail", "maps"), folder("fun", 1, 0, "music", "news"))),
                context(installed = setOf(identity("mail"))),
            )
        val folders = result.items().filterIsInstance<FolderItem>()
        assertEquals(listOf("work"), folders.map { it.label })
        assertEquals(listOf("mail"), folders.single().items.map { it.label })
    }

    @Test
    fun hiddenAppsAreNotPlacedAndTheSavedLayoutStillHoldsThem() {
        val layout = saved(page("p", app("mail", 0, 0), app("maps", 1, 0)))
        val result = SavedHomeReconciler.reconcile(layout, context(hidden = setOf(identity("maps"))))
        assertEquals(listOf("mail"), result.apps())
        assertEquals(2, layout.itemCount)
    }

    @Test
    fun appsInstalledSinceAreNotAdded() {
        val result = SavedHomeReconciler.reconcile(saved(page("p", app("mail", 0, 0))), context())
        assertEquals(listOf("mail"), result.apps())
    }

    @Test
    fun anAppInTheDockStaysInTheDockAndIsNotDuplicatedOnHome() {
        val layout =
            saved(
                page("p", app("mail", 0, 0), app("maps", 1, 0, shortcut = "route"), folder("f", 2, 0, "mail", "notes")),
            )
        val result =
            SavedHomeReconciler.reconcile(layout, context(dock = setOf(DockApp(identity("mail")))))
        // The app icon goes, the shortcut of another kind of the same app stays, the folder keeps its other app.
        assertEquals(listOf("maps"), result.apps())
        assertEquals(listOf("notes"), result.items().filterIsInstance<FolderItem>().single().items.map { it.label })
    }

    @Test
    fun everyItemGetsANewIdSoASavedIdCannotMeanALiveItem() {
        val live = page("live", app("maps", 0, 0))
        val result = SavedHomeReconciler.reconcile(saved(page("p", app("mail", 0, 0))), context(live = listOf(live)))
        val ids = result.items().map { it.id.value }
        assertFalse("item-mail" in ids)
        assertEquals(ids.toSet().size, ids.size)
    }

    @Test
    fun pagesTheRuleLeftEmptyAreDroppedAndOnePageAlwaysStays() {
        val layout = saved(page("a", app("mail", 0, 0)), page("b", app("maps", 0, 0)), page("c", app("music", 0, 0)))
        val dropped =
            SavedHomeReconciler.reconcile(
                layout,
                context(installed = setOf(identity("mail"), identity("music"))),
            )
        assertEquals(
            listOf("mail", "music"),
            dropped.pages.map {
                    p ->
                p.items.single().let { (it as AppShortcutItem).label }
            },
        )
        val none = SavedHomeReconciler.reconcile(layout, context(installed = emptySet()))
        assertEquals(1, none.pages.size)
        assertTrue(none.pages.single().items.isEmpty())
    }

    @Test
    fun anEmptyPageTheUserKeptInTheMiddleStaysButTrailingEmptyPagesGo() {
        val layout = saved(page("a", app("mail", 0, 0)), page("b"), page("c", app("music", 0, 0)), page("d"))
        val result = SavedHomeReconciler.reconcile(layout, context())
        assertEquals(listOf(1, 0, 1), result.pages.map { it.items.size })
    }

    @Test
    fun aPinnedEmptyPageStays() {
        val layout = saved(page("a", app("mail", 0, 0)), page("b", pinned = true))
        val result = SavedHomeReconciler.reconcile(layout, context())
        assertEquals(2, result.pages.size)
    }

    @Test
    fun itemsThatNoLongerFitTheGridMoveToTheFirstFreeCellAndNothingOverlaps() {
        // Saved on a 4 x 3 grid, restored onto 2 x 2: three of the four apps are outside.
        val layout = saved(page("p", app("mail", 0, 0), app("maps", 3, 0), app("music", 0, 2), app("notes", 3, 2)))
        val small = GridDimensions(2, 2)
        val result = SavedHomeReconciler.reconcile(layout, context(grid = small))
        assertEquals(4, result.items().size)
        assertTrue(result.pages.all { it.grid == small })
        result.pages.forEach { page ->
            val cells = page.items.map { it.placement!!.cell }
            assertEquals(cells.toSet().size, cells.size)
            assertTrue(page.items.all { small.holds(it.placement) })
        }
    }

    @Test
    fun overlappingSavedItemsAreSeparated() {
        val layout = saved(page("p", app("mail", 0, 0), app("maps", 0, 0)))
        val result = SavedHomeReconciler.reconcile(layout, context())
        assertEquals(2, result.items().size)
        assertEquals(2, result.items().map { it.placement!!.cell }.toSet().size)
    }

    @Test
    fun aFullPageSpillsToANewPage() {
        val full = (0 until 4).flatMap { c -> (0 until 3).map { r -> "a$c$r" to (c to r) } }
        val items = full.map { (name, cell) -> app("mail", cell.first, cell.second).copy(id = LauncherItemId(name)) }
        val layout = saved(page("p", *items.toTypedArray()))
        val result = SavedHomeReconciler.reconcile(layout, context(grid = GridDimensions(2, 2)))
        assertEquals(12, result.items().size)
        assertEquals(3, result.pages.size)
    }

    @Test
    fun aWidgetThatMatchesTheLiveOneKeepsItsLiveId() {
        val live = page("live", widget("cal", host = 10, column = 0, row = 0))
        val layout = saved(live)
        val result =
            SavedHomeReconciler.reconcile(
                layout,
                context(live = listOf(live), liveProviders = providers(10 to "calendar")),
            )
        val kept = result.items().single() as WidgetItem
        assertEquals(HostedWidgetId(10), kept.appWidgetId)
        assertEquals(LauncherItemId("widget-cal"), kept.id)
        assertEquals(1, result.keptWidgets)
        assertTrue(result.binds.isEmpty())
    }

    @Test
    fun aWidgetAtAnotherCellOrOfAnotherProviderIsNotKeptAndIsBoundAgain() {
        val live = page("live", widget("cal", host = 10, column = 0, row = 0))
        val saved = saved(page("p", widget("cal", host = 10, column = 2, row = 1)))
        val moved =
            SavedHomeReconciler.reconcile(
                saved,
                context(live = listOf(live), liveProviders = providers(10 to "calendar")),
            )
        val item = moved.items().single() as WidgetItem
        assertEquals(HostedWidgetId(0), item.appWidgetId)
        assertEquals(listOf(WidgetBind(item.id, 0, provider("calendar"))), moved.binds)
        assertEquals(0, moved.keptWidgets)

        val otherProvider =
            SavedHomeReconciler.reconcile(
                saved(live),
                context(live = listOf(live), liveProviders = providers(10 to "weather")),
            )
        assertEquals(HostedWidgetId(0), (otherProvider.items().single() as WidgetItem).appWidgetId)
    }

    @Test
    fun aLiveWidgetIsKeptForOneSavedWidgetOnly() {
        val live = page("live", widget("cal", host = 10, column = 0, row = 0))
        val layout = saved(page("p", widget("a", host = 10, column = 0, row = 0)))
        val twice =
            layout.copy(
                pages = layout.pages + layout.pages,
            )
        val result =
            SavedHomeReconciler.reconcile(
                twice,
                context(live = listOf(live), liveProviders = providers(10 to "calendar")),
            )
        assertEquals(1, result.keptWidgets)
    }

    @Test
    fun aWidgetWithNoKnownProviderBecomesAPlaceholderWithNothingToBind() {
        val layout = saved(page("p", widget("cal", host = 99, column = 0, row = 0)))
        val result = SavedHomeReconciler.reconcile(layout, context())
        val item = result.items().single() as WidgetItem
        assertEquals(HostedWidgetId(0), item.appWidgetId)
        assertEquals("cal", item.label)
        assertTrue(result.binds.isEmpty())
    }

    @Test
    fun aStackIsRestoredInOrderWithEveryPlatformMemberToBind() {
        val layout =
            saved(
                page("p", stack("s", 0, 0, "calendar" to 10, "weather" to 11, "tasks" to 12)).let {
                    it.copy(items = it.items)
                },
            )
        val result = SavedHomeReconciler.reconcile(layout, context())
        val item = result.items().single() as WidgetItem
        assertEquals(listOf("calendar", "weather", "tasks"), item.stack.map { it.label })
        assertEquals(listOf(0, 1, 2), result.binds.map { it.memberIndex })
        assertEquals(
            listOf(provider("calendar"), provider("weather"), provider("tasks")),
            result.binds.map { it.provider },
        )
    }

    @Test
    fun aStackThatIsTheSameLiveKeepsItsHostIds() {
        val live = page("live", stack("s", 0, 0, "calendar" to 10, "weather" to 11))
        val result =
            SavedHomeReconciler.reconcile(
                saved(live),
                context(live = listOf(live), liveProviders = providers(10 to "calendar", 11 to "weather")),
            )
        val item = result.items().single() as WidgetItem
        assertEquals(listOf(HostedWidgetId(10), HostedWidgetId(11)), item.stack.map { it.appWidgetId })
        assertTrue(result.binds.isEmpty())
    }

    @Test
    fun resolvedHostIdsAreWrittenIntoThePlainWidgetAndIntoStackMembers() {
        val layout = saved(page("p", widget("cal", 10, 0, 0), stack("s", 0, 1, "weather" to 11, "tasks" to 12)))
        val result = SavedHomeReconciler.reconcile(layout, context())
        val plainBind = result.binds.first { it.memberIndex == 0 }
        val bound =
            SavedHomeReconciler.withBoundHosts(
                result.pages,
                result.binds.associate {
                    val offset = if (it == plainBind) 0 else 50
                    (it.itemId to it.memberIndex) to HostedWidgetId(100 + it.memberIndex + offset)
                },
            )
        val plain = bound.flatMap { it.items }.filterIsInstance<WidgetItem>().first { !it.isStack }
        assertEquals(HostedWidgetId(100), plain.appWidgetId)
        val stacked = bound.flatMap { it.items }.filterIsInstance<WidgetItem>().first { it.isStack }
        assertEquals(listOf(HostedWidgetId(150), HostedWidgetId(151)), stacked.stack.map { it.appWidgetId })
        assertEquals(HostedWidgetId(150), stacked.appWidgetId)
    }

    @Test
    fun aGlanceWidgetNeedsNoBind() {
        val glance =
            com.riffle.core.domain.launcher.glance.GlanceWidgetKind.entries.first().let { kind ->
                widget("g", 0, 0, 0).copy(glance = kind, label = kind.defaultLabel)
            }
        val result = SavedHomeReconciler.reconcile(saved(page("p", glance)), context())
        assertTrue(result.binds.isEmpty())
        assertEquals(glance.glance, (result.items().single() as WidgetItem).glance)
    }

    @Test
    fun replacingHomePagesKeepsGeneratedPagesAndASelectedPage() {
        val base = HomeLayoutDefaults.standard()
        val newPages = listOf(page("x"), page("y"))
        val replaced = SavedHomeReconciler.replacingHomePages(base, newPages)
        assertEquals(listOf(LauncherPageId("x"), LauncherPageId("y")), replaced.pages.map { it.id })
        assertEquals(LauncherPageId("x"), replaced.selectedPageId)
        assertNull(replaced.pages.firstOrNull { it.id == LauncherPageId("home") })
    }
}
