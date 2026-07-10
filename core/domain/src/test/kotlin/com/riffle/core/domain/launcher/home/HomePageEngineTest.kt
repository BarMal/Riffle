package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HomePageEngineTest {
    private val engine = HomePageEngine()
    private val layout = HomeLayoutDefaults.standard()

    @Test
    fun addsPageWithoutMutatingOriginalLayout() {
        val page = page(id = "widgets")

        val result = engine.addPage(layout = layout, page = page)

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(listOf(pageId("home")), layout.pages.map { existingPage -> existingPage.id })
        assertEquals(
            listOf(pageId("home"), pageId("widgets")),
            updated.layout.pages.map { existingPage -> existingPage.id },
        )
        assertEquals(pageId("home"), updated.layout.selectedPageId)
    }

    @Test
    fun rejectsDuplicatePageIds() {
        val result = engine.addPage(layout = layout, page = page(id = "home"))

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.DUPLICATE_PAGE_ID, rejected.reason)
    }

    @Test
    fun rejectsPagesWithInvalidGridDimensions() {
        val result =
            engine.addPage(
                layout = layout,
                page = page(id = "widgets").copy(grid = GridDimensions(columns = 0, rows = 5)),
            )

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.INVALID_GRID_DIMENSIONS, rejected.reason)
    }

    @Test
    fun rejectsPagesWithItemsOutsideTheirGrid() {
        val shortcut =
            AppShortcutItem(
                id = itemId("camera"),
                appIdentity = appIdentity("camera"),
                label = "Camera",
                placement = GridPlacement(cell = GridCell(column = 4, row = 0)),
            )

        val result =
            engine.addPage(
                layout = layout,
                page = page(id = "widgets").copy(items = listOf(shortcut)),
            )

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.GRID_ITEMS_OUT_OF_BOUNDS, rejected.reason)
    }

    @Test
    fun rejectsPagesWithDuplicateItemIds() {
        val duplicateId = itemId("camera")
        val result =
            engine.addPage(
                layout = layout,
                page =
                    page(id = "widgets").copy(
                        items =
                            listOf(
                                AppShortcutItem(
                                    id = duplicateId,
                                    appIdentity = appIdentity("camera"),
                                    label = "Camera",
                                    placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                                ),
                                AppShortcutItem(
                                    id = duplicateId,
                                    appIdentity = appIdentity("clock"),
                                    label = "Clock",
                                    placement = GridPlacement(cell = GridCell(column = 1, row = 0)),
                                ),
                            ),
                    ),
            )

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.INVALID_PAGE_ITEMS, rejected.reason)
    }

    @Test
    fun rejectsPagesWithCollidingItems() {
        val result =
            engine.addPage(
                layout = layout,
                page =
                    page(id = "widgets").copy(
                        items =
                            listOf(
                                AppShortcutItem(
                                    id = itemId("camera"),
                                    appIdentity = appIdentity("camera"),
                                    label = "Camera",
                                    placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                                ),
                                AppShortcutItem(
                                    id = itemId("clock"),
                                    appIdentity = appIdentity("clock"),
                                    label = "Clock",
                                    placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                                ),
                            ),
                    ),
            )

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.INVALID_PAGE_ITEMS, rejected.reason)
    }

    @Test
    fun selectsExistingPage() {
        val layoutWithPages = layout.copy(pages = layout.pages + page(id = "widgets"))

        val result = engine.selectPage(layout = layoutWithPages, pageId = pageId("widgets"))

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(pageId("widgets"), updated.layout.selectedPageId)
    }

    @Test
    fun rejectsSelectingMissingPage() {
        val result = engine.selectPage(layout = layout, pageId = pageId("missing"))

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.PAGE_NOT_FOUND, rejected.reason)
    }

    @Test
    fun deletesUnselectedPageWithoutChangingSelection() {
        val layoutWithPages =
            layout.copy(
                pages = layout.pages + page(id = "widgets"),
                selectedPageId = pageId("home"),
            )

        val result = engine.deletePage(layout = layoutWithPages, pageId = pageId("widgets"))

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(listOf(pageId("home")), updated.layout.pages.map { page -> page.id })
        assertEquals(pageId("home"), updated.layout.selectedPageId)
    }

    @Test
    fun deletingSelectedMiddlePageSelectsNextPage() {
        val layoutWithPages =
            layout.copy(
                pages = listOf(page(id = "home"), page(id = "widgets"), page(id = "search")),
                selectedPageId = pageId("widgets"),
                editMode = HomeEditMode.EditingPage(pageId = pageId("widgets")),
            )

        val result = engine.deletePage(layout = layoutWithPages, pageId = pageId("widgets"))

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(listOf(pageId("home"), pageId("search")), updated.layout.pages.map { page -> page.id })
        assertEquals(pageId("search"), updated.layout.selectedPageId)
        assertEquals(HomeEditMode.EditingPage(pageId = pageId("search")), updated.layout.editMode)
    }

    @Test
    fun deletingSelectedLastPageSelectsPreviousPage() {
        val layoutWithPages =
            layout.copy(
                pages = listOf(page(id = "home"), page(id = "widgets")),
                selectedPageId = pageId("widgets"),
                editMode = HomeEditMode.EditingPage(pageId = pageId("widgets")),
            )

        val result = engine.deletePage(layout = layoutWithPages, pageId = pageId("widgets"))

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(listOf(pageId("home")), updated.layout.pages.map { page -> page.id })
        assertEquals(pageId("home"), updated.layout.selectedPageId)
        assertEquals(HomeEditMode.EditingPage(pageId = pageId("home")), updated.layout.editMode)
    }

    @Test
    fun deletingDifferentPageKeepsCurrentPageEditMode() {
        val layoutWithPages =
            layout.copy(
                pages = listOf(page(id = "home"), page(id = "widgets"), page(id = "search")),
                selectedPageId = pageId("home"),
                editMode = HomeEditMode.EditingPage(pageId = pageId("home")),
            )

        val result = engine.deletePage(layout = layoutWithPages, pageId = pageId("widgets"))

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(HomeEditMode.EditingPage(pageId = pageId("home")), updated.layout.editMode)
    }

    @Test
    fun deletingPageKeepsPageOverviewMode() {
        val layoutWithPages =
            layout.copy(
                pages = listOf(page(id = "home"), page(id = "widgets")),
                selectedPageId = pageId("home"),
                editMode = HomeEditMode.ManagingPages,
            )

        val result = engine.deletePage(layout = layoutWithPages, pageId = pageId("widgets"))

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(HomeEditMode.ManagingPages, updated.layout.editMode)
    }

    @Test
    fun rejectsDeletingMissingPage() {
        val result = engine.deletePage(layout = layout, pageId = pageId("missing"))

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.PAGE_NOT_FOUND, rejected.reason)
    }

    @Test
    fun rejectsDeletingLastPage() {
        val result = engine.deletePage(layout = layout, pageId = pageId("home"))

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.CANNOT_DELETE_LAST_PAGE, rejected.reason)
    }

    @Test
    fun duplicatesPageAfterSourcePageAndSelectsCopy() {
        val camera =
            AppShortcutItem(
                id = itemId("camera"),
                appIdentity = appIdentity("camera"),
                label = "Camera",
                placement = GridPlacement(cell = GridCell(column = 1, row = 2)),
            )
        val layoutWithPages =
            layout.copy(
                pages = listOf(layout.selectedPage.copy(items = listOf(camera)), page(id = "widgets")),
                selectedPageId = pageId("home"),
                editMode = HomeEditMode.EditingPage(pageId = pageId("home")),
            )

        val result =
            engine.duplicatePage(
                layout = layoutWithPages,
                pageId = pageId("home"),
                duplicatedPageId = pageId("home-copy"),
                itemIdProvider = itemIdProvider(),
            )

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(
            listOf(pageId("home"), pageId("home-copy"), pageId("widgets")),
            updated.layout.pages.map { page -> page.id },
        )
        val duplicatedShortcut = updated.layout.selectedPage.items.single() as AppShortcutItem
        assertEquals(pageId("home-copy"), updated.layout.selectedPageId)
        assertEquals(HomeEditMode.EditingPage(pageId = pageId("home-copy")), updated.layout.editMode)
        assertEquals(camera.appIdentity, duplicatedShortcut.appIdentity)
        assertEquals(camera.label, duplicatedShortcut.label)
        assertEquals(camera.placement, duplicatedShortcut.placement)
        assertEquals(itemId("copy-1"), duplicatedShortcut.id)
    }

    @Test
    fun duplicatesFolderItemsWithFreshIds() {
        val folder =
            FolderItem(
                id = itemId("folder"),
                label = "Tools",
                items =
                    listOf(
                        AppShortcutItem(
                            id = itemId("camera"),
                            appIdentity = appIdentity("camera"),
                            label = "Camera",
                        ),
                        AppShortcutItem(
                            id = itemId("clock"),
                            appIdentity = appIdentity("clock"),
                            label = "Clock",
                        ),
                    ),
            )
        val layoutWithFolder =
            layout.copy(
                pages = listOf(layout.selectedPage.copy(items = listOf(folder))),
            )

        val result =
            engine.duplicatePage(
                layout = layoutWithFolder,
                pageId = pageId("home"),
                duplicatedPageId = pageId("home-copy"),
                itemIdProvider = itemIdProvider(),
            )

        val updated = assertIs<HomePageEditResult.Updated>(result)
        val duplicatedFolder = updated.layout.selectedPage.items.single() as FolderItem
        assertEquals(itemId("copy-1"), duplicatedFolder.id)
        assertEquals(
            listOf(itemId("copy-2"), itemId("copy-3")),
            duplicatedFolder.items.map { item -> item.id },
        )
        assertEquals(
            folder.items.map { item -> item.appIdentity },
            duplicatedFolder.items.map { item -> item.appIdentity },
        )
    }

    @Test
    fun rejectsDuplicatingPageWithWidgets() {
        val widget =
            WidgetItem(
                id = itemId("weather"),
                appWidgetId = HostedWidgetId(42),
                label = "Weather",
                placement =
                    GridPlacement(
                        cell = GridCell(column = 0, row = 0),
                        span = GridSpan(columns = 2, rows = 2),
                    ),
            )
        val layoutWithWidget =
            layout.copy(
                pages = listOf(layout.selectedPage.copy(items = listOf(widget))),
            )

        val result =
            engine.duplicatePage(
                layout = layoutWithWidget,
                pageId = pageId("home"),
                duplicatedPageId = pageId("home-copy"),
                itemIdProvider = { error("Widget page duplication must reject before copying items.") },
            )

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.CANNOT_DUPLICATE_PAGE_WITH_WIDGETS, rejected.reason)
    }

    @Test
    fun rejectsDuplicatePageWhenSourcePageIsMissing() {
        val result =
            engine.duplicatePage(
                layout = layout,
                pageId = pageId("missing"),
                duplicatedPageId = pageId("home-copy"),
                itemIdProvider = itemIdProvider(),
            )

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.PAGE_NOT_FOUND, rejected.reason)
    }

    @Test
    fun rejectsDuplicatePageWhenNewPageIdAlreadyExists() {
        val result =
            engine.duplicatePage(
                layout = layout,
                pageId = pageId("home"),
                duplicatedPageId = pageId("home"),
                itemIdProvider = itemIdProvider(),
            )

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.DUPLICATE_PAGE_ID, rejected.reason)
    }

    @Test
    fun movesPageToTargetIndex() {
        val layoutWithPages =
            layout.copy(
                pages = listOf(page(id = "home"), page(id = "widgets"), page(id = "search")),
                selectedPageId = pageId("widgets"),
            )

        val result = engine.movePage(layout = layoutWithPages, pageId = pageId("search"), targetIndex = 0)

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(
            listOf(pageId("search"), pageId("home"), pageId("widgets")),
            updated.layout.pages.map { page -> page.id },
        )
        assertEquals(pageId("widgets"), updated.layout.selectedPageId)
    }

    @Test
    fun rejectsMovingMissingPage() {
        val result = engine.movePage(layout = layout, pageId = pageId("missing"), targetIndex = 0)

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.PAGE_NOT_FOUND, rejected.reason)
    }

    @Test
    fun rejectsMovingPageOutsideBounds() {
        val result = engine.movePage(layout = layout, pageId = pageId("home"), targetIndex = 1)

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.INDEX_OUT_OF_BOUNDS, rejected.reason)
    }

    @Test
    fun updatesPageTypeWithoutChangingSelection() {
        val layoutWithPages =
            layout.copy(
                pages = listOf(page(id = "home"), page(id = "today")),
                selectedPageId = pageId("today"),
            )

        val result =
            engine.updatePageType(
                layout = layoutWithPages,
                pageId = pageId("home"),
                type = LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY),
            )

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY), updated.layout.pages[0].type)
        assertEquals(LauncherPageType.Home, updated.layout.pages[1].type)
        assertEquals(pageId("today"), updated.layout.selectedPageId)
    }

    @Test
    fun rejectsPageTypeUpdateForMissingPage() {
        val result =
            engine.updatePageType(
                layout = layout,
                pageId = pageId("missing"),
                type = LauncherPageType.AllApps,
            )

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.PAGE_NOT_FOUND, rejected.reason)
    }

    @Test
    fun updatesPageGridDimensionsOnlyForSelectedPage() {
        val settingsGrid = GridDimensions(columns = 4, rows = 5)
        val widgetsGrid = GridDimensions(columns = 6, rows = 7)
        val layoutWithPages =
            layout.copy(
                pages =
                    listOf(
                        page(id = "home").copy(grid = settingsGrid),
                        page(id = "widgets").copy(grid = widgetsGrid),
                    ),
                selectedPageId = pageId("widgets"),
            )

        val result =
            engine.updatePageGridDimensions(
                layout = layoutWithPages,
                pageId = pageId("widgets"),
                dimensions = GridDimensions(columns = 5, rows = 6),
            )

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(settingsGrid, updated.layout.pages[0].grid)
        assertEquals(GridDimensions(columns = 5, rows = 6), updated.layout.pages[1].grid)
        assertEquals(settingsGrid, updated.layout.settings.grid.dimensions)
    }

    @Test
    fun rejectsPageGridDimensionsForMissingPage() {
        val result =
            engine.updatePageGridDimensions(
                layout = layout,
                pageId = pageId("missing"),
                dimensions = GridDimensions(columns = 5, rows = 6),
            )

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.PAGE_NOT_FOUND, rejected.reason)
    }

    @Test
    fun rejectsPageGridDimensionsSmallerThanOneCell() {
        val result =
            engine.updatePageGridDimensions(
                layout = layout,
                pageId = pageId("home"),
                dimensions = GridDimensions(columns = 0, rows = 5),
            )

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.INVALID_GRID_DIMENSIONS, rejected.reason)
    }

    @Test
    fun rejectsPageGridDimensionsThatWouldClipPlacedItemsOnThatPage() {
        val camera =
            AppShortcutItem(
                id = itemId("camera"),
                appIdentity = appIdentity("camera"),
                label = "Camera",
                placement = GridPlacement(cell = GridCell(column = 3, row = 4)),
            )
        val layoutWithShortcut =
            layout.copy(
                pages = listOf(layout.selectedPage.copy(items = listOf(camera))),
            )

        val result =
            engine.updatePageGridDimensions(
                layout = layoutWithShortcut,
                pageId = pageId("home"),
                dimensions = GridDimensions(columns = 3, rows = 5),
            )

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.GRID_ITEMS_OUT_OF_BOUNDS, rejected.reason)
    }

    @Test
    fun entersPageEditModeForExistingPage() {
        val layoutWithPages =
            layout.copy(
                pages = layout.pages + page(id = "widgets"),
                selectedPageId = pageId("home"),
            )

        val result = engine.enterPageEditMode(layout = layoutWithPages, pageId = pageId("widgets"))

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(pageId("widgets"), updated.layout.selectedPageId)
        assertEquals(HomeEditMode.EditingPage(pageId = pageId("widgets")), updated.layout.editMode)
    }

    @Test
    fun rejectsPageEditModeForMissingPage() {
        val result = engine.enterPageEditMode(layout = layout, pageId = pageId("missing"))

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.PAGE_NOT_FOUND, rejected.reason)
    }

    @Test
    fun entersPageOverviewWithoutChangingSelection() {
        val result = engine.enterPageOverview(layout = layout)

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(pageId("home"), updated.layout.selectedPageId)
        assertEquals(HomeEditMode.ManagingPages, updated.layout.editMode)
    }

    @Test
    fun exitsEditModeWithoutChangingSelection() {
        val editingLayout =
            layout.copy(
                editMode = HomeEditMode.EditingPage(pageId = pageId("home")),
            )

        val result = engine.exitEditMode(layout = editingLayout)

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(pageId("home"), updated.layout.selectedPageId)
        assertEquals(HomeEditMode.Browsing, updated.layout.editMode)
    }

    @Test
    fun updatesGridDimensionsForAllPagesAndSettings() {
        val layoutWithPages =
            layout.copy(
                pages = listOf(page(id = "home"), page(id = "widgets")),
            )

        val result =
            engine.updateGridDimensions(
                layout = layoutWithPages,
                dimensions = GridDimensions(columns = 5, rows = 6),
            )

        val updated = assertIs<HomePageEditResult.Updated>(result)
        assertEquals(
            listOf(GridDimensions(columns = 5, rows = 6), GridDimensions(columns = 5, rows = 6)),
            updated.layout.pages.map { page -> page.grid },
        )
        assertEquals(GridDimensions(columns = 5, rows = 6), updated.layout.settings.grid.dimensions)
    }

    @Test
    fun rejectsGridDimensionsSmallerThanOneCell() {
        val result =
            engine.updateGridDimensions(
                layout = layout,
                dimensions = GridDimensions(columns = 0, rows = 5),
            )

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.INVALID_GRID_DIMENSIONS, rejected.reason)
    }

    @Test
    fun rejectsGridDimensionsThatWouldClipPlacedItems() {
        val camera =
            AppShortcutItem(
                id = itemId("camera"),
                appIdentity = appIdentity("camera"),
                label = "Camera",
                placement = GridPlacement(cell = GridCell(column = 3, row = 4)),
            )
        val layoutWithShortcut =
            layout.copy(
                pages = listOf(layout.selectedPage.copy(items = listOf(camera))),
            )

        val result =
            engine.updateGridDimensions(
                layout = layoutWithShortcut,
                dimensions = GridDimensions(columns = 3, rows = 5),
            )

        val rejected = assertIs<HomePageEditResult.Rejected>(result)
        assertEquals(HomePageEditRejectionReason.GRID_ITEMS_OUT_OF_BOUNDS, rejected.reason)
    }

    private fun page(id: String): LauncherPage =
        LauncherPage(
            id = pageId(id),
            grid = GridDimensions(columns = 4, rows = 5),
        )

    private fun pageId(value: String): LauncherPageId = LauncherPageId(value)

    private fun itemId(value: String): LauncherItemId = LauncherItemId(value)

    private fun appIdentity(value: String) =
        com.riffle.core.domain.launcher.apps.AppIdentity(
            packageName = com.riffle.core.domain.launcher.apps.AppPackageName("com.riffle.$value"),
            activityName = com.riffle.core.domain.launcher.apps.AppActivityName(".MainActivity"),
        )

    private fun itemIdProvider(): () -> LauncherItemId {
        var ordinal = 0

        return {
            ordinal += 1
            itemId("copy-$ordinal")
        }
    }
}
