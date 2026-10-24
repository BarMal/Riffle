package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacementEngine
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.WidgetResizeConstraints
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PoolHomeProjectionTest {
    private val all = LauncherPageId("all")
    private val today = LauncherPageId("today")

    /** The per-mode layout `HomeLayoutSet` keeps: two Home slots around an All apps page, a generated page. */
    private val base =
        HomeLayout(
            viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
            pages =
                listOf(
                    LauncherPage(P1, grid = GridDimensions(4, 4)),
                    LauncherPage(all, LauncherPageType.AllApps, GridDimensions(4, 4)),
                    LauncherPage(P2, grid = GridDimensions(4, 4)),
                    LauncherPage(
                        today,
                        LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY),
                        GridDimensions(4, 4),
                    ),
                ),
            selectedPageId = P2,
            dock = DockModel(capacity = 3),
        )

    /** W1 and W2 share app `a` and widget `w`; W1 has folder `f`; the dock pins `d` and holds widget `dw`. */
    private val pool: PlacedItemPool =
        emptyPool()
            .add(poolApp("a"), cell = at(0, 0))
            .add(poolFolder("f", "x", "y"), cell = at(1, 0))
            .add(poolWidget("w", host = 10), page = P2, cell = at(0, 0, 2, 2))
            .let { PoolPlacement.placeExisting(it, W2, P1, PoolItemId("a"), at(3, 3)).done().pool }
            .let { PoolPlacement.placeExisting(it, W2, P2, PoolItemId("w"), at(2, 2, 2, 2)).done().pool }
            .let { withDock(it, listOf(poolApp("d")), listOf(poolWidget("dw", host = 20) to at(0, 0, 2, 1))) }

    private fun withDock(
        pool: PlacedItemPool,
        pins: List<PoolItem>,
        panel: List<Pair<PoolItem, com.riffle.core.domain.launcher.home.GridPlacement>>,
    ): PlacedItemPool =
        pool.copy(
            items = pool.items + (pins + panel.map { it.first }).associateBy { it.id },
            dock =
                PoolDock(
                    pins = pins.map { it.id },
                    panel =
                        ArrangementPage(
                            LauncherPageId("panel"),
                            GridDimensions(4, 2),
                            panel.map {
                                    (item, at) ->
                                Placement(item.id, at)
                            },
                        ),
                    settings = PoolDockSettings(capacity = 4, libraryPosition = DockPosition.RIGHT),
                ),
        )

    private val ids = counterIds("fresh")

    private fun project(ws: WorkspaceId = W1) = PoolHomeProjection.project(pool, ws, base)

    private fun ingest(
        after: HomeLayout,
        ws: WorkspaceId = W1,
    ) = PoolHomeProjection.ingest(pool, ws, project(ws), after, ids)

    private fun HomeLayout.mapHome(transform: (LauncherPage) -> LauncherPage) =
        copy(pages = pages.map { if (it.type == LauncherPageType.Home) transform(it) else it })

    private fun HomeLayout.page(id: LauncherPageId) = pages.first { it.id == id }

    private fun HomeLayout.withPage(page: LauncherPage) = copy(pages = pages.map { if (it.id == page.id) page else it })

    private fun HomeLayout.item(id: String): LauncherItem =
        (pages.flatMap { it.items } + dock.items + dock.panel?.items.orEmpty()).first { it.id.value == id }

    @Test
    fun projectionReplacesHomeSlotsKeepsOtherPagesAndUsesPoolIds() {
        val projected = project()
        assertEquals(listOf(P1, all, P2, today), projected.pages.map { it.id })
        assertEquals(P2, projected.selectedPageId)
        assertEquals(setOf("a", "f"), projected.page(P1).items.map { it.id.value }.toSet())
        assertEquals(HostedWidgetId(10), (projected.item("w") as WidgetItem).appWidgetId)
        assertEquals(listOf("d"), projected.dock.items.map { it.id.value })
        assertEquals(listOf("dw"), projected.dock.panel!!.items.map { it.id.value })
        assertEquals(4, projected.dock.capacity)
        assertEquals(base.settings, projected.settings)
    }

    @Test
    fun projectionHandlesMoreAndFewerArrangementPagesAndKeepsALayoutValid() {
        val three = PoolHomeEditing.addPage(pool, W1, counterIds("p")).done().pool
        val projected = PoolHomeProjection.project(three, W1, base)
        assertEquals(listOf(P1, all, P2, LauncherPageId("page-p1"), today), projected.pages.map { it.id })
        val one =
            PoolHomeEditing.removeEmptyPage(
                PoolRemoval.remove(pool, W2, PoolItemId("w")).done().pool,
                W2,
                P2,
            ).done().pool
        val fewer = PoolHomeProjection.project(one, W2, base)
        assertEquals(listOf(P1, all, today), fewer.pages.map { it.id })
        assertEquals(P1, fewer.selectedPageId, "the selected page left: the first Home page is selected")
        val none =
            PoolHomeProjection.project(
                pool,
                W3,
                base.copy(pages = listOf(base.pages.first()), selectedPageId = P1),
            )
        assertEquals(listOf(P1), none.pages.map { it.id })
        assertEquals(emptyList(), none.pages.single().items)
        assertEquals(base.dock, PoolHomeProjection.project(pool.copy(dock = null), W1, base).dock, "no pool dock yet")
    }

    @Test
    fun projectThenIngestWithNoChangeIsTheIdentity() {
        listOf(W1, W2).forEach { ws ->
            val projected = project(ws)
            val edit = PoolHomeProjection.ingest(pool, ws, projected, projected, ids)
            assertEquals(pool, edit.pool)
            assertEquals(emptySet(), edit.releasedHostIds)
            assertEquals(emptyList(), edit.removedItems)
            assertEquals(emptyList(), edit.createdItemIds)
        }
    }

    @Test
    fun projectThenIngestIsTheIdentityOverRandomPools() {
        for (seed in 1..120) {
            val random = randomPool(seed)
            assertInvariants(random)
            random.arrangements.keys.forEach { ws ->
                val projected = PoolHomeProjection.project(random, ws, base)
                val edit = PoolHomeProjection.ingest(random, ws, projected, projected, counterIds("r$seed-"))
                assertEquals(random, edit.pool, "seed $seed workspace $ws")
                assertEquals(emptySet(), edit.releasedHostIds, "seed $seed")
            }
        }
    }

    @Test
    fun aMoveIsAPlacementEdit() {
        val before = project()
        val moved = GridPlacementEngine().moveItem(before.page(P1), LauncherItemId("a"), at(2, 2))
        val after = before.withPage((moved as com.riffle.core.domain.launcher.home.PlaceLauncherItemResult.Placed).page)
        val edit = ingest(after)
        assertEquals(at(2, 2), PoolHomeEditing.siteOf(edit.pool, W1, PoolItemId("a"))!!.placement.at)
        assertEquals(at(3, 3), PoolHomeEditing.siteOf(edit.pool, W2, PoolItemId("a"))!!.placement.at, "W2 untouched")
        assertEquals(pool.items, edit.pool.items)
        assertInvariants(edit.pool)
    }

    @Test
    fun removingASharedAppOrWidgetKeepsItForTheOtherWorkspace() {
        val before = project()
        val after =
            before.mapHome {
                    page ->
                page.copy(items = page.items.filterNot { it.id.value in setOf("a", "w") })
            }
        val edit = ingest(after)
        assertEquals(1, edit.pool.refs("a"))
        assertEquals(1, edit.pool.refs("w"))
        assertEquals(emptySet(), edit.releasedHostIds, "W2 still shows the widget instance")
        val again =
            PoolHomeProjection.ingest(
                edit.pool,
                W2,
                PoolHomeProjection.project(edit.pool, W2, base),
                PoolHomeProjection.project(edit.pool, W2, base).mapHome {
                        p ->
                    p.copy(items = p.items.filterNot { it.id.value == "w" })
                },
                ids,
            )
        assertEquals(setOf(HostedWidgetId(10)), again.releasedHostIds, "the last reference releases the host id")
        assertInvariants(again.pool)
    }

    @Test
    fun removingAnUnsharedFolderCollectsIt() {
        val after = project().mapHome { page -> page.copy(items = page.items.filterNot { it.id.value == "f" }) }
        val edit = ingest(after)
        assertNull(edit.pool.items[PoolItemId("f")])
        assertEquals(listOf(PoolItemId("f")), edit.removedItems.map { it.id })
    }

    @Test
    fun creatingAFolderGivesItAFreshPoolIdAndRenameAddRemoveEditTheOneFolder() {
        val before = project()
        val z1 = AppShortcutItem(LauncherItemId("z1"), appIdentity("z"), "z")
        val a = before.item("a") as AppShortcutItem
        val created =
            before.withPage(
                before.page(P1).copy(
                    items =
                        before.page(P1).items.filterNot { it.id.value == "a" } +
                            FolderItem(LauncherItemId("folder:1"), "New", listOf(a.copy(placement = null)), at(0, 0)),
                ),
            )
        val edit = ingest(created)
        val folder = edit.pool.items.values.filterIsInstance<PoolFolder>().single { it.label == "New" }
        assertTrue(folder.id.value.startsWith("fresh"), "engine id is not trusted")
        assertEquals(listOf("a"), folder.entries.map { it.entryId })
        assertEquals(1, edit.pool.refs("a"), "W2 still shows a")
        val renamed =
            before.withPage(
                before.page(P1).copy(
                    items =
                        before.page(P1).items.map {
                            if (it is FolderItem) {
                                it.copy(
                                    label = "Renamed",
                                    items = it.items.drop(1) + z1,
                                )
                            } else {
                                it
                            }
                        },
                ),
            )
        val folderEdit = ingest(renamed).pool.items.getValue(PoolItemId("f")) as PoolFolder
        assertEquals("Renamed", folderEdit.label)
        assertEquals(listOf("f-y", "z1"), folderEdit.entries.map { it.entryId })
        assertInvariants(edit.pool)
    }

    @Test
    fun addingResizingAndRemovingAWidget() {
        val before = project()
        val newWidget = WidgetItem(LauncherItemId("widget:1"), HostedWidgetId(30), "New", placement = at(0, 2, 2, 2))
        val added =
            before.withPage(
                before.page(
                    P1,
                ).copy(
                    items = before.page(P1).items + newWidget,
                ),
            )
        val addEdit = ingest(added)
        val widget =
            addEdit.pool.items.values.filterIsInstance<PoolWidget>().single { it.hostedId == HostedWidgetId(30) }
        assertNotEquals("widget:1", widget.id.value)
        assertEquals(1, addEdit.pool.refs(widget.id.value))

        val resized =
            before.withPage(
                before.page(P2).copy(
                    items =
                        before.page(P2).items.map {
                            if (it.id.value == "w") {
                                (it as WidgetItem).copy(
                                    placement = at(0, 0, 3, 2),
                                    resizeConstraints = WidgetResizeConstraints(minSpan = GridSpan(2, 2)),
                                )
                            } else {
                                it
                            }
                        },
                ),
            )
        val resizeEdit = ingest(resized)
        assertEquals(at(0, 0, 3, 2), PoolHomeEditing.siteOf(resizeEdit.pool, W1, PoolItemId("w"))!!.placement.at)
        assertEquals(at(2, 2, 2, 2), PoolHomeEditing.siteOf(resizeEdit.pool, W2, PoolItemId("w"))!!.placement.at)
        assertEquals(
            GridSpan(2, 2),
            (resizeEdit.pool.items.getValue(PoolItemId("w")) as PoolWidget).resizeConstraints.minSpan,
        )
        assertEquals(HostedWidgetId(10), (resizeEdit.pool.items.getValue(PoolItemId("w")) as PoolWidget).hostedId)

        val removeNew =
            PoolHomeProjection.ingest(
                addEdit.pool,
                W1,
                PoolHomeProjection.project(addEdit.pool, W1, base),
                before,
                ids,
            )
        assertEquals(setOf(HostedWidgetId(30)), removeNew.releasedHostIds)
        assertInvariants(resizeEdit.pool)
    }

    @Test
    fun pagesAreAddedDeletedAndReordered() {
        val before = project()
        val newPage =
            LauncherPage(
                LauncherPageId("p9"),
                grid = GridDimensions(4, 4),
                items = listOf(AppShortcutItem(LauncherItemId("app:n:0"), appIdentity("n"), "n", placement = at(0, 0))),
            )
        val added = before.copy(pages = before.pages + newPage)
        val addEdit = ingest(added)
        assertEquals(listOf(P1, P2, LauncherPageId("p9")), addEdit.pool.arrangements.getValue(W1).pages.map { it.id })
        val reordered = before.copy(pages = listOf(before.page(P2), before.page(all), before.page(P1)))
        assertEquals(listOf(P2, P1), ingest(reordered).pool.arrangements.getValue(W1).pages.map { it.id })
        val deleted = before.copy(pages = before.pages.filter { it.id != P2 }, selectedPageId = P1)
        val deleteEdit = ingest(deleted)
        assertEquals(listOf(P1), deleteEdit.pool.arrangements.getValue(W1).pages.map { it.id })
        assertEquals(emptySet(), deleteEdit.releasedHostIds, "w is still in W2")
        assertEquals(pool.arrangements.getValue(W2), deleteEdit.pool.arrangements.getValue(W2))
        assertInvariants(deleteEdit.pool)
    }

    @Test
    fun nonHomePagesAreNotThePoolsAndAreIgnored() {
        val before = project()
        val after = before.copy(pages = before.pages.filter { it.type == LauncherPageType.Home }, selectedPageId = P1)
        assertEquals(pool, ingest(after).pool)
    }

    @Test
    fun dockPinsAreAddedRemovedAndReordered() {
        val before = project()
        val pinned =
            before.copy(
                dock =
                    before.dock.copy(
                        items = before.dock.items + AppShortcutItem(LauncherItemId("app:p:0"), appIdentity("p"), "p"),
                    ),
            )
        val pinEdit = ingest(pinned)
        val pins = pinEdit.pool.dock!!.pins
        assertEquals(2, pins.size)
        assertEquals(PoolItemId("d"), pins.first())
        assertTrue(pins[1].value.startsWith("fresh"))
        val back = PoolHomeProjection.project(pinEdit.pool, W1, base)
        val reordered =
            PoolHomeProjection.ingest(
                pinEdit.pool,
                W1,
                back,
                back.copy(dock = back.dock.copy(items = back.dock.items.reversed())),
                ids,
            )
        assertEquals(pins.reversed(), reordered.pool.dock!!.pins)
        val unpinned = ingest(before.copy(dock = before.dock.copy(items = emptyList())))
        assertEquals(emptyList(), unpinned.pool.dock!!.pins)
        assertNull(unpinned.pool.items[PoolItemId("d")])
        assertEquals(DockPosition.RIGHT, unpinned.pool.dock!!.settings.libraryPosition, "Library's edge is kept")
        assertInvariants(pinEdit.pool)
    }

    @Test
    fun dockPanelWidgetsAndDockSettings() {
        val before = project()
        val panelWidget = WidgetItem(LauncherItemId("widget:p"), HostedWidgetId(40), "P", placement = at(2, 0, 2, 1))
        val panel = before.dock.panel!!
        val added =
            before.copy(
                dock =
                    before.dock.copy(
                        iconSizeDp = 36,
                        panel =
                            panel.copy(
                                items = panel.items + panelWidget,
                            ),
                    ),
            )
        val addEdit = ingest(added)
        assertEquals(2, addEdit.pool.dock!!.panel!!.placements.size)
        assertEquals(36, addEdit.pool.dock!!.settings.iconSizeDp)
        val removed = ingest(before.copy(dock = before.dock.copy(panel = panel.copy(items = emptyList()))))
        assertEquals(setOf(HostedWidgetId(20)), removed.releasedHostIds)
        val disabled = ingest(before.copy(dock = before.dock.copy(panel = null)))
        assertNull(disabled.pool.dock!!.panel)
        assertEquals(setOf(HostedWidgetId(20)), disabled.releasedHostIds)
        assertInvariants(addEdit.pool)
    }

    @Test
    fun movingASharedAppToTheDockClonesItAndASharedWidgetLeavesTheOtherArrangement() {
        val before = project()
        val a = before.item("a")
        val appToDock =
            before.mapHome { p -> p.copy(items = p.items.filterNot { it.id == a.id }) }
                .let {
                    it.copy(
                        dock = it.dock.copy(items = it.dock.items + (a as AppShortcutItem).copy(placement = null)),
                    )
                }
        val appEdit = ingest(appToDock)
        assertEquals(1, appEdit.pool.refs("a"), "W2 keeps its a")
        val pinned = appEdit.pool.dock!!.pins.last()
        assertNotEquals(PoolItemId("a"), pinned)
        assertEquals(
            (pool.items.getValue(PoolItemId("a")) as PoolApp).appIdentity,
            (appEdit.pool.items.getValue(pinned) as PoolApp).appIdentity,
        )
        assertInvariants(appEdit.pool)

        val w = before.item("w")
        val panel = before.dock.panel!!
        val widgetToDock =
            before.mapHome { p -> p.copy(items = p.items.filterNot { it.id == w.id }) }
                .let {
                    it.copy(
                        dock =
                            it.dock.copy(
                                panel =
                                    panel.copy(
                                        items = panel.items + (w as WidgetItem).copy(placement = at(2, 0, 2, 2)),
                                    ),
                            ),
                    )
                }
        val widgetEdit =
            ingest(
                widgetToDock.copy(
                    dock = widgetToDock.dock.copy(panel = widgetToDock.dock.panel!!.copy(grid = GridDimensions(4, 2))),
                ),
            )
        assertEquals(1, widgetEdit.pool.refs("w"), "one instance, now only in the dock")
        assertTrue(PoolItemId("w") in widgetEdit.pool.dock!!.referenced)
        assertEquals(0, PoolReferences.index(widgetEdit.pool)[PoolItemId("w")]?.size ?: 0)
        assertEquals(emptySet(), widgetEdit.releasedHostIds)
        assertInvariants(widgetEdit.pool)
    }

    @Test
    fun anEngineMintedIdEqualToAnotherArrangementsItemIsNotTrusted() {
        val w2Only = PoolPlacement.addNew(pool, W2, P1, poolApp("only2"), at(0, 3)).done().pool
        val before = PoolHomeProjection.project(w2Only, W1, base)
        val clash = AppShortcutItem(LauncherItemId("only2"), appIdentity("other"), "other", placement = at(3, 3))
        val after = before.withPage(before.page(P1).copy(items = before.page(P1).items + clash))
        val edit = PoolHomeProjection.ingest(w2Only, W1, before, after, ids)
        assertEquals(appIdentity("only2"), (edit.pool.items.getValue(PoolItemId("only2")) as PoolApp).appIdentity)
        val minted = PoolHomeEditing.siteOf(edit.pool, W1, PoolItemId("only2"))
        assertNull(minted, "W1 got a fresh id, not W2's item")
        assertEquals(1, edit.pool.items.values.count { it is PoolApp && it.appIdentity == appIdentity("other") })
        assertInvariants(edit.pool)
    }

    @Test
    fun aWorkspaceWithoutAnArrangementGetsOneOnItsFirstIngest() {
        val before = PoolHomeProjection.project(pool, W3, base)
        assertEquals(listOf(all, today), before.pages.map { it.id }, "no Home pages, and no stand-in needed")
        val first =
            LauncherPage(
                P1,
                grid = GridDimensions(4, 4),
                items = listOf(AppShortcutItem(LauncherItemId("app:q:0"), appIdentity("q"), "q", placement = at(0, 0))),
            )
        val after = before.copy(pages = listOf(first) + before.pages)
        val edit = PoolHomeProjection.ingest(pool, W3, before, after, ids)
        assertEquals(listOf(P1), edit.pool.arrangements.getValue(W3).pages.map { it.id })
        assertEquals(1, edit.pool.arrangements.getValue(W3).pages.first().placements.size)
        assertInvariants(edit.pool)
    }

    /** A random valid pool over W1..W3 with shared apps, folders and widgets, and a random dock. */
    private fun randomPool(seed: Int): PlacedItemPool {
        val r = Random(seed)
        var p = emptyPool(listOf(W1, W2, W3))
        var n = 0
        repeat(25) {
            val ws = listOf(W1, W2, W3)[r.nextInt(3)]
            val page = listOf(P1, P2)[r.nextInt(2)]
            val cell = if (r.nextBoolean()) at(r.nextInt(4), r.nextInt(4), 1 + r.nextInt(2), 1) else null
            n++
            val result =
                when (r.nextInt(4)) {
                    0 -> PoolPlacement.addNew(p, ws, page, poolApp("a$n", "p${r.nextInt(3)}"), cell)
                    1 -> PoolPlacement.addNew(p, ws, page, poolFolder("f$n", "p${r.nextInt(3)}"), cell)
                    2 ->
                        PoolPlacement.addNew(
                            p,
                            ws,
                            page,
                            poolWidget("w$n", host = if (r.nextInt(4) == 0) null else 100 + n),
                            cell,
                        )
                    else ->
                        p.items.keys.toList().takeIf { it.isNotEmpty() }?.let { keys ->
                            val id = keys[r.nextInt(keys.size)]
                            if (p.items[id] is PoolFolder) {
                                PoolPlacement.shareFolder(p, ws, page, id, cell)
                            } else {
                                PoolPlacement.placeExisting(p, ws, page, id, cell)
                            }
                        } ?: PoolResult.Rejected(PoolRejection.UNKNOWN_ITEM)
                }
            p = result.poolOr(p)
        }
        val pins = (0 until r.nextInt(4)).map { if (it == 1) poolFolder("df$it", "p0") else poolApp("dp$it", "p$it") }
        val panel = if (r.nextBoolean()) listOf(poolWidget("dw", host = 900 + seed) to at(0, 0, 2, 1)) else emptyList()
        return withDock(p, pins, panel)
    }
}
