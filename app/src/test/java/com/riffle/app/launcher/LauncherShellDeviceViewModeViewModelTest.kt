package com.riffle.app.launcher

import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellDeviceViewModeViewModelTest {
    @Test
    fun settingsViewModeEditsStayIndependentPerDeviceClass() {
        val phoneStandardKey =
            HomeLayoutKey(
                viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                deviceClass = HomeLayoutDeviceClass.PHONE,
            )
        val foldableLibraryKey =
            HomeLayoutKey(
                viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                deviceClass = HomeLayoutDeviceClass.FOLDABLE,
            )
        val repository =
            FakeHomeLayoutRepository(
                savedLayout = HomeLayoutDefaults.standard(HomeLayoutDeviceClass.PHONE),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        viewModeAvailability =
                            LauncherViewModeAvailability(
                                enabledExperimentalModesByDeviceClass =
                                    mapOf(
                                        HomeLayoutDeviceClass.FOLDABLE to
                                            setOf(LauncherViewMode.HOME_SCREEN_LIBRARY),
                                    ),
                            ),
                    ),
            )

        viewModel.onHomePageEdited(
            LauncherShellAction.SelectHomeLayoutDeviceClass(
                deviceClass = HomeLayoutDeviceClass.PHONE,
                availableDeviceClasses = setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE),
            ),
        )
        viewModel.onNavigationActionSelected(ShellNavigationAction.OpenSettings)
        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectSettingsLayoutDeviceClass(HomeLayoutDeviceClass.PHONE),
        )
        viewModel.onHomePageEdited(LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.STANDARD_APP_DRAWER))
        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectSettingsLayoutDeviceClass(HomeLayoutDeviceClass.FOLDABLE),
        )
        assertEquals(HomeLayoutDeviceClass.FOLDABLE, viewModel.state.value.settingsLayoutDeviceClass)
        assertEquals(phoneStandardKey, viewModel.state.value.homeLayoutSet.activeKey)

        viewModel.onHomePageEdited(LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.HOME_SCREEN_LIBRARY))

        val savedLayoutSet = checkNotNull(repository.savedLayoutSet)
        assertEquals(phoneStandardKey, savedLayoutSet.activeKey)
        assertEquals(
            LauncherViewMode.STANDARD_APP_DRAWER,
            savedLayoutSet.preferredModesByDeviceClass[HomeLayoutDeviceClass.PHONE],
        )
        assertEquals(
            LauncherViewMode.HOME_SCREEN_LIBRARY,
            savedLayoutSet.preferredModesByDeviceClass[HomeLayoutDeviceClass.FOLDABLE],
        )
        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, savedLayoutSet.layoutFor(foldableLibraryKey).viewMode)

        viewModel.onHomePageEdited(
            LauncherShellAction.SelectHomeLayoutDeviceClass(
                deviceClass = HomeLayoutDeviceClass.FOLDABLE,
                availableDeviceClasses = setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE),
            ),
        )

        assertEquals(foldableLibraryKey, viewModel.state.value.homeLayoutSet.activeKey)
        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, viewModel.state.value.homeLayout.viewMode)
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeHomeLayoutRepository(
        savedLayout: HomeLayout,
    ) : HomeLayoutRepository {
        var savedLayoutSet: HomeLayoutSet? = HomeLayoutSet.fromLayout(savedLayout)

        override fun loadHomeLayout(): HomeLayout? = savedLayoutSet?.activeLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayoutSet =
                savedLayoutSet
                    ?.withActiveLayout(layout)
                    ?: HomeLayoutSet.fromLayout(layout)
        }

        override fun loadHomeLayoutSet(): HomeLayoutSet? = savedLayoutSet

        override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
            savedLayoutSet = layoutSet
        }
    }
}
