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

    private val standardKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER)
}
