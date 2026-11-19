package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.StackedWidget
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.WidgetResizeConstraints
import com.riffle.core.domain.launcher.home.restacked
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeWidgetPlaceholderContextMenuTest {
    @Test
    fun editSurfaceExposesOnlyProviderSupportedResizeAxes() {
        val widget =
            WidgetItem(
                id = LauncherItemId("widget"),
                appWidgetId = HostedWidgetId(42),
                label = "Calendar",
                resizeConstraints =
                    WidgetResizeConstraints(
                        supportsHorizontalResize = true,
                        supportsVerticalResize = false,
                    ),
            )

        assertEquals(
            listOf("Make wider", "Make narrower"),
            widget.resizeHandleLabels(),
        )
    }

    @Test
    fun widgetPlaceholderMenuExposesOnlyProviderSupportedResizeAxis() {
        val widget =
            WidgetItem(
                id = LauncherItemId("widget"),
                appWidgetId = HostedWidgetId(42),
                label = "Calendar",
                resizeConstraints =
                    WidgetResizeConstraints(
                        minSpan = GridSpan(columns = 1, rows = 1),
                        maxSpan = GridSpan(columns = 3, rows = 1),
                        supportsHorizontalResize = true,
                        supportsVerticalResize = false,
                    ),
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )

        val items =
            widgetPlaceholderContextMenuItems(widget, GridDimensions(columns = 3, rows = 3), text = SettingsTestText)

        assertEquals(true, items.single { item -> item.label == "Make wider" }.enabled)
        assertEquals(false, items.single { item -> item.label == "Make taller" }.enabled)
    }

    @Test
    fun widgetPlaceholderMenuRemovesWidgetFromHome() {
        val widget =
            WidgetItem(
                id = LauncherItemId("widget"),
                appWidgetId = HostedWidgetId(42),
                label = "Calendar",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )

        val items = widgetPlaceholderContextMenuItems(widget, text = SettingsTestText)

        assertEquals(
            listOf(
                ShortcutContextMenuItem(
                    label = "Add widget to stack",
                    action = LauncherShellAction.OpenWidgetPickerForStack(widget.id),
                ),
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
                    label = "Remove from Home",
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

        val items =
            widgetPlaceholderContextMenuItems(widget, GridDimensions(columns = 3, rows = 3), text = SettingsTestText)

        assertEquals(false, items.single { item -> item.label == "Make wider" }.enabled)
        assertEquals(true, items.single { item -> item.label == "Make narrower" }.enabled)
        assertEquals(false, items.single { item -> item.label == "Make taller" }.enabled)
        assertEquals(true, items.single { item -> item.label == "Make shorter" }.enabled)
    }

    @Test
    fun widgetPlaceholderMenuDisablesHorizontalResizeWhenAnotherItemWouldCollide() {
        val widget =
            WidgetItem(
                id = LauncherItemId("widget"),
                appWidgetId = HostedWidgetId(42),
                label = "Calendar",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val blocker =
            WidgetItem(
                id = LauncherItemId("widget-2"),
                appWidgetId = HostedWidgetId(43),
                label = "Weather",
                placement = GridPlacement(cell = GridCell(column = 1, row = 0)),
            )

        val items =
            widgetPlaceholderContextMenuItems(
                widget = widget,
                grid = GridDimensions(columns = 3, rows = 3),
                pageItems = listOf(widget, blocker),
                text = SettingsTestText,
            )

        assertEquals(false, items.single { item -> item.label == "Make wider" }.enabled)
    }

    @Test
    fun widgetPlaceholderMenuDisablesVerticalResizeWhenAnotherItemWouldCollide() {
        val widget =
            WidgetItem(
                id = LauncherItemId("widget"),
                appWidgetId = HostedWidgetId(42),
                label = "Calendar",
                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
            )
        val blocker =
            WidgetItem(
                id = LauncherItemId("widget-2"),
                appWidgetId = HostedWidgetId(43),
                label = "Weather",
                placement = GridPlacement(cell = GridCell(column = 0, row = 1)),
            )

        val items =
            widgetPlaceholderContextMenuItems(
                widget = widget,
                grid = GridDimensions(columns = 3, rows = 3),
                pageItems = listOf(widget, blocker),
                text = SettingsTestText,
            )

        assertEquals(false, items.single { item -> item.label == "Make taller" }.enabled)
    }

    private fun stackOf(count: Int): WidgetItem =
        WidgetItem(
            id = LauncherItemId("stack"),
            appWidgetId = HostedWidgetId(1),
            label = "Widget 1",
            placement = GridPlacement(cell = GridCell(column = 0, row = 0), span = GridSpan(columns = 2, rows = 2)),
        ).restacked((1..count).map { StackedWidget(HostedWidgetId(it), "Widget $it") }, shown = 0)

    @Test
    fun aStacksMenuLeadsWithEditStackAndAddWidgetToStackAndRemovesAfterAsking() {
        val stack = stackOf(3)

        val items = widgetPlaceholderContextMenuItems(stack, text = SettingsTestText)

        assertEquals(
            listOf("Edit stack", "Add widget to stack"),
            items.take(2).map { item -> item.label },
        )
        assertEquals(LauncherShellAction.OpenWidgetStackEditor(stack.id), items.first().action)
        assertEquals(
            LauncherShellAction.ConfirmRemoveWidgetStack(stack.id),
            items.single { item -> item.label == "Remove from Home" }.action,
        )
    }

    @Test
    fun aFullStackCannotTakeAnotherWidget() {
        val items = widgetPlaceholderContextMenuItems(stackOf(10), text = SettingsTestText)

        assertEquals(false, items.single { item -> item.label == "Add widget to stack" }.enabled)
    }

    @Test
    fun theDockAndItsPanelNeverOfferAStack() {
        listOf(ShortcutContextSurface.DOCK, ShortcutContextSurface.DOCK_PANEL).forEach { surface ->
            val labels =
                widgetPlaceholderContextMenuItems(stackOf(2), contextSurface = surface, text = SettingsTestText)
                    .map { item -> item.label }

            assertEquals(false, "Edit stack" in labels)
            assertEquals(false, "Add widget to stack" in labels)
        }
    }

    @Test
    fun aStacksResizeMenuFollowsWhatEveryMemberSupports() {
        val stack = stackOf(2)

        val items = widgetPlaceholderContextMenuItems(stack, GridDimensions(4, 4), text = SettingsTestText)

        assertEquals(true, items.single { item -> item.label == "Make wider" }.enabled)
        assertEquals(
            LauncherShellAction.ResizeHomeWidget(stack.id, GridSpan(3, 2)),
            items.single { item -> item.label == "Make wider" }.action,
        )
    }
}
