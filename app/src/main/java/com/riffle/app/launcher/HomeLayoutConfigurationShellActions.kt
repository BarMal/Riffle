package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomePageEditRejectionReason
import com.riffle.core.domain.launcher.home.HomePageEditResult
import com.riffle.core.domain.launcher.home.HomePageEngine
import com.riffle.core.domain.launcher.home.LauncherViewMode

internal fun HomePageEngine.applyHomeLayoutConfigurationEdit(
    action: LauncherShellAction,
    layout: HomeLayout,
): HomePageEditResult =
    when (action) {
        is LauncherShellAction.SelectHomeGridDimensions ->
            updateGridDimensions(layout = layout, dimensions = action.dimensions)

        is LauncherShellAction.SelectLauncherViewMode ->
            HomePageEditResult.Updated(layout.withLauncherViewMode(action.mode))

        else -> HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)
    }

private fun HomeLayout.withLauncherViewMode(mode: LauncherViewMode): HomeLayout =
    copy(viewMode = mode).let { updatedLayout ->
        when (mode) {
            LauncherViewMode.HOME_SCREEN_LIBRARY -> updatedLayout
            LauncherViewMode.STANDARD_APP_DRAWER,
            LauncherViewMode.CARD_INTERFACE,
            -> updatedLayout.withoutHomeScreenLibraryApps()
        }
    }
