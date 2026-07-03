package com.riffle.app.launcher

import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellActiveLayoutGridTest {
    @Test
    fun activeLauncherGridFollowsDeviceClassNotSettingsTab() {
        val phoneGrid = GridDimensions(columns = 3, rows = 4)
        val foldableGrid = GridDimensions(columns = 6, rows = 7)
        val phoneKey =
            HomeLayoutKey(
                viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                deviceClass = HomeLayoutDeviceClass.PHONE,
            )
        val foldableKey =
            HomeLayoutKey(
                viewMode = LauncherViewMode.STANDARD_APP_DRAWER,
                deviceClass = HomeLayoutDeviceClass.FOLDABLE,
            )
        val repository =
            FakeHomeLayoutRepository(
                layoutSet =
                    HomeLayoutSet(
                        activeKey = phoneKey,
                        layouts =
                            mapOf(
                                phoneKey to
                                    HomeLayoutDefaults
                                        .standard(HomeLayoutDeviceClass.PHONE)
                                        .withGrid(phoneGrid),
                                foldableKey to
                                    HomeLayoutDefaults
                                        .standard(HomeLayoutDeviceClass.FOLDABLE)
                                        .withGrid(foldableGrid),
                            ),
                    ),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomePageEdited(
            LauncherShellAction.SelectHomeLayoutDeviceClass(
                deviceClass = HomeLayoutDeviceClass.PHONE,
                availableDeviceClasses = setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE),
            ),
        )
        viewModel.onNavigationActionSelected(ShellNavigationAction.OpenSettings)
        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectSettingsLayoutDeviceClass(HomeLayoutDeviceClass.FOLDABLE),
        )

        assertEquals(phoneGrid, viewModel.state.value.homeLayout.settings.grid.dimensions)

        viewModel.onHomePageEdited(
            LauncherShellAction.SelectHomeLayoutDeviceClass(
                deviceClass = HomeLayoutDeviceClass.FOLDABLE,
                availableDeviceClasses = setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE),
            ),
        )

        assertEquals(foldableGrid, viewModel.state.value.homeLayout.settings.grid.dimensions)
        assertEquals(HomeLayoutDeviceClass.FOLDABLE, viewModel.state.value.settingsLayoutDeviceClass)
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeHomeLayoutRepository(
        private var layoutSet: HomeLayoutSet,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout = layoutSet.activeLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            layoutSet = layoutSet.withActiveLayout(layout)
        }

        override fun loadHomeLayoutSet(): HomeLayoutSet = layoutSet

        override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
            this.layoutSet = layoutSet
        }
    }

    private fun HomeLayout.withGrid(dimensions: GridDimensions): HomeLayout =
        copy(
            pages = pages.map { page -> page.copy(grid = dimensions) },
            settings =
                settings.copy(
                    grid = settings.grid.copy(dimensions = dimensions),
                ),
        )
}
