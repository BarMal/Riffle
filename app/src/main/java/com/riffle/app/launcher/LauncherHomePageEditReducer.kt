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

            action is LauncherShellAction.ExitAdaptiveStage ->
                state
                    .withExitedAdaptiveStage(
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
            val settingsLayout = settingsTargetLayout(homeLayoutRepository)
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
 * Deliberately does not go through [withHomeLayout] (and its disk reload of the layout set):
 * that reload discards the in-memory `homeLayoutSet` for whatever is currently persisted, then
 * stamps the resulting layout with *that reloaded set's* active view mode -- so if the persisted
 * active key were ever a step behind the mode actually on screen (in memory), pressing Home would
 * silently fall back to whatever mode disk still remembers. Updating `homeLayoutSet` directly off
 * the state already held in memory keeps the layout that's rewritten in step with the mode that's
 * actually showing, no matter what disk has.
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
