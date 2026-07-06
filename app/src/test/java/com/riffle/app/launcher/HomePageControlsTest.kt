package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GeneratedLauncherPageKind
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.WidgetItem
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
    fun emptyHomeCellMenuOnlyExposesEntryPointToPageManagement() {
        val items = homeWorkspaceContextMenuItems()

        assertEquals(
            listOf(
                ShortcutContextMenuItem(
                    "Create folder",
                    LauncherShellAction.CreateEmptyHomeFolder(label = "Folder"),
                ),
                ShortcutContextMenuItem("Widgets", LauncherShellAction.OpenWidgetPicker),
                ShortcutContextMenuItem("Settings", LauncherShellAction.OpenSettings),
                ShortcutContextMenuItem("Edit page", LauncherShellAction.EnterHomeEditMode),
                ShortcutContextMenuItem("Manage pages", LauncherShellAction.EnterHomePageOverview),
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

    @Test
    fun pageGridDimensionOptionsAreCenteredOnSelectedPageGrid() {
        assertEquals(
            listOf(
                PageGridDimensionOption("4 x 5", GridDimensions(columns = 4, rows = 5)),
                PageGridDimensionOption("3 x 5", GridDimensions(columns = 3, rows = 5)),
                PageGridDimensionOption("5 x 5", GridDimensions(columns = 5, rows = 5)),
                PageGridDimensionOption("4 x 4", GridDimensions(columns = 4, rows = 4)),
                PageGridDimensionOption("4 x 6", GridDimensions(columns = 4, rows = 6)),
            ),
            pageGridDimensionOptions(GridDimensions(columns = 4, rows = 5)),
        )
    }

    @Test
    fun pagePreviewCellsRepresentPlacedItemsAndWidgetSpans() {
        val page =
            LauncherPage(
                id = LauncherPageId("home"),
                grid = GridDimensions(columns = 3, rows = 3),
                items =
                    listOf(
                        AppShortcutItem(
                            id = LauncherItemId("app:camera"),
                            appIdentity = appIdentity("camera"),
                            label = "Camera",
                            placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                        ),
                        WidgetItem(
                            id = LauncherItemId("widget:weather"),
                            appWidgetId = HostedWidgetId(42),
                            label = "Weather",
                            placement =
                                GridPlacement(
                                    cell = GridCell(column = 1, row = 1),
                                    span = GridSpan(columns = 2, rows = 2),
                                ),
                        ),
                    ),
            )

        assertEquals(
            listOf(
                PagePreviewCell(GridCell(column = 0, row = 0), PagePreviewCellKind.APP),
                PagePreviewCell(GridCell(column = 1, row = 1), PagePreviewCellKind.WIDGET),
                PagePreviewCell(GridCell(column = 2, row = 1), PagePreviewCellKind.WIDGET),
                PagePreviewCell(GridCell(column = 1, row = 2), PagePreviewCellKind.WIDGET),
                PagePreviewCell(GridCell(column = 2, row = 2), PagePreviewCellKind.WIDGET),
            ),
            page.previewCells(),
        )
    }

    @Test
    fun pagePreviewCellsClipItemsToGridBounds() {
        val page =
            LauncherPage(
                id = LauncherPageId("home"),
                grid = GridDimensions(columns = 2, rows = 2),
                items =
                    listOf(
                        WidgetItem(
                            id = LauncherItemId("widget:weather"),
                            appWidgetId = HostedWidgetId(42),
                            label = "Weather",
                            placement =
                                GridPlacement(
                                    cell = GridCell(column = 1, row = 1),
                                    span = GridSpan(columns = 2, rows = 2),
                                ),
                        ),
                    ),
            )

        assertEquals(
            listOf(
                PagePreviewCell(GridCell(column = 1, row = 1), PagePreviewCellKind.WIDGET),
            ),
            page.previewCells(),
        )
    }

    private fun appIdentity(label: String): AppIdentity =
        AppIdentity(
            packageName = AppPackageName("com.riffle.$label"),
            activityName = AppActivityName(".MainActivity"),
        )
}
