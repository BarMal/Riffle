package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WidgetEngineTest {
    private val engine = WidgetEngine()

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

    private companion object {
        private const val DEFAULT_GRID_CELL_COUNT = 20
    }
}
