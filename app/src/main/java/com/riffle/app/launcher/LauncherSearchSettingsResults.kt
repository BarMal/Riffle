package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.search.LauncherSearchProvider
import com.riffle.core.domain.launcher.search.LauncherSearchResult

internal fun LauncherShellState.searchSettingsResults(query: String): List<LauncherSearchResult.Setting> =
    LauncherSearchProvider()
        .search(
            query = query,
            apps = emptyList(),
            settingsEntries =
                settingsLauncherSearchEntries(
                    SettingsOverviewStatus(
                        homeRoleStatus = homeRoleStatus,
                        notificationAccessStatus = notificationAccessStatus,
                        overlayDockPermissionStatus = overlayDockPermissionStatus,
                        hiddenAppCount = hiddenApps.size,
                    ),
                ),
        )
        .filterIsInstance<LauncherSearchResult.Setting>()
