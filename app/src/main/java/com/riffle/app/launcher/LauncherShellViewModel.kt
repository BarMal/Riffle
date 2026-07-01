package com.riffle.app.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.LauncherShellStateReducer
import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.apps.AppIdentity
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
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HomePageEditResult
import com.riffle.core.domain.launcher.home.HomePageEngine
import com.riffle.core.domain.launcher.home.HomeShortcutEngine
import com.riffle.core.domain.launcher.home.HomeShortcutResult
import com.riffle.core.domain.launcher.home.PlacementRejectionReason
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WidgetEditResult
import com.riffle.core.domain.launcher.home.WidgetEngine
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LauncherShellViewModel(
    private val firstRunRepository: FirstRunRepository,
    private val installedAppRepository: InstalledAppRepository = InstalledAppRepository { emptyList() },
    private val appVisibilityRepository: AppVisibilityRepository = NoopAppVisibilityRepository,
    private val homeLayoutRepository: HomeLayoutRepository = NoopHomeLayoutRepository,
    private val launcherSettingsRepository: LauncherSettingsRepository = NoopLauncherSettingsRepository,
    private val platformDependencies: LauncherShellPlatformDependencies = LauncherShellPlatformDependencies(),
    private val refreshDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val reducer = LauncherShellStateReducer()
    private val appCatalog = InstalledAppCatalog()
    private val appListActionReducer = LauncherAppListActionReducer(appCatalog)
    private val appShortcutRepository =
        installedAppRepository as? AppShortcutRepository ?: NoopAppShortcutRepository
    private val installedAppRefreshDependencies =
        InstalledAppRefreshDependencies(
            installedAppRepository = installedAppRepository,
            appVisibilityRepository = appVisibilityRepository,
            appCatalog = appCatalog,
            homeLayoutRepository = homeLayoutRepository,
            appShortcutRepository = appShortcutRepository,
        )
    private val refreshCoordinator =
        LauncherShellRefreshCoordinator(
            installedAppDependencies = installedAppRefreshDependencies,
            notificationDependencies =
                LauncherNotificationRefreshDependencies(
                    notificationRepository = platformDependencies.notificationRepository,
                    epochMillisProvider = platformDependencies.epochMillisProvider,
                ),
            widgetProviderDependencies =
                LauncherWidgetProviderRefreshDependencies(
                    widgetProviderRepository = platformDependencies.widgetProviderRepository,
                ),
        )
    private val shortcutEngine = HomeShortcutEngine()
    private val homePageEngine = HomePageEngine()
    private val dockEngine = DockEngine()
    private val folderEngine = FolderEngine()
    private val widgetEngine = WidgetEngine()

    private val mutableState =
        MutableStateFlow(
            createInitialState(
                homeLayoutRepository = homeLayoutRepository,
                launcherSettingsRepository = launcherSettingsRepository,
                firstRunRepository = firstRunRepository,
                reducer = reducer,
                platformDependencies = platformDependencies,
            ),
        )
    val state: StateFlow<LauncherShellState> = mutableState.asStateFlow()
    internal val refreshActions =
        LauncherShellRefreshActions(
            coroutineScope = viewModelScope,
            refreshDispatcher = refreshDispatcher,
            currentState = { mutableState.value },
            updateState = { state -> mutableState.value = state },
            refreshCoordinator = refreshCoordinator,
        )

    init {
        if (platformDependencies.loadInitialPlatformState) {
            refreshInstalledApps()
            refreshNotifications()
            refreshWidgetProviders()
        }
    }

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

    fun onAppActionSelected(action: LauncherShellAction): Job? {
        var launchedRefresh: Job? = null

        mutableState.value =
            when (action) {
                LauncherShellAction.RefreshInstalledApps -> {
                    launchedRefresh = refreshInstalledApps()
                    mutableState.value
                }

                is LauncherShellAction.AppDrawerQueryChanged,
                is LauncherShellAction.AppDrawerProfileFilterSelected,
                is LauncherShellAction.SearchQueryChanged,
                is LauncherShellAction.SearchProfileFilterSelected,
                -> appListActionReducer.reduce(mutableState.value, action) ?: mutableState.value

                is LauncherShellAction.HideApp,
                is LauncherShellAction.UnhideApp,
                -> {
                    launchedRefresh =
                        refreshActions.refreshInstalledApps {
                            action.applyAppVisibilityAction(appVisibilityRepository)
                        }
                    mutableState.value
                }

                LauncherShellAction.OpenWidgetPicker,
                LauncherShellAction.CloseWidgetPicker,
                -> {
                    if (action == LauncherShellAction.OpenWidgetPicker) {
                        launchedRefresh = refreshWidgetProviders()
                    }
                    mutableState.value
                        .withWidgetPickerAction(action)
                }

                else -> mutableState.value
            }

        return launchedRefresh
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
                is LauncherShellAction.CreateEmptyHomeFolder,
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

                is LauncherShellAction.AddHostedWidgetToHome ->
                    when (
                        val result =
                            widgetEngine.addWidgetToSelectedPage(
                                layout = mutableState.value.homeLayout,
                                hostedWidgetId = action.hostedWidgetId,
                                label = action.label,
                                preferredSpan = action.preferredSpan,
                            )
                    ) {
                        is WidgetEditResult.Updated ->
                            mutableState.value.withHomeLayout(result.layout, homeLayoutRepository)

                        is WidgetEditResult.Rejected -> mutableState.value
                    }

                is LauncherShellAction.ResizeHomeWidget ->
                    when (
                        val result =
                            widgetEngine.resizeWidgetOnSelectedPage(
                                layout = mutableState.value.homeLayout,
                                itemId = action.itemId,
                                span = action.span,
                            )
                    ) {
                        is WidgetEditResult.Updated ->
                            mutableState.value.withHomeLayout(result.layout, homeLayoutRepository)

                        is WidgetEditResult.Rejected -> mutableState.value
                    }

                else ->
                    when (
                        val result =
                            applyShortcutOrFolderDropEdit(
                                action = action,
                                layout = mutableState.value.homeLayout,
                                shortcutEngine = shortcutEngine,
                                folderEngine = folderEngine,
                            )
                    ) {
                        is ShortcutOrFolderDropEditResult.Updated ->
                            mutableState.value
                                .withHomeLayout(result.layout, homeLayoutRepository)
                                .withLibraryReflowAfterShortcutMove(action, homeLayoutRepository)

                        ShortcutOrFolderDropEditResult.Rejected -> mutableState.value
                    }
            }
    }

    fun onHomePageEdited(action: LauncherShellAction) {
        mutableState.value =
            when {
                action is LauncherShellAction.SelectLauncherViewMode ->
                    mutableState.value
                        .withSelectedHomeLayoutMode(action.mode, homeLayoutRepository)
                        .withHomeScreenLibraryApps(homeLayoutRepository)

                action is LauncherShellAction.SelectHomeLayoutDeviceClass ->
                    mutableState.value
                        .withSelectedHomeLayoutDeviceClass(action.deviceClass, homeLayoutRepository)
                        .withHomeScreenLibraryApps(homeLayoutRepository)

                mutableState.value.shouldEditSettingsTargetLayout(action) ->
                    mutableState.value.withSettingsHomePageEdit(
                        action = action,
                        homePageEngine = homePageEngine,
                        homeLayoutRepository = homeLayoutRepository,
                    )

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
            if (mutableState.value.shouldEditSettingsTargetDock(action)) {
                mutableState.value.withSettingsDockEdit(
                    action = action,
                    dockEngine = dockEngine,
                    homeLayoutRepository = homeLayoutRepository,
                )
            } else {
                when (val result = dockEngine.applyEdit(action = action, layout = mutableState.value.homeLayout)) {
                    is DockEditResult.Updated -> mutableState.value.withHomeLayout(result.layout, homeLayoutRepository)
                    is DockEditResult.Rejected -> mutableState.value
                }
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

                is LauncherShellAction.SelectSettingsLayoutDeviceClass ->
                    mutableState.value.withSettingsLayoutDeviceClass(action.deviceClass)

                is LauncherShellAction.ImportLauncherBackup ->
                    mutableState.value
                        .withImportedBackup(
                            document = action.document,
                            homeLayoutRepository = homeLayoutRepository,
                            launcherSettingsRepository = launcherSettingsRepository,
                        )
                        .withoutUnavailableApps(homeLayoutRepository)
                        .withHomeScreenLibraryApps(homeLayoutRepository)

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
    platformDependencies: LauncherShellPlatformDependencies,
): LauncherShellState {
    val storedLayoutSet = homeLayoutRepository.loadHomeLayoutSet()
    val initialLayoutSet =
        storedLayoutSet?.let { layoutSet ->
            platformDependencies.initialHomeLayoutDeviceClass
                ?.let(layoutSet::selectDeviceClass)
                ?: layoutSet
        }

    if (initialLayoutSet != null && initialLayoutSet.activeKey != storedLayoutSet?.activeKey) {
        homeLayoutRepository.saveHomeLayoutSet(initialLayoutSet)
    }

    val layoutSet = initialLayoutSet ?: HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard())

    return LauncherShellState(
        homeLayout = layoutSet.activeLayout,
        homeLayoutSet = layoutSet,
        settingsLayoutDeviceClass = layoutSet.activeKey.deviceClass,
        launcherSettings = launcherSettingsRepository.loadLauncherSettings() ?: LauncherSettings(),
    ).let { initialState ->
        if (firstRunRepository.isFirstRunComplete()) {
            reducer.firstRunCompleted(initialState)
        } else {
            initialState
        }
    }
}

internal fun LauncherShellState.withInstalledApps(
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

internal fun LauncherShellState.withAppShortcuts(
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

private fun persistCompletedFirstRun(
    state: LauncherShellState,
    firstRunRepository: FirstRunRepository,
) {
    if (state.shouldShowEmptyHome) {
        firstRunRepository.setFirstRunComplete()
    }
}

internal fun HomeShortcutEngine.applyEdit(
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

        is LauncherShellAction.MoveHomeShortcutToCell ->
            moveShortcutToCellOnSelectedPage(
                layout = layout,
                itemId = action.itemId,
                cell = action.cell,
            )

        else -> HomeShortcutResult.Rejected(PlacementRejectionReason.ITEM_NOT_FOUND)
    }

private fun LauncherShellState.folderEditLayout(action: LauncherShellAction): HomeLayout =
    when (action) {
        is LauncherShellAction.CreateEmptyHomeFolder,
        is LauncherShellAction.CreateHomeFolder,
        -> homeLayout.withHomeScreenLibraryApps(installedApps)
        else -> homeLayout
    }
