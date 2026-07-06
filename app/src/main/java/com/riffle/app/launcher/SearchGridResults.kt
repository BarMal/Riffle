package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.search.LauncherSearchResult

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
    settings: List<LauncherSearchResult.Setting> = emptyList(),
    webQuery: String = "",
): List<SearchGridResult> =
    (
        apps.map { app -> SearchGridResult.App(app) } +
            shortcuts.map { shortcut -> SearchGridResult.Shortcut(shortcut) } +
            settings.map { setting -> SearchGridResult.Setting(setting) } +
            searchWebGridResult(webQuery)
    ).sortedWith(
        compareBy<SearchGridResult> { result -> result.label.lowercase() }
            .thenBy { result -> result.sortKey }
            .thenBy { result -> result.key },
    )

internal fun searchWebGridResult(query: String): List<SearchGridResult.Web> {
    val trimmedQuery = query.trim()
    return if (trimmedQuery.isEmpty()) {
        emptyList()
    } else {
        listOf(SearchGridResult.Web(trimmedQuery))
    }
}

internal sealed interface SearchGridResult {
    val key: String
    val label: String
    val action: LauncherShellAction
    val sortKey: String

    data class App(
        val app: InstalledApp,
    ) : SearchGridResult {
        override val key: String = "app:${app.identity.stableSearchKey}"
        override val label: String = app.label
        override val action: LauncherShellAction = LauncherShellAction.LaunchApp(app.identity)
        override val sortKey: String = app.identity.packageName.value
    }

    data class Shortcut(
        val shortcut: AppShortcut,
    ) : SearchGridResult {
        override val key: String = "shortcut:${shortcut.appIdentity.stableSearchKey}:${shortcut.id.value}"
        override val label: String = shortcut.shortLabel
        override val action: LauncherShellAction = LauncherShellAction.LaunchAppShortcut(shortcut)
        override val sortKey: String = shortcut.appIdentity.packageName.value
    }

    data class Web(
        val query: String,
    ) : SearchGridResult {
        override val key: String = "web:$query"
        override val label: String = "Search Google for $query"
        override val action: LauncherShellAction = LauncherShellAction.SearchWeb(query)
        override val sortKey: String = query
    }

    data class Setting(
        val setting: LauncherSearchResult.Setting,
    ) : SearchGridResult {
        override val key: String = setting.key
        override val label: String = setting.title
        override val action: LauncherShellAction = LauncherShellAction.OpenSettings
        override val sortKey: String = setting.entry.id.value
    }
}

private val AppIdentity.stableSearchKey: String
    get() = "${packageName.value}/${activityName.value}/${profile.id.value}"
