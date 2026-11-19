package com.riffle.core.domain.launcher.icons

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage

/**
 * The names the user gave single apps (Change icon > Rename), applied to what is drawn: the installed apps (the
 * Library, search, the pickers), the app icons on Home, in folders and in the Dock. It maps a copy for display; the
 * stored layout keeps the app's own label, so resetting a name is only removing the override. A shortcut of an app
 * (an `appShortcutId`) keeps its own label: the rename is of the app. Pure.
 */
fun LauncherShellState.withAppLabelOverrides(icons: IconStyleSettings): LauncherShellState {
    val names = icons.overrides.filterValues { it.label != null }
    if (names.isEmpty()) return this
    val rename: (AppShortcutItem) -> AppShortcutItem = { item ->
        names[item.appIdentity]?.label?.takeIf { item.appShortcutId == null }?.let { item.copy(label = it) } ?: item
    }
    val mapItem: (LauncherItem) -> LauncherItem = { item ->
        when (item) {
            is AppShortcutItem -> rename(item)
            is FolderItem -> item.copy(items = item.items.map(rename))
            else -> item
        }
    }
    val mapPage: (LauncherPage) -> LauncherPage = { page -> page.copy(items = page.items.map(mapItem)) }
    return copy(
        installedApps = installedApps.map { app -> names[app.identity]?.label?.let { app.copy(label = it) } ?: app },
        homeLayout =
            homeLayout.copy(
                pages = homeLayout.pages.map(mapPage),
                dock =
                    homeLayout.dock.copy(
                        items = homeLayout.dock.items.map(mapItem),
                        panel = homeLayout.dock.panel?.let(mapPage),
                    ),
            ),
    )
}
