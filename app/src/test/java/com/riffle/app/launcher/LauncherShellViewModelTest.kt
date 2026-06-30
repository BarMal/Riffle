package com.riffle.app.launcher

import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockItemMoveDirection
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeEditMode
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProviderRepository
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherShellViewModelTest {
    @Test
    fun startsWithDefaultHomePromptVisible() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())

        assertTrue(viewModel.state.value.shouldShowDefaultHomePrompt)
    }

    @Test
    fun recordsDefaultHomeRequestInProgress() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())

        viewModel.onDefaultHomeRequestStarted()

        assertEquals(FirstRunStatus.REQUESTING_HOME_ROLE, viewModel.state.value.firstRunStatus)
    }

    @Test
    fun hidesPromptWhenAppBecomesDefaultHome() {
        val repository = FakeFirstRunRepository()
        val viewModel = LauncherShellViewModel(firstRunRepository = repository)

        viewModel.onHomeRoleStatusChanged(HomeRoleStatus.DEFAULT_HOME)

        assertFalse(viewModel.state.value.shouldShowDefaultHomePrompt)
        assertTrue(viewModel.state.value.shouldShowEmptyHome)
        assertTrue(repository.isFirstRunComplete())
    }

    @Test
    fun restoresCompletedFirstRunFromRepository() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(isComplete = true),
            )

        assertEquals(FirstRunStatus.COMPLETE, viewModel.state.value.firstRunStatus)
        assertTrue(viewModel.state.value.shouldShowEmptyHome)
    }

    @Test
    fun startsWithUnknownNotificationAccessStatus() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())

        assertEquals(NotificationAccessStatus.UNKNOWN, viewModel.state.value.notificationAccessStatus)
    }

    @Test
    fun loadsSortedWidgetProviders() {
        val clock = widgetProvider(label = "Clock", packageName = "com.example.clock")
        val calendar = widgetProvider(label = "Calendar", packageName = "com.example.calendar")
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        widgetProviderRepository = FakeWidgetProviderRepository(providers = listOf(clock, calendar)),
                    ),
            )

        assertEquals(listOf(calendar, clock), viewModel.state.value.installedWidgetProviders)
    }

    @Test
    fun opensAndClosesWidgetPicker() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())

        viewModel.onAppActionSelected(LauncherShellAction.OpenWidgetPicker)

        assertTrue(viewModel.state.value.isWidgetPickerOpen)

        viewModel.onAppActionSelected(LauncherShellAction.CloseWidgetPicker)

        assertFalse(viewModel.state.value.isWidgetPickerOpen)
    }

    @Test
    fun refreshesNotificationAccessStatus() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())

        viewModel.onHomeRoleStatusChanged(
            homeRoleStatus = HomeRoleStatus.DEFAULT_HOME,
            notificationAccessStatus = NotificationAccessStatus.GRANTED,
        )

        assertEquals(NotificationAccessStatus.GRANTED, viewModel.state.value.notificationAccessStatus)
    }

    @Test
    fun navigatesBetweenShellDestinations() {
        val viewModel = LauncherShellViewModel(firstRunRepository = FakeFirstRunRepository())

        viewModel.onNavigationActionSelected(ShellNavigationAction.OpenAppDrawer)
        assertEquals(ShellDestination.APP_DRAWER, viewModel.state.value.destination)

        viewModel.onNavigationActionSelected(ShellNavigationAction.OpenHome)
        assertEquals(ShellDestination.HOME, viewModel.state.value.destination)
    }

    @Test
    fun loadsVisibleInstalledAppsIntoInitialState() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository =
                    FakeInstalledAppRepository(
                        apps =
                            listOf(
                                app(label = "Hidden", visibility = AppVisibility.HIDDEN),
                                app(label = "Camera"),
                                app(label = "Browser"),
                            ),
                    ),
            )

        assertEquals(
            listOf("Browser", "Camera"),
            viewModel.state.value.installedApps.map { app -> app.label },
        )
    }

    @Test
    fun refreshesInstalledApps() {
        val repository = FakeInstalledAppRepository(apps = listOf(app(label = "Camera")))
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = repository,
            )

        repository.apps = listOf(app(label = "Calendar"))
        viewModel.refreshInstalledApps()

        assertEquals(listOf("Calendar"), viewModel.state.value.installedApps.map { app -> app.label })
    }

    @Test
    fun refreshInstalledAppsActionRefreshesInstalledApps() {
        val repository = FakeInstalledAppRepository(apps = listOf(app(label = "Camera")))
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = repository,
            )

        repository.apps = listOf(app(label = "Calendar"))
        viewModel.onAppActionSelected(LauncherShellAction.RefreshInstalledApps)

        assertEquals(listOf("Calendar"), viewModel.state.value.installedApps.map { app -> app.label })
    }

    @Test
    fun loadsNotificationCountsIntoInitialState() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                platformDependencies =
                    LauncherShellPlatformDependencies(
                        notificationRepository =
                            FakeNotificationRepository(
                                notifications =
                                    listOf(
                                        notification(key = "camera-1", packageName = "com.riffle.camera"),
                                        notification(key = "camera-2", packageName = "com.riffle.camera"),
                                    ),
                            ),
                    ),
            )

        assertEquals(2, viewModel.state.value.notificationCountsByPackage[AppPackageName("com.riffle.camera")])
    }

    @Test
    fun refreshesNotificationCounts() {
        val repository = FakeNotificationRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                platformDependencies = LauncherShellPlatformDependencies(notificationRepository = repository),
            )

        repository.notifications = listOf(notification(key = "camera-1", packageName = "com.riffle.camera"))
        viewModel.refreshInstalledApps()

        assertEquals(1, viewModel.state.value.notificationCountsByPackage[AppPackageName("com.riffle.camera")])
    }

    @Test
    fun filtersSearchResultsWhenQueryChanges() {
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository =
                    FakeInstalledAppRepository(
                        apps =
                            listOf(
                                app(label = "Camera"),
                                app(label = "Calendar"),
                                app(label = "Maps"),
                            ),
                    ),
            )

        viewModel.onAppActionSelected(LauncherShellAction.SearchQueryChanged("cam"))

        assertEquals("cam", viewModel.state.value.searchQuery)
        assertEquals(listOf("Camera"), viewModel.state.value.searchResults.map { app -> app.label })
    }

    @Test
    fun refreshesSearchResultsForCurrentQuery() {
        val repository = FakeInstalledAppRepository(apps = listOf(app(label = "Camera")))
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = repository,
            )
        viewModel.onAppActionSelected(LauncherShellAction.SearchQueryChanged("cal"))

        repository.apps = listOf(app(label = "Calendar"))
        viewModel.refreshInstalledApps()

        assertEquals("cal", viewModel.state.value.searchQuery)
        assertEquals(listOf("Calendar"), viewModel.state.value.searchResults.map { app -> app.label })
    }

    @Test
    fun addsAppShortcutToFirstAvailableHomeCell() {
        val camera = app(label = "Camera")
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera)),
            )

        viewModel.onAddAppToHome(camera)

        val shortcut = viewModel.state.value.homeLayout.selectedPage.items.single() as AppShortcutItem
        assertEquals(camera.identity, shortcut.appIdentity)
        assertEquals("Camera", shortcut.label)
        assertEquals(GridPlacement(cell = GridCell(column = 0, row = 0)), shortcut.placement)
    }

    @Test
    fun ignoresDuplicateHomeAppShortcut() {
        val camera = app(label = "Camera")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera)),
                homeLayoutRepository = repository,
            )

        viewModel.onAddAppToHome(camera)
        val layoutBeforeDuplicate = viewModel.state.value.homeLayout
        viewModel.onAddAppToHome(camera)

        val shortcuts = viewModel.state.value.homeLayout.selectedPage.items.filterIsInstance<AppShortcutItem>()
        assertEquals(1, shortcuts.size)
        assertEquals(layoutBeforeDuplicate, viewModel.state.value.homeLayout)
        assertEquals(layoutBeforeDuplicate, repository.savedLayout)
    }

    @Test
    fun restoresSavedHomeLayout() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())

        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        assertEquals(repository.savedLayout, viewModel.state.value.homeLayout)
    }

    @Test
    fun restoresSavedLauncherSettings() {
        val repository =
            FakeLauncherSettingsRepository(
                savedSettings =
                    LauncherSettings(
                        appearance =
                            AppearanceSettings(
                                wallpaper = WallpaperSettings(source = WallpaperSource.SOLID_COLOR),
                            ),
                    ),
            )

        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                launcherSettingsRepository = repository,
            )

        assertEquals(repository.savedSettings, viewModel.state.value.launcherSettings)
    }

    @Test
    fun savesWallpaperSourceSelection() {
        val repository = FakeLauncherSettingsRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                launcherSettingsRepository = repository,
            )

        viewModel.onLauncherSettingsActionSelected(
            LauncherShellAction.SelectWallpaperSource(WallpaperSource.SOLID_COLOR),
        )

        assertEquals(WallpaperSource.SOLID_COLOR, viewModel.state.value.launcherSettings.appearance.wallpaper.source)
        assertEquals(viewModel.state.value.launcherSettings, repository.savedSettings)
    }

    @Test
    fun savesHomeLayoutAfterAddingAppShortcut() {
        val camera = app(label = "Camera")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera)),
                homeLayoutRepository = repository,
            )

        viewModel.onAddAppToHome(camera)

        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun entersAndExitsHomeEditMode() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomePageEdited(LauncherShellAction.EnterHomeEditMode)

        assertEquals(
            HomeEditMode.EditingPage(viewModel.state.value.homeLayout.selectedPageId),
            viewModel.state.value.homeLayout.editMode,
        )

        viewModel.onHomePageEdited(LauncherShellAction.ExitHomeEditMode)

        assertEquals(HomeEditMode.Browsing, viewModel.state.value.homeLayout.editMode)
    }

    @Test
    fun addsHomePageSelectsItAndSavesLayout() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)

        assertEquals(listOf(LauncherPageId("home"), LauncherPageId("home-2")), viewModel.state.value.homeLayout.pageIds)
        assertEquals(LauncherPageId("home-2"), viewModel.state.value.homeLayout.selectedPageId)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun selectsAdjacentHomePagesAndSavesLayout() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)

        viewModel.onHomePageEdited(LauncherShellAction.SelectPreviousHomePage)

        assertEquals(LauncherPageId("home"), viewModel.state.value.homeLayout.selectedPageId)

        viewModel.onHomePageEdited(LauncherShellAction.SelectNextHomePage)

        assertEquals(LauncherPageId("home-2"), viewModel.state.value.homeLayout.selectedPageId)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun ignoresPageSelectionOutsideLayoutBounds() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        val layoutBeforeSelection = viewModel.state.value.homeLayout

        viewModel.onHomePageEdited(LauncherShellAction.SelectPreviousHomePage)

        assertEquals(layoutBeforeSelection, viewModel.state.value.homeLayout)
        assertEquals(layoutBeforeSelection, repository.savedLayout)
    }

    @Test
    fun movesSelectedHomePageLeftAndSavesLayout() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)

        viewModel.onHomePageEdited(LauncherShellAction.MoveSelectedHomePageLeft)

        assertEquals(listOf(LauncherPageId("home-2"), LauncherPageId("home")), viewModel.state.value.homeLayout.pageIds)
        assertEquals(LauncherPageId("home-2"), viewModel.state.value.homeLayout.selectedPageId)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun movesSelectedHomePageRightAndSavesLayout() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)
        viewModel.onHomePageEdited(LauncherShellAction.SelectPreviousHomePage)

        viewModel.onHomePageEdited(LauncherShellAction.MoveSelectedHomePageRight)

        assertEquals(listOf(LauncherPageId("home-2"), LauncherPageId("home")), viewModel.state.value.homeLayout.pageIds)
        assertEquals(LauncherPageId("home"), viewModel.state.value.homeLayout.selectedPageId)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun ignoresSelectedHomePageMoveOutsideLayoutBounds() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        val layoutBeforeMove = viewModel.state.value.homeLayout

        viewModel.onHomePageEdited(LauncherShellAction.MoveSelectedHomePageLeft)

        assertEquals(layoutBeforeMove, viewModel.state.value.homeLayout)
        assertEquals(layoutBeforeMove, repository.savedLayout)
    }

    @Test
    fun deletesSelectedHomePageAndSavesLayout() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        viewModel.onHomePageEdited(LauncherShellAction.AddHomePage)

        viewModel.onHomePageEdited(LauncherShellAction.DeleteSelectedHomePage)

        assertEquals(listOf(LauncherPageId("home")), viewModel.state.value.homeLayout.pageIds)
        assertEquals(LauncherPageId("home"), viewModel.state.value.homeLayout.selectedPageId)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun ignoresDeletingLastHomePage() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        val layoutBeforeDeletion = viewModel.state.value.homeLayout

        viewModel.onHomePageEdited(LauncherShellAction.DeleteSelectedHomePage)

        assertEquals(layoutBeforeDeletion, viewModel.state.value.homeLayout)
        assertEquals(layoutBeforeDeletion, repository.savedLayout)
    }

    @Test
    fun addsAppShortcutToDockAndSavesLayout() {
        val phone = app(label = "Phone")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(phone)),
                homeLayoutRepository = repository,
            )

        viewModel.onDockEdited(LauncherShellAction.AddAppToDock(phone))

        val shortcut = viewModel.state.value.homeLayout.dock.items.single() as AppShortcutItem
        assertEquals(phone.identity, shortcut.appIdentity)
        assertEquals("Phone", shortcut.label)
        assertEquals(null, shortcut.placement)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun ignoresDuplicateDockShortcut() {
        val phone = app(label = "Phone")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(phone)),
                homeLayoutRepository = repository,
            )
        viewModel.onDockEdited(LauncherShellAction.AddAppToDock(phone))
        val layoutBeforeDuplicate = viewModel.state.value.homeLayout

        viewModel.onDockEdited(LauncherShellAction.AddAppToDock(phone))

        assertEquals(layoutBeforeDuplicate, viewModel.state.value.homeLayout)
        assertEquals(layoutBeforeDuplicate, repository.savedLayout)
    }

    @Test
    fun ignoresDockShortcutWhenDockIsFull() {
        val phone = app(label = "Phone")
        val repository =
            FakeHomeLayoutRepository(
                savedLayout = HomeLayoutDefaults.standard().copy(dock = DockModel(capacity = 0)),
            )
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )
        val layoutBeforeAdd = viewModel.state.value.homeLayout

        viewModel.onDockEdited(LauncherShellAction.AddAppToDock(phone))

        assertEquals(layoutBeforeAdd, viewModel.state.value.homeLayout)
        assertEquals(layoutBeforeAdd, repository.savedLayout)
    }

    @Test
    fun removesDockShortcutAndSavesLayout() {
        val phone = app(label = "Phone")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(phone)),
                homeLayoutRepository = repository,
            )
        viewModel.onDockEdited(LauncherShellAction.AddAppToDock(phone))
        val shortcut = viewModel.state.value.homeLayout.dock.items.single() as AppShortcutItem

        viewModel.onDockEdited(LauncherShellAction.RemoveDockShortcut(shortcut.id))

        assertEquals(emptyList<AppShortcutItem>(), viewModel.state.value.homeLayout.dock.items)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun movesDockShortcutLeftAndSavesLayout() {
        val phone = app(label = "Phone")
        val camera = app(label = "Camera")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(phone, camera)),
                homeLayoutRepository = repository,
            )
        viewModel.onDockEdited(LauncherShellAction.AddAppToDock(phone))
        viewModel.onDockEdited(LauncherShellAction.AddAppToDock(camera))
        val cameraShortcut = viewModel.state.value.homeLayout.dock.items[1] as AppShortcutItem

        viewModel.onDockEdited(
            LauncherShellAction.MoveDockShortcut(
                itemId = cameraShortcut.id,
                direction = DockItemMoveDirection.LEFT,
            ),
        )

        assertEquals(listOf("Camera", "Phone"), viewModel.state.value.homeLayout.dock.labels)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun movesDockShortcutRightAndSavesLayout() {
        val phone = app(label = "Phone")
        val camera = app(label = "Camera")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(phone, camera)),
                homeLayoutRepository = repository,
            )
        viewModel.onDockEdited(LauncherShellAction.AddAppToDock(phone))
        viewModel.onDockEdited(LauncherShellAction.AddAppToDock(camera))
        val phoneShortcut = viewModel.state.value.homeLayout.dock.items[0] as AppShortcutItem

        viewModel.onDockEdited(
            LauncherShellAction.MoveDockShortcut(
                itemId = phoneShortcut.id,
                direction = DockItemMoveDirection.RIGHT,
            ),
        )

        assertEquals(listOf("Camera", "Phone"), viewModel.state.value.homeLayout.dock.labels)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun ignoresDockShortcutMoveOutsideBounds() {
        val phone = app(label = "Phone")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(phone)),
                homeLayoutRepository = repository,
            )
        viewModel.onDockEdited(LauncherShellAction.AddAppToDock(phone))
        val layoutBeforeMove = viewModel.state.value.homeLayout
        val phoneShortcut = layoutBeforeMove.dock.items.single()

        viewModel.onDockEdited(
            LauncherShellAction.MoveDockShortcut(
                itemId = phoneShortcut.id,
                direction = DockItemMoveDirection.LEFT,
            ),
        )

        assertEquals(layoutBeforeMove, viewModel.state.value.homeLayout)
        assertEquals(layoutBeforeMove, repository.savedLayout)
    }

    @Test
    fun removesHomeShortcutAndSavesLayout() {
        val camera = app(label = "Camera")
        val repository = FakeHomeLayoutRepository()
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                installedAppRepository = FakeInstalledAppRepository(apps = listOf(camera)),
                homeLayoutRepository = repository,
            )
        viewModel.onAddAppToHome(camera)
        val shortcut = viewModel.state.value.homeLayout.selectedPage.items.single() as AppShortcutItem

        viewModel.onHomeShortcutEdited(LauncherShellAction.RemoveHomeShortcut(shortcut.id))

        assertEquals(emptyList<AppShortcutItem>(), viewModel.state.value.homeLayout.selectedPage.items)
        assertEquals(viewModel.state.value.homeLayout, repository.savedLayout)
    }

    @Test
    fun ignoresMissingHomeShortcutRemoval() {
        val repository = FakeHomeLayoutRepository(savedLayout = HomeLayoutDefaults.standard())
        val viewModel =
            LauncherShellViewModel(
                firstRunRepository = FakeFirstRunRepository(),
                homeLayoutRepository = repository,
            )

        viewModel.onHomeShortcutEdited(LauncherShellAction.RemoveHomeShortcut(LauncherItemId("missing")))

        assertEquals(HomeLayoutDefaults.standard(), viewModel.state.value.homeLayout)
        assertEquals(HomeLayoutDefaults.standard(), repository.savedLayout)
    }

    private class FakeFirstRunRepository(
        private var isComplete: Boolean = false,
    ) : FirstRunRepository {
        override fun isFirstRunComplete(): Boolean = isComplete

        override fun setFirstRunComplete() {
            isComplete = true
        }
    }

    private class FakeInstalledAppRepository(
        var apps: List<InstalledApp> = emptyList(),
    ) : InstalledAppRepository {
        override fun installedApps(): List<InstalledApp> = apps
    }

    private class FakeHomeLayoutRepository(
        var savedLayout: HomeLayout? = null,
    ) : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = savedLayout

        override fun saveHomeLayout(layout: HomeLayout) {
            savedLayout = layout
        }
    }

    private class FakeLauncherSettingsRepository(
        var savedSettings: LauncherSettings? = null,
    ) : LauncherSettingsRepository {
        override fun loadLauncherSettings(): LauncherSettings? = savedSettings

        override fun saveLauncherSettings(settings: LauncherSettings) {
            savedSettings = settings
        }
    }

    private class FakeNotificationRepository(
        var notifications: List<LauncherNotification> = emptyList(),
    ) : LauncherNotificationRepository {
        override fun activeNotifications(): List<LauncherNotification> = notifications
    }

    private class FakeWidgetProviderRepository(
        var providers: List<InstalledWidgetProvider> = emptyList(),
    ) : InstalledWidgetProviderRepository {
        override fun installedWidgetProviders(): List<InstalledWidgetProvider> = providers
    }

    private fun app(
        label: String,
        visibility: AppVisibility = AppVisibility.VISIBLE,
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
            visibility = visibility,
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

    private fun widgetProvider(
        label: String,
        packageName: String,
    ): InstalledWidgetProvider =
        InstalledWidgetProvider(
            identity =
                WidgetProviderIdentity(
                    packageName = AppPackageName(packageName),
                    className = WidgetProviderClassName(".WidgetProvider"),
                ),
            label = label,
            dimensions = WidgetProviderDimensions(minWidthDp = 100, minHeightDp = 50),
        )

    private val HomeLayout.pageIds: List<LauncherPageId>
        get() = pages.map { page -> page.id }

    private val DockModel.labels: List<String>
        get() = items.filterIsInstance<AppShortcutItem>().map { item -> item.label }
}
