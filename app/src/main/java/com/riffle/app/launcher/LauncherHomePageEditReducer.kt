package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomePageEditResult
import com.riffle.core.domain.launcher.home.HomePageEngine

internal class LauncherHomePageEditReducer(
    private val homePageEngine: HomePageEngine = HomePageEngine(),
    private val homeLayoutRepository: HomeLayoutRepository,
) {
    fun reduce(
        state: LauncherShellState,
        action: LauncherShellAction,
    ): LauncherShellState =
        when {
            state.shouldEditSettingsTargetLayout(action) ->
                state.withSettingsHomePageEdit(
                    action = action,
                    homePageEngine = homePageEngine,
                    homeLayoutRepository = homeLayoutRepository,
                )

            action is LauncherShellAction.SelectLauncherViewMode ->
                state
                    .withSelectedHomeLayoutMode(action.mode, homeLayoutRepository)
                    .withHomeScreenLibraryApps(homeLayoutRepository)

            action is LauncherShellAction.SelectHomeLayoutDeviceClass ->
                state
                    .withSelectedHomeLayoutDeviceClass(
                        deviceClass = action.deviceClass,
                        availableDeviceClasses = action.availableDeviceClasses,
                        homeLayoutRepository = homeLayoutRepository,
                    )
                    .withHomeScreenLibraryApps(homeLayoutRepository)

            else ->
                when (
                    val result =
                        homePageEngine.applyEdit(
                            action = action,
                            layout = state.homeLayout,
                        )
                ) {
                    is HomePageEditResult.Updated ->
                        state
                            .withHomeLayout(result.layout, homeLayoutRepository)
                            .withHomeScreenLibraryApps(homeLayoutRepository)

                    is HomePageEditResult.Rejected -> state
                }
        }
}
