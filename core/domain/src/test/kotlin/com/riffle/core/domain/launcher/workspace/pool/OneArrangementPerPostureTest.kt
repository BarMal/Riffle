package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibrary
import com.riffle.core.domain.launcher.workspace.LibraryAdd
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageRole
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.TestWorkspaces
import com.riffle.core.domain.launcher.workspace.WorkspaceHome
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.WorkspaceSetCodec
import com.riffle.core.domain.launcher.workspace.appsLens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * One arrangement per posture (ADR 0003, P8): each layout's home is the arrangement of its ACTIVE stored workspace.
 * Nothing switches workspaces any more, so the inactive ones and their arrangements must stay in storage, decodable
 * and untouched, and nothing an inactive arrangement references may be collected.
 */
class OneArrangementPerPostureTest {
    private val ids = counterIds("new")
    private val home = LauncherPageId("home")
    private val phoneKey = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.PHONE)
    private val foldableKey = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.FOLDABLE)
    private val layouts = listOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE)

    private fun app(
        id: String,
        column: Int = 0,
    ) = AppShortcutItem(LauncherItemId(id), appIdentity(id), id, placement = at(column, 0))

    private val legacyLayout =
        HomeLayout(
            viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
            pages =
                listOf(
                    LauncherPage(
                        home,
                        grid = GridDimensions(4, 5),
                        items =
                            listOf(
                                app("mail"),
                                app("maps", 1),
                                WidgetItem(
                                    LauncherItemId("clock"),
                                    HostedWidgetId(42),
                                    "Clock",
                                    placement = at(0, 1, 2, 2),
                                ),
                            ),
                    ),
                ),
            selectedPageId = home,
            dock = DockModel(capacity = 5, items = listOf(app("dock-app"))),
        )
    private val legacy =
        HomeLayoutSet(activeKey = phoneKey, layouts = mapOf(phoneKey to legacyLayout, foldableKey to legacyLayout))

    private fun workspaceA(layout: HomeLayoutDeviceClass) =
        TestWorkspaces.nova(
            WorkspaceId("ws-a-${layout.name}"),
            "First",
        )

    private fun workspaceB(layout: HomeLayoutDeviceClass) =
        TestWorkspaces.nova(
            WorkspaceId("ws-b-${layout.name}"),
            "Second",
        )

    /** Two workspaces per layout; [active] picks which one each layout has active (the second by default). */
    private fun storedSet(second: Boolean = true): WorkspaceSet =
        WorkspaceSet(
            layouts.associateWith { layout ->
                val a = workspaceA(layout)
                val b = workspaceB(layout)
                LayoutWorkspaces(listOf(a, b), if (second) b.id else a.id, a.id)
            },
        )

    private fun open(set: WorkspaceSet): PoolHomeCommit =
        PoolHomeSync.commit(
            state = PoolCutover.resync(null, legacy).state,
            previous = null,
            next = legacy,
            ids = ids,
            shown = WorkspaceHome.shownWorkspaces(set),
        )

    /** The commit of [change] applied to the layout shown by [commit], through the same resolver. */
    private fun PoolHomeCommit.edit(
        set: WorkspaceSet,
        change: (HomeLayout) -> HomeLayout,
    ): PoolHomeCommit =
        PoolHomeSync.commit(
            state = state,
            previous = shown,
            next = shown.withActiveLayout(change(shown.activeLayout)),
            ids = ids,
            shown = WorkspaceHome.shownWorkspaces(set),
        )

    private fun HomeLayout.homeItems(): List<LauncherItem> =
        pages.filter { it.type == LauncherPageType.Home }.flatMap { it.items }

    private fun HomeLayout.withHomeItems(change: (List<LauncherItem>) -> List<LauncherItem>): HomeLayout =
        copy(pages = pages.map { if (it.type == LauncherPageType.Home) it.copy(items = change(it.items)) else it })

    private fun PoolHomeCommit.pool(layout: HomeLayoutDeviceClass): PlacedItemPool = state.pools.getValue(layout)

    @Test
    fun theSecondWorkspaceIsActiveAndItsArrangementIsWhatHomeShowsAndEdits() {
        // The first workspace was the active one in an older build and got its own arrangement by an edit.
        val first = storedSet(second = false)
        val withFirstEdited =
            open(first).edit(first) { current ->
                current.withHomeItems { items -> items + app("only-in-first", column = 3) }
            }
        val firstArrangements =
            layouts.associateWith { withFirstEdited.pool(it).arrangements[workspaceA(it).id] }
        // Only the active layout (the phone) is shown and edited; it forked.
        assertNotNull(firstArrangements.getValue(HomeLayoutDeviceClass.PHONE))

        // Now the stored active workspace is the second one: Home shows the second's arrangement, not the first's.
        val second = storedSet(second = true)
        val shownSecond = WorkspaceHome.shown(second, phoneKey, phoneKey.viewMode)
        assertEquals(workspaceB(HomeLayoutDeviceClass.PHONE).id, shownSecond.workspaceId)
        val reopened =
            PoolHomeSync.reproject(
                withFirstEdited.state,
                PoolHomeSync.stored(withFirstEdited.shown, withFirstEdited.state.pools.keys),
                ids,
                WorkspaceHome.shownWorkspaces(second),
            )
        assertTrue(
            reopened.shown.activeLayout.homeItems().none { it.id.value.endsWith("only-in-first") },
            "the first workspace's edit is not shown",
        )

        // An edit lands in the second's arrangement (forked from what it borrowed) and nothing else moves.
        val edited =
            PoolHomeCommit(reopened.state, reopened.shown, reopened.releasedHostIds)
                .edit(second) { current ->
                    current.withHomeItems {
                            items ->
                        items.filterNot { it.id.value.endsWith("maps") }
                    }
                }
        val phone = HomeLayoutDeviceClass.PHONE
        assertTrue(PoolArrangements.ownsArrangement(edited.pool(phone), workspaceB(phone).id))
        assertTrue(edited.shown.activeLayout.homeItems().none { it.id.value.endsWith("maps") })
        assertEquals(
            firstArrangements.getValue(phone),
            edited.pool(phone).arrangements[workspaceA(phone).id],
            "the inactive workspace's arrangement is exactly as it was",
        )
    }

    @Test
    fun afterAnEditAndSaveBothWorkspacesStillDecodeAndTheInactiveOneIsUnchanged() {
        val set = storedSet(second = true)
        val edited =
            open(set).edit(set) { current -> current.withHomeItems { items -> items + app("added", column = 3) } }

        // "Save": the pool and the workspace set are written and read back.
        val savedPool = PoolStoreCodec.decode(PoolStoreCodec.encode(edited.state)).state
        assertNotNull(savedPool)
        val savedSet = WorkspaceSetCodec.decode(WorkspaceSetCodec.encode(set))
        assertNotNull(savedSet)

        layouts.forEach { layout ->
            val before = set.workspacesFor(layout)
            val after = savedSet.workspacesFor(layout)
            assertEquals(before.workspaces, after.workspaces, "both workspaces of $layout decode")
            assertEquals(workspaceB(layout).id, after.activeId)
            assertEquals(workspaceA(layout).id, after.defaultId)
        }
        // Nothing wrote to the stored workspaces: the set that was read is the set that is still stored.
        assertEquals(set, savedSet)
        // The pool still holds every arrangement it held, and a restart shows the edit.
        val restarted =
            PoolHomeSync.commit(
                state = checkNotNull(savedPool),
                previous = null,
                next = PoolHomeSync.stored(edited.shown, savedPool.pools.keys),
                ids = ids,
                shown = WorkspaceHome.shownWorkspaces(set),
            )
        assertTrue(restarted.shown.activeLayout.homeItems().any { it.id.value == "added" })
    }

    @Test
    fun poolItemsReferencedOnlyByAnInactiveArrangementSurviveCollection() {
        // The first workspace gets its own arrangement with an app and a widget nothing else references.
        val first = storedSet(second = false)
        val withOnlyInFirst =
            open(first).edit(first) { current ->
                current.withHomeItems { items ->
                    items +
                        app("only-in-first", column = 3) +
                        WidgetItem(
                            LauncherItemId("only-widget"),
                            HostedWidgetId(77),
                            "Only",
                            placement = at(0, 3, 2, 1),
                        )
                }
            }
        val phone = HomeLayoutDeviceClass.PHONE
        val firstId = workspaceA(phone).id
        val onlyItems =
            withOnlyInFirst.pool(phone).arrangements.getValue(firstId).pages
                .flatMap { page -> page.placements.map { it.item } }
                .filter { id ->
                    val item = withOnlyInFirst.pool(phone).items[id]
                    (item as? PoolApp)?.appIdentity == appIdentity("only-in-first") ||
                        (item as? PoolWidget)?.hostedId == HostedWidgetId(77)
                }
        assertEquals(2, onlyItems.size)

        // Now the second workspace is active and edits remove and add things, which runs collection.
        val second = storedSet(second = true)
        val reopened =
            PoolHomeSync.reproject(
                withOnlyInFirst.state,
                PoolHomeSync.stored(withOnlyInFirst.shown, withOnlyInFirst.state.pools.keys),
                ids,
                WorkspaceHome.shownWorkspaces(second),
            )
        val edited =
            PoolHomeCommit(reopened.state, reopened.shown, reopened.releasedHostIds).edit(second) { current ->
                current.withHomeItems {
                        items ->
                    items.filterNot { it.id.value.endsWith("mail") } + app("new", column = 2)
                }
            }

        val pool = edited.pool(phone)
        onlyItems.forEach { id -> assertTrue(id in pool.items, "$id was collected") }
        assertTrue(PoolReferences.unreferenced(pool).none { it in onlyItems })
        assertTrue(
            HostedWidgetId(77) in PoolHostIds.referencedHostIds(edited.state),
            "the widget only the inactive arrangement holds keeps its host id",
        )
        assertTrue(HostedWidgetId(77) !in edited.releasedHostIds)
        assertEquals(
            withOnlyInFirst.pool(phone).arrangements.getValue(firstId),
            pool.arrangements.getValue(firstId),
            "the inactive arrangement still resolves all its items",
        )
    }

    @Test
    fun aStoredWorkspaceWithLensPagesAFinderPageSavedLensesAndADockSectionDecodesAndHomeStillRendersItsPlacedPages() {
        val library =
            (LensLibrary().tryAdd("Apps", appsLens(), WorkspaceIdFactory { "saved-1" }) as LibraryAdd.Added).library
        val everything = TestWorkspaces.everything(WorkspaceId("ws-everything"), LensId("saved-1"))
        val dangling = TestWorkspaces.everything(WorkspaceId("ws-dangling"), LensId("deleted-lens"))
        val phone = HomeLayoutDeviceClass.PHONE
        val stored =
            WorkspaceSet(
                mapOf(
                    phone to
                        LayoutWorkspaces(
                            listOf(TestWorkspaces.nova(), dangling, everything),
                            activeId = everything.id,
                            defaultId = TestWorkspaces.NovaId,
                            library = library,
                        ),
                ),
            )

        val decoded = checkNotNull(WorkspaceSetCodec.decode(WorkspaceSetCodec.encode(stored)))

        val layout = decoded.workspacesFor(phone)
        assertEquals(listOf("Nova", "Everything", "Everything"), layout.workspaces.map { it.name })
        val active = layout.active
        assertTrue(active.pages.any { it is PageSetContainer })
        assertTrue(active.pages.filterIsInstance<PageContainer>().any { it.role == PageRole.FINDER })
        assertNotNull(active.dock.dynamicSection)
        assertEquals("saved-1", layout.library.lenses.single().id.value)
        assertEquals(listOf(SourceIds.ALL_APPS), layout.library.lenses.single().lens.sources)
        // A saved-lens reference to a lens that no longer exists decodes too (it keeps its snapshot).
        assertNotNull(layout.find(dangling.id))

        // Home still renders the placed-items pages of the active workspace; the other pages are stored, never drawn.
        assertTrue(WorkspaceHome.hasHomeGrid(active))
        val opened = open(decoded)
        assertEquals(
            legacyLayout.homeItems().map { it.id.value.substringAfterLast(':') },
            opened.shown.layoutFor(phoneKey).homeItems().map { it.id.value.substringAfterLast(':') },
        )
        assertEquals(everything.id, WorkspaceHome.shown(decoded, phoneKey, phoneKey.viewMode).workspaceId)
        assertEquals(home, WorkspaceHome.startPageOf(active, opened.shown.layoutFor(phoneKey)))
    }
}
