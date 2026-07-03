package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass

internal enum class SettingsPage(
    val title: String,
) {
    MAIN("Settings"),
    LAYOUT("Layout"),
    APPEARANCE("Appearance"),
    FLOATING_DOCK("Floating dock"),
    GESTURES("Gestures"),
    HAPTICS("Haptics"),
    PERMISSIONS("Permissions"),
    APPS("Apps"),
    BACKUP("Backup"),
    HIDDEN_APPS("Hidden apps"),
    VERSION("Version"),
}

internal data class SettingsPageEntry(
    val label: String,
    val page: SettingsPage,
)

internal fun settingsMainPageEntries(): List<SettingsPageEntry> =
    listOf(
        SettingsPageEntry(label = "Layout", page = SettingsPage.LAYOUT),
        SettingsPageEntry(label = "Appearance", page = SettingsPage.APPEARANCE),
        SettingsPageEntry(label = "Floating dock", page = SettingsPage.FLOATING_DOCK),
        SettingsPageEntry(label = "Gestures", page = SettingsPage.GESTURES),
        SettingsPageEntry(label = "Haptics", page = SettingsPage.HAPTICS),
        SettingsPageEntry(label = "Permissions", page = SettingsPage.PERMISSIONS),
        SettingsPageEntry(label = "Apps", page = SettingsPage.APPS),
        SettingsPageEntry(label = "Backup", page = SettingsPage.BACKUP),
        SettingsPageEntry(label = "Hidden apps", page = SettingsPage.HIDDEN_APPS),
        SettingsPageEntry(label = "Version", page = SettingsPage.VERSION),
    )

internal fun settingsLayoutPageTabs(availableDeviceClasses: Set<HomeLayoutDeviceClass>): List<SettingsLayoutDeviceTab> =
    settingsLayoutDeviceTabs(availableDeviceClasses)
        .takeIf { tabs -> tabs.size > 1 }
        .orEmpty()
