package com.riffle.app.launcher

import androidx.lifecycle.ViewModel
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.LauncherShellStateReducer
import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomePageEditResult
import com.riffle.core.domain.launcher.home.HomePageEngine
import com.riffle.core.domain.launcher.home.HomeShortcutEngine
import com.riffle.core.domain.launcher.home.HomeShortcutResult
import com.riffle.core.domain.launcher.home.LauncherItemId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LauncherShellViewModel(
    private val firstRunRepository: FirstRunRepository,
    private val installedAppRepository: InstalledAppRepository = InstalledAppRepository { emptyList() },
    private val reducer: LauncherShellStateReducer = LauncherShellStateReducer(),
    private val appCatalog: InstalledAppCatalog = InstalledAppCatalog(),
    private val shortcutEngine: HomeShortcutEngine = HomeShortcutEngine(),
    private val homePageEngine: HomePageEngine = HomePageEngine(),
    private val homeLayoutRepository: HomeLayoutRepository = NoopHomeLayoutRepository,
) : ViewModel() {
    private val mutableState =
        MutableStateFlow(
            createInitialState(
                homeLayoutRepository = homeLayoutRepository,
                firstRunRepository = firstRunRepository,
                reducer = reducer,
            ).withInstalledApps(installedAppRepository, appCatalog),
        )
    val state: StateFlow<LauncherShellState> = mutableState.asStateFlow()

    fun onHomeRoleStatusChanged(homeRoleStatus: HomeRoleStatus) {
        mutableState.value =
            reducer.homeRoleChanged(
                currentState = mutableState.value,
                homeRoleStatus = homeRoleStatus,
            ).also { state -> persistCompletedFirstRun(state, firstRunRepository) }
    }

    fun onDefaultHomeRequestStarted() {
        mutableState.value = reducer.defaultHomeRequestStarted(mutableState.value)
    }

    fun onFirstRunCompleted() {
        firstRunRepository.setFirstRunComplete()
        mutableState.value = reducer.firstRunCompleted(mutableState.value)
    }

    fun onNavigationActionSelected(action: ShellNavigationAction) {
        mutableState.value =
            reducer.navigationActionSelected(
                currentState = mutableState.value,
                action = action,
            )
    }

    fun refreshInstalledApps() {
        mutableState.value = mutableState.value.withInstalledApps(installedAppRepository, appCatalog)
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

    fun onEnterHomeEditMode() {
        mutableState.value =
            when (
                val result =
                    homePageEngine.enterPageEditMode(
                        layout = mutableState.value.homeLayout,
                        pageId = mutableState.value.homeLayout.selectedPageId,
                    )
            ) {
                is HomePageEditResult.Updated -> mutableState.value.withHomeLayout(result.layout, homeLayoutRepository)
                is HomePageEditResult.Rejected -> mutableState.value
            }
    }

    fun onExitHomeEditMode() {
        mutableState.value =
            when (val result = homePageEngine.exitEditMode(layout = mutableState.value.homeLayout)) {
                is HomePageEditResult.Updated -> mutableState.value.withHomeLayout(result.layout, homeLayoutRepository)
                is HomePageEditResult.Rejected -> mutableState.value
            }
    }

    fun onRemoveHomeShortcut(itemId: LauncherItemId) {
        mutableState.value =
            when (val result = shortcutEngine.removeShortcutFromSelectedPage(mutableState.value.homeLayout, itemId)) {
                is HomeShortcutResult.Updated -> mutableState.value.withHomeLayout(result.layout, homeLayoutRepository)
                is HomeShortcutResult.Rejected -> mutableState.value
            }
    }

    private object NoopHomeLayoutRepository : HomeLayoutRepository {
        override fun loadHomeLayout(): HomeLayout? = null

        override fun saveHomeLayout(layout: HomeLayout) = Unit
    }
}

private fun createInitialState(
    homeLayoutRepository: HomeLayoutRepository,
    firstRunRepository: FirstRunRepository,
    reducer: LauncherShellStateReducer,
): LauncherShellState =
    LauncherShellState(homeLayout = homeLayoutRepository.loadHomeLayout() ?: HomeLayoutDefaults.standard())
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
