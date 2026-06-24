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
            )

        val updated = assertIs<WidgetEditResult.Updated>(result)
        assertEquals(
            WidgetItem(
                id = LauncherItemId("widget:42"),
                appWidgetId = HostedWidgetId(42),
                label = "Weather",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            ),
            updated.layout.selectedPage.items.single(),
        )
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
