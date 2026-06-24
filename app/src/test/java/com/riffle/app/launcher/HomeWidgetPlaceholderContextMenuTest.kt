package com.riffle.app.launcher

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
            )

        val items = widgetPlaceholderContextMenuItems(widget)

        assertEquals(
            listOf(
                ShortcutContextMenuItem(
                    label = "Remove from home",
                    action = LauncherShellAction.RemoveHomeShortcut(widget.id),
                ),
            ),
            items,
        )
    }
}
