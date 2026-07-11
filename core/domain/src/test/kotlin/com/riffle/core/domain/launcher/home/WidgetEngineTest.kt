package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WidgetEngineTest {
    private val engine = WidgetEngine()

    @Test
    fun rejectsAddingWidgetToGeneratedPage() {
        val layout =
            HomeLayoutDefaults.standard().let { standard ->
                standard.copy(
                    pages =
                        listOf(
                            standard.selectedPage.copy(
                                type = LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY),
                            ),
                        ),
                )
            }

        val result = engine.addWidgetToSelectedPage(layout, HostedWidgetId(42), "Weather")

        assertEquals(
            PlacementRejectionReason.GENERATED_PAGE,
            assertIs<WidgetEditResult.Rejected>(result).reason,
        )
    }

    @Test
    fun addsWidgetToFirstAvailableCellOnSelectedPage() {
        val result =
            engine.addWidgetToSelectedPage(
                layout = HomeLayoutDefaults.standard(),
                hostedWidgetId = HostedWidgetId(42),
                label = "Weather",
                preferredSpan = GridSpan(columns = 2, rows = 2),
            )

        val updated = assertIs<WidgetEditResult.Updated>(result)
        assertEquals(
            WidgetItem(
                id = LauncherItemId("widget:42"),
                appWidgetId = HostedWidgetId(42),
                label = "Weather",
                placement =
                    GridPlacement(
                        cell = GridCell(column = 0, row = 0),
                        span = GridSpan(columns = 2, rows = 2),
                    ),
            ),
            updated.layout.selectedPage.items.single(),
        )
        assertEquals(GridSpan(columns = 2, rows = 2), updated.placedSpan)
    }

    @Test
    fun fallsBackToGenericLabel() {
        val result =
            engine.addWidgetToSelectedPage(
                layout = HomeLayoutDefaults.standard(),
                hostedWidgetId = HostedWidgetId(7),
                label = "",
            )

        val updated = assertIs<WidgetEditResult.Updated>(result)
        assertEquals("Widget", (updated.layout.selectedPage.items.single() as WidgetItem).label)
    }

    @Test
    fun shrinksWidgetSpanWhenPreferredSpanDoesNotFit() {
        val layout =
            HomeLayoutDefaults.standard()
                .copy(
                    pages =
                        listOf(
                            HomeLayoutDefaults.standard().selectedPage.copy(
                                grid = GridDimensions(columns = 2, rows = 2),
                            ),
                        ),
                )

        val result =
            engine.addWidgetToSelectedPage(
                layout = layout,
                hostedWidgetId = HostedWidgetId(42),
                label = "Weather",
                preferredSpan = GridSpan(columns = 4, rows = 3),
            )

        val updated = assertIs<WidgetEditResult.Updated>(result)
        assertEquals(
            GridPlacement(
                cell = GridCell(column = 0, row = 0),
                span = GridSpan(columns = 2, rows = 2),
            ),
            updated.layout.selectedPage.items.single().placement,
        )
        assertEquals(GridSpan(columns = 2, rows = 2), updated.placedSpan)
    }

    @Test
    fun addsWidgetToRequestedCellWhenTargetIsAvailable() {
        val result =
            engine.addWidgetToSelectedPage(
                layout = HomeLayoutDefaults.standard(),
                hostedWidgetId = HostedWidgetId(42),
                label = "Weather",
                preferredSpan = GridSpan(columns = 2, rows = 2),
                targetCell = GridCell(column = 1, row = 2),
            )

        val updated = assertIs<WidgetEditResult.Updated>(result)
        assertEquals(
            GridPlacement(
                cell = GridCell(column = 1, row = 2),
                span = GridSpan(columns = 2, rows = 2),
            ),
            updated.layout.selectedPage.items.single().placement,
        )
    }

    @Test
    fun fallsBackToSmallerSpanAtRequestedCellWhenPreferredSpanDoesNotFit() {
        val result =
            engine.addWidgetToSelectedPage(
                layout = HomeLayoutDefaults.standard(),
                hostedWidgetId = HostedWidgetId(42),
                label = "Weather",
                preferredSpan = GridSpan(columns = 3, rows = 3),
                targetCell = GridCell(column = 3, row = 4),
            )

        val updated = assertIs<WidgetEditResult.Updated>(result)
        assertEquals(
            GridPlacement(
                cell = GridCell(column = 3, row = 4),
                span = GridSpan(columns = 1, rows = 1),
            ),
            updated.layout.selectedPage.items.single().placement,
        )
    }

    @Test
    fun rejectsWidgetWhenSelectedPageIsFull() {
        val defaultGrid = HomeLayoutDefaults.standard().settings.grid.dimensions
        val fullPage =
            HomeLayoutDefaults.standard().selectedPage.copy(
                items =
                    (0 until DEFAULT_GRID_CELL_COUNT).map { index ->
                        WidgetItem(
                            id = LauncherItemId("widget:$index"),
                            appWidgetId = HostedWidgetId(index),
                            label = "Widget $index",
                            placement =
                                GridPlacement(
                                    cell =
                                        GridCell(
                                            column = index % defaultGrid.columns,
                                            row = index / defaultGrid.columns,
                                        ),
                                ),
                        )
                    },
            )
        val layout = HomeLayoutDefaults.standard().copy(pages = listOf(fullPage))

        val result =
            engine.addWidgetToSelectedPage(
                layout = layout,
                hostedWidgetId = HostedWidgetId(99),
                label = "Weather",
            )

        val rejected = assertIs<WidgetEditResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.NO_AVAILABLE_CELL, rejected.reason)
    }

    @Test
    fun rejectsWidgetWhenHostedWidgetIdAlreadyExistsOnAnotherPage() {
        val existingWidget =
            WidgetItem(
                id = LauncherItemId("widget:42"),
                appWidgetId = HostedWidgetId(42),
                label = "Existing",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val existingPage = HomeLayoutDefaults.standard().selectedPage.copy(items = listOf(existingWidget))
        val targetPage = HomeLayoutDefaults.standard().selectedPage.copy(id = LauncherPageId("widgets"))
        val layout =
            HomeLayoutDefaults.standard().copy(
                pages = listOf(existingPage, targetPage),
                selectedPageId = targetPage.id,
            )

        val result =
            engine.addWidgetToSelectedPage(
                layout = layout,
                hostedWidgetId = HostedWidgetId(42),
                label = "Weather",
            )

        val rejected = assertIs<WidgetEditResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.DUPLICATE_ITEM_ID, rejected.reason)
        assertEquals(emptyList<WidgetItem>(), layout.selectedPage.items.filterIsInstance<WidgetItem>())
    }

    @Test
    fun rejectsWidgetWhenHostedWidgetIdAlreadyExistsInDock() {
        val layout =
            HomeLayoutDefaults.standard().copy(
                dock =
                    DockModel(
                        capacity = 1,
                        items =
                            listOf(
                                WidgetItem(
                                    id = LauncherItemId("dock-widget:42"),
                                    appWidgetId = HostedWidgetId(42),
                                    label = "Existing",
                                ),
                            ),
                    ),
            )

        val result =
            engine.addWidgetToSelectedPage(
                layout = layout,
                hostedWidgetId = HostedWidgetId(42),
                label = "Weather",
            )

        val rejected = assertIs<WidgetEditResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.DUPLICATE_ITEM_ID, rejected.reason)
        assertEquals(emptyList<WidgetItem>(), layout.selectedPage.items.filterIsInstance<WidgetItem>())
    }

    @Test
    fun resizesWidgetOnSelectedPage() {
        val widget =
            WidgetItem(
                id = LauncherItemId("widget:42"),
                appWidgetId = HostedWidgetId(42),
                label = "Weather",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val layout = HomeLayoutDefaults.standard().withSelectedPageItems(widget)

        val result =
            engine.resizeWidgetOnSelectedPage(
                layout = layout,
                itemId = widget.id,
                span = GridSpan(columns = 2, rows = 2),
            )

        val updated = assertIs<WidgetEditResult.Updated>(result)
        assertEquals(
            GridPlacement(
                cell = GridCell(column = 0, row = 0),
                span = GridSpan(columns = 2, rows = 2),
            ),
            updated.layout.selectedPage.items.single().placement,
        )
    }

    @Test
    fun rejectsWidgetResizeIntoOccupiedCells() {
        val widget =
            WidgetItem(
                id = LauncherItemId("widget:42"),
                appWidgetId = HostedWidgetId(42),
                label = "Weather",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val blocker =
            WidgetItem(
                id = LauncherItemId("widget:7"),
                appWidgetId = HostedWidgetId(7),
                label = "Clock",
                placement = GridPlacement(cell = GridCell(column = 1, row = 1)),
            )
        val layout = HomeLayoutDefaults.standard().withSelectedPageItems(widget, blocker)

        val result =
            engine.resizeWidgetOnSelectedPage(
                layout = layout,
                itemId = widget.id,
                span = GridSpan(columns = 2, rows = 2),
            )

        val rejected = assertIs<WidgetEditResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.COLLISION, rejected.reason)
    }

    @Test
    fun rejectsResizingShortcutThroughWidgetPath() {
        val shortcut =
            AppShortcutItem(
                id = LauncherItemId("app:camera:1"),
                appIdentity = appIdentity("camera"),
                label = "Camera",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val layout =
            HomeLayoutDefaults.standard().copy(
                pages = listOf(HomeLayoutDefaults.standard().selectedPage.copy(items = listOf(shortcut))),
            )

        val result =
            engine.resizeWidgetOnSelectedPage(
                layout = layout,
                itemId = shortcut.id,
                span = GridSpan(columns = 2, rows = 2),
            )

        val rejected = assertIs<WidgetEditResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.ITEM_NOT_FOUND, rejected.reason)
        assertEquals(GridPlacement(cell = GridCell(column = 0, row = 0)), layout.selectedPage.items.single().placement)
    }

    @Test
    fun rejectsResizingFolderThroughWidgetPath() {
        val folder =
            FolderItem(
                id = LauncherItemId("folder:tools"),
                label = "Tools",
                items = emptyList(),
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val layout =
            HomeLayoutDefaults.standard().copy(
                pages = listOf(HomeLayoutDefaults.standard().selectedPage.copy(items = listOf(folder))),
            )

        val result =
            engine.resizeWidgetOnSelectedPage(
                layout = layout,
                itemId = folder.id,
                span = GridSpan(columns = 2, rows = 2),
            )

        val rejected = assertIs<WidgetEditResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.ITEM_NOT_FOUND, rejected.reason)
        assertEquals(GridPlacement(cell = GridCell(column = 0, row = 0)), layout.selectedPage.items.single().placement)
    }

    private fun HomeLayout.withSelectedPageItems(vararg items: WidgetItem): HomeLayout =
        copy(pages = listOf(selectedPage.copy(items = items.toList())))

    private companion object {
        private const val DEFAULT_GRID_CELL_COUNT = 20

        private fun appIdentity(value: String) =
            com.riffle.core.domain.launcher.apps.AppIdentity(
                packageName = com.riffle.core.domain.launcher.apps.AppPackageName("com.riffle.$value"),
                activityName = com.riffle.core.domain.launcher.apps.AppActivityName(".MainActivity"),
            )
    }
}
