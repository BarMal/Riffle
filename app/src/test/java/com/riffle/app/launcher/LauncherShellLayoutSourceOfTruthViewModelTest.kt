package com.riffle.app.launcher

import com.riffle.core.domain.launcher.ShellDestination
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

/**
 * The in-memory layout set is the source of truth (#1198): reducers never read the repository back,
 * mode actions off Settings target the device being held, and a recreated view model starts from
 * whatever the repository was last told.
 */
class LauncherShellLayoutSourceOfTruthViewModelTest {
    private val phoneStandardKey =
        HomeLayoutKey(viewMode = LauncherViewMode.STANDARD_APP_DRAWER, deviceClass = HomeLayoutDeviceClass.PHONE)
    private val phoneLibraryKey =
        HomeLayoutKey(viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY, deviceClass = HomeLayoutDeviceClass.PHONE)
    private val libraryEverywhere =
        LauncherShellPlatformDependencies(
            viewModeAvailability =
                LauncherViewModeAvailability(
                    enabledExperimentalModesByDeviceClass =
                        mapOf(
                            HomeLayoutDeviceClass.PHONE to setOf(LauncherViewMode.HOME_SCREEN_LIBRARY),
                            HomeLayoutDeviceClass.FOLDABLE to setOf(LauncherViewMode.HOME_SCREEN_LIBRARY),
                        ),
                ),
        )

    @Test
    fun interleavedEditsAndModeSwitchesKeepEveryEditWhenStorageLags() {
        // Storage that never reflects a write when read back: any reducer still reloading from it
        // would rebuild the layout set from the stale copy and drop the page added below.
        val repository = LaggingHomeLayoutRepository(HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()))
        val viewModel = viewModel(repository)
        val initialPageCount = viewModel.state.value.homeLayout.pages.size

        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)
        viewModel.onHomePageEdited(LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.HOME_SCREEN_LIBRARY))
        viewModel.onHomePageEdited(LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.STANDARD_APP_DRAWER))
        viewModel.onHomePageEdited(LauncherShellAction.OpenDefaultHome)

        val state = viewModel.state.value
        assertEquals(phoneStandardKey, state.homeLayoutSet.activeKey)
        assertEquals(initialPageCount + 1, state.homeLayout.pages.size)
        assertEquals(state.homeLayoutSet, repository.lastSaved)
        assertEquals(initialPageCount + 1, checkNotNull(repository.lastSaved).layoutFor(phoneStandardKey).pages.size)
    }

    @Test
    fun gestureModeSwitchAppliesToTheVisibleDeviceClassAfterSettingsPointedElsewhere() {
        val repository = InMemoryHomeLayoutRepository(HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()))
        val viewModel = viewModel(repository)
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
        viewModel.onNavigationActionSelected(ShellNavigationAction.OpenHome)
        assertEquals(ShellDestination.HOME, viewModel.state.value.destination)
        assertEquals(HomeLayoutDeviceClass.FOLDABLE, viewModel.state.value.settingsLayoutDeviceClass)

        // A home gesture acts on what is on screen: the phone layout, not the foldable one that
        // Settings was last pointed at.
        viewModel.onHomePageEdited(LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.HOME_SCREEN_LIBRARY))

        assertEquals(phoneLibraryKey, viewModel.state.value.homeLayoutSet.activeKey)
        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, viewModel.state.value.homeLayout.viewMode)
        assertEquals(
            LauncherViewMode.HOME_SCREEN_LIBRARY,
            repository.layoutSet?.preferredModesByDeviceClass?.get(HomeLayoutDeviceClass.PHONE),
        )
    }

    @Test
    fun settingsModeSwitchStillTargetsTheDeviceSettingsIsConfiguring() {
        val repository = InMemoryHomeLayoutRepository(HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()))
        val viewModel = viewModel(repository)
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

        viewModel.onHomePageEdited(LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.HOME_SCREEN_LIBRARY))

        assertEquals(phoneStandardKey, viewModel.state.value.homeLayoutSet.activeKey)
        assertEquals(
            LauncherViewMode.HOME_SCREEN_LIBRARY,
            viewModel.state.value.homeLayoutSet.preferredModesByDeviceClass[HomeLayoutDeviceClass.FOLDABLE],
        )
    }

    @Test
    fun layoutStateSurvivesRecreation() {
        val repository = InMemoryHomeLayoutRepository(HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard()))
        val first = viewModel(repository)
        first.onHomePageEdited(LauncherShellAction.AddHomePage)
        first.onHomePageEdited(LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.HOME_SCREEN_LIBRARY))
        val before = first.state.value

        val recreated = viewModel(repository)

        assertEquals(before.homeLayoutSet, recreated.state.value.homeLayoutSet)
        assertEquals(before.homeLayout, recreated.state.value.homeLayout)
        assertEquals(phoneLibraryKey, recreated.state.value.homeLayoutSet.activeKey)
    }

    private fun viewModel(repository: HomeLayoutRepository): LauncherShellViewModel =
        LauncherShellViewModel(
            firstRunRepository = FakeFirstRunRepository(),
            homeLayoutRepository = repository,
            platformDependencies = libraryEverywhere,
        )

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class InMemoryHomeLayoutRepository(
        var layoutSet: HomeLayoutSet?,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = layoutSet?.activeLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            layoutSet = layoutSet?.withActiveLayout(layout) ?: HomeLayoutSet.fromLayout(layout)
        }

        override fun loadHomeLayoutSet(): HomeLayoutSet? = layoutSet

        override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
            this.layoutSet = layoutSet
        }
    }

    /** Records every save but always reads back the set it started with. */
    private class LaggingHomeLayoutRepository(
        private val staleLayoutSet: HomeLayoutSet,
    ) : HomeLayoutRepository {
        var lastSaved: HomeLayoutSet? = null

        override fun loadHomeLayout(): HomeLayout = staleLayoutSet.activeLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            lastSaved = (lastSaved ?: staleLayoutSet).withActiveLayout(layout)
        }

        override fun loadHomeLayoutSet(): HomeLayoutSet = staleLayoutSet

        override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
            lastSaved = layoutSet
        }
    }
}
