package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.workspace.preset.WorkspacePresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WorkspaceHomeTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val nova = WorkspacePresets.defaultFor(phone)
    private val ios = checkNotNull(WorkspacePresets.byId("ios")).compact
    private val library = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, phone)

    private fun set(active: Workspace): WorkspaceSet = WorkspaceSet(mapOf(phone to LayoutWorkspaces.single(active)))

    @Test
    fun migratedIdsAndModes() {
        for (mode in LauncherViewMode.entries) {
            val id = WorkspaceHome.migratedId(HomeLayoutKey(mode, HomeLayoutDeviceClass.PHONE_LANDSCAPE))
            assertEquals(WorkspaceHome.migratedId(HomeLayoutKey(mode, phone)), id, "N10: landscape is the phone layout")
            assertEquals(mode, WorkspaceHome.migratedMode(id, phone))
        }
        assertNull(WorkspaceHome.migratedMode(nova.id, phone))
        assertNull(WorkspaceHome.migratedMode(WorkspaceHome.migratedId(library), HomeLayoutDeviceClass.TABLET))
    }

    @Test
    fun withoutAPoolTheHomeModeFollowsAMigratedWorkspaceAndOtherwiseStays() {
        val standard =
            Workspace(WorkspaceHome.migratedId(HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, phone)), "S")
        assertEquals(
            LauncherViewMode.STANDARD_APP_DRAWER,
            WorkspaceHome.homeModeFor(standard, phone, null, LauncherViewMode.HOME_SCREEN_LIBRARY),
        )
        assertEquals(
            LauncherViewMode.HOME_SCREEN_LIBRARY,
            WorkspaceHome.homeModeFor(nova, phone, null, LauncherViewMode.HOME_SCREEN_LIBRARY),
        )
        assertEquals(
            LauncherViewMode.CARD_INTERFACE,
            WorkspaceHome.homeModeFor(null, phone, null, LauncherViewMode.CARD_INTERFACE),
        )
    }

    @Test
    fun shownWorkspaceRules() {
        // Nothing stored: the S4 rule.
        assertEquals(
            HomeShownWorkspace(WorkspaceHome.migratedId(library)),
            WorkspaceHome.shown(null, library, library.viewMode),
        )
        // The active Nova borrows the migrated arrangement of the shown mode first.
        val novaShown = WorkspaceHome.shown(set(nova), library, library.viewMode)
        assertEquals(nova.id, novaShown.workspaceId)
        assertEquals(listOf(nova.id, WorkspaceHome.migratedId(library)), novaShown.candidates.take(2))
        // A key of another mode keeps its migrated workspace.
        val standardKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, phone)
        assertEquals(
            WorkspaceHome.migratedId(standardKey),
            WorkspaceHome.shown(set(nova), standardKey, library.viewMode).workspaceId,
        )
        // A workspace with no placed page borrows nothing.
        val lensOnly = nova.copy(pages = nova.pages.filterNot(PageHost::readsPlacedItems))
        assertEquals(HomeShownWorkspace(nova.id), WorkspaceHome.shown(set(lensOnly), library, library.viewMode))
    }

    @Test
    fun extraPagesAreTheNonPlacedPagerPagesOfAPresetOnly() {
        assertTrue(WorkspaceHome.extraPages(nova, phone).isEmpty(), "Nova: home grid plus Finder, nothing extra")
        val extras = WorkspaceHome.extraPages(ios, phone)
        assertEquals(1, extras.size, "iOS: the Today page")
        assertTrue(extras.none { it.readsPlacedItems() })
        assertTrue(extras.none { it.isFinderPage() })
        val migrated = ios.copy(id = WorkspaceHome.migratedId(library))
        assertTrue(
            WorkspaceHome.extraPages(migrated, phone).isEmpty(),
            "a migrated workspace's lens pages are its layout's",
        )
        assertTrue(WorkspaceHome.extraPages(null, phone).isEmpty())
    }

    @Test
    fun layoutPagesOfWorkspacePages() {
        val layout =
            HomeLayout(
                viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                pages =
                    listOf(
                        LauncherPage(LauncherPageId("p0"), grid = GridDimensions(4, 5)),
                        LauncherPage(LauncherPageId("all"), LauncherPageType.AllApps, GridDimensions(4, 5)),
                    ),
                selectedPageId = LauncherPageId("p0"),
                dock = DockModel(capacity = 4),
            )
        val home = nova.pagerPages.first()
        val finder = checkNotNull(nova.finderPage)
        // Nova's home page names arrangement page "home", which this layout does not have: the first Home page.
        assertEquals(LauncherPageId("p0"), WorkspaceHome.layoutPageOf(nova, home.id, layout))
        assertNull(WorkspaceHome.layoutPageOf(nova, finder.id, layout))
        assertNull(WorkspaceHome.layoutPageOf(nova, ContainerId("missing"), layout))
        assertEquals(LauncherPageId("p0"), WorkspaceHome.startPageOf(nova, layout))
        // A migrated page names its layout page.
        val migrated = HomeLayoutWorkspaceMapper.map(phone, layout, LauncherViewMode.STANDARD_APP_DRAWER)
        assertEquals(LauncherPageId("all"), WorkspaceHome.layoutPageOf(migrated, ContainerId("page:all"), layout))
        // A start page on a lens page has no layout page.
        assertNull(WorkspaceHome.startPageOf(nova.copy(startPageId = finder.id), layout))
        assertNull(WorkspaceHome.startPageOf(null, layout))
    }

    @Test
    fun workspaceGestureBindingsLayerOverTheGlobalOnes() {
        val global = HomeGestureSettings()
        assertSame(global, WorkspaceHome.gestures(global, null))
        assertSame(global, WorkspaceHome.gestures(global, nova))
        val niagara = checkNotNull(WorkspacePresets.byId("niagara")).compact
        val applied = WorkspaceHome.gestures(global, niagara)
        assertEquals(LauncherGestureAction.OPEN_WORKSPACE_MENU, applied.actionFor(HomeGesture.ONE_FINGER_UP))
        val hostile =
            nova.copy(
                gestureBindings =
                    mapOf(
                        "NOT_A_GESTURE" to "OPEN_SEARCH",
                        HomeGesture.PINCH_IN.name to "NOT_AN_ACTION",
                        HomeGesture.PINCH_OUT.name to LauncherGestureAction.LAUNCH_APP.name,
                        HomeGesture.TWO_FINGER_UP.name to LauncherGestureAction.OPEN_SEARCH.name,
                    ),
            )
        val result = WorkspaceHome.gestures(global, hostile)
        assertEquals(global.actionFor(HomeGesture.PINCH_IN), result.actionFor(HomeGesture.PINCH_IN))
        assertEquals(global.actionFor(HomeGesture.PINCH_OUT), result.actionFor(HomeGesture.PINCH_OUT))
        assertEquals(LauncherGestureAction.OPEN_SEARCH, result.actionFor(HomeGesture.TWO_FINGER_UP))
    }

    @Test
    fun theHomeStateLayersTheWorkspaceGesturesAndDockOverride() {
        val state = LauncherShellState()
        assertSame(state, state.withWorkspaceHome(null))
        assertSame(state, state.withWorkspaceHome(nova))

        // Niagara hides its dock and binds a swipe up to the menu: both apply, the stored settings stay global.
        val niagara = checkNotNull(WorkspacePresets.byId("niagara")).compact
        val home = state.withWorkspaceHome(niagara)
        assertFalse(home.homeLayout.dock.isEnabled)
        assertEquals(state.homeLayout.dock.items, home.homeLayout.dock.items)
        assertEquals(
            LauncherGestureAction.OPEN_WORKSPACE_MENU,
            home.launcherSettings.gestures.homeGestures.actionFor(HomeGesture.ONE_FINGER_UP),
        )

        // A hidden dock without any gesture to the menu is drawn anyway (the stranding rule).
        val stranded = nova.copy(dock = nova.dock.copy(presentation = DockPresentation(hidden = true)))
        assertTrue(state.withWorkspaceHome(stranded).homeLayout.dock.isEnabled)

        val left = nova.copy(dock = nova.dock.copy(presentation = DockPresentation(edge = DockPosition.LEFT)))
        assertEquals(DockPosition.LEFT, state.withWorkspaceHome(left).homeLayout.dock.position)
    }
}
