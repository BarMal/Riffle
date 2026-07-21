@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riffle.app.launcher.notifications.AppStageShellStateReconciler
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.LauncherShellStateReducer
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppShortcutRepository
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog
import com.riffle.core.domain.launcher.apps.InstalledAppRefreshResult
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.apps.withHiddenApps
import com.riffle.core.domain.launcher.home.DockEngine
import com.riffle.core.domain.launcher.home.FolderEngine
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.HomeShortcutEngine
import com.riffle.core.domain.launcher.home.HomeShortcutResult
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability
import com.riffle.core.domain.launcher.home.PlacementRejectionReason
import com.riffle.core.domain.launcher.home.WidgetEditResult
import com.riffle.core.domain.launcher.home.WidgetEngine
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import com.riffle.core.domain.launcher.settings.stagePreferencesFor
import com.riffle.core.domain.launcher.settings.withMigratedStagePreferences
import com.riffle.core.domain.launcher.settings.withStagePreferences
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
    internal val viewModeAvailability: LauncherViewModeAvailability = platformDependencies.viewModeAvailability

    private val reducer = LauncherShellStateReducer()
    private val appCatalog = InstalledAppCatalog()
    private val appListActionReducer = LauncherAppListActionReducer(appCatalog)
    private val widgetPickerActionReducer = LauncherWidgetPickerActionReducer()
    private val appStateActionCoordinator =
        LauncherAppStateActionCoordinator(
            appVisibilityRepository = appVisibilityRepository,
            appListActionReducer = appListActionReducer,
            widgetPickerActionReducer = widgetPickerActionReducer,
            currentState = { mutableState.value },
            updateState = { state -> mutableState.value = state },
            refreshInstalledApps = { beforeRefresh -> refreshActions.refreshInstalledApps(beforeRefresh) },
            refreshWidgetProviders = { refreshWidgetProviders() },
        )
    private val settingsStateReducer =
        LauncherSettingsStateReducer(
            homeLayoutRepository = homeLayoutRepository,
            launcherSettingsRepository = launcherSettingsRepository,
            appVisibilityRepository = appVisibilityRepository,
        )
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
    private val homePageEditReducer =
        LauncherHomePageEditReducer(
            homeLayoutRepository = homeLayoutRepository,
            viewModeAvailability = platformDependencies.viewModeAvailability,
        )
    private val dockEngine = DockEngine()
    private val dockEditReducer =
        LauncherDockEditReducer(
            dockEngine = dockEngine,
            homeLayoutRepository = homeLayoutRepository,
        )
    private val folderEngine = FolderEngine()
    private val folderEditReducer =
        LauncherFolderEditReducer(
            folderEngine = folderEngine,
            homeLayoutRepository = homeLayoutRepository,
        )
    private val widgetEngine = WidgetEngine()

    /** Keeps navigation aligned with the retained empty stage rendered by the TimeScape surface. */
    private val appStageStateReconciler = AppStageShellStateReconciler()

    private val mutableState =
        MutableStateFlow(
            createInitialState(
                homeLayoutRepository = homeLayoutRepository,
                launcherSettingsRepository = launcherSettingsRepository,
                firstRunRepository = firstRunRepository,
                platformDependencies = platformDependencies,
                viewModeAvailability = platformDependencies.viewModeAvailability,
            ),
        )
    val state: StateFlow<LauncherShellState> = mutableState.asStateFlow()
    internal val refreshActions =
        LauncherShellRefreshActions(
            coroutineScope = viewModelScope,
            refreshDispatcher = refreshDispatcher,
            currentState = { mutableState.value },
            updateState = { state ->
                val previousState = mutableState.value
                appStageStateReconciler.reconcile(previousState)
                mutableState.value = state
                appStageStateReconciler.reconcile(state)
                if (state.launcherSettings != previousState.launcherSettings) {
                    launcherSettingsRepository.saveLauncherSettings(state.launcherSettings)
                }
            },
            refreshCoordinator = refreshCoordinator,
        )

    init {
        if (platformDependencies.loadInitialPlatformState) {
            refreshInstalledApps()
            refreshNotifications()
            refreshWidgetProviders()
        }
    }

    fun onConfirmedPackageRemoved(
        packageName: AppPackageName,
        profile: AppProfile,
    ) {
        val state = mutableState.value
        mutableState.value =
            state
                .withInstalledApps(
                    apps =
                        state.installedApps.filterNot { app ->
                            app.identity.packageName == packageName && app.identity.profile == profile
                        },
                    appVisibilityRepository = appVisibilityRepository,
                    appCatalog = appCatalog,
                )
                .withoutConfirmedPackage(
                    packageName = packageName,
                    profile = profile,
                    homeLayoutRepository = homeLayoutRepository,
                )
                .withHomeScreenLibraryApps(homeLayoutRepository)
                .withRefreshedGeneratedPages(homeLayoutRepository)
                .withAppShortcuts(appShortcutRepository, appCatalog)
    }

    fun onHomeRoleStatusChanged(
        homeRoleStatus: HomeRoleStatus,
        notificationAccessStatus: NotificationAccessStatus = mutableState.value.notificationAccessStatus,
        overlayDockPermissionStatus: OverlayDockPermissionStatus = mutableState.value.overlayDockPermissionStatus,
    ) {
        mutableState.value =
            reducer.homeRoleChanged(
                currentState = mutableState.value,
                homeRoleStatus = homeRoleStatus,
            ).copy(
                notificationAccessStatus = notificationAccessStatus,
                overlayDockPermissionStatus = overlayDockPermissionStatus,
            ).let { state ->
                state.copy(
                    searchSettingsResults = state.searchSettingsResults(state.searchQuery),
                    setupCardDismissed = state.setupCardDismissed || state.firstRunStatus == FirstRunStatus.COMPLETE,
                )
            }.also { state ->
                firstRunRepository.setHomeRoleRequestPending(
                    state.firstRunStatus == FirstRunStatus.REQUESTING_HOME_ROLE,
                )
                persistSetupCardDismissal(state, firstRunRepository)
            }
    }

    fun onDefaultHomeRequestStarted() {
        mutableState.value = reducer.defaultHomeRequestStarted(mutableState.value)
        firstRunRepository.setHomeRoleRequestPending(pending = true)
    }

    fun onDefaultHomeRequestLaunchFailed() {
        mutableState.value = reducer.defaultHomeRequestLaunchFailed(mutableState.value)
        firstRunRepository.setHomeRoleRequestPending(pending = false)
    }

    /**
     * Activity results only prove that Android returned from its UI. Live role truth is refreshed
     * separately, but the presentation-only pending marker must not trap a cancelled request.
     */
    fun onDefaultHomeRequestReturned() {
        mutableState.value = reducer.defaultHomeRequestReturned(mutableState.value)
        firstRunRepository.setHomeRoleRequestPending(pending = false)
    }

    fun onSetupCardDismissed() {
        mutableState.value = reducer.setupCardDismissed(mutableState.value)
        firstRunRepository.setSetupCardDismissed()
    }

    fun onNavigationActionSelected(action: ShellNavigationAction) {
        val previousDestination = mutableState.value.destination

        mutableState.value =
            reducer.navigationActionSelected(
                currentState = mutableState.value,
                action = action,
            ).let { state ->
                when (action) {
                    ShellNavigationAction.OpenSettings ->
                        state.copy(settingsLayoutDeviceClass = state.homeLayoutSet.activeKey.deviceClass)

                    else -> state
                }
            }.let { state ->
                when {
                    previousDestination == ShellDestination.SEARCH && state.destination != ShellDestination.SEARCH ->
                        appListActionReducer.reduce(state, LauncherShellAction.SearchQueryChanged("")) ?: state

                    previousDestination == ShellDestination.APP_DRAWER &&
                        state.destination != ShellDestination.APP_DRAWER ->
                        appListActionReducer.reduce(state, LauncherShellAction.AppDrawerQueryChanged("")) ?: state

                    else -> state
                }
            }.copy(dockEditRejectionReason = null)
    }

    fun onDockEditFeedbackDismissed() {
        mutableState.value = mutableState.value.copy(dockEditRejectionReason = null)
    }

    fun onAppActionSelected(action: LauncherShellAction): Job? {
        return appStateActionCoordinator.handle(action)
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
        val previousState = mutableState.value
        val removedHostedWidgetId = previousState.removedHomeHostedWidgetId(action)

        mutableState.value =
            reduceHomeShortcutEdit(
                previousState = previousState,
                action = action,
                folderEditReducer = folderEditReducer,
                widgetEngine = widgetEngine,
                homeLayoutRepository = homeLayoutRepository,
                shortcutEngine = shortcutEngine,
                folderEngine = folderEngine,
            )

        deleteRemovedHomeHostedWidget(
            action = action,
            previousState = previousState,
            currentState = mutableState.value,
            removedHostedWidgetId = removedHostedWidgetId,
            deleteHostedWidgetId = platformDependencies.deleteHostedWidgetId,
        )
    }

    fun onHomePageEdited(action: LauncherShellAction) {
        if (action.isCardsChapterAction()) {
            mutableState.value = mutableState.value.withCardsChapterAction(action, launcherSettingsRepository)
            return
        }
        if (action.isAppStageAction()) {
            val currentState = mutableState.value
            val snapshot = appStageStateReconciler.reconcile(currentState).snapshot
            mutableState.value =
                currentState.withAppStageAction(
                    action = action,
                    launcherSettingsRepository = launcherSettingsRepository,
                    snapshot = snapshot,
                )
            appStageStateReconciler.reconcile(mutableState.value)
            return
        }

        val previousState = mutableState.value
        val removedHostedWidgetIds =
            when (action) {
                LauncherShellAction.DeleteSelectedHomePage -> previousState.homeLayout.selectedPageHostedWidgetIds()
                else -> emptyList()
            }

        mutableState.value =
            homePageEditReducer
                .reduce(previousState, action)
                .let { state ->
                    if (action == LauncherShellAction.ExitHomeEditMode) {
                        state.copy(dockEditRejectionReason = null)
                    } else {
                        state
                    }
                }

        if (action == LauncherShellAction.DeleteSelectedHomePage && mutableState.value != previousState) {
            removedHostedWidgetIds.forEach(platformDependencies.deleteHostedWidgetId)
        }
    }

    fun onDockEdited(action: LauncherShellAction) {
        val previousState = mutableState.value
        val removedHostedWidgetId = previousState.removedDockHostedWidgetId(action)

        mutableState.value = dockEditReducer.reduce(previousState, action)

        if (action is LauncherShellAction.RemoveDockShortcut && mutableState.value != previousState) {
            removedHostedWidgetId?.let(platformDependencies.deleteHostedWidgetId)
        }
    }

    fun onLauncherSettingsActionSelected(action: LauncherShellAction) {
        mutableState.value = settingsStateReducer.reduce(mutableState.value, action)
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

private fun LauncherShellAction.isCardsChapterAction(): Boolean =
    when (this) {
        is LauncherShellAction.SelectCardsChapter,
        is LauncherShellAction.ToggleCardsChapterPinned,
        -> true

        else -> false
    }

private fun LauncherShellAction.isAppStageAction(): Boolean =
    when (this) {
        is LauncherShellAction.SelectAppStage,
        is LauncherShellAction.ToggleAppStagePinned,
        LauncherShellAction.SelectPreviousAppStage,
        LauncherShellAction.SelectNextAppStage,
        -> true

        else -> false
    }

private fun LauncherShellState.withCardsChapterAction(
    action: LauncherShellAction,
    launcherSettingsRepository: LauncherSettingsRepository,
): LauncherShellState {
    val preferences = launcherSettings.cards.chapterPreferences
    val updatedPreferences =
        when (action) {
            is LauncherShellAction.SelectCardsChapter -> preferences.select(action.chapterId)
            is LauncherShellAction.ToggleCardsChapterPinned ->
                if (action.chapterId in preferences.pinnedChapterIds) {
                    preferences.unpin(action.chapterId)
                } else {
                    preferences.pin(action.chapterId)
                }

            else -> return this
        }
    val updatedState =
        copy(
            launcherSettings =
                launcherSettings.copy(
                    cards = launcherSettings.cards.copy(chapterPreferences = updatedPreferences),
                ),
        )
            .withReconciledCardsChapterSelection()
    launcherSettingsRepository.saveLauncherSettings(updatedState.launcherSettings)
    return updatedState
}

private fun LauncherShellState.withAppStageAction(
    action: LauncherShellAction,
    launcherSettingsRepository: LauncherSettingsRepository,
    snapshot: com.riffle.core.domain.launcher.cards.AppStageSnapshot,
): LauncherShellState {
    val layoutKey = homeLayoutSet.activeKey
    val preferences = launcherSettings.cards.stagePreferencesFor(layoutKey)
    val updatedPreferences =
        when (action) {
            is LauncherShellAction.SelectAppStage ->
                action.stageId.takeIf { it in snapshot.stages.map { stage -> stage.id } }
                    ?.let(preferences::select)
                    ?: preferences

            is LauncherShellAction.ToggleAppStagePinned ->
                if (action.stageId in preferences.pinnedStageIds) {
                    preferences.unpin(action.stageId)
                } else {
                    preferences.pin(action.stageId)
                }

            LauncherShellAction.SelectPreviousAppStage,
            LauncherShellAction.SelectNextAppStage,
            -> {
                val selectedIndex =
                    snapshot.stages.indexOfFirst { stage -> stage.id == snapshot.preferences.selectedStageId }
                val offset = if (action == LauncherShellAction.SelectPreviousAppStage) -1 else 1
                snapshot.stages.getOrNull(selectedIndex + offset)?.let { stage -> preferences.select(stage.id) }
                    ?: preferences
            }

            else -> preferences
        }
    if (updatedPreferences == preferences) return this
    val updatedState =
        copy(
            launcherSettings =
                launcherSettings.copy(
                    cards = launcherSettings.cards.withStagePreferences(layoutKey, updatedPreferences),
                ),
        )
    launcherSettingsRepository.saveLauncherSettings(updatedState.launcherSettings)
    return updatedState
}

private fun reduceHomeShortcutEdit(
    previousState: LauncherShellState,
    action: LauncherShellAction,
    folderEditReducer: LauncherFolderEditReducer,
    widgetEngine: WidgetEngine,
    homeLayoutRepository: HomeLayoutRepository,
    shortcutEngine: HomeShortcutEngine,
    folderEngine: FolderEngine,
): LauncherShellState =
    when (action) {
        is LauncherShellAction.CreateEmptyHomeFolder,
        is LauncherShellAction.CreateHomeFolder,
        is LauncherShellAction.RenameHomeFolder,
        is LauncherShellAction.AddAppToFolder,
        is LauncherShellAction.RemoveAppFromFolder,
        is LauncherShellAction.MoveAppInFolder,
        is LauncherShellAction.MoveAppOutOfFolder,
        ->
            folderEditReducer.reduce(previousState, action)

        is LauncherShellAction.AddHostedWidgetToHome ->
            when (
                val result =
                    widgetEngine.addWidgetToSelectedPage(
                        layout = previousState.homeLayout,
                        hostedWidgetId = action.hostedWidgetId,
                        label = action.label,
                        preferredSpan = action.preferredSpan,
                        resizeConstraints = action.resizeConstraints,
                        targetCell = action.targetCell,
                    )
            ) {
                is WidgetEditResult.Updated ->
                    previousState.withHomeLayout(result.layout, homeLayoutRepository)

                is WidgetEditResult.Rejected -> previousState
            }

        is LauncherShellAction.ResizeHomeWidget ->
            when (
                val result =
                    widgetEngine.resizeWidgetOnSelectedPage(
                        layout = previousState.homeLayout,
                        itemId = action.itemId,
                        span = action.span,
                    )
            ) {
                is WidgetEditResult.Updated ->
                    previousState.withHomeLayout(result.layout, homeLayoutRepository)

                is WidgetEditResult.Rejected -> previousState
            }

        else ->
            when (
                val result =
                    applyShortcutOrFolderDropEdit(
                        action = action,
                        layout = previousState.homeLayout,
                        shortcutEngine = shortcutEngine,
                        folderEngine = folderEngine,
                    )
            ) {
                is ShortcutOrFolderDropEditResult.Updated ->
                    previousState
                        .withHomeLayout(result.layout, homeLayoutRepository)
                        .withLibraryReflowAfterShortcutMove(action, homeLayoutRepository)

                ShortcutOrFolderDropEditResult.Rejected -> previousState
            }
    }

private fun deleteRemovedHomeHostedWidget(
    action: LauncherShellAction,
    previousState: LauncherShellState,
    currentState: LauncherShellState,
    removedHostedWidgetId: HostedWidgetId?,
    deleteHostedWidgetId: (HostedWidgetId) -> Unit,
) {
    if (action is LauncherShellAction.RemoveHomeShortcut && currentState != previousState) {
        removedHostedWidgetId?.let(deleteHostedWidgetId)
    }
}

private fun LauncherShellState.removedHomeHostedWidgetId(action: LauncherShellAction) =
    when (action) {
        is LauncherShellAction.RemoveHomeShortcut ->
            homeLayout.selectedPageHostedWidgetIdForItem(action.itemId)
        else -> null
    }

private fun LauncherShellState.removedDockHostedWidgetId(action: LauncherShellAction) =
    when (action) {
        is LauncherShellAction.RemoveDockShortcut ->
            homeLayout.dockHostedWidgetIdForItem(action.itemId)
        else -> null
    }

private fun createInitialState(
    homeLayoutRepository: HomeLayoutRepository,
    launcherSettingsRepository: LauncherSettingsRepository,
    firstRunRepository: FirstRunRepository,
    platformDependencies: LauncherShellPlatformDependencies,
    viewModeAvailability: LauncherViewModeAvailability,
): LauncherShellState {
    val hasRecoveredHomeRoleRequest = firstRunRepository.isHomeRoleRequestPending()

    val storedLayoutSet = homeLayoutRepository.loadHomeLayoutSet()
    val initialLayoutSet =
        storedLayoutSet?.let { layoutSet ->
            layoutSet.selectInitialDeviceClass(
                deviceClass = platformDependencies.initialHomeLayoutDeviceClass ?: layoutSet.activeKey.deviceClass,
                availability = viewModeAvailability,
            )
        }

    if (initialLayoutSet != null && initialLayoutSet.activeKey != storedLayoutSet?.activeKey) {
        homeLayoutRepository.saveHomeLayoutSet(initialLayoutSet)
    }

    val layoutSet = initialLayoutSet ?: HomeLayoutSet.fromLayout(HomeLayoutDefaults.standard())

    val storedSettings = launcherSettingsRepository.loadLauncherSettings() ?: LauncherSettings()
    val launcherSettings =
        storedSettings.copy(
            cards = storedSettings.cards.withMigratedStagePreferences(layoutSet.stagePreferenceLayoutKeys()),
        )
    if (launcherSettings != storedSettings) {
        launcherSettingsRepository.saveLauncherSettings(launcherSettings)
    }

    return LauncherShellState(
        homeLayout = layoutSet.activeLayout,
        homeLayoutSet = layoutSet,
        settingsLayoutDeviceClass = layoutSet.activeKey.deviceClass,
        availableLayoutDeviceClasses = setOf(layoutSet.activeKey.deviceClass),
        launcherSettings = launcherSettings,
    ).copy(
        firstRunStatus =
            if (hasRecoveredHomeRoleRequest) {
                FirstRunStatus.REQUESTING_HOME_ROLE
            } else {
                FirstRunStatus.NEEDS_HOME_ROLE
            },
        hasRecoveredHomeRoleRequest = hasRecoveredHomeRoleRequest,
        setupCardDismissed = firstRunRepository.isSetupCardDismissed(),
    )
}

private fun HomeLayoutSet.stagePreferenceLayoutKeys(): Set<HomeLayoutKey> =
    layouts.keys +
        HomeLayoutDeviceClass.entries.map { deviceClass ->
            HomeLayoutKey(viewMode = LauncherViewMode.CARD_INTERFACE, deviceClass = deviceClass)
        }

private fun HomeLayoutSet.selectInitialDeviceClass(
    deviceClass: HomeLayoutDeviceClass,
    availability: LauncherViewModeAvailability,
): HomeLayoutSet {
    val key = availability.availableKeyFor(layoutSet = this, deviceClass = deviceClass)
    if (key in layouts) {
        return selectDeviceClass(deviceClass = deviceClass, availability = availability)
    }

    return if (layouts.size == 1) {
        copy(
            activeKey = key,
            layouts = layouts + (key to activeLayout.copy(viewMode = key.viewMode)),
            preferredModesByDeviceClass = preferredModesByDeviceClass + (key.deviceClass to key.viewMode),
        )
    } else {
        selectDeviceClass(deviceClass = deviceClass, availability = availability)
    }
}

internal fun LauncherShellState.withInstalledApps(
    installedAppRepository: InstalledAppRepository,
    appVisibilityRepository: AppVisibilityRepository,
    appCatalog: InstalledAppCatalog,
): LauncherShellState =
    when (val result = installedAppRepository.refreshResult()) {
        is InstalledAppRefreshResult.Authoritative ->
            withInstalledApps(
                apps = result.apps,
                appVisibilityRepository = appVisibilityRepository,
                appCatalog = appCatalog,
            )

        is InstalledAppRefreshResult.Partial,
        InstalledAppRefreshResult.Unavailable,
        -> this
    }

internal fun LauncherShellState.withInstalledApps(
    apps: List<InstalledApp>,
    appVisibilityRepository: AppVisibilityRepository,
    appCatalog: InstalledAppCatalog,
): LauncherShellState =
    apps
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
                        filters = state.searchFilters,
                    ),
                searchShortcutResults =
                    state.searchShortcutResults(
                        query = state.searchQuery,
                        filters = state.searchFilters,
                    ),
                searchSettingsResults = state.searchSettingsResults(state.searchQuery),
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

private fun persistSetupCardDismissal(
    state: LauncherShellState,
    firstRunRepository: FirstRunRepository,
) {
    if (state.firstRunStatus == FirstRunStatus.COMPLETE) {
        firstRunRepository.setSetupCardDismissed()
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
