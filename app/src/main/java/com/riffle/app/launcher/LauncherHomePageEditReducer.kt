package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomePageEditResult
import com.riffle.core.domain.launcher.home.HomePageEngine
import com.riffle.core.domain.launcher.home.LauncherTemplateCatalog
import com.riffle.core.domain.launcher.home.LauncherTemplateCatalogDefaults
import com.riffle.core.domain.launcher.home.LauncherViewModeAvailability

internal class LauncherHomePageEditReducer(
    private val homePageEngine: HomePageEngine = HomePageEngine(),
    private val homeLayoutRepository: HomeLayoutRepository,
    private val viewModeAvailability: LauncherViewModeAvailability = LauncherViewModeAvailability(),
    private val templateCatalog: LauncherTemplateCatalog = LauncherTemplateCatalogDefaults.catalog,
) {
    fun reduce(
        state: LauncherShellState,
        action: LauncherShellAction,
    ): LauncherShellState =
        when {
            action is LauncherShellAction.SelectLauncherTemplate ->
                state.withSelectedHomeLayoutTemplate(
                    templateId = action.templateId,
                    mode = action.mode,
                    homeLayoutRepository = homeLayoutRepository,
                    viewModeAvailability = viewModeAvailability,
                    templateCatalog = templateCatalog,
                ).withHomeScreenLibraryApps(homeLayoutRepository)

            state.shouldEditSettingsTargetLayout(action) ->
                state.withSettingsHomePageEdit(
                    action = action,
                    homePageEngine = homePageEngine,
                    homeLayoutRepository = homeLayoutRepository,
                )

            action is LauncherShellAction.SelectLauncherViewMode ->
                state
                    .withSelectedHomeLayoutMode(
                        mode = action.mode,
                        homeLayoutRepository = homeLayoutRepository,
                        viewModeAvailability = viewModeAvailability,
                    )
                    .withHomeScreenLibraryApps(homeLayoutRepository)

            action is LauncherShellAction.SelectHomeLayoutDeviceClass ->
                state
                    .withSelectedHomeLayoutDeviceClass(
                        deviceClass = action.deviceClass,
                        availableDeviceClasses = action.availableDeviceClasses,
                        homeLayoutRepository = homeLayoutRepository,
                        viewModeAvailability = viewModeAvailability,
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
