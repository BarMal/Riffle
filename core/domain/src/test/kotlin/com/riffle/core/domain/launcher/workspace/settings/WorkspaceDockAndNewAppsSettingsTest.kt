package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.DockPresentation
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.NewAppDefaults
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceNewApps
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.pool.NewAppPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceDockAndNewAppsSettingsTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val menu = listOf(LauncherGestureAction.OPEN_WORKSPACE_MENU)
    private val page =
        PageContainer(
            ContainerId("p"),
            PageContent.Bound(LensBinding(Lens(sources = listOf(SourceId("apps.all"))), ExpressionKind.LIST)),
        )
    private val a = Workspace(WorkspaceId("a"), "Alpha", listOf(page), presetId = "ios")
    private val b = Workspace(WorkspaceId("b"), "Beta", listOf(page))
    private val set = WorkspaceSet(mapOf(phone to LayoutWorkspaces(listOf(a, b), a.id, a.id)))

    private fun stored(
        change: WorkspacesSettingsChange,
        id: WorkspaceId = a.id,
    ) = change.set.workspacesFor(phone).find(id)!!

    @Test
    fun hidingTheDockNeedsAMenuGesture() {
        val refused =
            WorkspacesSettingsAction.SetDockPresentation(
                a.id,
                DockPresentation(hidden = true),
            ).applyTo(set, phone)
        assertFalse(refused.applied)
        assertEquals(WorkspacesSettingsMessage.DockHiddenNeedsMenuGesture, refused.message)
        assertEquals(set, refused.set)
        val allowed =
            WorkspacesSettingsAction.SetDockPresentation(
                a.id,
                DockPresentation(hidden = true),
                menu,
            ).applyTo(set, phone)
        assertTrue(allowed.applied)
        assertEquals(DockPresentation(hidden = true), stored(allowed).dock.presentation)
        assertEquals(WorkspacesSettingsMessage.DockChanged("Alpha", DockPresentation(hidden = true)), allowed.message)
        assertFalse(allowed.undoable)
    }

    @Test
    fun edgeAndSizeNeedNoGestureAndFollowIsStoredAsNoOverride() {
        val moved =
            WorkspacesSettingsAction.SetDockPresentation(
                a.id,
                DockPresentation(edge = DockPosition.LEFT, iconSizeDp = 40),
            )
                .applyTo(set, phone)
        assertEquals(DockPresentation(edge = DockPosition.LEFT, iconSizeDp = 40), stored(moved).dock.presentation)
        val back = WorkspacesSettingsAction.SetDockPresentation(a.id, DockPresentation()).applyTo(moved.set, phone)
        assertNull(stored(back).dock.presentation)
        assertEquals(WorkspacesSettingsMessage.DockChanged("Alpha", null), back.message)
        val again = WorkspacesSettingsAction.SetDockPresentation(a.id, null).applyTo(back.set, phone)
        assertFalse(again.applied)
        // Only that workspace changes, and only its dock override.
        assertEquals(b, stored(moved, b.id))
        assertEquals(a.dock.dynamicSection, stored(moved).dock.dynamicSection)
    }

    @Test
    fun rowsCarryTheDockOverrideAndWhetherHiddenIsOffered() {
        val withDock =
            WorkspacesSettingsAction.SetDockPresentation(b.id, DockPresentation(edge = DockPosition.RIGHT))
                .applyTo(set, phone).set
        val plain = WorkspacesSettingsPlanner.plan(withDock, phone, phone)
        assertEquals(listOf(false, false), plain.rows.map { it.canHideDock })
        assertEquals(DockPresentation(edge = DockPosition.RIGHT), plain.rows[1].dock)
        assertNull(plain.rows[0].dock)
        val withGesture = WorkspacesSettingsPlanner.plan(withDock, phone, phone, globalGestureActions = menu)
        assertEquals(listOf(true, true), withGesture.rows.map { it.canHideDock })
    }

    @Test
    fun newAppsFollowThePresetUntilSetAndCanBeResetToIt() {
        val rows = WorkspacesSettingsPlanner.plan(set, phone, phone).rows
        assertEquals(NewAppDefaults.HOME_AND_PAGES, rows[0].newApps)
        assertEquals(NewAppDefaults.MIGRATED, rows[1].newApps)
        assertTrue(rows.all { it.newAppsFollowPreset })

        val finderOnly = WorkspaceNewApps(NewAppPlacement.FINDER_ONLY)
        val changed = WorkspacesSettingsAction.SetNewApps(a.id, finderOnly).applyTo(set, phone)
        assertEquals(finderOnly, stored(changed).newApps)
        assertEquals(
            WorkspacesSettingsMessage.NewAppsChanged("Alpha", finderOnly, followsPreset = false),
            changed.message,
        )
        val row = WorkspacesSettingsPlanner.plan(changed.set, phone, phone).rows[0]
        assertEquals(finderOnly, row.newApps)
        assertFalse(row.newAppsFollowPreset)

        val reset = WorkspacesSettingsAction.SetNewApps(a.id, null).applyTo(changed.set, phone)
        assertNull(stored(reset).newApps)
        assertEquals(
            WorkspacesSettingsMessage.NewAppsChanged("Alpha", NewAppDefaults.HOME_AND_PAGES, followsPreset = true),
            reset.message,
        )
        assertFalse(WorkspacesSettingsAction.SetNewApps(a.id, null).applyTo(set, phone).applied)
        assertFalse(WorkspacesSettingsAction.SetNewApps(WorkspaceId("zz"), finderOnly).applyTo(set, phone).applied)
    }
}
