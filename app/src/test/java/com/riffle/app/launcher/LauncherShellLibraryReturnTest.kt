package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.LibraryExitTrigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Leaving Library (a cold start, a Home press, an app launch, Back) never switches the shown mode, and the "after
 * leaving Library" setting that once chose it is gone (P9): it only ever decoded and was kept.
 */
class LauncherShellLibraryReturnTest {
    @Test
    fun noTriggerSwitchesMode() {
        LibraryExitTrigger.entries.forEach { trigger ->
            val repository = RecordingHomeLayoutRepository()
            val reducer =
                LauncherHomePageEditReducer(
                    homeLayoutRepository = repository,
                    viewModeAvailability = defaultLauncherViewModeAvailability(),
                )
            val state = libraryState()

            val updated = reducer.reduce(state, LauncherShellAction.LeaveLibrary(trigger))

            assertEquals("$trigger", state, updated)
            assertNull("$trigger: nothing is written", repository.saved)
        }
    }

    @Test
    fun leavingLibraryIsAnActivityAction() {
        assertEquals(
            LauncherActionDomain.ACTIVITY,
            LauncherShellAction.LeaveLibrary(LibraryExitTrigger.BACK).launcherActionDomain(),
        )
    }

    private fun libraryState(): LauncherShellState {
        val layoutSet =
            HomeLayoutSet.fromLayout(
                HomeLayoutDefaults.standard().copy(viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY),
            )
        return LauncherShellState(homeLayout = layoutSet.activeLayout, homeLayoutSet = layoutSet)
    }

    private class RecordingHomeLayoutRepository : HomeLayoutRepository {
        var saved: HomeLayoutSet? = null

        override fun loadHomeLayout(): HomeLayout? = saved?.activeLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            saved = HomeLayoutSet.fromLayout(layout)
        }

        override fun loadHomeLayoutSet(): HomeLayoutSet? = saved

        override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
            saved = layoutSet
        }
    }
}
