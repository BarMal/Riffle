package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.search.LauncherSearchProvider
import com.riffle.core.domain.launcher.search.LauncherSearchResult

/** The launcher's own settings pages that match [query]; none while Settings > Library > Search turns them off. */
internal fun LauncherShellState.searchSettingsResults(query: String): List<LauncherSearchResult.Setting> =
    if (!launcherSettings.search.launcherSettings) {
        emptyList()
    } else {
        matchingLauncherSettings(query)
    }

private fun LauncherShellState.matchingLauncherSettings(query: String): List<LauncherSearchResult.Setting> =
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
                        developerVisibility = launcherSettings.diagnostics.developerVisibility(),
                    ),
                ),
        )
        .filterIsInstance<LauncherSearchResult.Setting>()
