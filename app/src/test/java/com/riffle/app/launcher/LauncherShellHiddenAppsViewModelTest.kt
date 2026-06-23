package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellHiddenAppsViewModelTest {
    @Test
    fun excludesHiddenAppPreferencesFromLauncherAppSurfaces() {
        val camera = app(label = "Camera")
        val docs = app(label = "Docs", profile = AppProfile.work())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera, docs)),
                appVisibilityRepository = FakeAppVisibilityRepository(hiddenApps = setOf(docs.identity)),
            )

        viewModel.onAppActionSelected(LauncherShellAction.SearchQueryChanged(""))

        assertEquals(listOf(camera.identity), viewModel.state.value.installedApps.map { app -> app.identity })
        assertEquals(listOf(docs.identity), viewModel.state.value.hiddenApps.map { app -> app.identity })
        assertEquals(listOf(camera.identity), viewModel.state.value.appDrawerApps.map { app -> app.identity })
        assertEquals(listOf(camera.identity), viewModel.state.value.searchResults.map { app -> app.identity })
    }

    @Test
    fun refreshUsesLatestHiddenAppPreferences() {
        val camera = app(label = "Camera")
        val docs = app(label = "Docs")
        val appVisibilityRepository = FakeAppVisibilityRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera, docs)),
                appVisibilityRepository = appVisibilityRepository,
            )

        appVisibilityRepository.hiddenApps = setOf(camera.identity)
        viewModel.refreshInstalledApps()

        assertEquals(listOf(docs.identity), viewModel.state.value.installedApps.map { app -> app.identity })
    }

    @Test
    fun hidesAppAndRefreshesLauncherAppSurfaces() {
        val camera = app(label = "Camera")
        val docs = app(label = "Docs")
        val appVisibilityRepository = FakeAppVisibilityRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera, docs)),
                appVisibilityRepository = appVisibilityRepository,
            )

        viewModel.onAppActionSelected(LauncherShellAction.HideApp(camera.identity))

        assertEquals(setOf(camera.identity), appVisibilityRepository.hiddenApps)
        assertEquals(listOf(docs.identity), viewModel.state.value.installedApps.map { app -> app.identity })
        assertEquals(listOf(camera.identity), viewModel.state.value.hiddenApps.map { app -> app.identity })
        assertEquals(listOf(docs.identity), viewModel.state.value.appDrawerApps.map { app -> app.identity })
    }

    @Test
    fun hidingHomeAppFreesGridCellForNewApps() {
        val camera = app(label = "Camera")
        val docs = app(label = "Docs")
        val maps = app(label = "Maps")
        val homeLayoutRepository =
            FakeHomeLayoutRepository(
                savedLayout =
                    HomeLayoutDefaults.standard().copy(
                        pages =
                            listOf(
                                LauncherPage(
                                    id = LauncherPageId("home"),
                                    grid = GridDimensions(columns = 1, rows = 1),
                                    items =
                                        listOf(
                                            shortcut(
                                                id = "camera",
                                                app = camera,
                                                placement = GridPlacement(cell = GridCell(column = 0, row = 0)),
                                            ),
                                        ),
                                ),
                            ),
                    ),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera, docs, maps)),
                appVisibilityRepository = FakeAppVisibilityRepository(),
                homeLayoutRepository = homeLayoutRepository,
            )

        viewModel.onAppActionSelected(LauncherShellAction.HideApp(camera.identity))
        viewModel.onAddAppToHome(maps)

        val shortcut = viewModel.state.value.homeLayout.selectedPage.items.single() as AppShortcutItem
        assertEquals(maps.identity, shortcut.appIdentity)
        assertEquals(GridPlacement(cell = GridCell(column = 0, row = 0)), shortcut.placement)
        assertEquals(viewModel.state.value.homeLayout, homeLayoutRepository.savedLayout)
    }

    @Test
    fun hidingDockAppFreesDockSlotForNewApps() {
        val phone = app(label = "Phone")
        val camera = app(label = "Camera")
        val homeLayoutRepository =
            FakeHomeLayoutRepository(
                savedLayout =
                    HomeLayoutDefaults.standard().copy(
                        dock = DockModel(capacity = 1, items = listOf(shortcut(id = "phone", app = phone))),
                    ),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(phone, camera)),
                appVisibilityRepository = FakeAppVisibilityRepository(),
                homeLayoutRepository = homeLayoutRepository,
            )

        viewModel.onAppActionSelected(LauncherShellAction.HideApp(phone.identity))
        viewModel.onDockEdited(LauncherShellAction.AddAppToDock(camera))

        val shortcut = viewModel.state.value.homeLayout.dock.items.single() as AppShortcutItem
        assertEquals(camera.identity, shortcut.appIdentity)
        assertEquals(viewModel.state.value.homeLayout, homeLayoutRepository.savedLayout)
    }

    @Test
    fun unhidesAppAndRefreshesLauncherAppSurfaces() {
        val camera = app(label = "Camera")
        val docs = app(label = "Docs")
        val appVisibilityRepository = FakeAppVisibilityRepository(hiddenApps = setOf(camera.identity))
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera, docs)),
                appVisibilityRepository = appVisibilityRepository,
            )

        viewModel.onAppActionSelected(LauncherShellAction.UnhideApp(camera.identity))

        assertEquals(emptySet<AppIdentity>(), appVisibilityRepository.hiddenApps)
        assertEquals(
            listOf(camera.identity, docs.identity),
            viewModel.state.value.installedApps.map { app -> app.identity },
        )
        assertEquals(emptyList<AppIdentity>(), viewModel.state.value.hiddenApps.map { app -> app.identity })
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeInstalledAppRepository(
        var apps: List<InstalledApp> = emptyList(),
    ) : InstalledAppRepository {
        override fun installedApps(): List<InstalledApp> = apps
    }

    private class FakeAppVisibilityRepository(
        var hiddenApps: Set<AppIdentity> = emptySet(),
    ) : AppVisibilityRepository {
        override fun hiddenAppIdentities(): Set<AppIdentity> = hiddenApps

        override fun hideApp(identity: AppIdentity) {
            hiddenApps = hiddenApps + identity
        }

        override fun showApp(identity: AppIdentity) {
            hiddenApps = hiddenApps - identity
        }
    }

    private class FakeHomeLayoutRepository(
        var savedLayout: HomeLayout? = null,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = savedLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayout = layout
        }
    }

    private fun app(
        label: String,
        profile: AppProfile = AppProfile.personal(),
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                    profile = profile,
                ),
            label = label,
        )

    private fun shortcut(
        id: String,
        app: InstalledApp,
        placement: GridPlacement? = null,
    ): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(id),
            appIdentity = app.identity,
            label = app.label,
            placement = placement,
        )
}
