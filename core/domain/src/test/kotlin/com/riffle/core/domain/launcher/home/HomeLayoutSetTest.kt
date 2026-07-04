package com.riffle.core.domain.launcher.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HomeLayoutSetTest {
    @Test
    fun standardSetStartsOnStandardPhoneLayout() {
        val layoutSet = HomeLayoutSet.standard()

        assertEquals(HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER), layoutSet.activeKey)
        assertEquals(LauncherViewMode.STANDARD_APP_DRAWER, layoutSet.activeLayout.viewMode)
        assertTrue(layoutSet.activeLayout.selectedPage.items.isEmpty())
    }

    @Test
    fun selectingModeUsesSeparateLayoutForThatMode() {
        val layoutSet =
            HomeLayoutSet.standard()
                .selectMode(LauncherViewMode.HOME_SCREEN_LIBRARY)

        assertEquals(HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY), layoutSet.activeKey)
        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, layoutSet.activeLayout.viewMode)
        assertEquals(LauncherViewMode.STANDARD_APP_DRAWER, layoutSet.layoutFor(standardKey).viewMode)
    }

    @Test
    fun selectingDeviceClassUsesSeparateLayoutForThatClass() {
        val layoutSet =
            HomeLayoutSet.standard()
                .selectDeviceClass(HomeLayoutDeviceClass.FOLDABLE)

        assertEquals(
            HomeLayoutKey(
                viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                deviceClass = HomeLayoutDeviceClass.FOLDABLE,
            ),
            layoutSet.activeKey,
        )
        assertEquals(HomeLayoutDeviceClass.PHONE, standardKey.deviceClass)
        assertEquals(LauncherViewMode.STANDARD_APP_DRAWER, layoutSet.activeLayout.viewMode)
        assertEquals(GridDimensions(columns = 5, rows = 6), layoutSet.activeLayout.settings.grid.dimensions)
        assertEquals(6, layoutSet.activeLayout.dock.capacity)
    }

    @Test
    fun selectingDeviceClassUsesPreferredModeForThatClass() {
        val layoutSet =
            HomeLayoutSet.standard()
                .withPreferredMode(
                    deviceClass = HomeLayoutDeviceClass.FOLDABLE,
                    mode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                )
                .selectDeviceClass(HomeLayoutDeviceClass.FOLDABLE)

        assertEquals(
            HomeLayoutKey(
                viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                deviceClass = HomeLayoutDeviceClass.FOLDABLE,
            ),
            layoutSet.activeKey,
        )
        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, layoutSet.activeLayout.viewMode)
    }

    @Test
    fun updatingActiveLayoutDoesNotMutateOtherModeLayouts() {
        val libraryPage =
            HomeLayoutDefaults.standard()
                .selectedPage
                .copy(id = LauncherPageId("library-home"))
        val layoutSet =
            HomeLayoutSet.standard()
                .selectMode(LauncherViewMode.HOME_SCREEN_LIBRARY)
                .withActiveLayout(
                    HomeLayoutDefaults.standard().copy(
                        viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                        pages = listOf(libraryPage),
                        selectedPageId = libraryPage.id,
                    ),
                )

        assertEquals(LauncherPageId("home"), layoutSet.layoutFor(standardKey).selectedPageId)
        assertEquals(LauncherPageId("library-home"), layoutSet.activeLayout.selectedPageId)
    }

    @Test
    fun updatingSpecificLayoutDoesNotChangeActiveKey() {
        val foldableKey =
            HomeLayoutKey(
                viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                deviceClass = HomeLayoutDeviceClass.FOLDABLE,
            )
        val foldablePage =
            HomeLayoutDefaults.standard()
                .selectedPage
                .copy(id = LauncherPageId("foldable-home"))
        val layoutSet =
            HomeLayoutSet.standard()
                .withLayout(
                    key = foldableKey,
                    layout =
                        HomeLayoutDefaults.standard().copy(
                            pages = listOf(foldablePage),
                            selectedPageId = foldablePage.id,
                        ),
                )

        assertEquals(standardKey, layoutSet.activeKey)
        assertEquals(LauncherPageId("home"), layoutSet.activeLayout.selectedPageId)
        assertEquals(LauncherPageId("foldable-home"), layoutSet.layoutFor(foldableKey).selectedPageId)
    }

    private val standardKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER)
}
