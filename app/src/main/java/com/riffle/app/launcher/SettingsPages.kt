package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass

internal enum class SettingsPage(
    val title: String,
) {
    MAIN("Settings"),
    LAYOUT("Layout"),
    DOCK("Dock"),
    APPEARANCE("Appearance"),
    FLOATING_DOCK("Floating dock"),
    GESTURES("Gestures"),
    HAPTICS("Haptics"),
    PERMISSIONS("Permissions"),
    APPS("App drawer"),
    BACKUP("Backup"),
    HIDDEN_APPS("Hidden apps"),
    VERSION("About"),
}

internal enum class SettingsPageGroup(
    val title: String,
) {
    HOME("Home screen"),
    INTERACTION("Interaction"),
    APPS("Apps"),
    SYSTEM("System"),
}

internal data class SettingsPageEntry(
    val label: String,
    val subtitle: String,
    val page: SettingsPage,
    val group: SettingsPageGroup,
)

internal fun settingsMainPageEntries(): List<SettingsPageEntry> =
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
            subtitle = "Wallpaper source",
            page = SettingsPage.APPEARANCE,
            group = SettingsPageGroup.HOME,
        ),
        SettingsPageEntry(
            label = "Floating dock",
            subtitle = "Overlay handle and floating shortcuts",
            page = SettingsPage.FLOATING_DOCK,
            group = SettingsPageGroup.HOME,
        ),
        SettingsPageEntry(
            label = "Gestures",
            subtitle = "Home swipe actions",
            page = SettingsPage.GESTURES,
            group = SettingsPageGroup.INTERACTION,
        ),
        SettingsPageEntry(
            label = "Haptics",
            subtitle = "Feedback strength",
            page = SettingsPage.HAPTICS,
            group = SettingsPageGroup.INTERACTION,
        ),
        SettingsPageEntry(
            label = "App drawer",
            subtitle = "Refresh installed apps",
            page = SettingsPage.APPS,
            group = SettingsPageGroup.APPS,
        ),
        SettingsPageEntry(
            label = "Hidden apps",
            subtitle = "Apps removed from drawer and search",
            page = SettingsPage.HIDDEN_APPS,
            group = SettingsPageGroup.APPS,
        ),
        SettingsPageEntry(
            label = "Permissions",
            subtitle = "Home, notifications, and overlays",
            page = SettingsPage.PERMISSIONS,
            group = SettingsPageGroup.SYSTEM,
        ),
        SettingsPageEntry(
            label = "Backup",
            subtitle = "Import and export launcher data",
            page = SettingsPage.BACKUP,
            group = SettingsPageGroup.SYSTEM,
        ),
        SettingsPageEntry(
            label = "About",
            subtitle = "Version and build information",
            page = SettingsPage.VERSION,
            group = SettingsPageGroup.SYSTEM,
        ),
    )

internal fun settingsMainPageGroups(): List<SettingsPageGroup> =
    listOf(
        SettingsPageGroup.HOME,
        SettingsPageGroup.INTERACTION,
        SettingsPageGroup.APPS,
        SettingsPageGroup.SYSTEM,
    )

internal fun settingsLayoutPageTabs(availableDeviceClasses: Set<HomeLayoutDeviceClass>): List<SettingsLayoutDeviceTab> =
    settingsLayoutDeviceTabs(availableDeviceClasses)
