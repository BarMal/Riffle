package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.InstalledApp

internal fun searchGridApps(apps: List<InstalledApp>): List<InstalledApp> =
    apps.sortedWith(
        compareBy<InstalledApp> { app -> app.label.lowercase() }
            .thenBy { app -> app.identity.packageName.value }
            .thenBy { app -> app.identity.activityName.value }
            .thenBy { app -> app.identity.profile.id.value },
    )

internal fun searchGridResults(
    apps: List<InstalledApp>,
    shortcuts: List<AppShortcut>,
): List<SearchGridResult> =
    (
        apps.map { app -> SearchGridResult.App(app) } +
            shortcuts.map { shortcut -> SearchGridResult.Shortcut(shortcut) }
    ).sortedWith(
        compareBy<SearchGridResult> { result -> result.label.lowercase() }
            .thenBy { result -> result.appIdentity.packageName.value }
            .thenBy { result -> result.key },
    )

internal sealed interface SearchGridResult {
    val key: String
    val label: String
    val appIdentity: AppIdentity
    val action: LauncherShellAction

    data class App(
        val app: InstalledApp,
    ) : SearchGridResult {
        override val key: String = "app:${app.identity.stableSearchKey}"
        override val label: String = app.label
        override val appIdentity: AppIdentity = app.identity
        override val action: LauncherShellAction = LauncherShellAction.LaunchApp(app.identity)
    }

    data class Shortcut(
        val shortcut: AppShortcut,
    ) : SearchGridResult {
        override val key: String = "shortcut:${shortcut.appIdentity.stableSearchKey}:${shortcut.id.value}"
        override val label: String = shortcut.shortLabel
        override val appIdentity: AppIdentity = shortcut.appIdentity
        override val action: LauncherShellAction = LauncherShellAction.LaunchAppShortcut(shortcut)
    }
}

private val AppIdentity.stableSearchKey: String
    get() = "${packageName.value}/${activityName.value}/${profile.id.value}"
