package com.riffle.app.launcher

import androidx.lifecycle.ViewModel
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.LauncherShellStateReducer
import com.riffle.core.domain.launcher.ShellNavigationAction
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.InstalledAppCatalog
import com.riffle.core.domain.launcher.apps.InstalledAppRepository
import com.riffle.core.domain.launcher.home.HomeShortcutEngine
import com.riffle.core.domain.launcher.home.HomeShortcutResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LauncherShellViewModel(
    private val firstRunRepository: FirstRunRepository,
    private val installedAppRepository: InstalledAppRepository = InstalledAppRepository { emptyList() },
    private val reducer: LauncherShellStateReducer = LauncherShellStateReducer(),
    private val appCatalog: InstalledAppCatalog = InstalledAppCatalog(),
    private val shortcutEngine: HomeShortcutEngine = HomeShortcutEngine(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(createInitialState().withInstalledApps())
    val state: StateFlow<LauncherShellState> = mutableState.asStateFlow()

    fun onHomeRoleStatusChanged(homeRoleStatus: HomeRoleStatus) {
        mutableState.value =
            reducer.homeRoleChanged(
                currentState = mutableState.value,
                homeRoleStatus = homeRoleStatus,
            ).also(::persistCompletedFirstRun)
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
        mutableState.value = mutableState.value.withInstalledApps()
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
                is HomeShortcutResult.Updated -> mutableState.value.copy(homeLayout = result.layout)

                is HomeShortcutResult.Rejected -> mutableState.value
            }
    }

    private fun createInitialState(): LauncherShellState =
        if (firstRunRepository.isFirstRunComplete()) {
            reducer.firstRunCompleted(LauncherShellState())
        } else {
            LauncherShellState()
        }

    private fun LauncherShellState.withInstalledApps(): LauncherShellState =
        appCatalog.visibleApps(installedAppRepository.installedApps()).let { visibleApps ->
            copy(
                installedApps = visibleApps,
                searchResults = appCatalog.searchApps(visibleApps, searchQuery),
            )
        }

    private fun persistCompletedFirstRun(state: LauncherShellState) {
        if (state.shouldShowEmptyHome) {
            firstRunRepository.setFirstRunComplete()
        }
    }
}
