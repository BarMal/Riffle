package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockAlignment
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.DockVisualEffect
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.workspace.StoredValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A layout set whose phone dock has two pins, a panel with a widget, custom appearance and a Library edge. */
internal fun dockedLayoutSet(): HomeLayoutSet {
    val base = cutoverLayoutSet()
    val dock =
        DockModel(
            capacity = 6,
            items =
                listOf(
                    AppShortcutItem(LauncherItemId("dock-phone"), appIdentity("com.phone"), "Phone"),
                    FolderItem(
                        LauncherItemId("dock-folder"),
                        "Tools",
                        listOf(AppShortcutItem(LauncherItemId("t1"), appIdentity("com.tool"), "Tool")),
                    ),
                ),
            iconSizeDp = 40,
            visualEffect = DockVisualEffect.OUTLINED,
            alignment = DockAlignment.END,
            position = DockPosition.BOTTOM,
            panel =
                LauncherPage(
                    LauncherPageId("dock-panel"),
                    grid = GridDimensions(4, 2),
                    items =
                        listOf(
                            WidgetItem(
                                LauncherItemId("dock-clock"),
                                HostedWidgetId(77),
                                "Clock",
                                placement = at(0, 0, 2, 1),
                            ),
                        ),
                ),
        )
    return base.copy(
        docks = mapOf(HomeLayoutDeviceClass.PHONE to dock),
        libraryDockEdgesByDeviceClass = mapOf(HomeLayoutDeviceClass.PHONE to DockPosition.RIGHT),
    )
}

class PoolDockTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val layoutSet = dockedLayoutSet()
    private val pool = PoolMigration.migrate(layoutSet).pools.getValue(phone)
    private val dock = pool.dock!!
    private val dockPhone = PoolItemId("pi:phone:dock:dock-phone")

    private fun removeA1(pool: PlacedItemPool) =
        PoolRemoval.removeEverywhere(pool, PoolItemId("pi:phone:home_screen_library:a1")).done().pool

    @Test
    fun migrationImportsPinsPanelAndSettingsWithDeterministicIds() {
        assertEquals(listOf(PoolItemId("pi:phone:dock:dock-phone"), PoolItemId("pi:phone:dock:dock-folder")), dock.pins)
        assertEquals(listOf(PoolItemId("pi:phone:dock:dock-clock")), dock.panel!!.placements.map { it.item })
        assertEquals(GridDimensions(4, 2), dock.panel!!.grid)
        assertEquals(
            HostedWidgetId(77),
            (pool.items.getValue(PoolItemId("pi:phone:dock:dock-clock")) as PoolWidget).hostedId,
        )
        assertEquals(6, dock.settings.capacity)
        assertEquals(40, dock.settings.iconSizeDp)
        assertEquals(DockVisualEffect.OUTLINED, dock.settings.visualEffect)
        assertEquals(DockAlignment.END, dock.settings.alignment)
        assertEquals(DockPosition.BOTTOM, dock.settings.position)
        assertEquals(DockPosition.RIGHT, dock.settings.libraryPosition)
        assertEquals(PoolMigration.migrate(layoutSet).pools, PoolMigration.migrate(layoutSet).pools, "deterministic")
        assertInvariants(pool)
    }

    @Test
    fun theDockRoundTripsThroughTheProjectionAsTheOriginalDockModel() {
        val original = layoutSet.docks.getValue(phone)
        val projected = PoolHomeProjection.projectDock(pool)!!
        assertEquals(
            original.copy(items = emptyList(), panel = null),
            projected.copy(items = emptyList(), panel = null),
        )
        assertEquals(original.items.map { it.javaClass }, projected.items.map { it.javaClass })
        assertEquals(
            listOf("Phone", "Tools"),
            projected.items.map {
                (it as? AppShortcutItem)?.label ?: (it as FolderItem).label
            },
        )
        assertEquals(original.panel!!.items.single().placement, projected.panel!!.items.single().placement)
    }

    @Test
    fun importingADockKeepsAnEditedPoolAndIsIdempotent() {
        val edited = emptyPool(listOf(W1)).add(poolApp("pi:phone:dock:dock-phone"))
        val imported = PoolMigration.importDock(edited, layoutSet, phone).pool
        assertEquals(edited.arrangements, imported.arrangements)
        assertEquals(
            edited.items.getValue(PoolItemId("pi:phone:dock:dock-phone")),
            imported.items.getValue(PoolItemId("pi:phone:dock:dock-phone")),
        )
        assertEquals(
            PoolItemId("pi:phone:dock:dock-phone#2"),
            imported.dock!!.pins.first(),
            "an id in use gets a suffix",
        )
        assertEquals(imported, PoolMigration.importDock(imported, layoutSet, phone).pool)
        assertTrue(PoolValidation.repair(imported).isClean)
    }

    @Test
    fun codecRoundTripsTheDockAndAPoolWithoutOneDecodesWithNone() {
        val decoded = PoolCodec.decode(PoolCodec.encode(pool))
        assertEquals(pool, decoded.pool)
        assertEquals(emptyList(), decoded.issues)
        val legacy = PoolCodec.encode(pool.copy(dock = null))
        assertNull(PoolCodec.decode(legacy).pool.dock)
        val garbage = StoredValue.Obj(PoolCodec.encode(pool).fields + ("dock" to StoredValue.Str("x")))
        assertNull(PoolCodec.decode(garbage).pool.dock)
    }

    @Test
    fun aStoreMigratedUnderVersionOneGetsItsDockImportedOnceAndKeepsItsArrangements() {
        val v2 = PoolStoreCodec.encode(PoolStoreState(migrated = true, pools = mapOf(phone to pool.copy(dock = null))))
        val v1 = StoredValue.Obj(v2.fields + ("version" to StoredValue.Num(1)) - "dockImported")
        val decoded = PoolStoreCodec.decode(v1).state!!
        assertTrue(decoded.dockImportPending)
        val edited =
            decoded.copy(
                pools =
                    mapOf(
                        phone to removeA1(decoded.pools.getValue(phone)),
                    ),
            )
        val first = PoolCutover.ensureMigrated(edited, layoutSet)
        assertTrue(first.changed)
        assertFalse(first.state.dockImportPending)
        val migrated = first.state.pools.getValue(phone)
        assertEquals(edited.pools.getValue(phone).arrangements, migrated.arrangements, "edits survive")
        assertEquals(dock, migrated.dock)
        val second =
            PoolCutover.ensureMigrated(
                PoolStoreCodec.decode(PoolStoreCodec.encode(first.state)).state,
                layoutSet,
            )
        assertFalse(second.changed)
        assertEquals(first.state, second.state)
    }

    @Test
    fun aVersionTwoStoreIsNotPendingAndAFutureVersionIsNotStored() {
        val state = PoolStoreState(migrated = true, pools = mapOf(phone to pool))
        assertEquals(state, PoolStoreCodec.decode(PoolStoreCodec.encode(state)).state)
        val future = StoredValue.Obj(PoolStoreCodec.encode(state).fields + ("version" to StoredValue.Num(3)))
        assertNull(PoolStoreCodec.decode(future).state)
    }

    @Test
    fun dockItemsAreNeverCollectedAndHoldTheirHostIds() {
        assertEquals(0, PoolReferences.index(pool)[PoolItemId("pi:phone:dock:dock-clock")]?.size ?: 0)
        assertEquals(1, pool.refs("pi:phone:dock:dock-clock"))
        assertEquals(pool, PoolGc.collect(pool))
        val state = PoolStoreState(migrated = true, pools = mapOf(phone to pool))
        assertTrue(HostedWidgetId(77) in PoolHostIds.referencedHostIds(state))
        assertTrue(HostedWidgetId(42) in PoolHostIds.referencedHostIds(state))
        assertEquals(
            setOf(HostedWidgetId(5)),
            PoolHostIds.unreferenced(
                setOf(HostedWidgetId(5), HostedWidgetId(42), HostedWidgetId(77), HostedWidgetId(9)),
                state,
                pending = setOf(HostedWidgetId(9)),
            ),
        )
    }

    @Test
    fun removeEverywhereReachesTheDockAndReleasesAPanelWidget() {
        val edit = PoolRemoval.removeEverywhere(pool, PoolItemId("pi:phone:dock:dock-clock")).done()
        assertEquals(setOf(HostedWidgetId(77)), edit.releasedHostIds)
        assertEquals(emptyList(), edit.pool.dock!!.panel!!.placements)
        assertInvariants(edit.pool)
    }

    @Test
    fun uninstallPruningRemovesDockPinsAndDockFolderEntriesOfThePackage() {
        val pruned = PoolRemoval.pruneUninstalled(pool, AppPackageName("com.phone"), AppProfile.personal())
        assertEquals(listOf(PoolItemId("pi:phone:dock:dock-folder")), pruned.pool.dock!!.pins)
        val noTool = PoolRemoval.pruneUninstalled(pruned.pool, AppPackageName("com.tool"), AppProfile.personal())
        assertEquals(emptyList(), noTool.pool.dock!!.pins, "a dock folder left empty goes")
        assertInvariants(noTool.pool)
    }

    @Test
    fun aDockItemCannotAlsoBePlacedInAnArrangementAndRepairKeepsTheDock() {
        val workspace = pool.arrangements.keys.first()
        val page = pool.arrangements.getValue(workspace).pages.first().id
        assertEquals(
            PoolRejection.ITEM_IN_DOCK,
            PoolPlacement.placeExisting(
                pool,
                workspace,
                page,
                PoolItemId("pi:phone:dock:dock-phone"),
                at(3, 3),
            ).rejection(),
        )
        val arrangement = pool.arrangements.getValue(workspace)
        val first = arrangement.pages.first()
        val withPin = first.copy(placements = first.placements + Placement(dockPhone, at(3, 3)))
        val forced =
            pool.copy(
                arrangements =
                    pool.arrangements +
                        (workspace to arrangement.copy(pages = listOf(withPin) + arrangement.pages.drop(1))),
            )
        val repaired = PoolValidation.repair(forced)
        assertEquals(listOf(PoolIssueKind.DOCK_ITEM_IN_ARRANGEMENT), repaired.issues.map { it.kind })
        assertEquals(pool, repaired.pool)
    }

    @Test
    fun addAppNeverReusesADockPin() {
        val workspace = pool.arrangements.keys.first()
        val added =
            PoolHomeEditing.addApp(
                pool,
                workspace,
                null,
                appIdentity("com.phone"),
                "Phone",
                counterIds(),
            ).done().pool
        assertEquals(2, added.items.values.count { it is PoolApp && it.appIdentity == appIdentity("com.phone") })
        assertInvariants(added)
    }

    @Test
    fun aRestoredStateBuiltInCodeIsNotPending() {
        assertFalse(PoolStoreState(migrated = true).dockImportPending)
        val result =
            PoolCutover.ensureMigrated(
                PoolStoreState(migrated = true, pools = mapOf(phone to pool)),
                layoutSet,
            )
        assertFalse(result.changed)
        assertNotNull(result.state.pools.getValue(phone).dock)
    }
}
