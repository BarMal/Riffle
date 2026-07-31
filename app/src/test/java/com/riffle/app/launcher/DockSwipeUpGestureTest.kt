package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DockSwipeUpGestureTest {
    @Test
    fun exitTimescapeMapsToSwitchingBackToStandardHome() {
        assertEquals(
            LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.STANDARD_APP_DRAWER),
            LauncherGestureAction.EXIT_TIMESCAPE.toDockSwipeUpShellAction(),
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
        assertNull(LauncherGestureAction.ENTER_TIMESCAPE.toDockSwipeUpShellAction())
    }

    @Test
    fun triggersOnlyForDominantUpwardDragPastThreshold() {
        assertTrue(dockSwipeUpGestureTriggered(horizontalDragPx = 0f, verticalDragPx = -90f))
        assertFalse(dockSwipeUpGestureTriggered(horizontalDragPx = 0f, verticalDragPx = -40f))
        assertFalse(dockSwipeUpGestureTriggered(horizontalDragPx = 0f, verticalDragPx = 90f))
        assertFalse(dockSwipeUpGestureTriggered(horizontalDragPx = 120f, verticalDragPx = -90f))
    }
}
