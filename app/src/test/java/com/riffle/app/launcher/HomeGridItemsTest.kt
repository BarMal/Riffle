package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
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

    @Test
    fun dragPreviewLeavesSpannedWidgetInPlaceWhenTargetCellIsEmpty() {
        val shortcut = shortcut(placement = GridPlacement(cell = GridCell(column = 0, row = 0)))
        val widget =
            widget(
                span = GridSpan(columns = 2, rows = 2),
                cell = GridCell(column = 1, row = 1),
            )
        val page =
            LauncherPage(
                id = LauncherPageId("home"),
                grid = GridDimensions(columns = 4, rows = 5),
                items = listOf(shortcut, widget),
            )

        val previewItems =
            page.itemsForDragPreview(
                HomeDragSession(
                    item = shortcut,
                    originCell = GridCell(column = 0, row = 0),
                    projectedCell = GridCell(column = 3, row = 4),
                ),
            )

        assertEquals(null, previewItems.itemAt(GridCell(column = 0, row = 0)))
        assertSame(widget, previewItems.itemAt(GridCell(column = 1, row = 1)))
        assertEquals(widget.placement, previewItems.single { item -> item.id == widget.id }.placement)
    }

    private fun shortcut(placement: GridPlacement): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId("camera"),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.camera"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = "Camera",
            placement = placement,
        )

    private fun widget(
        span: GridSpan,
        cell: GridCell = GridCell(column = 1, row = 1),
    ): WidgetItem =
        WidgetItem(
            id = LauncherItemId("widget"),
            appWidgetId = HostedWidgetId(10),
            label = "Clock",
            placement =
                GridPlacement(
                    cell = cell,
                    span = span,
                ),
        )
}
