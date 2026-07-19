package com.riffle.app.launcher.overlay

import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.RecentAppUsage
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.settings.OverlayDockSettings

internal data class OverlayDockShortcuts(
    val pinnedShortcuts: List<AppShortcutItem>,
    val recentShortcuts: List<AppShortcutItem>,
    val recentAppsAccessRequired: Boolean,
)

internal fun OverlayDockSettings.visibleOverlayDockShortcuts(installedApps: List<InstalledApp>): List<AppShortcutItem> {
    val installedIdentities = installedApps.map { app -> app.identity }.toSet()

    return items.filter { item -> item.appIdentity in installedIdentities }
}

/** Resolves package-level usage records to launchable apps for the non-persistent recent-apps shelf. */
internal fun OverlayDockSettings.contentFor(
    installedApps: List<InstalledApp>,
    recentAppUsages: List<RecentAppUsage>,
    canReadRecentApps: Boolean = true,
): OverlayDockShortcuts =
    OverlayDockShortcuts(
        pinnedShortcuts = visibleOverlayDockShortcuts(installedApps),
        recentShortcuts = recentOverlayDockShortcuts(recentAppUsages, installedApps),
        recentAppsAccessRequired = !canReadRecentApps,
    )

private fun recentOverlayDockShortcuts(
    recentAppUsages: List<RecentAppUsage>,
    installedApps: List<InstalledApp>,
): List<AppShortcutItem> {
    val installedAppsByPackage = installedApps.associateBy { app -> app.identity.packageName }

    return recentAppUsages.asSequence()
        .distinctBy { usage -> usage.packageName }
        .mapNotNull { usage -> installedAppsByPackage[usage.packageName] }
        .take(MAX_RECENT_OVERLAY_DOCK_SHORTCUTS)
        .map { app ->
            AppShortcutItem(
                id = LauncherItemId("overlay-recent:${app.identity.packageName.value}"),
                appIdentity = app.identity,
                label = app.label,
            )
        }
        .toList()
}

/** Keeps the transient overlay shelf small enough to create promptly on high-usage devices. */
internal const val MAX_RECENT_OVERLAY_DOCK_SHORTCUTS = 6
