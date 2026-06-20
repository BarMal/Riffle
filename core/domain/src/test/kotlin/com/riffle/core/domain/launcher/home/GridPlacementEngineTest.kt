package com.riffle.core.domain.launcher.home

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

    private fun appItem(
        id: String,
        placement: GridPlacement? = null,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            packageName = "com.riffle.$id",
            activityName = ".MainActivity",
            placement = placement,
        )
}
