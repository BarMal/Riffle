package com.riffle.app.launcher

import androidx.lifecycle.ViewModel
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.LauncherShellStateReducer
import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.DockEditResult
import com.riffle.core.domain.launcher.home.DockEngine
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomePageEditRejectionReason
import com.riffle.core.domain.launcher.home.HomePageEditResult
import com.riffle.core.domain.launcher.home.HomePageEngine
import com.riffle.core.domain.launcher.home.HomeShortcutEngine
import com.riffle.core.domain.launcher.home.HomeShortcutResult
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.PlacementRejectionReason
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.notifications.AppNotificationCounter
import com.riffle.core.domain.launcher.notifications.AppNotificationGrouper
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LauncherShellViewModel(
    private val firstRunRepository: FirstRunRepository,
    private val installedAppRepository: InstalledAppRepository = InstalledAppRepository { emptyList() },
    private val homeLayoutRepository: HomeLayoutRepository = NoopHomeLayoutRepository,
    private val launcherSettingsRepository: LauncherSettingsRepository = NoopLauncherSettingsRepository,
    private val notificationRepository: LauncherNotificationRepository = LauncherNotificationRepository { emptyList() },
    private val epochMillisProvider: EpochMillisProvider = SystemEpochMillisProvider,
) : ViewModel() {
    private val reducer = LauncherShellStateReducer()
    private val appCatalog = InstalledAppCatalog()
    private val appNotificationCounter = AppNotificationCounter()
    private val appNotificationGrouper = AppNotificationGrouper()
    private val shortcutEngine = HomeShortcutEngine()
    private val homePageEngine = HomePageEngine()
    private val dockEngine = DockEngine()

    private val mutableState =
        MutableStateFlow(
            createInitialState(
                homeLayoutRepository = homeLayoutRepository,
                launcherSettingsRepository = launcherSettingsRepository,
                firstRunRepository = firstRunRepository,
                reducer = reducer,
            ).withInstalledApps(installedAppRepository, appCatalog)
                .withNotificationState(
                    notificationRepository = notificationRepository,
                    appNotificationCounter = appNotificationCounter,
                    appNotificationGrouper = appNotificationGrouper,
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
                .withInstalledApps(installedAppRepository, appCatalog)
                .withNotificationState(
                    notificationRepository = notificationRepository,
                    appNotificationCounter = appNotificationCounter,
                    appNotificationGrouper = appNotificationGrouper,
                    nowEpochMillis = epochMillisProvider.nowEpochMillis(),
                )
    }

    fun onSearchQueryChanged(query: String) {
        mutableState.value =
            mutableState.value.copy(
                searchQuery = query,
                searchResults = appCatalog.searchApps(mutableState.value.installedApps, query),
            )
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
            when (val result = shortcutEngine.applyEdit(action = action, layout = mutableState.value.homeLayout)) {
                is HomeShortcutResult.Updated -> mutableState.value.withHomeLayout(result.layout, homeLayoutRepository)
                is HomeShortcutResult.Rejected -> mutableState.value
            }
    }

    fun onHomePageEdited(action: LauncherShellAction) {
        mutableState.value =
            when (val result = homePageEngine.applyEdit(action = action, layout = mutableState.value.homeLayout)) {
                is HomePageEditResult.Updated -> mutableState.value.withHomeLayout(result.layout, homeLayoutRepository)
                is HomePageEditResult.Rejected -> mutableState.value
            }
    }

    fun onDockEdited(action: LauncherShellAction) {
        mutableState.value =
            when (val result = dockEngine.applyEdit(action = action, layout = mutableState.value.homeLayout)) {
                is DockEditResult.Updated -> mutableState.value.withHomeLayout(result.layout, homeLayoutRepository)
                is DockEditResult.Rejected -> mutableState.value
            }
    }

    fun onWallpaperSourceSelected(action: LauncherShellAction.SelectWallpaperSource) {
        mutableState.value =
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
    }

    private object NoopHomeLayoutRepository : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = null

        override fun saveHomeLayout(layout: HomeLayout) = Unit
    }

    private object NoopLauncherSettingsRepository : LauncherSettingsRepository {
        override fun loadLauncherSettings(): LauncherSettings? = null

        override fun saveLauncherSettings(settings: LauncherSettings) = Unit
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
    appCatalog: InstalledAppCatalog,
): LauncherShellState =
    appCatalog.visibleApps(installedAppRepository.installedApps()).let { visibleApps ->
        copy(
            installedApps = visibleApps,
            searchResults = appCatalog.searchApps(visibleApps, searchQuery),
        )
    }

private fun LauncherShellState.withHomeLayout(
    layout: HomeLayout,
    homeLayoutRepository: HomeLayoutRepository,
): LauncherShellState =
    copy(homeLayout = layout)
        .also { state -> homeLayoutRepository.saveHomeLayout(state.homeLayout) }

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

private fun HomePageEngine.applyEdit(
    action: LauncherShellAction,
    layout: HomeLayout,
): HomePageEditResult =
    when (action) {
        LauncherShellAction.EnterHomeEditMode ->
            enterPageEditMode(
                layout = layout,
                pageId = layout.selectedPageId,
            )

        LauncherShellAction.ExitHomeEditMode ->
            exitEditMode(layout = layout)

        LauncherShellAction.AddHomePage ->
            layout.newHomePage().let { page ->
                when (val result = addPage(layout = layout, page = page)) {
                    is HomePageEditResult.Updated -> selectPage(layout = result.layout, pageId = page.id)
                    is HomePageEditResult.Rejected -> result
                }
            }

        LauncherShellAction.SelectPreviousHomePage ->
            selectPageAtOffset(layout = layout, offset = -1)

        LauncherShellAction.SelectNextHomePage ->
            selectPageAtOffset(layout = layout, offset = 1)

        LauncherShellAction.MoveSelectedHomePageLeft ->
            moveSelectedPageByOffset(layout = layout, offset = -1)

        LauncherShellAction.MoveSelectedHomePageRight ->
            moveSelectedPageByOffset(layout = layout, offset = 1)

        LauncherShellAction.DeleteSelectedHomePage ->
            deletePage(
                layout = layout,
                pageId = layout.selectedPageId,
            )

        else -> HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)
    }

private fun HomePageEngine.selectPageAtOffset(
    layout: HomeLayout,
    offset: Int,
): HomePageEditResult =
    layout.pages.getOrNull(layout.selectedPageIndex + offset)
        ?.let { page -> selectPage(layout = layout, pageId = page.id) }
        ?: HomePageEditResult.Rejected(
            HomePageEditRejectionReason.INDEX_OUT_OF_BOUNDS,
        )

private fun HomePageEngine.moveSelectedPageByOffset(
    layout: HomeLayout,
    offset: Int,
): HomePageEditResult =
    (layout.selectedPageIndex + offset)
        .takeIf { targetIndex -> targetIndex in layout.pages.indices }
        ?.let { targetIndex ->
            movePage(
                layout = layout,
                pageId = layout.selectedPageId,
                targetIndex = targetIndex,
            )
        }
        ?: HomePageEditResult.Rejected(
            HomePageEditRejectionReason.INDEX_OUT_OF_BOUNDS,
        )

private fun HomeLayout.newHomePage(): LauncherPage =
    LauncherPage(
        id = nextHomePageId(),
        grid = settings.grid.dimensions,
    )

private fun HomeLayout.nextHomePageId(): LauncherPageId =
    generateSequence(2) { pageNumber -> pageNumber + 1 }
        .map { pageNumber -> LauncherPageId("home-$pageNumber") }
        .first { candidate -> pages.none { page -> page.id == candidate } }
