package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherViewMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellViewModeViewModelTest {
    @Test
    fun savesLauncherViewModeSelection() {
        val camera = app(label = "Camera")
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera)),
                homeLayoutRepository = repository,
            )

        viewModel.onHomePageEdited(
            LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.HOME_SCREEN_LIBRARY),
        )

        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, viewModel.state.value.homeLayout.viewMode)
        assertEquals(
            camera.identity,
            viewModel.state.value.homeLayout.selectedPage.items.singleAppShortcut().appIdentity,
        )
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun refreshAddsNewInstalledAppsWhenLibraryModeIsActive() {
        val camera = app(label = "Camera")
        val calendar = app(label = "Calendar")
        val installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera))
        val repository =
            FakeHomeLayoutRepository(
                savedLayout = HomeLayoutDefaults.standard().copy(viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = installedAppRepository,
                homeLayoutRepository = repository,
            )

        installedAppRepository.apps = listOf(camera, calendar)
        viewModel.refreshInstalledApps()

        assertEquals(
            listOf(camera.identity, calendar.identity),
            viewModel.state.value.homeLayout.selectedPage.items
                .filterIsInstance<AppShortcutItem>()
                .map { item -> item.appIdentity },
        )
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeHomeLayoutRepository(
        var savedLayout: HomeLayout? = null,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = savedLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayout = layout
        }
    }

    private class FakeInstalledAppRepository(
        var apps: List<InstalledApp>,
    ) : InstalledAppRepository {
        override fun installedApps(): List<InstalledApp> = apps
    }

    private fun app(label: String): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
        )

    private fun List<Any>.singleAppShortcut(): AppShortcutItem = single() as AppShortcutItem
}
