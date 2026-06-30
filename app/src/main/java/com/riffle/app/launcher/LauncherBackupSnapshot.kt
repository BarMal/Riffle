package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository

fun launcherBackupDocument(
    storedLayoutSet: HomeLayoutSet?,
    activeLayout: HomeLayout,
    launcherSettings: LauncherSettings,
): LauncherBackupDocument =
    LauncherBackupDocument(
        homeLayoutSet =
            (storedLayoutSet ?: HomeLayoutSet.fromLayout(activeLayout))
                .withActiveLayout(activeLayout),
        launcherSettings = launcherSettings,
    )

fun LauncherShellState.withImportedBackup(
    document: LauncherBackupDocument,
    homeLayoutRepository: HomeLayoutRepository,
    launcherSettingsRepository: LauncherSettingsRepository,
): LauncherShellState {
    homeLayoutRepository.saveHomeLayoutSet(document.homeLayoutSet)
    launcherSettingsRepository.saveLauncherSettings(document.launcherSettings)

    return copy(
        homeLayout = document.homeLayoutSet.activeLayout,
        launcherSettings = document.launcherSettings,
    )
}
