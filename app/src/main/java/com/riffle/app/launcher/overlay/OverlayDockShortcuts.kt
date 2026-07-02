package com.riffle.app.launcher.overlay

import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.settings.OverlayDockSettings

internal fun OverlayDockSettings.visibleOverlayDockShortcuts(installedApps: List<InstalledApp>): List<AppShortcutItem> {
    val installedIdentities = installedApps.map { app -> app.identity }.toSet()

    return items.filter { item -> item.appIdentity in installedIdentities }
}
