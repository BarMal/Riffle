package com.riffle.app.launcher

import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

internal fun settingsMainPageEntries(status: SettingsOverviewStatus = SettingsOverviewStatus()) =
    homeSettingsPageEntries() +
        interactionSettingsPageEntries() +
        appSettingsPageEntries() +
        systemSettingsPageEntries(status)

@Suppress("LongMethod")
private fun homeSettingsPageEntries(): List<SettingsPageEntry> =
    listOf(
        SettingsPageEntry(
            label = "Layout",
            subtitle = "Home mode, grid, pages, and labels",
            page = SettingsPage.LAYOUT,
            group = SettingsPageGroup.HOME,
            searchAliases =
                listOf(
                    "home layout",
                    "home mode",
                    "grid columns",
                    "grid rows",
                    "show labels",
                    "label background",
                    "label text size",
                    "label width",
                    "label sizing",
                    "label lines",
                ),
        ),
        SettingsPageEntry(
            label = "Dock",
            subtitle = "Home dock visibility, expanded cards, slots, and sizing",
            page = SettingsPage.DOCK,
            group = SettingsPageGroup.HOME,
            searchAliases =
                listOf(
                    "show dock",
                    "notification cards",
                    "dock cards",
                    "expanded dock cards",
                    "notification shelf",
                    "dock slots",
                    "dock icon size",
                    "dock background",
                    "dock background size",
                    "dock item spacing",
                ),
        ),
        SettingsPageEntry(
            label = "Appearance",
            subtitle = "Theme, wallpaper, and system bars",
            page = SettingsPage.APPEARANCE,
            group = SettingsPageGroup.HOME,
            searchAliases =
                listOf(
                    "change wallpaper",
                    "wallpaper picker",
                    "wallpaper scroll",
                    "theme colours",
                    "color picker",
                    "status bar",
                    "navigation bar",
                    "system UI",
                ),
        ),
        SettingsPageEntry(
            label = "TimeScape appearance",
            subtitle = "Cards, glass, colour, motion, and accessibility",
            page = SettingsPage.TIMESCAPE_APPEARANCE,
            group = SettingsPageGroup.HOME,
            searchAliases =
                listOf(
                    "timescape",
                    "card geometry",
                    "card stack",
                    "glass",
                    "blur",
                    "card colour",
                    "card color",
                    "card typography",
                    "card motion",
                    "reduced transparency",
                    "timescape preset",
                ),
        ),
        SettingsPageEntry(
            label = "Floating dock",
            subtitle = "Overlay handle and floating shortcuts",
            page = SettingsPage.FLOATING_DOCK,
            group = SettingsPageGroup.HOME,
            searchAliases =
                listOf(
                    "overlay dock",
                    "overlay permission",
                    "floating shortcuts",
                    "handle edge",
                    "handle thickness",
                    "handle height",
                    "handle offset",
                    "handle opacity",
                    "expanded icon size",
                    "expanded orientation",
                    "floating dock labels",
                ),
        ),
    )

private fun interactionSettingsPageEntries(): List<SettingsPageEntry> =
    listOf(
        SettingsPageEntry(
            label = "Gestures",
            subtitle = "Home swipe actions",
            page = SettingsPage.GESTURES,
            group = SettingsPageGroup.INTERACTION,
            searchAliases =
                listOf(
                    "swipe up",
                    "swipe down",
                    "double tap",
                    "open search",
                    "open settings",
                    "page overview",
                ),
        ),
        SettingsPageEntry(
            label = "Contextual behaviour",
            subtitle = "Dynamic launcher behaviour",
            page = SettingsPage.CONTEXTUAL,
            group = SettingsPageGroup.INTERACTION,
            searchAliases = listOf("contextual behaviour", "dynamic", "model", "actions"),
        ),
        SettingsPageEntry(
            label = "Motion",
            subtitle = "Animation performance, reduced motion, and haptics",
            page = SettingsPage.MOTION,
            group = SettingsPageGroup.INTERACTION,
            searchAliases = listOf("animations", "settle animations", "minimise motion", "haptics", "vibration"),
        ),
    )

private fun appSettingsPageEntries(): List<SettingsPageEntry> =
    listOf(
        SettingsPageEntry(
            label = "App drawer",
            subtitle = "Apps, search results, and hidden apps",
            page = SettingsPage.APPS,
            group = SettingsPageGroup.APPS,
            searchAliases =
                listOf(
                    "launchable apps",
                    "installed apps",
                    "refresh apps",
                    "refetch apps",
                    "search result layout",
                    "search icons",
                    "search list",
                    "app drawer",
                    "app list",
                    "app icons",
                    "app grid",
                    "app grid columns",
                ),
        ),
    )

private fun systemSettingsPageEntries(status: SettingsOverviewStatus): List<SettingsPageEntry> =
    listOf(
        SettingsPageEntry(
            label = "Permissions",
            subtitle = status.permissionsSummary(),
            page = SettingsPage.PERMISSIONS,
            group = SettingsPageGroup.SYSTEM,
            searchAliases =
                listOf(
                    "default home",
                    "home app",
                    "notifications",
                    "notification access",
                    "overlay access",
                    "floating dock permission",
                ),
        ),
        SettingsPageEntry(
            label = "Backup",
            subtitle = "Import and export launcher data",
            page = SettingsPage.BACKUP,
            group = SettingsPageGroup.SYSTEM,
            searchAliases = listOf("system", "launcher backup", "restore", "import", "export"),
        ),
        SettingsPageEntry(
            label = "About",
            subtitle = "Version and build information",
            page = SettingsPage.VERSION,
            group = SettingsPageGroup.SYSTEM,
        ),
    )

private fun SettingsOverviewStatus.permissionsSummary(): String =
    if (
        notificationAccessStatus == NotificationAccessStatus.GRANTED &&
        overlayDockPermissionStatus == OverlayDockPermissionStatus.GRANTED
    ) {
        homeRoleStatus.settingsOverviewLabel()
    } else {
        listOf(
            homeRoleStatus.settingsOverviewLabel(),
            notificationAccessStatus.settingsOverviewLabel("Notifications"),
            overlayDockPermissionStatus.settingsOverviewLabel("Floating dock"),
        ).joinToString(separator = " · ")
    }

private fun NotificationAccessStatus.settingsOverviewLabel(feature: String): String =
    when (this) {
        NotificationAccessStatus.GRANTED -> "$feature allowed"
        NotificationAccessStatus.NOT_GRANTED -> "$feature not allowed"
        NotificationAccessStatus.REVOKED -> "$feature revoked"
        NotificationAccessStatus.UNKNOWN -> "$feature checking"
    }

private fun OverlayDockPermissionStatus.settingsOverviewLabel(feature: String): String =
    when (this) {
        OverlayDockPermissionStatus.GRANTED -> "$feature allowed"
        OverlayDockPermissionStatus.NOT_GRANTED -> "$feature not allowed"
        OverlayDockPermissionStatus.UNKNOWN -> "$feature checking"
    }

private fun HomeRoleStatus.settingsOverviewLabel(): String =
    when (this) {
        HomeRoleStatus.DEFAULT_HOME -> "Home set"
        HomeRoleStatus.NOT_DEFAULT_HOME -> "Home not set"
        HomeRoleStatus.UNKNOWN -> "Home unknown"
    }
