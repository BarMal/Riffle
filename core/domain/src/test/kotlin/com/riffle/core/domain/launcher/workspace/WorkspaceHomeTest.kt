package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkspaceHomeTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val nova = TestWorkspaces.novaFor(phone)
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
        val lensOnly = TestWorkspaces.lensOnly(nova.id)
        assertEquals(HomeShownWorkspace(nova.id), WorkspaceHome.shown(set(lensOnly), library, library.viewMode))
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
}
