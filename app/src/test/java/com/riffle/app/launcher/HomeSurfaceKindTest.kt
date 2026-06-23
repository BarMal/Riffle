package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSurfaceKindTest {
    @Test
    fun standardAndLibraryModesUseGridHomeSurface() {
        assertEquals(HomeSurfaceKind.GRID, LauncherViewMode.STANDARD_APP_DRAWER.homeSurfaceKind())
        assertEquals(HomeSurfaceKind.GRID, LauncherViewMode.HOME_SCREEN_LIBRARY.homeSurfaceKind())
    }

    @Test
    fun cardModeUsesCardHomeSurface() {
        assertEquals(HomeSurfaceKind.CARDS, LauncherViewMode.CARD_INTERFACE.homeSurfaceKind())
    }
}
