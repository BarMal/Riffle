package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.containsHomeApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockPullPreviewTest {
    private val app =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.sample"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = "Sample",
        )

    @Test
    fun theIncomingLibraryIsDrawnFromItsOwnLayoutWithTheAppsInIt() {
        val cards = HomeLayoutDefaults.standard().copy(viewMode = LauncherViewMode.CARD_INTERFACE)
        val state = LauncherShellState(homeLayout = cards, installedApps = listOf(app))

        val preview = state.dockPullPreviewFor(LauncherViewMode.HOME_SCREEN_LIBRARY)

        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, preview.homeLayout.viewMode)
        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, preview.homeLayoutSet.activeKey.viewMode)
        assertTrue("Library shows the installed apps", preview.homeLayout.containsHomeApp(app.identity))
        // The dock is shared: the preview carries the same one.
        assertEquals(state.homeLayout.dock, preview.homeLayout.dock)
    }

    @Test
    fun thePreviewLeavesTheShellStateAlone() {
        val cards = HomeLayoutDefaults.standard().copy(viewMode = LauncherViewMode.CARD_INTERFACE)
        val state = LauncherShellState(homeLayout = cards, installedApps = listOf(app))

        state.dockPullPreviewFor(LauncherViewMode.HOME_SCREEN_LIBRARY)

        assertEquals(LauncherViewMode.CARD_INTERFACE, state.homeLayout.viewMode)
        assertEquals(LauncherViewMode.CARD_INTERFACE, state.homeLayoutSet.activeKey.viewMode)
    }
}
