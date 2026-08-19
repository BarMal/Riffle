package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DockSwipeUpGestureTest {
    @Test
    fun exitAdaptiveStageMapsToLeavingCardsWhichPicksItsOwnDestination() {
        assertEquals(
            LauncherShellAction.ExitAdaptiveStage,
            LauncherGestureAction.EXIT_ADAPTIVE_STAGE.toDockSwipeUpShellAction(LauncherViewMode.CARD_INTERFACE),
        )
    }

    @Test
    fun exitAdaptiveStageDoesNothingFromAModeThereIsNothingToExit() {
        // A swipe on the Library dock is not a request to be moved to Standard, which is a mode of
        // its own with a dock of its own.
        assertNull(
            LauncherGestureAction.EXIT_ADAPTIVE_STAGE.toDockSwipeUpShellAction(
                LauncherViewMode.HOME_SCREEN_LIBRARY,
            ),
        )
        assertNull(
            LauncherGestureAction.EXIT_ADAPTIVE_STAGE.toDockSwipeUpShellAction(
                LauncherViewMode.STANDARD_APP_DRAWER,
            ),
        )
    }

    @Test
    fun openAppDrawerMapsToOpeningTheAppDrawerFromEveryMode() {
        LauncherViewMode.entries.forEach { viewMode ->
            assertEquals(
                LauncherShellAction.OpenAppDrawer,
                LauncherGestureAction.OPEN_APP_DRAWER.toDockSwipeUpShellAction(viewMode),
            )
        }
    }

    @Test
    fun noneMapsToNoAction() {
        assertNull(LauncherGestureAction.NONE.toDockSwipeUpShellAction(LauncherViewMode.CARD_INTERFACE))
    }

    @Test
    fun actionsOutsideTheAllowedSetMapToNoAction() {
        assertNull(LauncherGestureAction.OPEN_SEARCH.toDockSwipeUpShellAction(LauncherViewMode.CARD_INTERFACE))
        assertNull(LauncherGestureAction.ENTER_ADAPTIVE_STAGE.toDockSwipeUpShellAction(LauncherViewMode.CARD_INTERFACE))
    }
}
