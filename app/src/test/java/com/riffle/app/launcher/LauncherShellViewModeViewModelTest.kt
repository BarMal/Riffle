package com.riffle.app.launcher

import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridSettings
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository
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

    @Test
    fun startsWithCurrentDeviceLayoutFromStoredLayoutSet() {
        val phonePage = HomeLayoutDefaults.standard().selectedPage.copy(id = LauncherPageId("phone-home"))
        val foldablePage = HomeLayoutDefaults.standard().selectedPage.copy(id = LauncherPageId("foldable-home"))
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
        val layoutSet =
            HomeLayoutSet(
                activeKey = foldableKey,
                layouts =
                    mapOf(
                        phoneKey to
                            HomeLayoutDefaults.standard().copy(
                                pages = listOf(phonePage),
                                selectedPageId = phonePage.id,
                            ),
                        foldableKey to
                            HomeLayoutDefaults.standard().copy(
                                pages = listOf(foldablePage),
                                selectedPageId = foldablePage.id,
                            ),
                    ),
            )
        val repository = FakeHomeLayoutRepository().also { repo -> repo.savedLayoutSet = layoutSet }
        repository.savedLayoutSetSaveCount = 0

        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        initialHomeLayoutDeviceClass = HomeLayoutDeviceClass.PHONE,
                    ),
            )

        assertEquals(phonePage.id, viewModel.state.value.homeLayout.selectedPageId)
        assertEquals(phoneKey, repository.savedLayoutSet?.activeKey)
    }

    @Test
    fun editsAfterInitialDeviceSelectionDoNotOverwritePreviousDeviceLayout() {
        val phonePage = HomeLayoutDefaults.standard().selectedPage.copy(id = LauncherPageId("phone-home"))
        val foldablePage = HomeLayoutDefaults.standard().selectedPage.copy(id = LauncherPageId("foldable-home"))
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
        val foldableLayout =
            HomeLayoutDefaults.standard().copy(
                pages = listOf(foldablePage),
                selectedPageId = foldablePage.id,
            )
        val layoutSet =
            HomeLayoutSet(
                activeKey = foldableKey,
                layouts =
                    mapOf(
                        phoneKey to
                            HomeLayoutDefaults.standard().copy(
                                pages = listOf(phonePage),
                                selectedPageId = phonePage.id,
                            ),
                        foldableKey to foldableLayout,
                    ),
            )
        val repository = FakeHomeLayoutRepository().also { repo -> repo.savedLayoutSet = layoutSet }
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        initialHomeLayoutDeviceClass = HomeLayoutDeviceClass.PHONE,
                    ),
            )

        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)

        assertEquals(foldableLayout, repository.savedLayoutSet?.layoutFor(foldableKey))
        assertEquals(
            listOf(LauncherPageId("phone-home"), LauncherPageId("home-2")),
            repository.savedLayoutSet?.layoutFor(phoneKey)?.pages?.map { page -> page.id },
        )
    }

    @Test
    fun selectingAlreadyActiveDeviceClassDoesNotRewriteStoredLayoutSet() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        repository.savedLayoutSetSaveCount = 0

        viewModel.onHomePageEdited(
            LauncherShellAction.SelectHomeLayoutDeviceClass(HomeLayoutDeviceClass.PHONE),
        )

        assertEquals(0, repository.savedLayoutSetSaveCount)
        assertEquals(HomeLayoutDefaults.standard(), viewModel.state.value.homeLayout)
    }

    @Test
    fun settingsCanEditFoldedLayoutWithoutChangingActiveUnfoldedLayout() {
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
        val phoneLayout =
            HomeLayoutDefaults.standard()
                .copy(settings = HomeLayoutDefaults.standard().settings.copy(grid = grid(columns = 4, rows = 5)))
        val foldableLayout =
            HomeLayoutDefaults.standard()
                .copy(settings = HomeLayoutDefaults.standard().settings.copy(grid = grid(columns = 3, rows = 4)))
        val repository =
            FakeHomeLayoutRepository().also { repo ->
                repo.savedLayoutSet =
                    HomeLayoutSet(
                        activeKey = phoneKey,
                        layouts = mapOf(phoneKey to phoneLayout, foldableKey to foldableLayout),
                    )
            }
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onNavigationActionSelected(ShellNavigationAction.OpenSettings)
        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectSettingsLayoutDeviceClass(HomeLayoutDeviceClass.FOLDABLE),
        )
        viewModel.onHomePageEdited(
            LauncherShellAction.SelectHomeGridDimensions(GridDimensions(columns = 5, rows = 6)),
        )

        assertEquals(ShellDestination.SETTINGS, viewModel.state.value.destination)
        assertEquals(phoneLayout, viewModel.state.value.homeLayout)
        assertEquals(
            GridDimensions(columns = 5, rows = 6),
            repository.savedLayoutSet?.layoutFor(foldableKey)?.settings?.grid?.dimensions,
        )
        assertEquals(
            phoneLayout.settings.grid.dimensions,
            repository.savedLayoutSet?.layoutFor(phoneKey)?.settings?.grid?.dimensions,
        )
        assertEquals(phoneKey, repository.savedLayoutSet?.activeKey)
    }

    @Test
    fun deviceClassSwitchPreservesNotificationCounters() {
        val camera = app(label = "Camera")
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        notificationRepository =
                            FakeNotificationRepository(
                                notifications =
                                    listOf(
                                        notification(
                                            key = "camera-1",
                                            packageName = camera.identity.packageName.value,
                                        ),
                                    ),
                            ),
                    ),
            )

        viewModel.onHomePageEdited(
            LauncherShellAction.SelectHomeLayoutDeviceClass(HomeLayoutDeviceClass.FOLDABLE),
        )
        viewModel.onHomePageEdited(
            LauncherShellAction.SelectHomeLayoutDeviceClass(HomeLayoutDeviceClass.PHONE),
        )

        assertEquals(1, viewModel.state.value.notificationCountsByPackage[camera.identity.packageName])
    }

    private class FakeFirstRunRepository : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = false

        override fun setFirstRunComplete() = Unit
    }

    private class FakeHomeLayoutRepository(
        var savedLayout: HomeLayout? = null,
    ) : HomeLayoutRepository {
        var savedLayoutSet: HomeLayoutSet? = savedLayout?.let(HomeLayoutSet::fromLayout)
        var savedLayoutSetSaveCount: Int = 0

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
            savedLayoutSetSaveCount += 1
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

    private class FakeNotificationRepository(
        private val notifications: List<LauncherNotification>,
    ) : LauncherNotificationRepository {
        override fun activeNotifications(): List<LauncherNotification> = notifications
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

    private fun notification(
        key: String,
        packageName: String,
    ): LauncherNotification =
        LauncherNotification(
            key = LauncherNotificationKey(key),
            packageName = AppPackageName(packageName),
            postedAtEpochMillis = 1_000L,
        )

    private fun grid(
        columns: Int,
        rows: Int,
    ) = GridSettings(
        dimensions = GridDimensions(columns = columns, rows = rows),
    )

    private fun List<Any>.singleAppShortcut(): AppShortcutItem = single() as AppShortcutItem
}
