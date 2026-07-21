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
                    "notification access",
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
            subtitle = "Wallpaper and system bars",
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
                    "fullscreen",
                    "system UI",
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
            label = "Contextual",
            subtitle = "Dynamic launcher behaviour",
            page = SettingsPage.CONTEXTUAL,
            group = SettingsPageGroup.INTERACTION,
            searchAliases = listOf("contextual behaviour", "dynamic", "model", "actions"),
        ),
        SettingsPageEntry(
            label = "Motion",
            subtitle = "Reduced motion",
            page = SettingsPage.MOTION,
            group = SettingsPageGroup.INTERACTION,
            searchAliases = listOf("animations", "settle animations", "minimise motion"),
        ),
        SettingsPageEntry(
            label = "Haptics",
            subtitle = "Feedback strength",
            page = SettingsPage.HAPTICS,
            group = SettingsPageGroup.INTERACTION,
            searchAliases =
                listOf(
                    "feedback strength",
                    "haptic strength",
                    "vibration",
                    "vibrate",
                ),
        ),
    )

private fun appSettingsPageEntries(status: SettingsOverviewStatus): List<SettingsPageEntry> =
    listOf(
        SettingsPageEntry(
            label = "App drawer",
            subtitle = "Apps and search results",
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
                ),
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
            subtitle = status.homeRoleStatus.settingsOverviewLabel(),
            page = SettingsPage.PERMISSIONS,
            group = SettingsPageGroup.SYSTEM,
            searchAliases =
                listOf(
                    "default home",
                    "home app",
                ),
        ),
        SettingsPageEntry(
            label = "Backup",
            subtitle = "Import and export launcher data",
            page = SettingsPage.BACKUP,
            group = SettingsPageGroup.SYSTEM,
            searchAliases = listOf("launcher backup", "restore", "import", "export"),
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

private fun HomeRoleStatus.settingsOverviewLabel(): String =
    when (this) {
        HomeRoleStatus.DEFAULT_HOME -> "Home set"
        HomeRoleStatus.NOT_DEFAULT_HOME -> "Home not set"
        HomeRoleStatus.UNKNOWN -> "Home unknown"
    }
