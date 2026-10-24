package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.LibraryExitTrigger
import com.riffle.core.domain.launcher.settings.AppDrawerSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LibraryReturnTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * S9 (design 3.2, closes #1323): leaving Library (a cold start, a Home press, an app launch, Back) never switches the
 * shown mode any more, whatever the stored "After leaving Library" value says. The value is still decoded and kept.
 */
class LauncherShellLibraryReturnTest {
    @Test
    fun noTriggerSwitchesModeUnderEitherStoredSetting() {
        LibraryReturnTarget.entries.forEach { target ->
            LibraryExitTrigger.entries.forEach { trigger ->
                val repository = RecordingHomeLayoutRepository()
                val reducer =
                    LauncherHomePageEditReducer(
                        homeLayoutRepository = repository,
                        viewModeAvailability = defaultLauncherViewModeAvailability(),
                    )
                val state = libraryState(target)

                val updated = reducer.reduce(state, LauncherShellAction.LeaveLibrary(trigger))

                assertEquals("$target $trigger", state, updated)
                assertNull("$target $trigger: nothing is written", repository.saved)
            }
        }
    }

    @Test
    fun theStoredValueIsKept() {
        val state = libraryState(LibraryReturnTarget.HOME)

        assertEquals(LibraryReturnTarget.HOME, state.launcherSettings.appDrawer.afterLeavingLibrary)
    }

    @Test
    fun selectingTheReturnTargetIsStillASettingsAction() {
        assertEquals(
            LauncherActionDomain.SETTINGS,
            LauncherShellAction.SelectLibraryReturnTarget(LibraryReturnTarget.LIBRARY).launcherActionDomain(),
        )
        assertEquals(
            LauncherActionDomain.ACTIVITY,
            LauncherShellAction.LeaveLibrary(LibraryExitTrigger.BACK).launcherActionDomain(),
        )
    }

    private fun libraryState(returnTarget: LibraryReturnTarget): LauncherShellState {
        val layoutSet =
            HomeLayoutSet.fromLayout(
                HomeLayoutDefaults.standard().copy(viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY),
            )
        return LauncherShellState(
            homeLayout = layoutSet.activeLayout,
            homeLayoutSet = layoutSet,
            launcherSettings = LauncherSettings(appDrawer = AppDrawerSettings(afterLeavingLibrary = returnTarget)),
        )
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
