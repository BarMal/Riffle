package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.LauncherPageType
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

    @Test
    fun emptyHomeCellMenuExposesHomeManagementActionsWithoutOverviewMode() {
        val items = homeWorkspaceContextMenuItems(pageCount = 2, selectedPageIndex = 0)

        assertEquals(
            listOf(
                ShortcutContextMenuItem(
                    "Create folder",
                    LauncherShellAction.CreateEmptyHomeFolder(label = "Folder"),
                ),
                ShortcutContextMenuItem("Widgets", LauncherShellAction.OpenWidgetPicker),
                ShortcutContextMenuItem("Settings", LauncherShellAction.OpenSettings),
                ShortcutContextMenuItem(
                    label = "Previous page",
                    action = LauncherShellAction.SelectPreviousHomePage,
                    enabled = false,
                ),
                ShortcutContextMenuItem("Next page", LauncherShellAction.SelectNextHomePage),
                ShortcutContextMenuItem("Add page", LauncherShellAction.AddHomePage),
                ShortcutContextMenuItem("Duplicate page", LauncherShellAction.DuplicateSelectedHomePage),
                ShortcutContextMenuItem(
                    label = "Move page left",
                    action = LauncherShellAction.MoveSelectedHomePageLeft,
                    enabled = false,
                ),
                ShortcutContextMenuItem("Move page right", LauncherShellAction.MoveSelectedHomePageRight),
                ShortcutContextMenuItem("Delete page", LauncherShellAction.DeleteSelectedHomePage),
            ),
            items,
        )
    }

    @Test
    fun pageTypeOptionsExposeClassicLibraryAndGeneratedPages() {
        assertEquals(
            listOf(
                PageTypeOption("Classic", LauncherPageType.Home),
                PageTypeOption("All apps", LauncherPageType.AllApps),
                PageTypeOption("Today", LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY)),
                PageTypeOption("Category", LauncherPageType.Generated(GeneratedLauncherPageKind.CATEGORY)),
                PageTypeOption("App", LauncherPageType.Generated(GeneratedLauncherPageKind.APP)),
                PageTypeOption("Work", LauncherPageType.Generated(GeneratedLauncherPageKind.WORK)),
                PageTypeOption("Personal", LauncherPageType.Generated(GeneratedLauncherPageKind.PERSONAL)),
                PageTypeOption("Favourites", LauncherPageType.Generated(GeneratedLauncherPageKind.FAVOURITES)),
                PageTypeOption("Frequent", LauncherPageType.Generated(GeneratedLauncherPageKind.FREQUENTLY_USED)),
                PageTypeOption("Cards", LauncherPageType.Generated(GeneratedLauncherPageKind.NOTIFICATION_CARDS)),
            ),
            pageTypeOptions,
        )
    }

    @Test
    fun pageTypeLabelsAreUserFacing() {
        assertEquals("Classic", LauncherPageType.Home.pageOverviewTypeLabel)
        assertEquals("All apps", LauncherPageType.AllApps.pageOverviewTypeLabel)
        assertEquals(
            "Today",
            LauncherPageType.Generated(GeneratedLauncherPageKind.TODAY).pageOverviewTypeLabel,
        )
    }
}
