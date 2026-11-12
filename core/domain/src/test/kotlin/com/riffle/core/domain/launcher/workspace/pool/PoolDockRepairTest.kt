package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.DockPresentation
import com.riffle.core.domain.launcher.workspace.WorkspaceDock
import com.riffle.core.domain.launcher.workspace.WorkspaceMigration
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** The dock size and edge a workspace held before the cut-over (P8) come back once, and only if still recoverable. */
class PoolDockRepairTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val phoneDefault = HomeLayoutDefaults.standard(phone).dock.iconSizeDp

    private fun stateWith(dock: DockModel): PoolStoreState {
        val pool = PlacedItemPool(dock = PoolDock(settings = PoolDockSettings.of(dock)))
        return PoolStoreState(migrated = true, pools = mapOf(phone to pool))
    }

    private fun workspacesWith(presentation: DockPresentation?): WorkspaceSet {
        val stored = WorkspaceMigration.defaultFor(phone)
        val active = stored.active.copy(dock = WorkspaceDock(presentation = presentation))
        return WorkspaceSet(mapOf(phone to stored.copy(workspaces = listOf(active))))
    }

    private fun settingsOf(state: PoolStoreState) = checkNotNull(state.pools.getValue(phone).dock).settings

    @Test
    fun theStoredWorkspaceDockSizeIsRestoredWhenTheSharedDockStillHoldsItsDefault() {
        val before = stateWith(DockModel(capacity = 5, iconSizeDp = phoneDefault))

        val after =
            PoolDockRepair.restoreWorkspaceOverrides(
                before,
                workspacesWith(DockPresentation(iconSizeDp = 56)),
            )

        assertEquals(56, settingsOf(after).iconSizeDp)
    }

    @Test
    fun aSizeTheUserSetAfterTheCutoverIsNeverOverwritten() {
        val before = stateWith(DockModel(capacity = 5, iconSizeDp = 40))

        val after =
            PoolDockRepair.restoreWorkspaceOverrides(
                before,
                workspacesWith(DockPresentation(iconSizeDp = 56)),
            )

        assertSame(before, after)
    }

    @Test
    fun theEdgeIsRestoredOnlyWhileTheSharedDockNamesNone() {
        val unset = stateWith(DockModel(capacity = 5, iconSizeDp = phoneDefault, position = null))
        val named = stateWith(DockModel(capacity = 5, iconSizeDp = phoneDefault, position = DockPosition.BOTTOM))
        val workspaces = workspacesWith(DockPresentation(edge = DockPosition.LEFT))

        val restoredUnset = PoolDockRepair.restoreWorkspaceOverrides(unset, workspaces)
        val restoredNamed = PoolDockRepair.restoreWorkspaceOverrides(named, workspaces)
        assertEquals(DockPosition.LEFT, settingsOf(restoredUnset).position)
        assertEquals(DockPosition.BOTTOM, settingsOf(restoredNamed).position)
    }

    @Test
    fun aHiddenOverrideIsNotCarriedOver() {
        val before = stateWith(DockModel(capacity = 5, iconSizeDp = phoneDefault))

        val after =
            PoolDockRepair.restoreWorkspaceOverrides(before, workspacesWith(DockPresentation(hidden = true)))

        assertSame(before, after)
        assertEquals(true, settingsOf(after).isEnabled)
    }

    @Test
    fun noWorkspacesNoOverrideOrNoDockChangesNothing() {
        val before = stateWith(DockModel(capacity = 5, iconSizeDp = phoneDefault))

        assertSame(before, PoolDockRepair.restoreWorkspaceOverrides(before, null))
        assertSame(before, PoolDockRepair.restoreWorkspaceOverrides(before, workspacesWith(null)))
        val dockless = PoolStoreState(migrated = true, pools = mapOf(phone to PlacedItemPool()))
        val withOverride = workspacesWith(DockPresentation(iconSizeDp = 56))
        assertSame(dockless, PoolDockRepair.restoreWorkspaceOverrides(dockless, withOverride))
    }

    @Test
    fun theRepairIsIdempotent() {
        val workspaces = workspacesWith(DockPresentation(iconSizeDp = 56))
        val start = stateWith(DockModel(capacity = 5, iconSizeDp = phoneDefault))
        val once = PoolDockRepair.restoreWorkspaceOverrides(start, workspaces)

        assertSame(once, PoolDockRepair.restoreWorkspaceOverrides(once, workspaces))
    }

    @Test
    fun storedDockSettingsSurviveTheMigrationIntoThePool() {
        // The shared dock's own size and appearance travel through PoolMigration, the pool codec and the projection.
        val stored =
            DockModel(
                capacity = 5,
                iconSizeDp = 52,
                backgroundAlphaPercent = 55,
                itemSpacingDp = 14,
                cornerRadiusDp = 28,
            )
        val layoutSet = dockedLayoutSet()
        val withStored = layoutSet.copy(docks = layoutSet.docks + (phone to stored))
        val pool = PoolMigration.migrate(withStored).pools.getValue(phone)

        val decoded = PoolCodec.decode(PoolCodec.encode(pool)).pool
        val projected = PoolHomeProjection.projectDock(decoded)

        assertEquals(52, projected?.iconSizeDp)
        assertEquals(55, projected?.backgroundAlphaPercent)
        assertEquals(14, projected?.itemSpacingDp)
        assertEquals(28, projected?.cornerRadiusDp)
    }
}
