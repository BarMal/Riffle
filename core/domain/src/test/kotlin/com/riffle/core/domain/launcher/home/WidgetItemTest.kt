package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetItemTest {
    @Test
    fun widgetItemCanBePlacedOnGrid() {
        val widget =
            WidgetItem(
                id = LauncherItemId("widget:weather"),
                appWidgetId = HostedWidgetId(42),
                label = "Weather",
            )
        val placement =
            GridPlacement(
                cell = GridCell(column = 1, row = 2),
                span = GridSpan(columns = 2, rows = 2),
            )

        assertEquals(
            widget.copy(placement = placement),
            widget.withPlacement(placement),
        )
    }
}
