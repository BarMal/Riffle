package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.containsHomeApp

internal fun HomeLayout.withCompactedLibraryApps(apps: List<InstalledApp>): HomeLayout {
    val visibleAppIdentities = apps.map { app -> app.identity }.toSet()
    // A hidden or uninstalled app's shortcut stays in the layout until something prunes it, and the
    // visibility filter that strips it from what's drawn runs after compaction, not before -- so an
    // unfiltered repack would still hand that stale item a real cell, leaving a visible hole exactly
    // where it used to be once rendering drops it again. Compaction is the one place that decides
    // the pack, so it is the one place that has to leave stale placements out of it.
    val libraryShortcuts =
        pages
            .flatMap { page -> page.libraryShortcuts() }
            .filter { shortcut -> shortcut.appIdentity in visibleAppIdentities }
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
