package com.riffle.app.launcher

import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Choosing a view mode in settings picks which layout is active. It is not an edit of the layout
 * that happens to be showing, and it must not carry that layout into the mode being switched to --
 * every mode keeps its own pages and its own dock.
 */
class LauncherShellSettingsViewModeViewModelTest {
    @Test
    fun switchingModeFromSettingsKeepsTheOtherModesDock() {
        val repository = repositoryWithBothModes()
        val viewModel = viewModelFor(repository)

        viewModel.onNavigationActionSelected(ShellNavigationAction.OpenSettings)
        viewModel.onHomePageEdited(
            LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.HOME_SCREEN_LIBRARY),
        )

        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, viewModel.state.value.homeLayout.viewMode)
        assertEquals(libraryDock.items, viewModel.state.value.homeLayout.dock.items)
    }

    @Test
    fun switchingModeFromSettingsLeavesTheModeItCameFromAlone() {
        val repository = repositoryWithBothModes()
        val viewModel = viewModelFor(repository)

        viewModel.onNavigationActionSelected(ShellNavigationAction.OpenSettings)
        viewModel.onHomePageEdited(
            LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.HOME_SCREEN_LIBRARY),
        )

        val saved = checkNotNull(repository.savedLayoutSet)
        assertEquals(standardDock.items, saved.layoutFor(standardKey).dock.items)
    }

    @Test
    fun switchingModeFromSettingsAndBackReturnsTheDockItStartedWith() {
        // The reported flow: leave the mode you configured, come back to it, find it emptied.
        val repository = repositoryWithBothModes()
        val viewModel = viewModelFor(repository)

        viewModel.onNavigationActionSelected(ShellNavigationAction.OpenSettings)
        viewModel.onHomePageEdited(
            LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.HOME_SCREEN_LIBRARY),
        )
        viewModel.onHomePageEdited(
            LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.STANDARD_APP_DRAWER),
        )
        viewModel.onHomePageEdited(
            LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.HOME_SCREEN_LIBRARY),
        )

        assertEquals(libraryDock.items, viewModel.state.value.homeLayout.dock.items)
    }

    private fun repositoryWithBothModes(): FakeHomeLayoutRepository =
        FakeHomeLayoutRepository().also { repository ->
            repository.savedLayoutSet =
                HomeLayoutSet(
                    activeKey = standardKey,
                    layouts =
                        mapOf(
                            standardKey to
                                HomeLayoutDefaults
                                    .standard(HomeLayoutDeviceClass.PHONE)
                                    .copy(dock = standardDock),
                            libraryKey to
                                HomeLayoutDefaults
                                    .standard(HomeLayoutDeviceClass.PHONE)
                                    .copy(viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY, dock = libraryDock),
                        ),
                )
        }

    private fun viewModelFor(repository: HomeLayoutRepository): LauncherShellViewModel =
        LauncherShellViewModel(
            firstRunRepository = FakeFirstRunRepository(),
            installedAppRepository = InstalledAppRepository { emptyList() },
            homeLayoutRepository = repository,
            platformDependencies =
                LauncherShellPlatformDependencies(
                    viewModeAvailability =
                        LauncherViewModeAvailability(
                            enabledExperimentalModesByDeviceClass =
                                HomeLayoutDeviceClass.entries.associateWith {
                                    setOf(LauncherViewMode.HOME_SCREEN_LIBRARY)
                                },
                        ),
                ),
        )

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = true

        override fun setFirstRunComplete() = Unit
    }

    private class FakeHomeLayoutRepository : HomeLayoutRepository {
        var savedLayoutSet: HomeLayoutSet? = null

        override fun loadHomeLayout(): HomeLayout? = savedLayoutSet?.activeLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayoutSet = savedLayoutSet?.withActiveLayout(layout) ?: HomeLayoutSet.fromLayout(layout)
        }

        override fun loadHomeLayoutSet(): HomeLayoutSet? = savedLayoutSet

        override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
            savedLayoutSet = layoutSet
        }
    }

    private companion object {
        private val standardKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.PHONE)
        private val libraryKey = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY, HomeLayoutDeviceClass.PHONE)

        private val standardDock = DockModel(capacity = 4, items = listOf(shortcut("camera")))
        private val libraryDock = DockModel(capacity = 4, items = listOf(shortcut("mail"), shortcut("clock")))

        private fun shortcut(name: String): AppShortcutItem =
            AppShortcutItem(
                id = LauncherItemId(name),
                appIdentity =
                    AppIdentity(
                        packageName = AppPackageName("com.riffle.$name"),
                        activityName = AppActivityName(".MainActivity"),
                    ),
                label = name,
            )
    }
}
