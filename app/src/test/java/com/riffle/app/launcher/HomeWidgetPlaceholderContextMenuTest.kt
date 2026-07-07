package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeWidgetPlaceholderContextMenuTest {
    @Test
    fun widgetPlaceholderMenuRemovesWidgetFromHome() {
        val widget =
            WidgetItem(
                id = LauncherItemId("widget"),
                appWidgetId = HostedWidgetId(42),
                label = "Calendar",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )

        val items = widgetPlaceholderContextMenuItems(widget)

        assertEquals(
            listOf(
                ShortcutContextMenuItem(
                    label = "Make wider",
                    action =
                        LauncherShellAction.ResizeHomeWidget(
                            itemId = widget.id,
                            span = GridSpan(columns = 2, rows = 1),
                        ),
                ),
                ShortcutContextMenuItem(
                    label = "Make narrower",
                    action =
                        LauncherShellAction.ResizeHomeWidget(
                            itemId = widget.id,
                            span = GridSpan(columns = 1, rows = 1),
                        ),
                    enabled = false,
                ),
                ShortcutContextMenuItem(
                    label = "Make taller",
                    action =
                        LauncherShellAction.ResizeHomeWidget(
                            itemId = widget.id,
                            span = GridSpan(columns = 1, rows = 2),
                        ),
                ),
                ShortcutContextMenuItem(
                    label = "Make shorter",
                    action =
                        LauncherShellAction.ResizeHomeWidget(
                            itemId = widget.id,
                            span = GridSpan(columns = 1, rows = 1),
                        ),
                    enabled = false,
                ),
                ShortcutContextMenuItem(
                    label = "Remove from home",
                    action = LauncherShellAction.RemoveHomeShortcut(widget.id),
                ),
            ),
            items,
        )
    }

    @Test
    fun widgetPlaceholderMenuDisablesResizeActionsAtGridBounds() {
        val widget =
            WidgetItem(
                id = LauncherItemId("widget"),
                appWidgetId = HostedWidgetId(42),
                label = "Calendar",
                placement =
                    GridPlacement(
                        cell = GridCell(column = 1, row = 1),
                        span = GridSpan(columns = 2, rows = 2),
                    ),
            )

        val items = widgetPlaceholderContextMenuItems(widget, GridDimensions(columns = 3, rows = 3))

        assertEquals(false, items.single { item -> item.label == "Make wider" }.enabled)
        assertEquals(true, items.single { item -> item.label == "Make narrower" }.enabled)
        assertEquals(false, items.single { item -> item.label == "Make taller" }.enabled)
        assertEquals(true, items.single { item -> item.label == "Make shorter" }.enabled)
    }
}
