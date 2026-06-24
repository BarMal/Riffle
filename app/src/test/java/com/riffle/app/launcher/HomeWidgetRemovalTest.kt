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
    }

    @Test
    fun ignoresMissingItems() {
        val layout = HomeLayoutDefaults.standard()

        assertNull(layout.selectedPageHostedWidgetIdForItem(LauncherItemId("missing")))
    }
}
