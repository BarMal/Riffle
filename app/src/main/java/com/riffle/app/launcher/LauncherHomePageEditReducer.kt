package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeEditMode
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
            action is LauncherShellAction.OpenDefaultHome ->
                state.withDefaultHomeOpened(homeLayoutRepository).withHomeScreenLibraryApps(homeLayoutRepository)

            action is LauncherShellAction.LeaveLibrary ->
                state.withLibraryLeft(
                    trigger = action.trigger,
                    homeLayoutRepository = homeLayoutRepository,
                    viewModeAvailability = viewModeAvailability,
                )

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
                ).refreshSettingsGeneratedPageAfterTypeSelection(action, homeLayoutRepository)

            action is LauncherShellAction.SelectLauncherViewMode ->
                state
                    .withSelectedHomeLayoutMode(
                        mode = action.mode,
                        homeLayoutRepository = homeLayoutRepository,
                        viewModeAvailability = viewModeAvailability,
                    )
                    .withHomeScreenLibraryApps(homeLayoutRepository)

            action is LauncherShellAction.SelectModeRingModeEnabled ->
                state
                    .withSettingsModeRingEdit(homeLayoutRepository) { ring ->
                        ring.withModeEnabled(mode = action.mode, enabled = action.enabled)
                    }
                    .withHomeScreenLibraryApps(homeLayoutRepository)

            action is LauncherShellAction.MoveModeRingMode ->
                state.withSettingsModeRingEdit(homeLayoutRepository) { ring ->
                    ring.withModeMoved(mode = action.mode, offset = action.offset)
                }

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
                            .refreshGeneratedPageAfterTypeSelection(action, homeLayoutRepository)

                    is HomePageEditResult.Rejected -> state
                }
        }

    private fun LauncherShellState.refreshGeneratedPageAfterTypeSelection(
        action: LauncherShellAction,
        homeLayoutRepository: HomeLayoutRepository,
    ): LauncherShellState =
        if (action is LauncherShellAction.SelectSelectedHomePageType) {
            withRefreshedGeneratedPages(homeLayoutRepository)
        } else {
            this
        }

    private fun LauncherShellState.refreshSettingsGeneratedPageAfterTypeSelection(
        action: LauncherShellAction,
        homeLayoutRepository: HomeLayoutRepository,
    ): LauncherShellState =
        if (action is LauncherShellAction.SelectSelectedHomePageType) {
            val settingsLayout = settingsTargetLayout
            val refreshedLayout = refreshedGeneratedPages(settingsLayout)
            if (refreshedLayout == settingsLayout) {
                this
            } else {
                withSettingsTargetLayout(refreshedLayout, homeLayoutRepository)
            }
        } else {
            this
        }
}

/**
 * Home always returns to the first page of whichever layout/view mode is already on screen --
 * never a mode switch. See #1176.
 *
 * Works directly off the in-memory `homeLayoutSet`, like every layout reducer since #1198: storage
 * is written behind and never read back, so whatever disk still remembers cannot pull Home onto an
 * older mode.
 */
private fun LauncherShellState.withDefaultHomeOpened(homeLayoutRepository: HomeLayoutRepository): LauncherShellState {
    val resetLayout =
        homeLayout.copy(
            selectedPageId = homeLayout.pages.firstOrNull()?.id ?: homeLayout.selectedPageId,
            editMode = HomeEditMode.Browsing,
        )
    val layoutSet = homeLayoutSet.withActiveLayout(resetLayout)
    homeLayoutRepository.saveHomeLayoutSet(layoutSet)
    return copy(homeLayout = layoutSet.activeLayout, homeLayoutSet = layoutSet)
}
