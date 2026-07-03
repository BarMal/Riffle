package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayoutRepository

class LauncherBackupExportCoordinator(
    private val homeLayoutRepository: HomeLayoutRepository,
    private val currentState: () -> LauncherShellState,
    private val epochMillisProvider: EpochMillisProvider = SystemEpochMillisProvider,
) {
    fun currentBackupDocument(): LauncherBackupDocument =
        currentState().let { state ->
            launcherBackupDocument(
                storedLayoutSet = homeLayoutRepository.loadHomeLayoutSet(),
                activeLayout = state.homeLayout,
                launcherSettings = state.launcherSettings,
                exportedAtEpochMillis = epochMillisProvider.nowEpochMillis(),
            )
        }
}
