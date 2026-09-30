package com.riffle.core.domain.launcher.workspace

import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.LauncherViewMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WorkspaceMigrationTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val grid = GridDimensions(4, 5)

    private fun page(
        id: String,
        type: LauncherPageType = LauncherPageType.Home,
    ) = LauncherPage(LauncherPageId(id), type, grid)

    private fun layout(
        mode: LauncherViewMode,
        vararg pages: LauncherPage,
        deviceClass: HomeLayoutDeviceClass = phone,
    ): HomeLayout = layoutOf(mode, pages.toList(), deviceClass)

    private fun layoutOf(
        mode: LauncherViewMode,
        pages: List<LauncherPage>,
        deviceClass: HomeLayoutDeviceClass = phone,
    ): HomeLayout =
        HomeLayoutDefaults.standard(deviceClass).copy(viewMode = mode)
            .let { it.copy(pages = pages, selectedPageId = pages.first().id) }

    private fun homeSet(
        active: HomeLayoutKey,
        vararg layouts: Pair<HomeLayoutKey, HomeLayout>,
    ) = HomeLayoutSet(activeKey = active, layouts = layouts.toMap())

    private fun key(
        mode: LauncherViewMode,
        deviceClass: HomeLayoutDeviceClass = phone,
    ) = HomeLayoutKey(mode, deviceClass)

    private val standard = LauncherViewMode.STANDARD_APP_DRAWER
    private val library = LauncherViewMode.HOME_SCREEN_LIBRARY
    private val cards = LauncherViewMode.CARD_INTERFACE

    private fun Workspace.boundLens(index: Int): Lens =
        ((pages[index] as PageContainer).content as PageContent.Bound).binding.lens

    @Test
    fun standardBecomesHomePagesReferencingTheHomeGrid() {
        val set = homeSet(key(standard), key(standard) to layout(standard, page("home"), page("two")))
        val layout = WorkspaceMigration.migrate(set).workspacesFor(phone)

        assertEquals(1, layout.workspaces.size)
        val ws = layout.active
        assertEquals("Standard", ws.name)
        assertEquals(listOf("page:home", "page:two"), ws.pages.map { it.id.value })
        assertEquals(LensFilter.GroupKeyIs("two"), ws.boundLens(1).filter)
        assertEquals(listOf(WorkspaceSourceIds.HOME_GRID), ws.boundLens(1).sources)
        val first = (ws.pages[0] as PageContainer).content as PageContent.Bound
        assertEquals(ExpressionKind.ICON_GRID, first.binding.expression)
        assertNull(ws.dock.dynamicSection)
        assertEquals(layout.activeId, layout.defaultId)
    }

    @Test
    fun libraryAddsAFinderPageWithAllAppsAsCategories() {
        val set = homeSet(key(library), key(library) to layout(library, page("home")))
        val ws = WorkspaceMigration.migrate(set).workspacesFor(phone).active

        assertEquals("Library", ws.name)
        val finder = ws.pages.last() as PageContainer
        assertEquals(PageRole.FINDER, finder.role)
        val binding = (finder.content as PageContent.Bound).binding
        assertEquals(ExpressionKind.CATEGORIES, binding.expression)
        assertEquals(listOf(SourceIds.ALL_APPS), binding.lens.sources)
        assertEquals(LensGroup.ByGroupKey, binding.lens.group)
    }

    @Test
    fun cardsLeadsWithANotificationsPageSet() {
        val set = homeSet(key(cards), key(cards) to layout(cards, page("home")))
        val ws = WorkspaceMigration.migrate(set).workspacesFor(phone).active

        assertEquals("Cards", ws.name)
        val pageSet = assertIs<PageSetContainer>(ws.pages.first())
        assertEquals(listOf(SourceIds.NOTIFICATIONS), pageSet.binding.lens.sources)
        assertEquals(LensGroup.ByGroupKey, pageSet.binding.lens.group)
        assertEquals(ExpressionKind.CARD_STACK, pageSet.binding.expression)
        assertEquals("page:home", ws.pages[1].id.value)
    }

    @Test
    fun dockNotificationStripBecomesTheDynamicSection() {
        val base = layout(cards, page("home"))
        val withStrip = base.copy(dock = base.dock.copy(showNotificationCards = true, notificationSlotCount = 4))
        val ws = WorkspaceMigration.migrate(homeSet(key(cards), key(cards) to withStrip)).workspacesFor(phone).active

        val binding = assertNotNull(ws.dock.dynamicSection)
        assertEquals(ExpressionKind.ICON_ROW, binding.expression)
        assertEquals(4, binding.lens.limit)
        assertEquals(listOf(SourceIds.NOTIFICATIONS), binding.lens.sources)
    }

    @Test
    fun everyStoredModeBecomesItsOwnWorkspaceAndShownModeIsActiveAndDefault() {
        val set =
            homeSet(
                key(library),
                key(standard) to layout(standard, page("home")),
                key(library) to layout(library, page("lib-home")),
                key(cards) to layout(cards, page("c-home")),
            )
        val layout = WorkspaceMigration.migrate(set).workspacesFor(phone)

        assertEquals(listOf("Standard", "Library", "Cards"), layout.workspaces.map { it.name })
        assertEquals("Library", layout.active.name)
        assertEquals(layout.activeId, layout.defaultId)
        // No page from any mode is lost.
        val pageIds = layout.workspaces.flatMap { w -> w.pages.map { it.id.value } }
        assertTrue(listOf("page:home", "page:lib-home", "page:c-home").all { it in pageIds })
    }

    @Test
    fun generatedAndAllAppsPagesMapToTheirSources() {
        fun generated(
            id: String,
            kind: GeneratedLauncherPageKind,
        ) = page(id, LauncherPageType.Generated(kind))
        val pages =
            listOf(
                page("drawer", LauncherPageType.AllApps),
                generated("g1", GeneratedLauncherPageKind.TODAY),
                generated("g2", GeneratedLauncherPageKind.FAVOURITES),
                generated("g3", GeneratedLauncherPageKind.FREQUENTLY_USED),
                generated("g4", GeneratedLauncherPageKind.WORK),
                generated("g5", GeneratedLauncherPageKind.PERSONAL),
                generated("g6", GeneratedLauncherPageKind.CATEGORY),
                generated("g7", GeneratedLauncherPageKind.NOTIFICATION_CARDS),
                generated("g8", GeneratedLauncherPageKind.APP),
            )
        val ws =
            WorkspaceMigration.migrate(homeSet(key(standard), key(standard) to layoutOf(standard, pages)))
                .workspacesFor(phone).active

        assertEquals(
            listOf(
                "apps.all", "apps.recent", "apps.favourite", "apps.frequent",
                "apps.all", "apps.all", "apps.all", "notifications", "apps.all",
            ),
            ws.pages.indices.map { ws.boundLens(it).sources.single().value },
        )
        assertEquals(
            LensFilter.ExtEquals(
                WorkspaceSourceIds.APP_PROFILE_EXT,
                ItemExtValue.Text(WorkspaceSourceIds.PROFILE_WORK),
            ),
            ws.boundLens(4).filter,
        )
    }

    @Test
    fun migratedWorkspacesValidateAgainstTheWs0Contracts() {
        val notificationCards =
            page("n", LauncherPageType.Generated(GeneratedLauncherPageKind.NOTIFICATION_CARDS))
        val set =
            homeSet(
                key(cards),
                key(standard) to layout(standard, page("a"), page("b", LauncherPageType.AllApps)),
                key(library) to layout(library, page("a")),
                key(cards) to
                    layout(cards, page("a"), notificationCards),
            )
        WorkspaceMigration.migrate(set).layouts.values.flatMap { it.workspaces }.forEach { ws ->
            assertEquals(emptyList(), WorkspaceValidation.validate(ws), ws.name)
        }
    }

    @Test
    fun deviceClassesAreMigratedIndependently() {
        val tablet = HomeLayoutDeviceClass.TABLET
        val set =
            HomeLayoutSet(
                activeKey = key(standard),
                layouts =
                    mapOf(
                        key(standard) to layout(standard, page("p1")),
                        key(cards, tablet) to layout(cards, page("t1"), deviceClass = tablet),
                    ),
                preferredModesByDeviceClass = mapOf(phone to standard, tablet to cards),
            )
        val migrated = WorkspaceMigration.migrate(set)

        assertEquals(setOf(phone, tablet), migrated.layouts.keys)
        assertEquals("Standard", migrated.workspacesFor(phone).active.name)
        assertEquals("Cards", migrated.workspacesFor(tablet).active.name)
        assertEquals(WorkspaceId("ws:tablet:card_interface"), migrated.workspacesFor(tablet).activeId)
    }

    @Test
    fun aDeviceClassWithoutStoredLayoutsStillGetsItsShownModeFromDefaults() {
        val set = HomeLayoutSet.standard()
        val layout = WorkspaceMigration.migrate(set).workspacesFor(phone)
        assertEquals(1, layout.workspaces.size)
        assertEquals(WorkspaceMigration.defaultFor(phone), layout)
    }

    @Test
    fun migrationIsDeterministicAndEnsureMigratedNeverOverwritesStoredWorkspaces() {
        val set = homeSet(key(library), key(library) to layout(library, page("home")))
        val once = WorkspaceMigration.migrate(set)
        assertEquals(once, WorkspaceMigration.migrate(set))
        assertEquals(once, WorkspaceMigration.ensureMigrated(null, set))

        val edited = once.update(phone) { it.rename(it.activeId, "Mine") }
        val again = WorkspaceMigration.ensureMigrated(edited, set)
        assertEquals(edited, again)
        assertEquals("Mine", again.workspacesFor(phone).active.name)
    }

    @Test
    fun ensureMigratedFillsDeviceClassesMissingFromStoredWorkspaces() {
        val tablet = HomeLayoutDeviceClass.TABLET
        val set =
            HomeLayoutSet(
                activeKey = key(standard),
                layouts =
                    mapOf(
                        key(standard) to layout(standard, page("p1")),
                        key(standard, tablet) to layout(standard, page("t1"), deviceClass = tablet),
                    ),
            )
        val stored = WorkspaceMigration.migrate(set).let { WorkspaceSet(mapOf(phone to it.workspacesFor(phone))) }
        val ensured = WorkspaceMigration.ensureMigrated(stored, set)
        assertSame(stored.workspacesFor(phone), ensured.workspacesFor(phone))
        assertEquals(setOf(phone, tablet), ensured.layouts.keys)
    }

    @Test
    fun duplicateOrBlankPageIdsGetUniqueContainerIds() {
        val set =
            homeSet(key(standard), key(standard) to layout(standard, page("same"), page("same"), page("")))
        val ids = WorkspaceMigration.migrate(set).workspacesFor(phone).active.pages.map { it.id.value }
        assertEquals(3, ids.toSet().size)
    }
}
