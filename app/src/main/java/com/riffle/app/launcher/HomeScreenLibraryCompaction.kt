package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.containsHomeApp

internal fun HomeLayout.withCompactedLibraryApps(apps: List<InstalledApp>): HomeLayout {
    val libraryShortcuts = pages.flatMap { page -> page.libraryShortcuts() }
    val libraryAppIdentities = libraryShortcuts.map { item -> item.appIdentity }.toSet()
    val compactBase = withoutHomeScreenLibraryApps().copy(viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY)
    val missingLibraryShortcuts =
        apps
            .filterNot { app -> compactBase.containsHomeApp(app.identity) || app.identity in libraryAppIdentities }
            .map { app -> app.libraryShortcut() }
    val compactedLayout =
        (libraryShortcuts + missingLibraryShortcuts)
            .sortedBy { shortcut -> shortcut.label.lowercase() }
            .fold(compactBase) { layout, shortcut -> layout.placeLibraryShortcut(shortcut) }

    return compactedLayout.copy(
        selectedPageId =
            compactedLayout.pages.firstOrNull { page -> page.id == selectedPageId }?.id
                ?: compactedLayout.selectedPageId,
    )
}

private fun LauncherPage.libraryShortcuts(): List<AppShortcutItem> =
    items
        .filterIsInstance<AppShortcutItem>()
        .filter { item -> item.isLibraryApp }
