package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import kotlinx.coroutines.Job

internal class LauncherAppStateActionCoordinator(
    private val appVisibilityRepository: AppVisibilityRepository,
    private val appListActionReducer: LauncherAppListActionReducer,
    private val widgetPickerActionReducer: LauncherWidgetPickerActionReducer,
    private val currentState: () -> LauncherShellState,
    private val updateState: (LauncherShellState) -> Unit,
    private val refreshInstalledApps: (() -> Unit) -> Job,
    private val refreshWidgetProviders: () -> Job,
) {
    fun handle(action: LauncherShellAction): Job? {
        var launchedRefresh: Job? = null
        val updatedState =
            when (val route = action.launcherAppActionRoute()) {
                LauncherAppActionRoute.RefreshInstalledApps -> {
                    launchedRefresh = refreshInstalledApps {}
                    currentState()
                }

                is LauncherAppActionRoute.AppVisibilityState -> {
                    launchedRefresh =
                        refreshInstalledApps {
                            route.action.applyAppVisibilityAction(appVisibilityRepository)
                        }
                    currentState()
                }

                is LauncherAppActionRoute.AppListState ->
                    appListActionReducer.reduce(currentState(), route.action) ?: currentState()

                is LauncherAppActionRoute.WidgetPickerState -> {
                    if (route.action == LauncherShellAction.OpenWidgetPicker) {
                        launchedRefresh = refreshWidgetProviders()
                    }
                    widgetPickerActionReducer.reduce(currentState(), route.action) ?: currentState()
                }

                else -> currentState()
            }

        updateState(updatedState)
        return launchedRefresh
    }
}
