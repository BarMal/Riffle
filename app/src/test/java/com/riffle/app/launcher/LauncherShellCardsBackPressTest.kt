package com.riffle.app.launcher

import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShellCardsBackPressTest {
    @Test
    fun exitsCardsModeWhenHomeIsShowingCards() {
        assertTrue(
            shouldExitCardsModeOnBackPress(
                destination = ShellDestination.HOME,
                viewMode = LauncherViewMode.CARD_INTERFACE,
            ),
        )
    }

    @Test
    fun doesNothingWhenHomeIsAlreadyShowingAGridViewMode() {
        assertFalse(
            shouldExitCardsModeOnBackPress(
                destination = ShellDestination.HOME,
                viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
            ),
        )
        assertFalse(
            shouldExitCardsModeOnBackPress(
                destination = ShellDestination.HOME,
                viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
            ),
        )
    }

    @Test
    fun doesNotFireWhenAnotherDestinationAlreadyOwnsBack() {
        // The app drawer/search/settings/notifications BackHandler already returns to Home in
        // this case; the Cards-exit handler must not also claim the same back press.
        assertFalse(
            shouldExitCardsModeOnBackPress(
                destination = ShellDestination.APP_DRAWER,
                viewMode = LauncherViewMode.CARD_INTERFACE,
            ),
        )
        assertFalse(
            shouldExitCardsModeOnBackPress(
                destination = ShellDestination.SEARCH,
                viewMode = LauncherViewMode.CARD_INTERFACE,
            ),
        )
    }
}
