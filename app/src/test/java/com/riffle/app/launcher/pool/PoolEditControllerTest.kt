package com.riffle.app.launcher.pool

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import com.riffle.core.domain.launcher.workspace.HomeLayoutWorkspaceMapper
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.pool.PlacedItemPool
import com.riffle.core.domain.launcher.workspace.pool.PoolApp
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeEditing
import com.riffle.core.domain.launcher.workspace.pool.PoolHomeTarget
import com.riffle.core.domain.launcher.workspace.pool.PoolHostIds
import com.riffle.core.domain.launcher.workspace.pool.PoolItemId
import com.riffle.core.domain.launcher.workspace.pool.PoolStoreState
import com.riffle.core.domain.launcher.workspace.pool.PoolWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoolEditControllerTest {
    private class FakeStore(
        var stored: PoolStoreState? = null,
        val failRead: Boolean = false,
        var failWrite: Boolean = false,
    ) : PoolStorePort {
        val writes = mutableListOf<PoolStoreState>()

        override suspend fun read(): PoolStoreState? {
            if (failRead) error("disk")
            return stored
        }

        override suspend fun write(state: PoolStoreState) {
            if (failWrite) error("full")
            writes += state
            stored = state
        }
    }

    private val phone = HomeLayoutDeviceClass.PHONE
    private val provider = WidgetProviderIdentity(AppPackageName("w.pkg"), WidgetProviderClassName("w.Clock"))
    private val workspace = HomeLayoutWorkspaceMapper.workspaceId(phone, LauncherViewMode.HOME_SCREEN_LIBRARY)
    private val page = LauncherPageId("home")
    private val target = PoolHomeTarget(workspace, page)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val released = mutableListOf<Set<HostedWidgetId>>()

    @After
    fun tearDown() = scope.cancel()

    private fun identity(pkg: String) = AppIdentity(AppPackageName(pkg), AppActivityName("$pkg.Main"))

    private fun cell(
        column: Int,
        row: Int,
    ) = GridPlacement(GridCell(column, row), GridSpan(1, 1))

    private fun layoutSet(): HomeLayoutSet {
        val items =
            listOf(
                AppShortcutItem(LauncherItemId("app:a"), identity("com.a"), "com.a", placement = cell(0, 0)),
                AppShortcutItem(LauncherItemId("app:b"), identity("com.b"), "com.b", placement = cell(1, 0)),
                WidgetItem(LauncherItemId("widget:42"), HostedWidgetId(42), "Clock", placement = cell(0, 1)),
            )
        val first = LauncherPage(page, LauncherPageType.Home, GridDimensions(4, 6), items)
        val second = LauncherPage(LauncherPageId("home2"), LauncherPageType.Home, GridDimensions(4, 6), emptyList())
        val layout =
            HomeLayout(LauncherViewMode.HOME_SCREEN_LIBRARY, listOf(first, second), page, DockModel(capacity = 5))
        val key = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, phone)
        return HomeLayoutSet(activeKey = key, layouts = mapOf(key to layout))
    }

    private fun repositoryOver(store: FakeStore): CachedPoolRepository =
        CachedPoolRepository(store).also {
            runBlocking { it.initialize(layoutSet()) { id -> provider.takeIf { id == HostedWidgetId(42) } } }
        }

    private fun controller(
        repository: CachedPoolRepository,
        debounce: Long = 60_000L,
        ids: WorkspaceIdFactory = WorkspaceIdFactory { "n${released.size}-${counter++}" },
    ) = PoolEditController(repository, scope, { released += it }, ids, debounce)

    private var counter = 0

    private fun PlacedItemPool.id(label: String): PoolItemId = items.values.first { it.label == label }.id

    private fun PlacedItemPool.cellOf(label: String) =
        PoolHomeEditing.siteOf(
            this,
            workspace,
            id(label),
        )?.placement?.at?.cell

    @Test
    fun enterNeedsAnInitializedPoolAndNothingIsWrittenBeforeAnEdit() {
        val store = FakeStore()
        val cold = CachedPoolRepository(store)
        val untouched = controller(cold)
        assertFalse(untouched.enter(phone) { emptySet() })
        assertFalse(untouched.state.value.editing)
        assertTrue(store.writes.isEmpty())
        val repository = repositoryOver(store)
        val writesAfterImport = store.writes.size
        val live = controller(repository)
        assertTrue(live.enter(phone) { emptySet() })
        assertTrue(live.state.value.editing)
        assertEquals(writesAfterImport, store.writes.size)
    }

    @Test
    fun aMoveIsPublishedAtOnceAndWrittenOnlyAfterTheQuietPeriodOrDone() {
        val store = FakeStore()
        val repository = repositoryOver(store)
        val writes = store.writes.size
        val edit = controller(repository)
        edit.enter(phone) { emptySet() }
        val pool = repository.pool(phone)!!
        edit.move(target, pool.id("com.b"), "com.b", 1, 0)
        assertEquals(GridCell(2, 0), repository.pool(phone)!!.cellOf("com.b"))
        assertEquals(writes, store.writes.size)
        assertTrue(edit.state.value.canUndo)
        assertEquals("Moved com.b", edit.state.value.notice?.text)
        assertTrue(edit.state.value.notice?.undoable == true)
        edit.exit()
        assertEquals(writes + 1, store.writes.size)
        assertEquals(GridCell(2, 0), store.stored!!.poolFor(phone)!!.cellOf("com.b"))
        assertFalse(edit.state.value.editing)
    }

    @Test
    fun theDebouncedFlushWritesWithoutLeavingEditMode() {
        val store = FakeStore()
        val repository = repositoryOver(store)
        val writes = store.writes.size
        val edit = controller(repository, debounce = 0L)
        edit.enter(phone) { emptySet() }
        edit.move(target, repository.pool(phone)!!.id("com.b"), "com.b", 0, 1)
        assertEquals(writes + 1, store.writes.size)
        assertTrue(edit.state.value.editing)
    }

    @Test
    fun flushNowWritesPendingEditsAndKeepsTheUndoHistory() {
        val store = FakeStore()
        val repository = repositoryOver(store)
        val writes = store.writes.size
        val edit = controller(repository)
        edit.enter(phone) { emptySet() }
        edit.move(target, repository.pool(phone)!!.id("com.b"), "com.b", 0, 1)
        edit.flushNow()
        assertEquals(writes + 1, store.writes.size)
        assertTrue(edit.state.value.canUndo)
    }

    @Test
    fun undoAndRedoRestoreExactPoolValues() {
        val repository = repositoryOver(FakeStore())
        val original = repository.pool(phone)!!
        val edit = controller(repository)
        edit.enter(phone) { emptySet() }
        edit.move(target, original.id("com.b"), "com.b", 1, 0)
        val moved = repository.pool(phone)
        edit.undo()
        assertEquals(original, repository.pool(phone))
        assertTrue(edit.state.value.canRedo)
        assertNull(edit.state.value.notice)
        edit.redo()
        assertEquals(moved, repository.pool(phone))
    }

    @Test
    fun aRejectedEditChangesNothingAndSaysWhy() {
        val repository = repositoryOver(FakeStore())
        val original = repository.pool(phone)!!
        val edit = controller(repository)
        edit.enter(phone) { emptySet() }
        edit.move(target, original.id("com.a"), "com.a", 1, 0)
        assertEquals(original, repository.pool(phone))
        assertEquals("That spot is taken.", edit.state.value.notice?.text)
        assertTrue(edit.state.value.notice?.undoable == false)
        assertFalse(edit.state.value.canUndo)
    }

    @Test
    fun movingAcrossPagesUsesTheEngineAndKeepsOnePlacement() {
        val repository = repositoryOver(FakeStore())
        val edit = controller(repository)
        edit.enter(phone) { emptySet() }
        val pool = repository.pool(phone)!!
        edit.moveToPage(target, pool.id("com.a"), "com.a", 1)
        val site = PoolHomeEditing.siteOf(repository.pool(phone)!!, workspace, pool.id("com.a"))
        assertEquals(LauncherPageId("home2"), site?.page?.id)
    }

    @Test
    fun removingFromThisWorkspaceDropsItAndUndoBringsItBack() {
        val repository = repositoryOver(FakeStore())
        val original = repository.pool(phone)!!
        val edit = controller(repository)
        edit.enter(phone) { emptySet() }
        edit.select(original.id("com.a"))
        edit.remove(target, original.id("com.a"), "com.a")
        assertNull(repository.pool(phone)!!.items[original.id("com.a")])
        assertNull(edit.state.value.selected)
        edit.undo()
        assertEquals(original, repository.pool(phone))
    }

    @Test
    fun aRemovedWidgetsHostIdIsReleasedOnlyAfterDoneAndOnlyWhenTheStandardHomeDoesNotHoldIt() {
        val store = FakeStore()
        val repository = repositoryOver(store)
        val widget = repository.pool(phone)!!.items.values.filterIsInstance<PoolWidget>().single()
        val shared = controller(repository)
        shared.enter(phone) { setOf(HostedWidgetId(42)) }
        shared.remove(target, widget.id, widget.label)
        shared.exit()
        assertTrue("the standard home still holds id 42", released.isEmpty())

        val repository2 = repositoryOver(FakeStore())
        val own = controller(repository2)
        own.enter(phone) { emptySet() }
        own.remove(target, widget.id, widget.label)
        assertTrue("not released while edit mode (the Undo window) is open", released.isEmpty())
        own.exit()
        assertEquals(listOf(setOf(HostedWidgetId(42))), released)
    }

    @Test
    fun undoingTheRemovalOfAWidgetNeverReleasesItsHostId() {
        val repository = repositoryOver(FakeStore())
        val widget = repository.pool(phone)!!.items.values.filterIsInstance<PoolWidget>().single()
        val edit = controller(repository)
        edit.enter(phone) { emptySet() }
        edit.remove(target, widget.id, widget.label)
        edit.undo()
        edit.exit()
        assertTrue(released.isEmpty())
    }

    @Test
    fun hostIdsAreNotReleasedWhenTheFinalWriteFails() {
        val store = FakeStore()
        val repository = repositoryOver(store)
        val widget = repository.pool(phone)!!.items.values.filterIsInstance<PoolWidget>().single()
        val edit = controller(repository)
        edit.enter(phone) { emptySet() }
        edit.remove(target, widget.id, widget.label)
        store.failWrite = true
        edit.exit()
        assertTrue(released.isEmpty())
    }

    @Test
    fun aReadFailureDisablesWritingSoEditsNeverOverwriteWhatIsOnDisk() {
        val store = FakeStore(failRead = true)
        val repository = repositoryOver(store)
        val edit = controller(repository, debounce = 0L)
        edit.enter(phone) { emptySet() }
        edit.move(target, repository.pool(phone)!!.id("com.b"), "com.b", 0, 1)
        edit.exit()
        assertTrue(store.writes.isEmpty())
        assertNotNull(repository.pool(phone))
    }

    @Test
    fun deleteEverywhereRemovesTheItemAndIsOneUndoStep() {
        val repository = repositoryOver(FakeStore())
        val original = repository.pool(phone)!!
        val edit = controller(repository)
        edit.enter(phone) { emptySet() }
        edit.deleteEverywhere(original.id("com.a"), "com.a")
        assertNull(repository.pool(phone)!!.items[original.id("com.a")])
        assertEquals("Deleted com.a everywhere", edit.state.value.notice?.text)
        edit.undo()
        assertEquals(original, repository.pool(phone))
    }

    @Test
    fun addAppPlacesInTheFirstFreeCellAndRefusesADuplicate() {
        val repository = repositoryOver(FakeStore())
        val edit = controller(repository)
        edit.enter(phone) { emptySet() }
        edit.addApp(target, identity("com.new"), "New")
        val pool = repository.pool(phone)!!
        assertEquals(GridCell(2, 0), pool.cellOf("New"))
        edit.addApp(target, identity("com.new"), "New")
        assertEquals("Already on this home.", edit.state.value.notice?.text)
        assertEquals(1, repository.pool(phone)!!.items.values.count { it is PoolApp && it.label == "New" })
    }

    @Test
    fun aSecondWidgetPlacementIsRefusedButASeparateCopyIsAnExplicitPlaceholder() {
        val repository = repositoryOver(FakeStore())
        val widget = repository.pool(phone)!!.items.values.filterIsInstance<PoolWidget>().single()
        val edit = controller(repository)
        edit.enter(phone) { emptySet() }
        edit.separateCopy(target, widget.id, widget.label)
        val widgets = repository.pool(phone)!!.items.values.filterIsInstance<PoolWidget>()
        assertEquals(2, widgets.size)
        assertEquals(1, widgets.count { it.hostedId == null })
        assertEquals("Added a separate copy of Clock", edit.state.value.notice?.text)
    }

    @Test
    fun addAndRemovePage() {
        val repository = repositoryOver(FakeStore())
        val edit = controller(repository)
        edit.enter(phone) { emptySet() }
        edit.addPage(workspace)
        assertEquals(3, repository.pool(phone)!!.arrangements.getValue(workspace).pages.size)
        val added = repository.pool(phone)!!.arrangements.getValue(workspace).pages.last().id
        edit.removePage(PoolHomeTarget(workspace, added))
        assertEquals(2, repository.pool(phone)!!.arrangements.getValue(workspace).pages.size)
        edit.removePage(target)
        assertEquals("Only an empty page can be removed.", edit.state.value.notice?.text)
    }

    @Test
    fun theStandardHomeLayoutSetIsNeverModifiedAndItsHostIdsAreProtected() {
        val set = layoutSet()
        val before = set.copy()
        val repository = repositoryOver(FakeStore())
        val edit = controller(repository)
        edit.enter(phone) { PoolHostIds.standardHome(set) }
        edit.move(target, repository.pool(phone)!!.id("com.b"), "com.b", 0, 1)
        edit.exit()
        assertEquals(before, set)
        assertEquals(setOf(HostedWidgetId(42)), PoolHostIds.standardHome(set))
    }

    @Test
    fun editModeCanBeEnteredAgainAfterDone() {
        val repository = repositoryOver(FakeStore())
        val edit = controller(repository)
        edit.enter(phone) { emptySet() }
        edit.exit()
        assertTrue(edit.enter(phone) { emptySet() })
        assertFalse(edit.state.value.canUndo)
        assertEquals(phone, edit.editingDeviceClass)
        edit.exit()
        assertNull(edit.editingDeviceClass)
    }

    @Test
    fun selectionIsClearedWhenTheItemIsGone() {
        val repository = repositoryOver(FakeStore())
        val original = repository.pool(phone)!!
        val edit = controller(repository)
        edit.enter(phone) { emptySet() }
        edit.select(original.id("com.a"))
        edit.deleteEverywhere(original.id("com.a"), "com.a")
        assertNull(edit.state.value.selected)
    }
}
