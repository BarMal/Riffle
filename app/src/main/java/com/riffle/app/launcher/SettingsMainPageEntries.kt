package com.riffle.app.launcher

import com.riffle.core.domain.launcher.HomeRoleStatus

internal fun settingsMainPageEntries(status: SettingsOverviewStatus = SettingsOverviewStatus()) =
    homeSettingsPageEntries() +
        interactionSettingsPageEntries() +
        appSettingsPageEntries(status) +
        systemSettingsPageEntries(status)

private fun homeSettingsPageEntries(): List<SettingsPageEntry> =
    listOf(
        SettingsPageEntry(
            label = "Layout",
            subtitle = "Home mode, grid, and labels",
            page = SettingsPage.LAYOUT,
            group = SettingsPageGroup.HOME,
        ),
        SettingsPageEntry(
            label = "Dock",
            subtitle = "Home dock visibility, slots, and sizing",
            page = SettingsPage.DOCK,
            group = SettingsPageGroup.HOME,
        ),
        SettingsPageEntry(
            label = "Appearance",
            subtitle = "Wallpaper and system bars",
            page = SettingsPage.APPEARANCE,
            group = SettingsPageGroup.HOME,
            searchAliases = listOf("status bar", "navigation bar", "fullscreen"),
        ),
        SettingsPageEntry(
            label = "Floating dock",
            subtitle = "Overlay handle and floating shortcuts",
            page = SettingsPage.FLOATING_DOCK,
            group = SettingsPageGroup.HOME,
        ),
    )

private fun interactionSettingsPageEntries(): List<SettingsPageEntry> =
    listOf(
        SettingsPageEntry(
            label = "Gestures",
            subtitle = "Home swipe actions",
            page = SettingsPage.GESTURES,
            group = SettingsPageGroup.INTERACTION,
        ),
        SettingsPageEntry(
            label = "Contextual",
            subtitle = "Dynamic launcher behaviour",
            page = SettingsPage.CONTEXTUAL,
            group = SettingsPageGroup.INTERACTION,
            searchAliases = listOf("dynamic", "model", "actions"),
        ),
        SettingsPageEntry(
            label = "Motion",
            subtitle = "Reduced motion",
            page = SettingsPage.MOTION,
            group = SettingsPageGroup.INTERACTION,
        ),
        SettingsPageEntry(
            label = "Haptics",
            subtitle = "Feedback strength",
            page = SettingsPage.HAPTICS,
            group = SettingsPageGroup.INTERACTION,
        ),
    )

private fun appSettingsPageEntries(status: SettingsOverviewStatus): List<SettingsPageEntry> =
    listOf(
        SettingsPageEntry(
            label = "App drawer",
            subtitle = "Refresh installed apps",
            page = SettingsPage.APPS,
            group = SettingsPageGroup.APPS,
        ),
        SettingsPageEntry(
            label = "Hidden apps",
            subtitle = status.hiddenAppsSummary(),
            page = SettingsPage.HIDDEN_APPS,
            group = SettingsPageGroup.APPS,
        ),
    )

private fun systemSettingsPageEntries(status: SettingsOverviewStatus): List<SettingsPageEntry> =
    listOf(
        SettingsPageEntry(
            label = "Permissions",
            subtitle = status.permissionsSummary(),
            page = SettingsPage.PERMISSIONS,
            group = SettingsPageGroup.SYSTEM,
            searchAliases = listOf("default home", "home app"),
        ),
        SettingsPageEntry(
            label = "Backup",
            subtitle = "Import and export launcher data",
            page = SettingsPage.BACKUP,
            group = SettingsPageGroup.SYSTEM,
            searchAliases = listOf("restore"),
        ),
        SettingsPageEntry(
            label = "About",
            subtitle = "Version and build information",
            page = SettingsPage.VERSION,
            group = SettingsPageGroup.SYSTEM,
        ),
    )

private fun SettingsOverviewStatus.hiddenAppsSummary(): String =
    when (hiddenAppCount) {
        0 -> "No hidden apps"
        1 -> "1 hidden app"
        else -> "$hiddenAppCount hidden apps"
    }

private fun SettingsOverviewStatus.permissionsSummary(): String =
    listOf(
        homeRoleStatus.settingsOverviewLabel(),
        "notifications ${notificationAccessStatus.settingsNotificationAccessLabel().lowercase()}",
        "overlay ${overlayDockPermissionStatus.settingsOverlayDockPermissionLabel().lowercase()}",
    ).joinToString(separator = ", ")

private fun HomeRoleStatus.settingsOverviewLabel(): String =
    when (this) {
        HomeRoleStatus.DEFAULT_HOME -> "Home set"
        HomeRoleStatus.NOT_DEFAULT_HOME -> "Home not set"
        HomeRoleStatus.UNKNOWN -> "Home unknown"
    }
