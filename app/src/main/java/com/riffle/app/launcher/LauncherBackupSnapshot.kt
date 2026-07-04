package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository

fun launcherBackupDocument(
    storedLayoutSet: HomeLayoutSet?,
    activeLayout: HomeLayout,
    launcherSettings: LauncherSettings,
    hiddenAppIdentities: Set<AppIdentity> = emptySet(),
    exportedAtEpochMillis: Long? = null,
): LauncherBackupDocument =
    LauncherBackupDocument(
        homeLayoutSet =
            (storedLayoutSet ?: HomeLayoutSet.fromLayout(activeLayout))
                .withActiveLayout(activeLayout),
        launcherSettings = launcherSettings,
        hiddenAppIdentities = hiddenAppIdentities,
        exportedAtEpochMillis = exportedAtEpochMillis,
    )

fun LauncherShellState.withImportedBackup(
    document: LauncherBackupDocument,
    homeLayoutRepository: HomeLayoutRepository,
    launcherSettingsRepository: LauncherSettingsRepository,
    appVisibilityRepository: AppVisibilityRepository,
): LauncherShellState {
    homeLayoutRepository.saveHomeLayoutSet(document.homeLayoutSet)
    launcherSettingsRepository.saveLauncherSettings(document.launcherSettings)
    appVisibilityRepository.replaceHiddenAppIdentities(document.hiddenAppIdentities)

    return copy(
        homeLayout = document.homeLayoutSet.activeLayout,
        homeLayoutSet = document.homeLayoutSet,
        settingsLayoutDeviceClass = document.homeLayoutSet.activeKey.deviceClass,
        launcherSettings = document.launcherSettings,
    )
}

internal fun AppVisibilityRepository.replaceHiddenAppIdentities(identities: Set<AppIdentity>) {
    val currentIdentities = hiddenAppIdentities()
    currentIdentities
        .filterNot { identity -> identity in identities }
        .forEach(::showApp)
    identities
        .filterNot { identity -> identity in currentIdentities }
        .forEach(::hideApp)
}
