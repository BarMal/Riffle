package com.riffle.app.launcher

import androidx.lifecycle.ViewModel
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.LauncherShellStateReducer
import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.AppShortcutRepository
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.apps.withHiddenApps
import com.riffle.core.domain.launcher.home.DockEditResult
import com.riffle.core.domain.launcher.home.DockEngine
import com.riffle.core.domain.launcher.home.FolderEditResult
import com.riffle.core.domain.launcher.home.FolderEngine
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomePageEditResult
import com.riffle.core.domain.launcher.home.HomePageEngine
import com.riffle.core.domain.launcher.home.HomeShortcutEngine
import com.riffle.core.domain.launcher.home.HomeShortcutResult
import com.riffle.core.domain.launcher.home.PlacementRejectionReason
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.notifications.AppNotificationCounter
import com.riffle.core.domain.launcher.notifications.AppNotificationGrouper
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationStaleFilter
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LauncherShellViewModel(
    private val firstRunRepository: FirstRunRepository,
    private val installedAppRepository: InstalledAppRepository = InstalledAppRepository { emptyList() },
    private val appVisibilityRepository: AppVisibilityRepository = NoopAppVisibilityRepository,
    private val homeLayoutRepository: HomeLayoutRepository = NoopHomeLayoutRepository,
    private val launcherSettingsRepository: LauncherSettingsRepository = NoopLauncherSettingsRepository,
    private val notificationRepository: LauncherNotificationRepository = LauncherNotificationRepository { emptyList() },
    private val epochMillisProvider: EpochMillisProvider = SystemEpochMillisProvider,
) : ViewModel() {
    private val reducer = LauncherShellStateReducer()
    private val appCatalog = InstalledAppCatalog()
    private val appNotificationCounter = AppNotificationCounter()
    private val appNotificationGrouper = AppNotificationGrouper()
    private val notificationStaleFilter = NotificationStaleFilter()
    private val appShortcutRepository =
        installedAppRepository as? AppShortcutRepository ?: NoopAppShortcutRepository
    private val shortcutEngine = HomeShortcutEngine()
    private val homePageEngine = HomePageEngine()
    private val dockEngine = DockEngine()
    private val folderEngine = FolderEngine()

    private val mutableState =
        MutableStateFlow(
            createInitialState(
                homeLayoutRepository = homeLayoutRepository,
                launcherSettingsRepository = launcherSettingsRepository,
                firstRunRepository = firstRunRepository,
                reducer = reducer,
            ).withInstalledApps(installedAppRepository, appVisibilityRepository, appCatalog)
                .withoutUnavailableApps(homeLayoutRepository)
                .withHomeScreenLibraryApps(homeLayoutRepository)
                .withAppShortcuts(appShortcutRepository, appCatalog)
                .withNotificationState(
                    notificationRepository = notificationRepository,
                    appNotificationCounter = appNotificationCounter,
                    appNotificationGrouper = appNotificationGrouper,
                    notificationStaleFilter = notificationStaleFilter,
                    nowEpochMillis = epochMillisProvider.nowEpochMillis(),
                ),
        )
    val state: StateFlow<LauncherShellState> = mutableState.asStateFlow()

    fun onHomeRoleStatusChanged(
        homeRoleStatus: HomeRoleStatus,
        notificationAccessStatus: NotificationAccessStatus = mutableState.value.notificationAccessStatus,
    ) {
        mutableState.value =
            reducer.homeRoleChanged(
                currentState = mutableState.value,
                homeRoleStatus = homeRoleStatus,
            ).copy(
                notificationAccessStatus = notificationAccessStatus,
            ).also { state -> persistCompletedFirstRun(state, firstRunRepository) }
    }

    fun onDefaultHomeRequestStarted() {
        mutableState.value = reducer.defaultHomeRequestStarted(mutableState.value)
    }

    fun onNavigationActionSelected(action: ShellNavigationAction) {
        mutableState.value =
            reducer.navigationActionSelected(
                currentState = mutableState.value,
                action = action,
            )
    }

    fun refreshInstalledApps() {
        mutableState.value =
            mutableState.value
                .withInstalledApps(installedAppRepository, appVisibilityRepository, appCatalog)
                .withoutUnavailableApps(homeLayoutRepository)
                .withHomeScreenLibraryApps(homeLayoutRepository)
                .withAppShortcuts(appShortcutRepository, appCatalog)
                .withNotificationState(
                    notificationRepository = notificationRepository,
                    appNotificationCounter = appNotificationCounter,
                    appNotificationGrouper = appNotificationGrouper,
                    notificationStaleFilter = notificationStaleFilter,
                    nowEpochMillis = epochMillisProvider.nowEpochMillis(),
                )
    }

    fun onAppActionSelected(action: LauncherShellAction) {
        mutableState.value =
            when (action) {
                is LauncherShellAction.AppDrawerQueryChanged ->
                    mutableState.value.copy(
                        appDrawerQuery = action.query,
                        appDrawerApps =
                            appCatalog.drawerApps(
                                apps = mutableState.value.installedApps,
                                query = action.query,
                                profileFilter = mutableState.value.appDrawerProfileFilter,
                                appShortcutsByApp = mutableState.value.appShortcutsByApp,
                            ),
                    )

                is LauncherShellAction.AppDrawerProfileFilterSelected ->
                    mutableState.value.copy(
                        appDrawerProfileFilter = action.filter,
                        appDrawerApps =
                            appCatalog.drawerApps(
                                apps = mutableState.value.installedApps,
                                query = mutableState.value.appDrawerQuery,
                                profileFilter = action.filter,
                                appShortcutsByApp = mutableState.value.appShortcutsByApp,
                            ),
                    )

                is LauncherShellAction.SearchQueryChanged ->
                    mutableState.value.copy(
                        searchQuery = action.query,
                        searchResults =
                            appCatalog.filteredApps(
                                apps = mutableState.value.installedApps,
                                query = action.query,
                                profileFilter = mutableState.value.searchProfileFilter,
                                appShortcutsByApp = mutableState.value.appShortcutsByApp,
                            ),
                    )

                is LauncherShellAction.SearchProfileFilterSelected ->
                    mutableState.value.copy(
                        searchProfileFilter = action.filter,
                        searchResults =
                            appCatalog.filteredApps(
                                apps = mutableState.value.installedApps,
                                query = mutableState.value.searchQuery,
                                profileFilter = action.filter,
                                appShortcutsByApp = mutableState.value.appShortcutsByApp,
                            ),
                    )

                is LauncherShellAction.HideApp -> {
                    appVisibilityRepository.hideApp(action.identity)
                    mutableState.value
                        .withInstalledApps(installedAppRepository, appVisibilityRepository, appCatalog)
                        .withoutUnavailableApps(homeLayoutRepository).withHomeScreenLibraryApps(homeLayoutRepository)
                        .withAppShortcuts(appShortcutRepository, appCatalog)
                        .withNotificationState(
                            notificationRepository = notificationRepository,
                            appNotificationCounter = appNotificationCounter,
                            appNotificationGrouper = appNotificationGrouper,
                            notificationStaleFilter = notificationStaleFilter,
                            nowEpochMillis = epochMillisProvider.nowEpochMillis(),
                        )
                }

                is LauncherShellAction.UnhideApp -> {
                    appVisibilityRepository.showApp(action.identity)
                    mutableState.value
                        .withInstalledApps(installedAppRepository, appVisibilityRepository, appCatalog)
                        .withoutUnavailableApps(homeLayoutRepository).withHomeScreenLibraryApps(homeLayoutRepository)
                        .withAppShortcuts(appShortcutRepository, appCatalog)
                        .withNotificationState(
                            notificationRepository = notificationRepository,
                            appNotificationCounter = appNotificationCounter,
                            appNotificationGrouper = appNotificationGrouper,
                            notificationStaleFilter = notificationStaleFilter,
                            nowEpochMillis = epochMillisProvider.nowEpochMillis(),
                        )
                }

                else -> mutableState.value
            }
    }

    fun onAddAppToHome(app: InstalledApp) {
        mutableState.value =
            when (val result = shortcutEngine.addAppToSelectedPage(mutableState.value.homeLayout, app)) {
                is HomeShortcutResult.Updated ->
                    mutableState.value.withHomeLayout(result.layout, homeLayoutRepository)

                is HomeShortcutResult.Rejected -> mutableState.value
            }
    }

    fun onHomeShortcutEdited(action: LauncherShellAction) {
        mutableState.value =
            when (action) {
                is LauncherShellAction.CreateHomeFolder,
                is LauncherShellAction.RenameHomeFolder,
                is LauncherShellAction.AddAppToFolder,
                is LauncherShellAction.RemoveAppFromFolder,
                ->
                    when (
                        val result =
                            folderEngine.applyEdit(
                                action = action,
                                layout = mutableState.value.folderEditLayout(action),
                            )
                    ) {
                        is FolderEditResult.Updated ->
                            mutableState.value.withHomeLayout(result.layout, homeLayoutRepository)

                        is FolderEditResult.Rejected -> mutableState.value
                    }

                else ->
                    when (
                        val result =
                            shortcutEngine.applyEdit(
                                action = action,
                                layout = mutableState.value.homeLayout,
                            )
                    ) {
                        is HomeShortcutResult.Updated ->
                            mutableState.value.withHomeLayout(result.layout, homeLayoutRepository)

                        is HomeShortcutResult.Rejected -> mutableState.value
                    }
            }
    }

    fun onHomePageEdited(action: LauncherShellAction) {
        mutableState.value =
            when (action) {
                is LauncherShellAction.SelectLauncherViewMode ->
                    mutableState.value
                        .withSelectedHomeLayoutMode(action.mode, homeLayoutRepository)
                        .withHomeScreenLibraryApps(homeLayoutRepository)

                is LauncherShellAction.SelectHomeLayoutDeviceClass ->
                    mutableState.value
                        .withSelectedHomeLayoutDeviceClass(action.deviceClass, homeLayoutRepository)
                        .withHomeScreenLibraryApps(homeLayoutRepository)

                else ->
                    when (
                        val result =
                            homePageEngine.applyEdit(
                                action = action,
                                layout = mutableState.value.homeLayout,
                            )
                    ) {
                        is HomePageEditResult.Updated ->
                            mutableState.value
                                .withHomeLayout(result.layout, homeLayoutRepository)
                                .withHomeScreenLibraryApps(homeLayoutRepository)

                        is HomePageEditResult.Rejected -> mutableState.value
                    }
            }
    }

    fun onDockEdited(action: LauncherShellAction) {
        mutableState.value =
            when (val result = dockEngine.applyEdit(action = action, layout = mutableState.value.homeLayout)) {
                is DockEditResult.Updated -> mutableState.value.withHomeLayout(result.layout, homeLayoutRepository)
                is DockEditResult.Rejected -> mutableState.value
            }
    }

    fun onLauncherSettingsActionSelected(action: LauncherShellAction) {
        mutableState.value =
            when (action) {
                is LauncherShellAction.SelectWallpaperSource ->
                    mutableState.value.withLauncherSettings(
                        settings =
                            mutableState.value.launcherSettings.copy(
                                appearance =
                                    mutableState.value.launcherSettings.appearance.copy(
                                        wallpaper = WallpaperSettings(source = action.source),
                                    ),
                            ),
                        launcherSettingsRepository = launcherSettingsRepository,
                    )

                is LauncherShellAction.SelectHomeSwipeGestureAction ->
                    mutableState.value.withHomeSwipeGestureAction(
                        direction = action.direction,
                        action = action.action,
                        launcherSettingsRepository = launcherSettingsRepository,
                    )

                LauncherShellAction.ResetHomeSwipeGestureActions ->
                    mutableState.value.withDefaultHomeSwipes(
                        repo = launcherSettingsRepository,
                    )

                is LauncherShellAction.SelectHapticFeedbackStrength ->
                    mutableState.value.withLauncherSettings(
                        settings =
                            mutableState.value.launcherSettings.copy(
                                haptics =
                                    mutableState.value.launcherSettings.haptics.copy(
                                        feedbackStrength = action.strength,
                                    ),
                            ),
                        launcherSettingsRepository = launcherSettingsRepository,
                    )

                else -> mutableState.value
            }
    }

    private object NoopHomeLayoutRepository : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = null

        override fun saveHomeLayout(layout: HomeLayout) = Unit
    }

    private object NoopLauncherSettingsRepository : LauncherSettingsRepository {
        override fun loadLauncherSettings(): LauncherSettings? = null

        override fun saveLauncherSettings(settings: LauncherSettings) = Unit
    }

    private object NoopAppVisibilityRepository : AppVisibilityRepository {
        override fun hiddenAppIdentities(): Set<AppIdentity> = emptySet()

        override fun hideApp(identity: AppIdentity) = Unit

        override fun showApp(identity: AppIdentity) = Unit
    }

    private object NoopAppShortcutRepository : AppShortcutRepository {
        override fun shortcutsFor(apps: List<InstalledApp>): AppShortcutsByApp = emptyMap()
    }
}

private fun createInitialState(
    homeLayoutRepository: HomeLayoutRepository,
    launcherSettingsRepository: LauncherSettingsRepository,
    firstRunRepository: FirstRunRepository,
    reducer: LauncherShellStateReducer,
): LauncherShellState =
    LauncherShellState(
        homeLayout = homeLayoutRepository.loadHomeLayout() ?: HomeLayoutDefaults.standard(),
        launcherSettings = launcherSettingsRepository.loadLauncherSettings() ?: LauncherSettings(),
    )
        .let { initialState ->
            if (firstRunRepository.isFirstRunComplete()) {
                reducer.firstRunCompleted(initialState)
            } else {
                initialState
            }
        }

private fun LauncherShellState.withInstalledApps(
    installedAppRepository: InstalledAppRepository,
    appVisibilityRepository: AppVisibilityRepository,
    appCatalog: InstalledAppCatalog,
): LauncherShellState =
    installedAppRepository.installedApps()
        .withHiddenApps(appVisibilityRepository.hiddenAppIdentities())
        .let { apps ->
            copy(
                installedApps = appCatalog.visibleApps(apps),
                hiddenApps = appCatalog.hiddenApps(apps),
            )
        }
        .let { state ->
            state.copy(
                appDrawerApps =
                    appCatalog.drawerApps(
                        apps = state.installedApps,
                        query = state.appDrawerQuery,
                        profileFilter = state.appDrawerProfileFilter,
                        appShortcutsByApp = state.appShortcutsByApp,
                    ),
                searchResults =
                    appCatalog.filteredApps(
                        apps = state.installedApps,
                        query = state.searchQuery,
                        profileFilter = state.searchProfileFilter,
                        appShortcutsByApp = state.appShortcutsByApp,
                    ),
            )
        }

private fun LauncherShellState.withAppShortcuts(
    appShortcutRepository: AppShortcutRepository,
    appCatalog: InstalledAppCatalog,
): LauncherShellState =
    installedApps
        .map { app -> app.identity }
        .toSet()
        .let { visibleAppIdentities ->
            appShortcutRepository.shortcutsFor(installedApps)
                .filterKeys { identity -> identity in visibleAppIdentities }
                .let { shortcutsByApp ->
                    copy(appShortcutsByApp = shortcutsByApp)
                        .withFilteredApps(appCatalog)
                }
        }

private fun InstalledAppCatalog.drawerApps(
    apps: List<InstalledApp>,
    query: String,
    profileFilter: AppDrawerProfileFilter,
    appShortcutsByApp: AppShortcutsByApp,
): List<InstalledApp> =
    filteredApps(
        apps = apps,
        query = query,
        profileFilter = profileFilter,
        appShortcutsByApp = appShortcutsByApp,
    )

private fun InstalledAppCatalog.filteredApps(
    apps: List<InstalledApp>,
    query: String,
    profileFilter: AppDrawerProfileFilter,
    appShortcutsByApp: AppShortcutsByApp,
): List<InstalledApp> {
    return searchApps(
        apps = apps,
        query = query,
        shortcutsByApp = appShortcutsByApp,
    )
        .filter { app -> app.matches(profileFilter) }
}

private fun LauncherShellState.withFilteredApps(appCatalog: InstalledAppCatalog): LauncherShellState =
    copy(
        appDrawerApps =
            appCatalog.drawerApps(
                apps = installedApps,
                query = appDrawerQuery,
                profileFilter = appDrawerProfileFilter,
                appShortcutsByApp = appShortcutsByApp,
            ),
        searchResults =
            appCatalog.filteredApps(
                apps = installedApps,
                query = searchQuery,
                profileFilter = searchProfileFilter,
                appShortcutsByApp = appShortcutsByApp,
            ),
    )

private fun InstalledApp.matches(profileFilter: AppDrawerProfileFilter): Boolean =
    when (profileFilter) {
        AppDrawerProfileFilter.ALL -> true
        AppDrawerProfileFilter.PERSONAL -> identity.profile.type == AppProfileType.PERSONAL
        AppDrawerProfileFilter.WORK -> identity.profile.type == AppProfileType.WORK
    }

private fun persistCompletedFirstRun(
    state: LauncherShellState,
    firstRunRepository: FirstRunRepository,
) {
    if (state.shouldShowEmptyHome) {
        firstRunRepository.setFirstRunComplete()
    }
}

private fun HomeShortcutEngine.applyEdit(
    action: LauncherShellAction,
    layout: HomeLayout,
): HomeShortcutResult =
    when (action) {
        is LauncherShellAction.AddAppShortcutToHome ->
            addAppShortcutToSelectedPage(
                layout = layout,
                shortcut = action.shortcut,
            )

        is LauncherShellAction.RemoveHomeShortcut ->
            removeShortcutFromSelectedPage(
                layout = layout,
                itemId = action.itemId,
            )

        is LauncherShellAction.MoveHomeShortcut ->
            moveShortcutOnSelectedPage(
                layout = layout,
                itemId = action.itemId,
                direction = action.direction,
            )

        else -> HomeShortcutResult.Rejected(PlacementRejectionReason.ITEM_NOT_FOUND)
    }

private fun LauncherShellState.folderEditLayout(action: LauncherShellAction): HomeLayout =
    when (action) {
        is LauncherShellAction.CreateHomeFolder -> homeLayout.withHomeScreenLibraryApps(installedApps)
        else -> homeLayout
    }
