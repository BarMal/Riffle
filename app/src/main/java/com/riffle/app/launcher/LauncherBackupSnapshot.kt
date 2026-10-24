package com.riffle.app.launcher

import com.riffle.app.launcher.pool.PoolHomeLayoutRepository
import com.riffle.app.launcher.rss.SettingsBackedConfiguredFeedSource
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
    // Once the home renders from the pool, its restore carries the pool section along (see PoolHomeSync.restore).
    val layoutSet =
        (homeLayoutRepository as? PoolHomeLayoutRepository)
            ?.restoreFromBackup(document.homeLayoutSet, document.pool, document.homeFromPool)
            ?: document.homeLayoutSet.also(homeLayoutRepository::saveHomeLayoutSet)
    launcherSettingsRepository.saveLauncherSettings(document.launcherSettings)
    appVisibilityRepository.replaceHiddenAppIdentities(document.hiddenAppIdentities)

    return copy(
        homeLayout = layoutSet.activeLayout,
        homeLayoutSet = layoutSet,
        settingsLayoutDeviceClass = layoutSet.activeKey.deviceClass,
        launcherSettings = document.launcherSettings,
        // Restore never triggers a network refresh; this only assigns configuration, with an
        // empty cache by construction since the article cache is never part of the backup document.
        configuredFeeds = SettingsBackedConfiguredFeedSource(document.launcherSettings).configuredFeeds(),
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
