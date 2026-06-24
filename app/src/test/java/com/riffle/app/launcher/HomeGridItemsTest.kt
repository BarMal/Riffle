package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGridItemsTest {
    @Test
    fun itemAtReturnsOnlyAnchorCell() {
        val widget = widget(span = GridSpan(columns = 2, rows = 2))
        val items = listOf(widget)

        assertSame(widget, items.itemAt(GridCell(column = 1, row = 1)))
        assertEquals(null, items.itemAt(GridCell(column = 2, row = 1)))
    }

    @Test
    fun occupyingItemAtIncludesSpannedWidgetCells() {
        val widget = widget(span = GridSpan(columns = 2, rows = 2))
        val items = listOf(widget)

        assertSame(widget, items.occupyingItemAt(GridCell(column = 1, row = 1)))
        assertSame(widget, items.occupyingItemAt(GridCell(column = 2, row = 1)))
        assertSame(widget, items.occupyingItemAt(GridCell(column = 1, row = 2)))
        assertSame(widget, items.occupyingItemAt(GridCell(column = 2, row = 2)))
        assertEquals(null, items.occupyingItemAt(GridCell(column = 3, row = 2)))
    }

    @Test
    fun occupiesTreatsInvalidSpanAsSingleCell() {
        val widget = widget(span = GridSpan(columns = 0, rows = 0))

        assertTrue(widget.occupies(GridCell(column = 1, row = 1)))
        assertFalse(widget.occupies(GridCell(column = 2, row = 1)))
    }

    private fun widget(span: GridSpan): WidgetItem =
        WidgetItem(
            id = LauncherItemId("widget"),
            appWidgetId = HostedWidgetId(10),
            label = "Clock",
            placement =
                GridPlacement(
                    cell = GridCell(column = 1, row = 1),
                    span = span,
                ),
        )
}
