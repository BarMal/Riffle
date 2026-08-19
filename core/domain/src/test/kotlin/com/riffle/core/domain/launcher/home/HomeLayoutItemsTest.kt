package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeLayoutItemsTest {
    @Test
    fun readsItemsFromThePagesTheDockAndThePanel() {
        val layout = layout(pageItem = shortcut("page"), dockItem = shortcut("dock"), panelItem = shortcut("panel"))

        assertEquals(
            listOf(LauncherItemId("page"), LauncherItemId("dock"), LauncherItemId("panel")),
            layout.allItems().map { item -> item.id },
        )
    }

    @Test
    fun aLayoutWithNoPanelReadsJustItsPagesAndDock() {
        val layout = layout(pageItem = shortcut("page"), dockItem = shortcut("dock"), panelItem = null)

        assertEquals(
            listOf(LauncherItemId("page"), LauncherItemId("dock")),
            layout.allItems().map { item -> item.id },
        )
    }

    @Test
    fun aWidgetOnThePanelCountsAsHosted() {
        // Otherwise the host ID looks unreferenced and is collected out from under the panel.
        val layout = layout(pageItem = null, dockItem = null, panelItem = widget(hostedWidgetId = 42))

        assertTrue(layout.hostsWidget(HostedWidgetId(42)))
    }

    @Test
    fun aWidgetNoLayoutHoldsIsNotHosted() {
        val layout = layout(pageItem = null, dockItem = null, panelItem = widget(hostedWidgetId = 42))

        assertFalse(layout.hostsWidget(HostedWidgetId(43)))
    }

    private fun layout(
        pageItem: LauncherItem?,
        dockItem: LauncherItem?,
        panelItem: LauncherItem?,
    ): HomeLayout =
        HomeLayoutDefaults.standard().let { defaults ->
            defaults.copy(
                pages = listOf(defaults.selectedPage.copy(items = listOfNotNull(pageItem))),
                dock =
                    defaults.dock.copy(
                        items = listOfNotNull(dockItem),
                        panel =
                            panelItem?.let { item ->
                                LauncherPage(
                                    id = LauncherPageId("dock-panel"),
                                    grid = GridDimensions(columns = 4, rows = 2),
                                    items = listOf(item),
                                )
                            },
                    ),
            )
        }

    private fun shortcut(id: String): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$id"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = id,
        )

    private fun widget(hostedWidgetId: Int): WidgetItem =
        WidgetItem(
            id = LauncherItemId("widget:$hostedWidgetId"),
            appWidgetId = HostedWidgetId(hostedWidgetId),
            label = "Clock",
        )
}
