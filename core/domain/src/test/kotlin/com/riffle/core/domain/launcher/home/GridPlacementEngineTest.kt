package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GridPlacementEngineTest {
    private val engine = GridPlacementEngine()
    private val page =
        LauncherPage(
            id = LauncherPageId("home"),
            grid = GridDimensions(columns = 4, rows = 5),
        )

    @Test
    fun placesItemInsideGridWithoutMutatingOriginalPage() {
        val item =
            appItem(
                id = "camera",
                placement = GridPlacement(cell = GridCell(column = 1, row = 2)),
            )

        val result = engine.placeItem(page = page, item = item)

        val placed = assertIs<PlaceLauncherItemResult.Placed>(result)
        assertTrue(page.items.isEmpty())
        assertEquals(listOf(item), placed.page.items)
    }

    @Test
    fun rejectsItemsWithoutPlacement() {
        val result = engine.placeItem(page = page, item = appItem(id = "camera"))

        val rejected = assertIs<PlaceLauncherItemResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.MISSING_PLACEMENT, rejected.reason)
    }

    @Test
    fun placesItemInFirstAvailableCell() {
        val result = engine.placeItemInFirstAvailableCell(page = page, item = appItem(id = "camera"))

        val placed = assertIs<PlaceLauncherItemResult.Placed>(result)
        assertEquals(GridPlacement(cell = GridCell(column = 0, row = 0)), placed.page.items.single().placement)
    }

    @Test
    fun skipsOccupiedCellsWhenPlacingFirstAvailableCell() {
        val occupiedPage =
            page.copy(
                items =
                    listOf(
                        appItem(
                            id = "calendar",
                            placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                        ),
                    ),
            )

        val result = engine.placeItemInFirstAvailableCell(page = occupiedPage, item = appItem(id = "camera"))

        val placed = assertIs<PlaceLauncherItemResult.Placed>(result)
        assertEquals(GridPlacement(cell = GridCell(column = 1, row = 0)), placed.page.items.last().placement)
    }

    @Test
    fun placesSpanningItemInFirstAvailableCellWhereItFits() {
        val occupiedPage =
            page.copy(
                items =
                    listOf(
                        appItem(
                            id = "calendar",
                            placement =
                                GridPlacement(
                                    cell = GridCell(column = 0, row = 0),
                                    span = GridSpan(columns = 3, rows = 1),
                                ),
                        ),
                    ),
            )

        val result =
            engine.placeItemInFirstAvailableCell(
                page = occupiedPage,
                item = appItem(id = "camera"),
                span = GridSpan(columns = 2, rows = 1),
            )

        val placed = assertIs<PlaceLauncherItemResult.Placed>(result)
        assertEquals(
            GridPlacement(
                cell = GridCell(column = 0, row = 1),
                span = GridSpan(columns = 2, rows = 1),
            ),
            placed.page.items.last().placement,
        )
    }

    @Test
    fun rejectsFirstAvailablePlacementWhenNoCellsFit() {
        val fullPage =
            LauncherPage(
                id = LauncherPageId("full"),
                grid = GridDimensions(columns = 1, rows = 1),
                items =
                    listOf(
                        appItem(
                            id = "calendar",
                            placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                        ),
                    ),
            )

        val result = engine.placeItemInFirstAvailableCell(page = fullPage, item = appItem(id = "camera"))

        val rejected = assertIs<PlaceLauncherItemResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.NO_AVAILABLE_CELL, rejected.reason)
    }

    @Test
    fun rejectsPlacementOutsideGridBounds() {
        val item =
            appItem(
                id = "camera",
                placement = GridPlacement(cell = GridCell(column = 4, row = 0)),
            )

        val result = engine.placeItem(page = page, item = item)

        val rejected = assertIs<PlaceLauncherItemResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.OUT_OF_BOUNDS, rejected.reason)
    }

    @Test
    fun rejectsSpansThatOverflowGridBounds() {
        val item =
            appItem(
                id = "calendar",
                placement =
                    GridPlacement(
                        cell = GridCell(column = 2, row = 4),
                        span = GridSpan(columns = 2, rows = 2),
                    ),
            )

        val result = engine.placeItem(page = page, item = item)

        val rejected = assertIs<PlaceLauncherItemResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.OUT_OF_BOUNDS, rejected.reason)
    }

    @Test
    fun rejectsCollidingItems() {
        val occupiedPage =
            page.copy(
                items =
                    listOf(
                        appItem(
                            id = "calendar",
                            placement =
                                GridPlacement(
                                    cell = GridCell(column = 1, row = 1),
                                    span = GridSpan(columns = 2, rows = 2),
                                ),
                        ),
                    ),
            )
        val candidate =
            appItem(
                id = "camera",
                placement = GridPlacement(cell = GridCell(column = 2, row = 2)),
            )

        val result = engine.placeItem(page = occupiedPage, item = candidate)

        val rejected = assertIs<PlaceLauncherItemResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.COLLISION, rejected.reason)
    }

    @Test
    fun allowsAdjacentItems() {
        val occupiedPage =
            page.copy(
                items =
                    listOf(
                        appItem(
                            id = "calendar",
                            placement =
                                GridPlacement(
                                    cell = GridCell(column = 0, row = 0),
                                    span = GridSpan(columns = 2, rows = 2),
                                ),
                        ),
                    ),
            )
        val candidate =
            appItem(
                id = "camera",
                placement = GridPlacement(cell = GridCell(column = 2, row = 0)),
            )

        val result = engine.placeItem(page = occupiedPage, item = candidate)

        val placed = assertIs<PlaceLauncherItemResult.Placed>(result)
        assertEquals(2, placed.page.items.size)
    }

    @Test
    fun removesItemWithoutMutatingOriginalPage() {
        val item =
            appItem(
                id = "camera",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val occupiedPage = page.copy(items = listOf(item))

        val updatedPage = engine.removeItem(page = occupiedPage, itemId = item.id)

        assertEquals(listOf(item), occupiedPage.items)
        assertTrue(updatedPage.items.isEmpty())
    }

    @Test
    fun movesItemWithoutMutatingOriginalPage() {
        val item =
            appItem(
                id = "camera",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val occupiedPage = page.copy(items = listOf(item))
        val newPlacement = GridPlacement(cell = GridCell(column = 2, row = 3))

        val result =
            engine.moveItem(
                page = occupiedPage,
                itemId = item.id,
                placement = newPlacement,
            )

        val placed = assertIs<PlaceLauncherItemResult.Placed>(result)
        assertEquals(GridPlacement(cell = GridCell(column = 0, row = 0)), occupiedPage.items.single().placement)
        assertEquals(newPlacement, placed.page.items.single().placement)
    }

    @Test
    fun rejectsMoveWhenItemIsMissing() {
        val result =
            engine.moveItem(
                page = page,
                itemId = LauncherItemId("missing"),
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )

        val rejected = assertIs<PlaceLauncherItemResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.ITEM_NOT_FOUND, rejected.reason)
    }

    @Test
    fun rejectsMoveIntoOccupiedCells() {
        val camera =
            appItem(
                id = "camera",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val calendar =
            appItem(
                id = "calendar",
                placement = GridPlacement(cell = GridCell(column = 2, row = 2)),
            )
        val occupiedPage = page.copy(items = listOf(camera, calendar))

        val result =
            engine.moveItem(
                page = occupiedPage,
                itemId = camera.id,
                placement = GridPlacement(cell = GridCell(column = 2, row = 2)),
            )

        val rejected = assertIs<PlaceLauncherItemResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.COLLISION, rejected.reason)
    }

    @Test
    fun resizesItemAtExistingCell() {
        val item =
            appItem(
                id = "calendar",
                placement = GridPlacement(cell = GridCell(column = 1, row = 1)),
            )
        val occupiedPage = page.copy(items = listOf(item))
        val newSpan = GridSpan(columns = 2, rows = 2)

        val result =
            engine.resizeItem(
                page = occupiedPage,
                itemId = item.id,
                span = newSpan,
            )

        val placed = assertIs<PlaceLauncherItemResult.Placed>(result)
        assertEquals(newSpan, placed.page.items.single().placement?.span)
        assertEquals(GridCell(column = 1, row = 1), placed.page.items.single().placement?.cell)
    }

    @Test
    fun rejectsResizeThatOverflowsGrid() {
        val item =
            appItem(
                id = "calendar",
                placement = GridPlacement(cell = GridCell(column = 3, row = 4)),
            )
        val occupiedPage = page.copy(items = listOf(item))

        val result =
            engine.resizeItem(
                page = occupiedPage,
                itemId = item.id,
                span = GridSpan(columns = 2, rows = 2),
            )

        val rejected = assertIs<PlaceLauncherItemResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.OUT_OF_BOUNDS, rejected.reason)
    }

    @Test
    fun rejectsResizeIntoOccupiedCells() {
        val calendar =
            appItem(
                id = "calendar",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val camera =
            appItem(
                id = "camera",
                placement = GridPlacement(cell = GridCell(column = 1, row = 1)),
            )
        val occupiedPage = page.copy(items = listOf(calendar, camera))

        val result =
            engine.resizeItem(
                page = occupiedPage,
                itemId = calendar.id,
                span = GridSpan(columns = 2, rows = 2),
            )

        val rejected = assertIs<PlaceLauncherItemResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.COLLISION, rejected.reason)
    }

    @Test
    fun rejectsResizeWhenItemIsMissing() {
        val result =
            engine.resizeItem(
                page = page,
                itemId = LauncherItemId("missing"),
                span = GridSpan(columns = 2, rows = 2),
            )

        val rejected = assertIs<PlaceLauncherItemResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.ITEM_NOT_FOUND, rejected.reason)
    }

    @Test
    fun rejectsResizeWhenExistingItemHasNoPlacement() {
        val item = appItem(id = "calendar")
        val occupiedPage = page.copy(items = listOf(item))

        val result =
            engine.resizeItem(
                page = occupiedPage,
                itemId = item.id,
                span = GridSpan(columns = 2, rows = 2),
            )

        val rejected = assertIs<PlaceLauncherItemResult.Rejected>(result)
        assertEquals(PlacementRejectionReason.MISSING_PLACEMENT, rejected.reason)
    }

    private fun appItem(
        id: String,
        placement: GridPlacement? = null,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$id"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            placement = placement,
        )
}
