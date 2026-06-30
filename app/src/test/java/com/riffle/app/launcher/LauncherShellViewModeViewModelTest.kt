package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherShellViewModeViewModelTest {
    @Test
    fun importsBackupDocumentIntoStateAndRepositories() {
        val libraryKey = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY)
        val layoutSet =
            HomeLayoutSet(
                activeKey = libraryKey,
                layouts =
                    mapOf(
                        libraryKey to
                            HomeLayoutDefaults.standard()
                                .copy(viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY),
                    ),
            )
        val settings =
            LauncherSettings(
                appearance =
                    AppearanceSettings(
                        wallpaper = WallpaperSettings(source = WallpaperSource.SOLID_COLOR),
                    ),
            )
        val homeLayoutRepository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val launcherSettingsRepository = FakeLauncherSettingsRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = homeLayoutRepository,
                launcherSettingsRepository = launcherSettingsRepository,
            )

        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.ImportLauncherBackup(
                LauncherBackupDocument(
                    homeLayoutSet = layoutSet,
                    launcherSettings = settings,
                ),
            ),
        )

        assertEquals(layoutSet, homeLayoutRepository.savedLayoutSet)
        assertEquals(settings, launcherSettingsRepository.savedSettings)
        assertEquals(layoutSet.activeLayout, viewModel.state.value.homeLayout)
        assertEquals(settings, viewModel.state.value.launcherSettings)
    }

    @Test
    fun backupDocumentPreservesStoredLayoutSet() {
        val standardKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER)
        val libraryKey = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY)
        val layoutSet =
            HomeLayoutSet(
                activeKey = standardKey,
                layouts =
                    mapOf(
                        standardKey to HomeLayoutDefaults.standard(),
                        libraryKey to
                            HomeLayoutDefaults.standard()
                                .copy(viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY),
                    ),
            )
        val repository = FakeHomeLayoutRepository().also { it.savedLayoutSet = layoutSet }
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        val backupDocument =
            launcherBackupDocument(
                storedLayoutSet = repository.loadHomeLayoutSet(),
                activeLayout = viewModel.state.value.homeLayout,
                launcherSettings = viewModel.state.value.launcherSettings,
            )

        assertEquals(layoutSet, backupDocument.homeLayoutSet)
    }

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
        runBlocking { viewModel.refreshInstalledApps().join() }

        assertEquals(
            listOf(camera.identity, calendar.identity),
            viewModel.state.value.homeLayout.selectedPage.items
                .filterIsInstance<AppShortcutItem>()
                .map { item -> item.appIdentity },
        )
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun switchingBackToStandardRemovesGeneratedLibraryApps() {
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
        viewModel.onHomePageEdited(
            LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.STANDARD_APP_DRAWER),
        )

        assertEquals(LauncherViewMode.STANDARD_APP_DRAWER, viewModel.state.value.homeLayout.viewMode)
        assertEquals(emptyList<AppShortcutItem>(), viewModel.state.value.homeLayout.selectedPage.items)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun switchingModesPreservesSeparateModeLayouts() {
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
        viewModel.onHomePageEdited(
            LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.STANDARD_APP_DRAWER),
        )
        viewModel.onHomePageEdited(
            LauncherShellAction.SelectLauncherViewMode(LauncherViewMode.HOME_SCREEN_LIBRARY),
        )

        assertEquals(LauncherViewMode.HOME_SCREEN_LIBRARY, viewModel.state.value.homeLayout.viewMode)
        assertEquals(
            camera.identity,
            viewModel.state.value.homeLayout.selectedPage.items.singleAppShortcut().appIdentity,
        )
        assertEquals(
            emptyList<AppShortcutItem>(),
            repository.savedLayoutSet
                ?.layoutFor(HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER))
                ?.selectedPage
                ?.items,
        )
    }

    @Test
    fun switchingDeviceClassPreservesSeparateDeviceLayoutsForCurrentMode() {
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
        viewModel.onHomePageEdited(
            LauncherShellAction.SelectHomeLayoutDeviceClass(HomeLayoutDeviceClass.FOLDABLE),
        )
        viewModel.onHomePageEdited(
            LauncherShellAction.SelectHomeLayoutDeviceClass(HomeLayoutDeviceClass.PHONE),
        )

        assertEquals(
            HomeLayoutKey(
                viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                deviceClass = HomeLayoutDeviceClass.PHONE,
            ),
            repository.savedLayoutSet?.activeKey,
        )
        assertEquals(
            camera.identity,
            viewModel.state.value.homeLayout.selectedPage.items.singleAppShortcut().appIdentity,
        )
        assertEquals(
            HomeLayoutDefaults.standard().selectedPageId,
            repository.savedLayoutSet
                ?.layoutFor(
                    HomeLayoutKey(
                        viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY,
                        deviceClass = HomeLayoutDeviceClass.FOLDABLE,
                    ),
                )
                ?.selectedPageId,
        )
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeHomeLayoutRepository(
        var savedLayout: HomeLayout? = null,
    ) : HomeLayoutRepository {
        var savedLayoutSet: HomeLayoutSet? = savedLayout?.let(HomeLayoutSet::fromLayout)

        override fun loadHomeLayout(): HomeLayout? = savedLayoutSet?.activeLayout ?: savedLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayout = layout
            savedLayoutSet =
                savedLayoutSet
                    ?.withActiveLayout(layout)
                    ?: HomeLayoutSet.fromLayout(layout)
        }

        override fun loadHomeLayoutSet(): HomeLayoutSet? = savedLayoutSet

        override fun saveHomeLayoutSet(layoutSet: HomeLayoutSet) {
            savedLayoutSet = layoutSet
            savedLayout = layoutSet.activeLayout
        }
    }

    private class FakeLauncherSettingsRepository : LauncherSettingsRepository {
        var savedSettings: LauncherSettings? = null

        override fun loadLauncherSettings(): LauncherSettings? = savedSettings

        override fun saveLauncherSettings(settings: LauncherSettings) {
            savedSettings = settings
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
