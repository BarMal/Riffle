package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockEditRejectionReason
import com.riffle.core.domain.launcher.home.DockEditResult
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.GridReflowEngine
import com.riffle.core.domain.launcher.home.GridReflowResult
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.containsHomeApp
import com.riffle.core.domain.launcher.home.workspaceGrid

/**
 * Whether this layout's library pages are packed edge-to-edge with no spare cells.
 *
 * A compacted layout never has a free cell for [GridReflowEngine] to displace a stranded item
 * into, so moving the dock onto a side edge -- which needs the workspace a column narrower --
 * has to repack from scratch via [repackedForDockPosition] instead of the generic reflow that
 * [com.riffle.core.domain.launcher.home.DockConfigurationEngine] runs for every other layout.
 */
internal val HomeLayout.usesCompactLibraryPacking: Boolean
    get() = viewMode == LauncherViewMode.HOME_SCREEN_LIBRARY && settings.grid.compactLibraryPages

/**
 * Moves the dock to [position] by repacking the library instead of reflowing it.
 *
 * The generic reflow only ever displaces a stranded item into a free cell an existing page
 * already has, and a compacted library page never has one -- so it would reject every side-edge
 * move on sight. Compaction itself can't get stuck the same way: it rebuilds every library page
 * from scratch at whatever width it is given, adding pages as needed, so it always finds room.
 * Non-library items (a manually placed folder or widget) are not compaction's to move, so those
 * still go through the ordinary reflow first, against the library apps stripped out -- which
 * leaves it just the handful of manual items to fit, not the whole library.
 */
internal fun HomeLayout.repackedForDockPosition(
    position: DockPosition,
    apps: List<InstalledApp>,
): DockEditResult {
    val strippedOfLibraryApps =
        withoutHomeScreenLibraryApps().copy(dock = dock.copy(position = position))

    return when (
        val reflowed =
            GridReflowEngine().reflowToGrid(strippedOfLibraryApps, strippedOfLibraryApps.workspaceGrid)
    ) {
        is GridReflowResult.Updated ->
            DockEditResult.Updated(
                reflowed.layout.withCompactedLibraryApps(apps).withoutTrailingEmptyLibraryPages(),
            )

        is GridReflowResult.Rejected -> DockEditResult.Rejected(DockEditRejectionReason.NO_ROOM_FOR_GRID)
    }
}

internal fun HomeLayout.withCompactedLibraryApps(apps: List<InstalledApp>): HomeLayout {
    val allLibraryShortcuts = pages.flatMap { page -> page.libraryShortcuts() }
    // A hidden or uninstalled app's shortcut stays in the layout until something prunes it, and the
    // visibility filter that strips it from what's drawn runs after compaction, not before -- so an
    // unfiltered repack would still hand that stale item a real cell, leaving a visible hole exactly
    // where it used to be once rendering drops it again. Compaction is the one place that decides
    // the pack, so it is the one place that has to leave stale placements out of it.
    //
    // apps is the caller's installed-app snapshot, refreshed asynchronously -- it starts out empty
    // before the first refresh lands. An empty apps list means "not loaded yet", not "everything is
    // gone", so it must not be read as nothing being visible: that would empty every library page
    // the instant compaction next ran, before the real refresh had a chance to repopulate it.
    val libraryShortcuts =
        if (apps.isEmpty()) {
            allLibraryShortcuts
        } else {
            val visibleAppIdentities = apps.map { app -> app.identity }.toSet()
            allLibraryShortcuts.filter { shortcut -> shortcut.appIdentity in visibleAppIdentities }
        }
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
