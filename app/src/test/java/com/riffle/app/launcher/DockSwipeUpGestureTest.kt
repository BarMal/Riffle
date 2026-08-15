package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DockSwipeUpGestureTest {
    @Test
    fun exitAdaptiveStageMapsToSwitchingBackToStandardHome() {
        assertEquals(
            LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.STANDARD_APP_DRAWER),
            LauncherGestureAction.EXIT_ADAPTIVE_STAGE.toDockSwipeUpShellAction(),
        )
    }

    @Test
    fun openAppDrawerMapsToOpeningTheAppDrawer() {
        assertEquals(
            LauncherShellAction.OpenAppDrawer,
            LauncherGestureAction.OPEN_APP_DRAWER.toDockSwipeUpShellAction(),
        )
    }

    @Test
    fun noneMapsToNoAction() {
        assertNull(LauncherGestureAction.NONE.toDockSwipeUpShellAction())
    }

    @Test
    fun actionsOutsideTheAllowedSetMapToNoAction() {
        assertNull(LauncherGestureAction.OPEN_SEARCH.toDockSwipeUpShellAction())
        assertNull(LauncherGestureAction.ENTER_ADAPTIVE_STAGE.toDockSwipeUpShellAction())
    }
}
