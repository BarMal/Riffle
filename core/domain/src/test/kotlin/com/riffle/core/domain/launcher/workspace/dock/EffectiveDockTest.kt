package com.riffle.core.domain.launcher.workspace.dock

import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.MAX_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.home.MIN_DOCK_ICON_SIZE_DP
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.workspace.DockMenuReachability
import com.riffle.core.domain.launcher.workspace.DockPresentation
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceDock
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceIssue
import com.riffle.core.domain.launcher.workspace.WorkspaceValidation
import com.riffle.core.domain.launcher.workspace.pool.PoolDock
import com.riffle.core.domain.launcher.workspace.pool.PoolDockSettings
import com.riffle.core.domain.launcher.workspace.pool.PoolItemId
import com.riffle.core.domain.launcher.workspace.preset.PresetPosture
import com.riffle.core.domain.launcher.workspace.preset.WorkspacePreset
import com.riffle.core.domain.launcher.workspace.preset.WorkspacePresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EffectiveDockTest {
    private val menuGesture = DockMenuReachability.WORKSPACE_MENU_ACTION
    private val shared =
        PoolDock(
            pins = listOf(PoolItemId("a"), PoolItemId("b")),
            settings = PoolDockSettings(iconSizeDp = 48, position = DockPosition.BOTTOM, capacity = 6),
        )

    private fun ws(
        presentation: DockPresentation? = null,
        gestures: Map<String, String> = emptyMap(),
    ) = Workspace(
        WorkspaceId("w"),
        "W",
        dock = WorkspaceDock(presentation = presentation),
        gestureBindings = gestures,
    )

    @Test
    fun applyingLayersHiddenEdgeAndIconSizeOverTheSharedSettings() {
        val base = shared.settings
        assertSame(base, EffectiveDock.apply(base, null))
        assertEquals(base, EffectiveDock.apply(base, DockPresentation()))
        assertFalse(EffectiveDock.apply(base, DockPresentation(hidden = true)).isEnabled)
        assertEquals(DockPosition.LEFT, EffectiveDock.apply(base, DockPresentation(edge = DockPosition.LEFT)).position)
        assertEquals(40, EffectiveDock.apply(base, DockPresentation(iconSizeDp = 40)).iconSizeDp)
        val all = EffectiveDock.apply(base, DockPresentation(true, DockPosition.RIGHT, 56))
        assertEquals(base.copy(isEnabled = false, position = DockPosition.RIGHT, iconSizeDp = 56), all)
        // A dock the user turned off stays off; an override can only hide, never show it.
        assertFalse(
            EffectiveDock.apply(base.copy(isEnabled = false), DockPresentation(edge = DockPosition.TOP)).isEnabled,
        )
    }

    @Test
    fun theDockModelFormNeverTouchesItemsOrCapacity() {
        val model = DockModel(capacity = 4, iconSizeDp = 44, position = null)
        val applied =
            EffectiveDock.apply(
                model,
                DockPresentation(hidden = true, edge = DockPosition.LEFT, iconSizeDp = 32),
            )
        assertEquals(model.items, applied.items)
        assertEquals(4, applied.capacity)
        assertEquals(DockPosition.LEFT, applied.position)
        assertEquals(32, applied.iconSizeDp)
        assertFalse(applied.isEnabled)
    }

    @Test
    fun resolveDockKeepsSharedPinsAndPanelAndFollowsWithoutAnOverride() {
        assertNull(resolveDock(null, ws()))
        assertEquals(shared, resolveDock(shared, null))
        assertEquals(shared, resolveDock(shared, ws()))
        val moved = resolveDock(shared, ws(DockPresentation(edge = DockPosition.LEFT)))!!
        assertEquals(shared.pins, moved.pins)
        assertEquals(shared.panel, moved.panel)
        assertEquals(DockPosition.LEFT, moved.settings.position)
        assertEquals(6, moved.settings.capacity)
    }

    @Test
    fun aHiddenDockWithoutAMenuGestureIsDrawnAnywayAndReported() {
        val stranded = ws(DockPresentation(hidden = true, iconSizeDp = 40))
        val resolution = EffectiveDock.presentationFor(stranded, globalGestureActions = emptyList())
        assertTrue(resolution.strandedFallback)
        assertEquals(DockPresentation(iconSizeDp = 40), resolution.presentation)
        assertTrue(resolveDock(shared, stranded)!!.settings.isEnabled)
        assertEquals(WorkspaceIssue.DockHiddenWithoutMenuEntry, DockMenuReachability.issue(stranded, emptyList()))
        // Never part of plain validation: a stranded workspace does not fall back to another workspace.
        assertTrue(WorkspaceIssue.DockHiddenWithoutMenuEntry !in WorkspaceValidation.validate(stranded))
    }

    @Test
    fun aMenuGestureInTheWorkspaceOrTheGlobalGesturesLetsTheDockHide() {
        val own = ws(DockPresentation(hidden = true), mapOf("ONE_FINGER_UP" to menuGesture))
        assertFalse(resolveDock(shared, own)!!.settings.isEnabled)
        assertNull(DockMenuReachability.issue(own, emptyList()))
        val global = ws(DockPresentation(hidden = true))
        assertFalse(resolveDock(shared, global, listOf("NONE", menuGesture))!!.settings.isEnabled)
        assertFalse(EffectiveDock.presentationFor(global, listOf(menuGesture)).strandedFallback)
        // Other gestures do not count.
        assertTrue(EffectiveDock.presentationFor(global, listOf("OPEN_SEARCH", "OPEN_SETTINGS")).strandedFallback)
    }

    @Test
    fun globalActionsListEveryHomeGestureIncludingDefaults() {
        val settings = HomeGestureSettings().withAction(HomeGesture.THREE_FINGER_UP, LauncherGestureAction.OPEN_SEARCH)
        val names = DockMenuReachability.globalActions(settings)
        assertEquals(HomeGesture.entries.size, names.size)
        assertTrue("OPEN_SEARCH" in names)
        assertTrue("OPEN_NOTIFICATIONS" in names)
        assertFalse(menuGesture in names)
    }

    @Test
    fun iconSizeOverridesAreBoundedAndClamped() {
        assertFailsWith<IllegalArgumentException> { DockPresentation(iconSizeDp = MIN_DOCK_ICON_SIZE_DP - 1) }
        assertFailsWith<IllegalArgumentException> { DockPresentation(iconSizeDp = MAX_DOCK_ICON_SIZE_DP + 1) }
        assertEquals(MIN_DOCK_ICON_SIZE_DP, DockPresentation.clampedIconSize(0))
        assertEquals(MAX_DOCK_ICON_SIZE_DP, DockPresentation.clampedIconSize(999))
        assertNull(DockPresentation.clampedIconSize(null))
        assertTrue(DockPresentation().followsShared)
        assertFalse(DockPresentation(edge = DockPosition.TOP).followsShared)
    }

    @Test
    fun presetsResolveToTheExpectedEffectiveDock() {
        val niagara = WorkspacePresets.byId("niagara")!!
        niagara.variants().forEach { workspace ->
            assertEquals(DockPresentation(hidden = true), workspace.dock.presentation)
            assertFalse(resolveDock(shared, workspace)!!.settings.isEnabled, "Niagara hides its dock")
            assertNull(DockMenuReachability.issue(workspace, emptyList()), "Niagara binds the menu gesture")
        }
        val timeScape = WorkspacePresets.byId("timescape")!!
        assertNull(timeScape.variant(PresetPosture.COMPACT).dock.presentation)
        assertEquals(
            DockPosition.LEFT,
            resolveDock(shared, timeScape.variant(PresetPosture.EXPANDED))!!.settings.position,
        )
        listOf("nova", "ios", "kvaesitso").forEach { id ->
            WorkspacePresets.byId(id)!!.variants().forEach { assertNull(it.dock.presentation, id) }
        }
        val installed = WorkspacePresets.installPreset(niagara, HomeLayoutDeviceClass.PHONE)
        assertEquals(DockPresentation(hidden = true), installed.dock.presentation)
    }

    @Test
    fun switchingWorkspacesChangesTheEffectiveDockButNeverItsPins() {
        val follow = ws()
        val hidden = ws(DockPresentation(hidden = true), mapOf("ONE_FINGER_UP" to menuGesture))
        val left = ws(DockPresentation(edge = DockPosition.LEFT))
        val docks = listOf(follow, hidden, left, follow).map { resolveDock(shared, it)!! }
        assertEquals(listOf(true, false, true, true), docks.map { it.settings.isEnabled })
        assertEquals(DockPosition.LEFT, docks[2].settings.position)
        docks.forEach { assertEquals(shared.pins, it.pins) }
        assertEquals(shared, docks.last())
    }

    @Test
    fun thePreviewDockReservesNothingWhenHiddenAndRunsDownAMovedEdge() {
        val dock = DockModel(capacity = 5, iconSizeDp = 48)
        val follow = PreviewDock.from(dock, null)
        assertEquals(PreviewDock.from(dock), follow)
        assertEquals(DockPosition.BOTTOM, follow.edge)
        assertEquals(follow.barHeightDp, follow.reservedExtentDp)
        val hidden = PreviewDock.from(dock, DockPresentation(hidden = true))
        assertTrue(hidden.isHiddenHere)
        assertFalse(hidden.isShown)
        assertEquals(0, hidden.reservedExtentDp)
        val left = PreviewDock.from(dock, DockPresentation(edge = DockPosition.LEFT, iconSizeDp = 56))
        assertTrue(left.isVertical)
        assertEquals(56, left.iconSizeDp)
        assertEquals(left.barHeightDp, left.reservedExtentDp)
        // The preview's own top bar holds the top edge.
        assertEquals(DockPosition.BOTTOM, PreviewDock.from(dock, DockPresentation(edge = DockPosition.TOP)).edge)
        assertEquals(40, PreviewDock.from(null, DockPresentation(iconSizeDp = 40)).iconSizeDp)
    }

    private fun WorkspacePreset.variants() = PresetPosture.entries.map { variant(it) }
}
