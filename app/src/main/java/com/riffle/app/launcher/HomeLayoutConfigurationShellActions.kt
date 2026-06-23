package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomePageEditRejectionReason
import com.riffle.core.domain.launcher.home.HomePageEditResult
import com.riffle.core.domain.launcher.home.HomePageEngine

internal fun HomePageEngine.applyHomeLayoutConfigurationEdit(
    action: LauncherShellAction,
    layout: HomeLayout,
): HomePageEditResult =
    when (action) {
        is LauncherShellAction.SelectHomeGridDimensions ->
            updateGridDimensions(layout = layout, dimensions = action.dimensions)

        is LauncherShellAction.SelectLauncherViewMode ->
            HomePageEditResult.Updated(layout.copy(viewMode = action.mode))

        else -> HomePageEditResult.Rejected(HomePageEditRejectionReason.PAGE_NOT_FOUND)
    }
