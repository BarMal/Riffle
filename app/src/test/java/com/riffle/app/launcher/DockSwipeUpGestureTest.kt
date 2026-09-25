package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DockSwipeUpGestureTest {
    @Test
    fun previousModeMapsToAStepBackAlongTheModeRingWhichPicksItsOwnDestination() {
        // Leaving Cards is no longer a special case (#1225): the swipe means "previous mode" in
        // every mode, and the layout set's ring decides which mode that is.
        assertEquals(
            LauncherShellAction.SelectPreviousLauncherViewMode,
            LauncherGestureAction.PREVIOUS_MODE.toDockSwipeUpShellAction(),
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
        assertNull(LauncherGestureAction.NEXT_MODE.toDockSwipeUpShellAction())
    }
}
