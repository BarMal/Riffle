package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HomeLayoutDefaultsTest {
    @Test
    fun standardLayoutNamesTheStandardAppDrawerModeExplicitly() {
        val layout = HomeLayoutDefaults.standard()

        assertEquals(LauncherViewMode.STANDARD_APP_DRAWER, layout.viewMode)
    }

    @Test
    fun standardLayoutStartsWithOneEmptyHomePage() {
        val layout = HomeLayoutDefaults.standard()

        assertEquals(1, layout.pages.size)
        assertSame(layout.pages.first(), layout.selectedPage)
        assertEquals(0, layout.selectedPageIndex)
        assertEquals(LauncherPageType.Home, layout.selectedPage.type)
        assertEquals(HomeEditMode.Browsing, layout.editMode)
        assertTrue(layout.selectedPage.items.isEmpty())
    }

    @Test
    fun updatesSelectedPageWithoutChangingSelectionOrOtherPages() {
        val defaultLayout = HomeLayoutDefaults.standard()
        val homePage = defaultLayout.selectedPage
        val widgetPage = homePage.copy(id = LauncherPageId("widgets"))
        val layout =
            defaultLayout.copy(
                pages = listOf(homePage, widgetPage),
                selectedPageId = widgetPage.id,
            )
        val updatedWidgetPage =
            widgetPage.copy(
                items =
                    listOf(
                        AppShortcutItem(
                            id = LauncherItemId("camera"),
                            appIdentity =
                                AppIdentity(
                                    packageName = AppPackageName("camera"),
                                    activityName = AppActivityName("MainActivity"),
                                ),
                            label = "Camera",
                        ),
                    ),
            )

        val updated = layout.withUpdatedSelectedPage(updatedWidgetPage)

        assertEquals(widgetPage.id, updated.selectedPageId)
        assertSame(homePage, updated.pages.first())
        assertEquals(updatedWidgetPage, updated.pages.last())
        assertEquals(updatedWidgetPage, updated.selectedPage)
    }

    @Test
    fun standardHomePageUsesConventionalPhoneGrid() {
        val layout = HomeLayoutDefaults.standard()

        assertEquals(GridDimensions(columns = 4, rows = 5), layout.selectedPage.grid)
        assertEquals(GridDimensions(columns = 4, rows = 5), layout.settings.grid.dimensions)
        assertEquals(20, layout.selectedPage.grid.cellCount)
    }

    @Test
    fun standardDockHasFiveEmptySlots() {
        val layout = HomeLayoutDefaults.standard()

        assertEquals(5, layout.dock.capacity)
        assertEquals(44, layout.dock.iconSizeDp)
        assertEquals(8, layout.dock.itemSpacingDp)
        assertEquals(5, layout.dock.availableSlots)
        assertTrue(layout.dock.items.isEmpty())
    }

    @Test
    fun foldableLayoutStartsWithLargerGridAndDock() {
        val layout = HomeLayoutDefaults.standard(HomeLayoutDeviceClass.FOLDABLE)

        assertEquals(GridDimensions(columns = 5, rows = 6), layout.selectedPage.grid)
        assertEquals(GridDimensions(columns = 5, rows = 6), layout.settings.grid.dimensions)
        assertEquals(6, layout.dock.capacity)
        assertEquals(48, layout.dock.iconSizeDp)
        assertEquals(10, layout.dock.itemSpacingDp)
    }

    @Test
    fun tabletLayoutStartsWithLargestGridAndDock() {
        val layout = HomeLayoutDefaults.standard(HomeLayoutDeviceClass.TABLET)

        assertEquals(GridDimensions(columns = 6, rows = 6), layout.selectedPage.grid)
        assertEquals(GridDimensions(columns = 6, rows = 6), layout.settings.grid.dimensions)
        assertEquals(7, layout.dock.capacity)
        assertEquals(52, layout.dock.iconSizeDp)
        assertEquals(12, layout.dock.itemSpacingDp)
    }
}
