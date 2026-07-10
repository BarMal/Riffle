package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeWidgetRemovalTest {
    @Test
    fun findsHostedWidgetIdForSelectedPageWidget() {
        val widget =
            WidgetItem(
                id = LauncherItemId("widget:42"),
                appWidgetId = HostedWidgetId(42),
                label = "Weather",
                placement = GridPlacement(GridCell(column = 0, row = 0)),
            )
        val layout =
            HomeLayoutDefaults.standard().let { defaultLayout ->
                defaultLayout.copy(
                    pages =
                        defaultLayout.pages.map { page ->
                            page.copy(items = listOf(widget))
                        },
                )
            }

        assertEquals(
            HostedWidgetId(42),
            layout.selectedPageHostedWidgetIdForItem(LauncherItemId("widget:42")),
        )
        assertEquals(
            HostedWidgetId(42),
            layout.hostedWidgetIdForItem(LauncherItemId("widget:42")),
        )
    }

    @Test
    fun findsHostedWidgetIdForDockWidget() {
        val widget =
            WidgetItem(
                id = LauncherItemId("dock-widget:43"),
                appWidgetId = HostedWidgetId(43),
                label = "Weather",
            )
        val layout =
            HomeLayoutDefaults.standard().let { defaultLayout ->
                defaultLayout.copy(dock = defaultLayout.dock.copy(items = listOf(widget)))
            }

        assertEquals(
            HostedWidgetId(43),
            layout.dockHostedWidgetIdForItem(LauncherItemId("dock-widget:43")),
        )
        assertEquals(
            HostedWidgetId(43),
            layout.hostedWidgetIdForItem(LauncherItemId("dock-widget:43")),
        )
    }

    @Test
    fun listsHostedWidgetIdsForSelectedPage() {
        val weather =
            WidgetItem(
                id = LauncherItemId("widget:42"),
                appWidgetId = HostedWidgetId(42),
                label = "Weather",
                placement = GridPlacement(GridCell(column = 0, row = 0)),
            )
        val calendar =
            WidgetItem(
                id = LauncherItemId("widget:43"),
                appWidgetId = HostedWidgetId(43),
                label = "Calendar",
                placement = GridPlacement(GridCell(column = 1, row = 0)),
            )
        val layout =
            HomeLayoutDefaults.standard().let { defaultLayout ->
                defaultLayout.copy(
                    pages =
                        defaultLayout.pages.map { page ->
                            page.copy(items = listOf(weather, calendar))
                        },
                )
            }

        assertEquals(listOf(HostedWidgetId(42), HostedWidgetId(43)), layout.selectedPageHostedWidgetIds())
    }

    @Test
    fun ignoresMissingItems() {
        val layout = HomeLayoutDefaults.standard()

        assertNull(layout.selectedPageHostedWidgetIdForItem(LauncherItemId("missing")))
        assertNull(layout.hostedWidgetIdForItem(LauncherItemId("missing")))
    }
}
