package com.riffle.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class HomePageControlsTest {
    @Test
    fun pageManagementMenuDisablesUnavailablePageMovement() {
        val items = pageManagementMenuItems(pageCount = 1, selectedPageIndex = 0)

        assertEquals(
            listOf(
                ShortcutContextMenuItem("Add page", LauncherShellAction.AddHomePage),
                ShortcutContextMenuItem("Manage pages", LauncherShellAction.EnterHomePageOverview),
                ShortcutContextMenuItem("Duplicate page", LauncherShellAction.DuplicateSelectedHomePage),
                ShortcutContextMenuItem(
                    label = "Move page left",
                    action = LauncherShellAction.MoveSelectedHomePageLeft,
                    enabled = false,
                ),
                ShortcutContextMenuItem(
                    label = "Move page right",
                    action = LauncherShellAction.MoveSelectedHomePageRight,
                    enabled = false,
                ),
                ShortcutContextMenuItem(
                    label = "Delete page",
                    action = LauncherShellAction.DeleteSelectedHomePage,
                    enabled = false,
                ),
            ),
            items,
        )
    }

    @Test
    fun pageManagementMenuCanOmitOverviewAction() {
        val items =
            pageManagementMenuItems(
                pageCount = 3,
                selectedPageIndex = 1,
                includeOverview = false,
            )

        assertEquals(
            listOf(
                ShortcutContextMenuItem("Add page", LauncherShellAction.AddHomePage),
                ShortcutContextMenuItem("Duplicate page", LauncherShellAction.DuplicateSelectedHomePage),
                ShortcutContextMenuItem("Move page left", LauncherShellAction.MoveSelectedHomePageLeft),
                ShortcutContextMenuItem("Move page right", LauncherShellAction.MoveSelectedHomePageRight),
                ShortcutContextMenuItem("Delete page", LauncherShellAction.DeleteSelectedHomePage),
            ),
            items,
        )
    }
}
