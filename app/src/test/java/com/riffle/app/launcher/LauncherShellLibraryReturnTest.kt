package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShellLibraryReturnTest {
    @Test
    fun everyTriggerLeavesLibraryForHomeByDefaultAndSavesTheSwitch() {
        LibraryExitTrigger.entries.forEach { trigger ->
            val repository = RecordingHomeLayoutRepository()
            val reducer =
                LauncherHomePageEditReducer(
                    homeLayoutRepository = repository,
                    viewModeAvailability = defaultLauncherViewModeAvailability(),
                )

            val updated = reducer.reduce(libraryState(), LauncherShellAction.LeaveLibrary(trigger))

            assertEquals("$trigger", LauncherViewMode.CARD_INTERFACE, updated.homeLayoutSet.activeKey.viewMode)
            assertEquals("$trigger", LauncherViewMode.CARD_INTERFACE, updated.homeLayout.viewMode)
            assertEquals("$trigger", LauncherViewMode.CARD_INTERFACE, repository.saved?.activeKey?.viewMode)
        }
    }

    @Test
    fun noTriggerLeavesLibraryWhenTheSettingKeepsLibrary() {
        LibraryExitTrigger.entries.forEach { trigger ->
            val state = libraryState(LibraryReturnTarget.LIBRARY)
            val reducer =
                LauncherHomePageEditReducer(
                    homeLayoutRepository = RecordingHomeLayoutRepository(),
                    viewModeAvailability = defaultLauncherViewModeAvailability(),
                )

            assertEquals("$trigger", state, reducer.reduce(state, LauncherShellAction.LeaveLibrary(trigger)))
        }
    }

    @Test
    fun backLeavesLibraryOnlyFromBrowsingLibraryOnHomeWithTheDefaultSetting() {
        assertTrue(libraryState().backLeavesLibrary)
        assertFalse(libraryState(LibraryReturnTarget.LIBRARY).backLeavesLibrary)
        assertFalse(libraryState().copy(destination = ShellDestination.SETTINGS).backLeavesLibrary)
        assertFalse(state(LauncherViewMode.CARD_INTERFACE, LibraryReturnTarget.HOME).backLeavesLibrary)
    }

    @Test
    fun selectingTheReturnTargetIsASettingsAction() {
        assertEquals(
            LauncherActionDomain.SETTINGS,
            LauncherShellAction.SelectLibraryReturnTarget(LibraryReturnTarget.LIBRARY).launcherActionDomain(),
        )
        assertEquals(
            LauncherActionDomain.ACTIVITY,
            LauncherShellAction.LeaveLibrary(LibraryExitTrigger.BACK).launcherActionDomain(),
        )
    }

    private fun libraryState(returnTarget: LibraryReturnTarget = LibraryReturnTarget.HOME): LauncherShellState =
        state(LauncherViewMode.HOME_SCREEN_LIBRARY, returnTarget)

    private fun state(
        mode: LauncherViewMode,
        returnTarget: LibraryReturnTarget,
    ): LauncherShellState {
        val layoutSet = HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard().copy(viewMode = mode))
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
