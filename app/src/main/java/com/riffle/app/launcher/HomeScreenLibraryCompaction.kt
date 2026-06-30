package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.containsHomeApp

internal fun HomeLayout.withCompactedLibraryApps(apps: List<InstalledApp>): HomeLayout {
    val libraryShortcuts = pages.flatMap { page -> page.libraryShortcutsInGridOrder() }
    val libraryAppIdentities = libraryShortcuts.map { item -> item.appIdentity }.toSet()
    val compactBase = withoutHomeScreenLibraryApps().copy(viewMode = LauncherViewMode.HOME_SCREEN_LIBRARY)
    val missingLibraryShortcuts =
        apps
            .filterNot { app -> compactBase.containsHomeApp(app.identity) || app.identity in libraryAppIdentities }
            .map { app -> app.libraryShortcut() }
    val compactedLayout =
        (libraryShortcuts + missingLibraryShortcuts)
            .fold(compactBase) { layout, shortcut -> layout.placeLibraryShortcut(shortcut) }

    return compactedLayout.copy(
        selectedPageId =
            compactedLayout.pages.firstOrNull { page -> page.id == selectedPageId }?.id
                ?: compactedLayout.selectedPageId,
    )
}

private fun LauncherPage.libraryShortcutsInGridOrder(): List<AppShortcutItem> =
    items
        .filterIsInstance<AppShortcutItem>()
        .filter { item -> item.isLibraryApp }
        .sortedBy { item -> item.placement?.cell?.let(grid::indexOf) ?: Int.MAX_VALUE }

private fun GridDimensions.indexOf(cell: GridCell): Int = (cell.row * columns) + cell.column
