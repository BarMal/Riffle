package com.riffle.app.launcher.library

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.library.LibraryHit
import com.riffle.core.domain.launcher.library.LibraryView
import com.riffle.core.domain.launcher.settings.AppDrawerSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryStateTest {
    private fun app(
        label: String,
        profile: AppProfile = AppProfile.personal(),
    ) = InstalledApp(AppIdentity(AppPackageName("com.$label"), AppActivityName(".Main"), profile), label)

    @Test
    fun readsTheViewOfTheActiveDeviceClassAndListsAppsAZ() {
        val state =
            LauncherShellState(
                installedApps = listOf(app("Maps"), app("camera"), app("Browser")),
                launcherSettings =
                    LauncherSettings(
                        appDrawer =
                            AppDrawerSettings(
                                libraryViews =
                                    mapOf(
                                        com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass.PHONE to
                                            LibraryView.Grid,
                                    ),
                            ),
                    ),
            ).libraryState()

        assertEquals(LibraryView.Grid, state.view)
        assertEquals(listOf("Browser", "camera", "Maps"), state.apps.map { it.label })
    }

    @Test
    fun theProfileFilterNarrowsTheAppsButNotTheChipCounts() {
        val state =
            LauncherShellState(
                installedApps = listOf(app("Mail"), app("Mail", AppProfile.work()), app("Maps")),
                appDrawerProfileFilter = AppDrawerProfileFilter.WORK,
            ).libraryState()

        assertEquals(1, state.apps.size)
        assertEquals(3, state.installedApps.size)
    }

    @Test
    fun searchResultsComeFromTheSearchQueryAppsFirst() {
        val state =
            LauncherShellState(
                installedApps = listOf(app("Camera")),
                searchQuery = "ca",
                searchResults = listOf(app("Camera")),
            ).libraryState()

        assertTrue(state.isSearching)
        assertTrue(state.hits.single() is LibraryHit.App)
        assertFalse(LauncherShellState().libraryState().isSearching)
    }

    @Test
    fun theSearchDestinationFocusesTheFieldAndOverStreamIsRemembered() {
        val focused =
            LauncherShellState(destination = ShellDestination.SEARCH, libraryOverStream = true).libraryState()

        assertTrue(focused.focusSearch)
        assertTrue(focused.overStream)
        assertFalse(LauncherShellState(destination = ShellDestination.APP_DRAWER).libraryState().focusSearch)
    }
}
